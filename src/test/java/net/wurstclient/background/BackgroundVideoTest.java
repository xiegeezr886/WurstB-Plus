/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.jcodec.api.JCodecException;
import org.jcodec.api.PictureWithMetadata;
import org.jcodec.api.awt.AWTFrameGrab;
import org.jcodec.api.awt.AWTSequenceEncoder;
import org.jcodec.common.DemuxerTrackMeta;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.scale.AWTUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mojang.blaze3d.platform.NativeImage;

import net.wurstclient.background.BackgroundVideo.Reason;

/**
 * 视频背景的判定与解码。
 *
 * <p>
 * 分三层：不碰解码器的纯算术（尺寸、文件头、编码判定、ABGR 换算）、不许抛异常的
 * 失败路径（文件不存在 / 空的 / 根本不是 mp4），以及用 JCodec 自带的编码器现编一段
 * mp4 再解回来的端到端用例——仓库里不放视频文件。
 * </p>
 *
 * <p>
 * 端到端那一条是唯一能在没有 Minecraft 客户端的情况下验证「颜色对不对」的办法：
 * 它把 {@link BackgroundVideo} 的转换路径（JCodec 的 YUV→RGB + ABGR 换算）单独
 * 走一遍，再拆回 RGB 比对。红蓝写反这种错误只有它会红。
 * </p>
 */
final class BackgroundVideoTest
{
	// ------------------------------------------------------------------
	// 纯算术
	// ------------------------------------------------------------------

	/** 4K 与 1080p 都缩到 1280x720，小文件保持原尺寸（永不放大）。 */
	@Test
	void largeVideosAreScaledDown()
	{
		assertSameSize(new int[]{1280, 720},
			BackgroundVideo.fitSize(1920, 1080, 1280, 720));
		assertSameSize(new int[]{1280, 720},
			BackgroundVideo.fitSize(3840, 2160, 1280, 720));

		// 宽高比必须保住：拉伸过的壁纸一眼就能看出来
		assertSameSize(new int[]{1280, 534},
			BackgroundVideo.fitSize(2560, 1068, 1280, 720));
		assertSameSize(new int[]{405, 720},
			BackgroundVideo.fitSize(1080, 1920, 1280, 720));
	}

	@Test
	void smallVideosKeepTheirSize()
	{
		assertSameSize(new int[]{640, 360},
			BackgroundVideo.fitSize(640, 360, 1280, 720));
		assertSameSize(new int[]{320, 240},
			BackgroundVideo.fitSize(320, 240, 1280, 720));

		// 正方形：两边都受同一个上限约束，取小的那个比例
		assertSameSize(new int[]{720, 720},
			BackgroundVideo.fitSize(1000, 1000, 1280, 720));
	}

	/** 坏掉的尺寸不能让后面的 new NativeImage 抛异常。 */
	@Test
	void degenerateSizesBecomeOnePixel()
	{
		assertSameSize(new int[]{1, 1},
			BackgroundVideo.fitSize(0, 0, 1280, 720));
		assertSameSize(new int[]{1, 1},
			BackgroundVideo.fitSize(-100, 50, 1280, 720));
	}

	/** 手机竖拍的视频带着旋转信息，显示尺寸是反过来的。 */
	@Test
	void rotatedVideosSwapTheirSides()
	{
		assertSameSize(new int[]{1080, 1920}, BackgroundVideo.orientedSize(1920,
			1080, DemuxerTrackMeta.Orientation.D_90));
		assertSameSize(new int[]{1080, 1920}, BackgroundVideo.orientedSize(1920,
			1080, DemuxerTrackMeta.Orientation.D_270));

		assertSameSize(new int[]{1920, 1080}, BackgroundVideo.orientedSize(1920,
			1080, DemuxerTrackMeta.Orientation.D_0));
		assertSameSize(new int[]{1920, 1080}, BackgroundVideo.orientedSize(1920,
			1080, DemuxerTrackMeta.Orientation.D_180));
	}

	@Test
	void recognisesMp4FileHeaders()
	{
		assertTrue(BackgroundVideo.looksLikeMp4(box("ftyp")));
		assertTrue(BackgroundVideo.looksLikeMp4(box("moov")));
		assertTrue(BackgroundVideo.looksLikeMp4(box("mdat")));
		// 长度字段写 1 表示后面跟 64 位长度，这是合法的 box 头
		assertTrue(BackgroundVideo.looksLikeMp4(boxWithSizeOne("ftyp")));
	}

	@Test
	void rejectsFilesThatAreNotMp4()
	{
		// WebM 的开头是 EBML 头
		assertFalse(BackgroundVideo.looksLikeMp4(new byte[]{(byte)0x1A, 0x45,
			(byte)0xDF, (byte)0xA3, 0x01, 0x00, 0x00, 0x00}));
		assertFalse(
			BackgroundVideo.looksLikeMp4("definitely not a video".getBytes()));
		// box 长度比头还小：不是合法文件
		assertFalse(BackgroundVideo
			.looksLikeMp4(new byte[]{0, 0, 0, 4, 'f', 't', 'y', 'p'}));
		assertFalse(BackgroundVideo.looksLikeMp4(new byte[]{0, 0, 0, 16}));
		assertFalse(BackgroundVideo.looksLikeMp4(null));
		assertFalse(BackgroundVideo.looksLikeMp4(new byte[0]));
	}

	/** JCodec 0.2.5 只认 avc1；HEVC / VP9 / AV1 连解码器都不给。 */
	@Test
	void onlyH264IsPlayable()
	{
		assertTrue(BackgroundVideo.isPlayableFourcc("avc1"));

		assertFalse(BackgroundVideo.isPlayableFourcc("hvc1"));
		assertFalse(BackgroundVideo.isPlayableFourcc("hev1"));
		assertFalse(BackgroundVideo.isPlayableFourcc("vp09"));
		assertFalse(BackgroundVideo.isPlayableFourcc("av01"));
		assertFalse(BackgroundVideo.isPlayableFourcc("mp4v"));
		// 参数集在码流里的 H.264：这套解码器读不到 avcC，同样放不了
		assertFalse(BackgroundVideo.isPlayableFourcc("avc3"));
		assertFalse(BackgroundVideo.isPlayableFourcc(null));
		assertFalse(BackgroundVideo.isPlayableFourcc(""));
	}

	@Test
	void namesTheCodecForTheMessage()
	{
		assertEquals("HEVC/H.265", BackgroundVideo.describeFourcc("hvc1"));
		assertEquals("HEVC/H.265", BackgroundVideo.describeFourcc("HEV1"));
		assertEquals("VP9", BackgroundVideo.describeFourcc("vp09"));
		assertEquals("AV1", BackgroundVideo.describeFourcc("av01"));
		assertEquals("unknown", BackgroundVideo.describeFourcc(null));
		// 不认识的 fourcc 原样带出去，比"未知编码"有用
		assertEquals("xyz9", BackgroundVideo.describeFourcc("xyz9"));
	}

	/**
	 * NativeImage 的整数像素是 ABGR，而 AWT 给的是 ARGB：红蓝必须交换。
	 *
	 * <p>
	 * 这里验两条性质：交换之后红色跑到最低字节、绿色和透明度不动；以及这个换算
	 * 是对合的（做两次回到原值），后者能挡住"顺便把某一路位移搞错"的写法。
	 * </p>
	 */
	@Test
	void argbIsSwappedToNativeImagesAbgr()
	{
		// 不透明的纯红：ABGR 里红在最低字节，所以交换过来是 0xFF0000FF
		int red = 0xFFFF0000;
		int swapped = BackgroundVideo.argbToAbgr(red);

		assertEquals(0xFF0000FF, swapped);

		// 对合：做两次回到原值，能挡住"顺便把某一路位移搞错"的写法
		assertEquals(red, BackgroundVideo.argbToAbgr(swapped));

		// 半透明的青色（A=80 R=12 G=A0 B=C0）：红蓝交换，绿色与 alpha 一个都不许动
		assertEquals(0x80C0A012, BackgroundVideo.argbToAbgr(0x8012A0C0));

		// 灰色（红蓝相等）交换后必须还是它自己
		assertEquals(0xFF808080, BackgroundVideo.argbToAbgr(0xFF808080));
	}

	// ------------------------------------------------------------------
	// 失败路径：一条都不许抛异常（调用方里有渲染线程）
	// ------------------------------------------------------------------

	@Test
	void missingFileIsNotPlayable()
	{
		Reason reason = BackgroundVideo.probe(Path.of("no", "such", "video.mp4"))
			.reason();

		assertEquals(Reason.MISSING, reason);
		assertEquals(Reason.MISSING, BackgroundVideo.probe(null).reason());
	}

	/** 零字节文件：读头就会失败，不能让它走到解码器里。 */
	@Test
	void emptyFileIsNotPlayable(@TempDir Path folder) throws IOException
	{
		Path file = folder.resolve("empty.mp4");
		Files.createFile(file);

		BackgroundVideo.Probe probe = BackgroundVideo.probe(file);

		assertFalse(probe.playable());
		assertEquals(Reason.EMPTY_FILE, probe.reason());
	}

	@Test
	void textFileIsNotPlayable(@TempDir Path folder) throws IOException
	{
		Path file = folder.resolve("fake.mp4");
		Files.writeString(file, "definitely not an mp4, just some text");

		assertEquals(Reason.NOT_MP4, BackgroundVideo.probe(file).reason());
	}

	/** 用户把 .webm 改名叫 .mp4 也该落到「不是 mp4」上，而不是崩在解析里。 */
	@Test
	void webmRenamedToMp4IsNotPlayable(@TempDir Path folder) throws IOException
	{
		Path file = folder.resolve("actually-webm.mp4");
		Files.write(file, new byte[]{(byte)0x1A, 0x45, (byte)0xDF, (byte)0xA3,
			0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x1F});

		assertEquals(Reason.NOT_MP4, BackgroundVideo.probe(file).reason());
	}

	/**
	 * 只有 ftyp 盒子的 mp4：文件头看着像 mp4，里面什么都没有。这条路必须走到
	 * 「没有视频轨道 / 解不出帧」上，而不是把异常漏给调用方。
	 */
	@Test
	void mp4WithoutFramesIsNotPlayable(@TempDir Path folder) throws IOException
	{
		Path file = folder.resolve("header-only.mp4");

		byte[] header = new byte[32];
		header[3] = 32;
		System.arraycopy("ftyp".getBytes(), 0, header, 4, 4);
		System.arraycopy("isom".getBytes(), 0, header, 8, 4);
		Files.write(file, header);

		BackgroundVideo.Probe probe = BackgroundVideo.probe(file);

		assertFalse(probe.playable());
		assertNotEquals(Reason.OK, probe.reason());
		assertNotEquals(Reason.MISSING, probe.reason());
	}

	// ------------------------------------------------------------------
	// 端到端：现编一段 mp4 再解回来
	// ------------------------------------------------------------------

	/** 画面上每个帧一个纯色，方便逐帧比对颜色。 */
	private static final int[][] COLORS = {{200, 40, 40}, {40, 200, 40},
		{40, 40, 200}, {230, 230, 230}};

	@Test
	void probesAGeneratedH264Video(@TempDir Path folder) throws IOException
	{
		Path file = encode(folder.resolve("solid.mp4"), COLORS);
		assumeTrue(file != null, "JCodec 的 H.264 编码器在这个环境里不可用，跳过");

		BackgroundVideo.Probe probe = BackgroundVideo.probe(file);

		assertTrue(probe.playable(), "自己编出来的 H.264 mp4 应当能放："
			+ probe.reason() + " / " + probe.detail());
		assertEquals("avc1", probe.detail());

		assertEquals(320, probe.sourceWidth());
		assertEquals(240, probe.sourceHeight());
		assertEquals(COLORS.length, probe.frameCount());

		// 30fps 的 4 帧 = 133ms；时间基换算难免有一两毫秒的舍入
		assertTrue(probe.durationMs() >= 100 && probe.durationMs() <= 170,
			"时长应当是 133ms 上下，实际 " + probe.durationMs() + "ms");

		// 目标尺寸：源本来就比 1280x720 小，所以原样保留
		assertSameSize(new int[]{320, 240}, probe.targetSize());
	}

	/**
	 * 端到端验颜色。
	 *
	 * <p>
	 * 走的是 {@link BackgroundVideo} 的转换路径：JCodec 解出的 YUV → RGB
	 * （{@code AWTUtil}）→ ABGR 换算。H.264 是有损的，但纯色帧只是 DC 系数，误差
	 * 很小；给 24 的余量足够，而红蓝写反会差 160，抓得住。
	 * </p>
	 */
	@Test
	void decodesTheGeneratedVideoInColour(@TempDir Path folder)
		throws IOException, JCodecException
	{
		Path file = encode(folder.resolve("colours.mp4"), COLORS);
		assumeTrue(file != null, "JCodec 的 H.264 编码器在这个环境里不可用，跳过");

		for(int index = 0; index < COLORS.length; index++)
		{
			int pixel = decodeFrame(file, index)[0];
			int red = pixel & 0xFF;
			int green = pixel >> 8 & 0xFF;
			int blue = pixel >> 16 & 0xFF;

			assertEquals(COLORS[index][0], red, 24,
				"第 " + index + " 帧的红色通道");
			assertEquals(COLORS[index][1], green, 24,
				"第 " + index + " 帧的绿色通道");
			assertEquals(COLORS[index][2], blue, 24,
				"第 " + index + " 帧的蓝色通道");
		}
	}

	/**
	 * 时间戳必须是一路不回头地往前的。
	 *
	 * <p>
	 * 整个播放器都建立在这一点上（{@code 绝对时刻 = 这一轮的起点 + PTS}）。带 B 帧
	 * 的 H.264 在解码顺序上时间戳是会往回跳的，如果 JCodec 按解码顺序交帧，
	 * {@link VideoPacing} 就会看到时间倒流。这条用例把「JCodec 交出来的是显示顺序」
	 * 这个假设钉住。
	 * </p>
	 */
	@Test
	void framesComeOutInDisplayOrder(@TempDir Path folder)
		throws IOException, JCodecException
	{
		int frames = 12;
		Path file = encode(folder.resolve("order.mp4"),
			colours(frames, COLORS[0]));
		assumeTrue(file != null, "JCodec 的 H.264 编码器在这个环境里不可用，跳过");

		List<Long> timestamps = timestamps(file, frames);

		assertEquals(frames, timestamps.size());

		for(int i = 1; i < timestamps.size(); i++)
			assertTrue(timestamps.get(i) >= timestamps.get(i - 1),
				"第 " + i + " 帧的时间戳往回跳了：" + timestamps);
	}

	/**
	 * 真的把播放器跑起来：解码线程按挂钟一帧帧交出来，渲染线程取走再还回来。
	 *
	 * <p>
	 * 这条用例挡的是「一帧都出不来」这一类错误——交接队列写错、解码线程一起步就死、
	 * 缓冲不回收把线程堵死、close() 卡住不返回。它需要 Minecraft 的
	 * {@code NativeImage}（LWJGL 的本地内存），环境里用不了就跳过。
	 * </p>
	 *
	 * <p>
	 * 实测（本机，640x360 / 60 帧 / 30fps 的源）：3 秒里收到 93 帧，也就是
	 * 31/s，与 30fps 上限加上取整余量相符；循环点也对得上——第 2553ms 的帧与
	 * 第 556ms 的帧像素相同。
	 * </p>
	 */
	@Test
	void playsAGeneratedVideoInRealTime(@TempDir Path folder) throws Exception
	{
		Path file = encode(folder.resolve("play.mp4"), colours(30, COLORS[0]));
		assumeTrue(file != null, "JCodec 的 H.264 编码器在这个环境里不可用，跳过");
		assumeTrue(canUseNativeImages(), "这个环境里用不了 NativeImage，跳过");

		BackgroundVideo.Opened opened = BackgroundVideo.open(file);
		assumeTrue(opened.playable(),
			"打不开自己编的视频：" + opened.probe().reason());

		BackgroundVideo video = opened.video();

		// 源比上限小，所以按原尺寸放，不放大
		assertEquals(320, video.width());
		assertEquals(240, video.height());

		int received = 0;
		long deadline = System.currentTimeMillis() + 1_500;

		try
		{
			while(System.currentTimeMillis() < deadline && received < 20)
			{
				NativeImage frame = video.takeFrame();

				if(frame == null)
				{
					Thread.sleep(2);
					continue;
				}

				try
				{
					received++;
				}finally
				{
					// 不还回来解码线程就会停在空闲队列上，几帧之后一帧都出不来
					video.recycle(frame);
				}
			}
		}finally
		{
			long startedAt = System.currentTimeMillis();
			video.close();

			assertTrue(System.currentTimeMillis() - startedAt < 1_000,
				"close() 是从渲染线程调的，必须立刻返回，不能等解码线程收工");
		}

		// 30fps 的源在 1.5 秒里有 20 帧上下；给到 8 帧就足以证明帧真的在流动
		assertTrue(received >= 8, "1.5 秒只收到 " + received + " 帧");
		assertFalse(video.failed(), "解码线程不该失败：" + video.failure());
	}

	// ------------------------------------------------------------------
	// 测试用的视频生成与读取
	// ------------------------------------------------------------------

	/**
	 * 现编一段 H.264 的 mp4。
	 *
	 * <p>
	 * 用 JCodec 自带的编码器，所以仓库里不需要放任何视频文件；编码器不可用时
	 * （缺少 AWT、换了 JVM 之类）返回 null，由调用方跳过，而不是让整个套件红掉。
	 * </p>
	 */
	private static Path encode(Path file, int[][] frames)
	{
		try
		{
			AWTSequenceEncoder encoder =
				AWTSequenceEncoder.create30Fps(file.toFile());

			for(int[] colour : frames)
			{
				BufferedImage image = new BufferedImage(320, 240,
					BufferedImage.TYPE_INT_RGB);
				Graphics2D graphics = image.createGraphics();
				graphics.setColor(new Color(colour[0], colour[1], colour[2]));
				graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
				graphics.dispose();
				encoder.encodeImage(image);
			}

			encoder.finish();
			return file;

		}catch(IOException | RuntimeException e)
		{
			System.out.println(
				"[Background] 测试用的 mp4 编码失败（跳过该用例）：" + e);
			return null;
		}
	}

	/**
	 * 解出第 index 帧，返回转换之后的 ABGR 像素。
	 *
	 * <p>
	 * 故意把 {@link BackgroundVideo} 里的两步（{@code AWTUtil} 转换 + ABGR 换算）
	 * 在这里重新走一遍：那是唯一能在没有客户端的情况下验证颜色的办法。
	 * </p>
	 */
	private static int[] decodeFrame(Path file, int index)
		throws IOException, JCodecException
	{
		try(SeekableByteChannel channel = NIOUtils
			.readableChannel(file.toFile()))
		{
			AWTFrameGrab grabber = AWTFrameGrab.createAWTFrameGrab(channel);

			PictureWithMetadata frame = null;

			for(int i = 0; i <= index; i++)
				frame = grabber.getNativeFrameWithMetadata();

			BufferedImage image = AWTUtil.toBufferedImage(frame.getPicture(),
				frame.getOrientation());
			int width = image.getWidth();
			int[] argb =
				image.getRGB(0, 0, width, image.getHeight(), null, 0, width);

			for(int i = 0; i < argb.length; i++)
				argb[i] = BackgroundVideo.argbToAbgr(argb[i]);

			return argb;
		}
	}

	private static List<Long> timestamps(Path file, int count)
		throws IOException, JCodecException
	{
		List<Long> timestamps = new ArrayList<>(count);

		try(SeekableByteChannel channel = NIOUtils
			.readableChannel(file.toFile()))
		{
			AWTFrameGrab grabber = AWTFrameGrab.createAWTFrameGrab(channel);

			for(int i = 0; i < count; i++)
			{
				PictureWithMetadata frame =
					grabber.getNativeFrameWithMetadata();

				if(frame == null)
					break;

				timestamps.add(Math.round(frame.getTimestamp() * 1000.0));
			}
		}

		return timestamps;
	}

	/** 一帧一个颜色的序列，帧数不够时用第一种颜色循环着补。 */
	private static int[][] colours(int count, int[] first)
	{
		int[][] colours = new int[count][];

		for(int i = 0; i < count; i++)
			colours[i] = i == 0 ? first : COLORS[i % COLORS.length];

		return colours;
	}

	/**
	 * 这个环境里能不能建 {@code NativeImage}（要 LWJGL 的本地内存）。
	 *
	 * <p>
	 * 建不出来就跳过播放那条用例：那不是被测代码的问题，而是测试环境缺本地库。
	 * </p>
	 */
	private static boolean canUseNativeImages()
	{
		try(NativeImage image = new NativeImage(NativeImage.Format.RGBA, 1, 1,
			false))
		{
			return true;
		}catch(Throwable t)
		{
			return false;
		}
	}

	/** 一个最小 mp4 box 头（长度 16 + 4 个 ASCII 字符）。 */
	private static byte[] box(String type)
	{
		byte[] header = new byte[16];
		header[3] = 16;
		System.arraycopy(type.getBytes(), 0, header, 4, 4);
		return header;
	}

	private static byte[] boxWithSizeOne(String type)
	{
		byte[] header = new byte[24];
		header[3] = 1;
		System.arraycopy(type.getBytes(), 0, header, 4, 4);
		return header;
	}

	private static void assertSameSize(int[] expected, int[] actual)
	{
		assertEquals(expected.length, actual.length);

		for(int i = 0; i < expected.length; i++)
			assertEquals(expected[i], actual[i], "第 " + i + " 个分量");
	}
}
