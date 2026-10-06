/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * MP4 盒子扫描。
 *
 * <p>
 * 全部用现搭的盒子结构验，不依赖仓库里的素材：这条路要能在文件坏掉、只有音轨、
 * 长度字段写成 64 位这些情况下安静地给出「读不通」，而不是抛异常或者转圈。
 * </p>
 */
final class Mp4ProbeTest
{
	// ------------------------------------------------------------------
	// 正常结构
	// ------------------------------------------------------------------

	@Test
	void readsTheVideoTrack(@TempDir Path folder) throws IOException
	{
		Path file = write(folder, "h264.mp4", mp4(videoTrack("avc1", 369)));

		Mp4Probe.Result result = Mp4Probe.read(file);

		assertTrue(result.parsed());
		assertTrue(result.hasVideoTrack());
		assertEquals("avc1", result.fourcc());
		assertEquals(369, result.sampleCount());
	}

	/** 换编码只是 stsd 里那四个字节变了。 */
	@Test
	void readsOtherCodecs(@TempDir Path folder) throws IOException
	{
		assertEquals("hvc1",
			Mp4Probe.read(write(folder, "hevc.mp4",
				mp4(videoTrack("hvc1", 24)))).fourcc());

		assertEquals("av01",
			Mp4Probe.read(write(folder, "av1.mp4",
				mp4(videoTrack("av01", 30)))).fourcc());
	}

	/** 视频轨道不在第一条：音频轨在前面，扫描要跳过去继续找。 */
	@Test
	void findsTheVideoTrackAfterAnAudioTrack(@TempDir Path folder)
		throws IOException
	{
		Path file = write(folder, "with-audio.mp4",
			mp4(track("soun", "mp4a", 0), videoTrack("avc1", 12)));

		Mp4Probe.Result result = Mp4Probe.read(file);

		assertTrue(result.hasVideoTrack());
		assertEquals("avc1", result.fourcc());
		assertEquals(12, result.sampleCount());
	}

	// ------------------------------------------------------------------
	// 各种"放不了"
	// ------------------------------------------------------------------

	/** 只有音轨：解析得通，但没有视频轨道——这就是 NO_VIDEO_TRACK 的来源。 */
	@Test
	void audioOnlyFileHasNoVideoTrack(@TempDir Path folder) throws IOException
	{
		Path file = write(folder, "audio.mp4", mp4(track("soun", "mp4a", 0)));

		Mp4Probe.Result result = Mp4Probe.read(file);

		assertTrue(result.parsed());
		assertFalse(result.hasVideoTrack());
		assertEquals("", result.fourcc());
		assertEquals(0, result.sampleCount());
	}

	/** 连 moov 都没有（只有 ftyp 的头）：这是"读不通"，不是"没有视频轨道"。 */
	@Test
	void fileWithoutMoovIsUnparsable(@TempDir Path folder) throws IOException
	{
		Path file = write(folder, "header-only.mp4",
			box("ftyp", ascii("isom")));

		Mp4Probe.Result result = Mp4Probe.read(file);

		assertFalse(result.parsed());
		assertFalse(result.hasVideoTrack());
	}

	/** 被截断的文件：不许越界读，也不许转圈。 */
	@Test
	void truncatedFileIsUnparsable(@TempDir Path folder) throws IOException
	{
		byte[] full = mp4(videoTrack("avc1", 369));
		byte[] truncated = new byte[16];
		System.arraycopy(full, 0, truncated, 0, truncated.length);

		assertFalse(Mp4Probe.read(write(folder, "cut.mp4", truncated)).parsed());
	}

	@Test
	void missingFileIsUnparsable(@TempDir Path folder)
	{
		assertFalse(Mp4Probe.read(folder.resolve("nope.mp4")).parsed());
		assertFalse(Mp4Probe.read(null).parsed());
	}

	/** 随机字节与全零：不许抛异常，也不许读一辈子。 */
	@Test
	void garbageDoesNotHang(@TempDir Path folder) throws IOException
	{
		byte[] data = new byte[4096];
		new Random(1234).nextBytes(data);

		assertFalse(
			Mp4Probe.read(write(folder, "garbage.mp4", data)).hasVideoTrack());
		assertFalse(Mp4Probe.read(write(folder, "zeros.mp4", new byte[1024]))
			.hasVideoTrack());
	}

	// ------------------------------------------------------------------
	// 长度字段的两种写法
	// ------------------------------------------------------------------

	/** 长度字段写 1：真正的长度是后面 8 字节的 64 位整数。 */
	@Test
	void readsSixtyFourBitBoxSizes(@TempDir Path folder) throws IOException
	{
		byte[] trak =
			track("vide", "avc1", 8);
		Path file = write(folder, "large.mp4", box("ftyp", ascii("isom")),
			largeBox("moov", trak));

		Mp4Probe.Result result = Mp4Probe.read(file);

		assertTrue(result.hasVideoTrack());
		assertEquals("avc1", result.fourcc());
		assertEquals(8, result.sampleCount());
	}

	/** 长度字段写 0：一直到父盒子（顶层就是文件）末尾。 */
	@Test
	void readsZeroLengthBoxes(@TempDir Path folder) throws IOException
	{
		Path file = write(folder, "zero.mp4", box("ftyp", ascii("isom")),
			zeroBox("moov", track("vide", "hvc1", 5)));

		Mp4Probe.Result result = Mp4Probe.read(file);

		assertTrue(result.hasVideoTrack());
		assertEquals("hvc1", result.fourcc());
		assertEquals(5, result.sampleCount());
	}

	// ------------------------------------------------------------------
	// 现搭盒子
	// ------------------------------------------------------------------

	/** ftyp + moov（里面放着传进来的 trak）。 */
	private static byte[] mp4(byte[]... traks)
	{
		return concat(box("ftyp", ascii("isom")), box("moov", concat(traks)));
	}

	/** 一条完整的 trak：mdia(hdlr + minf(stbl(stsd + stsz)))。 */
	private static byte[] videoTrack(String fourcc, int sampleCount)
	{
		return track("vide", fourcc, sampleCount);
	}

	private static byte[] track(String handlerType, String fourcc,
		int sampleCount)
	{
		byte[] stbl =
			box("stbl", stsd(fourcc), stsz(sampleCount));

		return box("trak",
			box("mdia", handler(handlerType), box("minf", stbl)));
	}

	/** hdlr：version/flags + pre_defined + handler_type。 */
	private static byte[] handler(String handlerType)
	{
		return box("hdlr", new byte[8], ascii(handlerType), new byte[12]);
	}

	/** stsd：version/flags + entry_count + 一个最小样本条目（长度 + fourcc）。 */
	private static byte[] stsd(String fourcc)
	{
		return box("stsd", ints(0, 1), box(fourcc, new byte[8]));
	}

	/** stsz：version/flags + sample_size + sample_count。 */
	private static byte[] stsz(int sampleCount)
	{
		return box("stsz", ints(0, 1024, sampleCount));
	}

	// ------------------------------------------------------------------
	// 盒子读写
	// ------------------------------------------------------------------

	private static byte[] box(String type, byte[]... parts)
	{
		byte[] payload = concat(parts);
		ByteBuffer out = ByteBuffer.allocate(8 + payload.length);
		out.putInt(8 + payload.length);
		out.put(ascii(type));
		out.put(payload);
		return out.array();
	}

	/** 长度字段写 1 的盒子（后面跟 8 字节的 64 位长度）。 */
	private static byte[] largeBox(String type, byte[]... parts)
	{
		byte[] payload = concat(parts);
		ByteBuffer out = ByteBuffer.allocate(16 + payload.length);
		out.putInt(1);
		out.put(ascii(type));
		out.putLong(16L + payload.length);
		out.put(payload);
		return out.array();
	}

	/** 长度字段写 0 的盒子（一直到父范围末尾）。 */
	private static byte[] zeroBox(String type, byte[]... parts)
	{
		byte[] payload = concat(parts);
		ByteBuffer out = ByteBuffer.allocate(8 + payload.length);
		out.putInt(0);
		out.put(ascii(type));
		out.put(payload);
		return out.array();
	}

	private static byte[] ints(int... values)
	{
		ByteBuffer out = ByteBuffer.allocate(values.length * 4);

		for(int value : values)
			out.putInt(value);

		return out.array();
	}

	private static byte[] ascii(String text)
	{
		return text.getBytes(StandardCharsets.US_ASCII);
	}

	private static byte[] concat(byte[]... parts)
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		for(byte[] part : parts)
			out.writeBytes(part);

		return out.toByteArray();
	}

	private static Path write(Path folder, String name, byte[]... parts)
		throws IOException
	{
		Path file = folder.resolve(name);
		Files.write(file, concat(parts));
		return file;
	}
}
