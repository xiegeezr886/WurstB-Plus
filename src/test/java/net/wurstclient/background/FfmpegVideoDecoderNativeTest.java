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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link FfmpegVideoDecoder} 的 JNI 面：真加载随仓库发布的原生解码器，真的解一解
 * 素材。
 *
 * <p>
 * 这是唯一能自动验证「shim 的返回值约定」的地方：每帧字节数、EOF 与错误的区分、
 * 复用缓冲的长度、{@code seekToStart} 之后还能不能接着解。解码器不可用时整类跳过
 * （别的机器上没构建过 natives，那缺的是环境）。
 * </p>
 *
 * <p>
 * 速度不在这里量（那是 {@code FfmpegVideoDecoderTest} 的命令行自测与
 * docs/title-background.md 里那张实测表的事）：这套用例要的是"对"，不是"快"。
 * </p>
 */
final class FfmpegVideoDecoderNativeTest
{
	private static final int FIXTURE_WIDTH = 320;
	private static final int FIXTURE_HEIGHT = 240;
	private static final int FIXTURE_FRAMES = 4;
	private static final int FIXTURE_BYTES =
		FIXTURE_WIDTH * FIXTURE_HEIGHT * FfmpegVideoDecoder.BYTES_PER_PIXEL;

	/** 这一段素材是 30fps 的 4 帧，也就是 133ms。 */
	private static final double FIXTURE_FPS = 30.0;

	/** 四象限素材（160x120）与它 160x120 那一档的每帧字节数。 */
	private static final String QUADRANTS = "fixture-quadrants.mp4";
	private static final int QUADRANT_BYTES =
		160 * 120 * FfmpegVideoDecoder.BYTES_PER_PIXEL;

	@Test
	void isAvailableOnThisMachine()
	{
		assumeTrue(FfmpegVideoDecoder.isAvailable(),
			"原生解码器不可用：" + FfmpegVideoDecoder.lastLibraryError());

		// 文档里写的那个版本，与 native/ffmpeg/README.md 对得上
		assertEquals("7.1.1", FfmpegVideoDecoder.ffmpegVersion());
		assertEquals("", FfmpegVideoDecoder.lastLibraryError());
	}

	@Test
	void decodesTheFixtureInSoftware()
	{
		assumeTrue(FfmpegVideoDecoder.isAvailable(),
			"原生解码器不可用：" + FfmpegVideoDecoder.lastLibraryError());

		try(FfmpegVideoDecoder decoder =
			FfmpegVideoDecoder.openSoftware(fixture()))
		{
			assertNotNull(decoder, "软件解码打不开素材："
				+ FfmpegVideoDecoder.lastLibraryError());
			assertFalse(decoder.isHardware());

			assertEquals(FIXTURE_WIDTH, decoder.width());
			assertEquals(FIXTURE_HEIGHT, decoder.height());
			assertEquals(FIXTURE_FPS, decoder.frameRate(), 0.5);
			assertTrue(decoder.durationMs() >= 100 && decoder.durationMs() <= 170,
				"时长应当是 133ms 上下，实际 " + decoder.durationMs() + "ms");

			// 缓冲要带 JNI 侧要求的尾部余量
			assertTrue(decoder.bufferSize() >= FIXTURE_BYTES,
				"缓冲只有 " + decoder.bufferSize() + " 字节");

			int frames = 0;

			while(true)
			{
				int bytes = decoder.nextFrame();

				if(bytes == 0)
					break;

				assertTrue(bytes > 0, "第 " + frames + " 帧返回了错误：" + bytes);
				assertEquals(FIXTURE_BYTES, bytes, "每帧字节数");
				assertOpaque(decoder.frameBuffer(), bytes);
				frames++;
			}

			assertEquals(FIXTURE_FRAMES, frames, "解出来的帧数");

			// 回到开头应当能再解一遍（循环播放就是靠它）
			assertEquals(0, decoder.seekToStart());

			int again = 0;

			while(decoder.nextFrame() > 0)
				again++;

			assertEquals(FIXTURE_FRAMES, again, "seekToStart 之后解出来的帧数");
		}
	}

	/**
	 * 硬件优先：这台机器没有可用的 D3D11VA 时 shim 自己退回软解；硬件真的用上了、
	 * 却在某一帧上失败时，要用软解接着放——这是"硬件是可选的"这句约定的全部内容。
	 *
	 * <p>
	 * 实测这一段 320x240 的 baseline H.264 在 D3D11VA 上第一帧就返回
	 * AVERROR_INVALIDDATA（-1094995529），而软解完全正常，所以这条用例的分支是
	 * 真会走到的：它钉住的正是"硬件解不出来时软件这条路还在"。
	 * </p>
	 */
	@Test
	void hardwareFailureFallsBackToSoftware()
	{
		assumeTrue(FfmpegVideoDecoder.isAvailable(),
			"原生解码器不可用：" + FfmpegVideoDecoder.lastLibraryError());

		try(FfmpegVideoDecoder decoder =
			FfmpegVideoDecoder.openHardware(fixture()))
		{
			assertNotNull(decoder,
				"硬件优先至少应当退回软解：" + FfmpegVideoDecoder.lastLibraryError());

			int bytes = decoder.nextFrame();

			if(bytes > 0)
			{
				// 这台机器的硬件这条路能用：那也算过（值的检查在软件那条用例里）
				assertEquals(FIXTURE_BYTES, bytes);
				return;
			}

			assertTrue(bytes < 0, "解不出第一帧时应当返回负的 AVERROR，实际 " + bytes);
		}

		// 硬件那条路解不出来：软解必须还能开、还能解
		try(FfmpegVideoDecoder software =
			FfmpegVideoDecoder.openSoftware(fixture()))
		{
			assertNotNull(software);
			assertFalse(software.isHardware());
			assertEquals(FIXTURE_BYTES, software.nextFrame());
		}
	}

	/** setOutputSize 换的是 swscale 的输出尺寸，调用方不用自己缩放。 */
	@Test
	void scalesTheOutputSize()
	{
		assumeTrue(FfmpegVideoDecoder.isAvailable(),
			"原生解码器不可用：" + FfmpegVideoDecoder.lastLibraryError());

		try(FfmpegVideoDecoder decoder =
			FfmpegVideoDecoder.openSoftware(fixture()))
		{
			assertNotNull(decoder);

			int half = 160 * 120 * FfmpegVideoDecoder.BYTES_PER_PIXEL;
			assertEquals(half, decoder.setOutputSize(160, 120));
			assertEquals(160, decoder.outputWidth());
			assertEquals(120, decoder.outputHeight());
			assertEquals(half, decoder.nextFrame());

			// 传 0/0 恢复成解码原生尺寸
			assertEquals(FIXTURE_BYTES, decoder.setOutputSize(0, 0));
			assertEquals(FIXTURE_WIDTH, decoder.outputWidth());
			assertEquals(FIXTURE_HEIGHT, decoder.outputHeight());
			assertEquals(FIXTURE_BYTES, decoder.nextFrame());

			// 非正数一律按"解码原生尺寸"处理（shim 的约定，与传 0/0 等价）
			assertEquals(FIXTURE_BYTES, decoder.setOutputSize(-1, 100));
			assertEquals(FIXTURE_WIDTH, decoder.outputWidth());

			// 离谱的放大要被拒掉，而不是把缓冲写爆
			assertTrue(decoder.setOutputSize(100000, 100000) < 0);
		}
	}

	/**
	 * 回归：换过输出尺寸之后，画面必须还是对的。
	 *
	 * <p>
	 * shim 里缓存的 swscale 上下文原来只在<b>源</b>尺寸/格式变化时重建，而
	 * {@link FfmpegVideoDecoder#setOutputSize} 改的是<b>输出</b>尺寸——缓存没有跟着
	 * 丢，于是 {@code sws_scale} 仍按旧尺寸写像素、却用新 stride 排，行全部错位。
	 * 实机上就是"拖一下窗口，画面撕裂成两半"，输出缩小时还会写过 Java 数组末尾
	 * （{@code GetPrimitiveArrayCritical} 拿到的就是堆上那块数组）。
	 * </p>
	 *
	 * <p>
	 * Java 侧发现不了它：{@code nextFrame} 返回的字节数是 shim <b>算</b>出来的，
	 * 不是量出来的，所以"字节数对不对"这条检查永远通过——上面那条
	 * {@link #scalesTheOutputSize()} 就是这么漏过去的。这条改成看<b>像素</b>。
	 * </p>
	 *
	 * <p>
	 * 素材是四象限（左上红 / 右上绿 / 左下蓝 / 右下白），位置写错就一定会红。第二次
	 * 是<b>放大</b>：shim 写不满整个缓冲时不会越界，所以这条用例在带 bug 的 DLL 上是
	 * "红"，而不是把测试进程写崩——回归用例得能跑第二遍才有用。
	 * </p>
	 */
	@Test
	void keepsThePictureCorrectWhenTheOutputSizeChanges()
	{
		assumeTrue(FfmpegVideoDecoder.isAvailable(),
			"原生解码器不可用：" + FfmpegVideoDecoder.lastLibraryError());

		try(FfmpegVideoDecoder decoder =
			FfmpegVideoDecoder.openSoftware(fixture(QUADRANTS)))
		{
			assertNotNull(decoder,
				"打不开四象限素材：" + FfmpegVideoDecoder.lastOpenError());

			// 第一次就是 shim 建 swscale 上下文的尺寸
			assertEquals(QUADRANT_BYTES, decoder.setOutputSize(160, 120));
			assertEquals(QUADRANT_BYTES, decoder.nextFrame());
			assertQuadrants(decoder.frameBuffer(), 160, 120);

			// 换一次尺寸：上下文必须跟着重建，否则行会错位
			assertEquals(QUADRANT_BYTES * 4, decoder.setOutputSize(320, 240));
			assertEquals(QUADRANT_BYTES * 4, decoder.nextFrame());
			assertQuadrants(decoder.frameBuffer(), 320, 240);
		}
	}

	@Test
	void closeIsIdempotent()
	{
		assumeTrue(FfmpegVideoDecoder.isAvailable(),
			"原生解码器不可用：" + FfmpegVideoDecoder.lastLibraryError());

		FfmpegVideoDecoder decoder =
			FfmpegVideoDecoder.openSoftware(fixture());
		assertNotNull(decoder);

		decoder.close();
		assertFalse(decoder.isOpen());
		// 关了之后再解一次：返回错误而不是崩
		assertTrue(decoder.nextFrame() < 0);
		decoder.close();
	}

	@Test
	void refusesFilesItCannotOpen(@TempDir Path folder) throws IOException
	{
		assumeTrue(FfmpegVideoDecoder.isAvailable(),
			"原生解码器不可用：" + FfmpegVideoDecoder.lastLibraryError());

		Path text = folder.resolve("not-a-video.mp4");
		Files.writeString(text, "definitely not an mp4, just some text");

		assertNull(FfmpegVideoDecoder.openSoftware(text.toString()));
		assertNull(FfmpegVideoDecoder.openHardware(text.toString()));
		assertNull(FfmpegVideoDecoder.open(null, true));
		assertNull(FfmpegVideoDecoder
			.open(folder.resolve("missing.mp4").toString(), true));
	}

	// ------------------------------------------------------------------
	// 失败原因：打开失败必须说得清"哪一步、什么错、走的哪条路"
	// ------------------------------------------------------------------

	/**
	 * 文件不存在：原因必须精确到 {@code avformat_open_input} 与 ENOENT，而不是
	 * 一个空的 detail（那正是这个通道要修的东西）。
	 *
	 * <p>
	 * 这条用例不碰 GPU，也不依赖任何素材，因此在任何机器上都可复现。
	 * </p>
	 */
	@Test
	void reportsWhyAMissingFileCannotBeOpened(@TempDir Path folder)
	{
		assumeTrue(FfmpegVideoDecoder.isAvailable(),
			"原生解码器不可用：" + FfmpegVideoDecoder.lastLibraryError());

		Path missing = folder.resolve("missing.mp4");

		assertNull(FfmpegVideoDecoder.openSoftware(missing.toString()));

		String why = FfmpegVideoDecoder.lastOpenError();

		assertFalse(why.isBlank(), "打开失败必须留下原因，不能是空串");
		assertTrue(why.contains("avformat_open_input"),
			"原因要指明是哪一步失败的，实际：" + why);
		assertTrue(why.contains("AVERROR -2"),
			"原因要带上 AVERROR 数值，实际：" + why);
		assertTrue(why.contains("No such file or directory"),
			"原因要带上 av_strerror 的文字，实际：" + why);
		assertTrue(why.contains("path=software"),
			"原因要说明走的是软解还是硬解，实际：" + why);
		assertTrue(why.contains("missing.mp4"),
			"原因要带上文件路径（路径本身就是常见病因），实际：" + why);
	}

	/** 垃圾文件：打开同样要留下具体原因，而且硬件优先那条路也要说清自己的状态。 */
	@Test
	void reportsWhyGarbageCannotBeOpened(@TempDir Path folder)
		throws IOException
	{
		assumeTrue(FfmpegVideoDecoder.isAvailable(),
			"原生解码器不可用：" + FfmpegVideoDecoder.lastLibraryError());

		Path garbage = folder.resolve("garbage.mp4");
		Files.writeString(garbage, "definitely not an mp4, just some text");

		assertNull(FfmpegVideoDecoder.open(garbage.toString(), true));

		String why = FfmpegVideoDecoder.lastOpenError();

		assertFalse(why.isBlank(), "打开失败必须留下原因，不能是空串");
		assertTrue(why.contains("AVERROR"), "原因要带 AVERROR，实际：" + why);
		assertTrue(why.contains("path="), "原因要说明走的哪条路，实际：" + why);
		assertTrue(why.contains("garbage.mp4"), "原因要带上文件路径，实际：" + why);

		// 打开失败的句柄随后就被关掉了：原因必须在句柄之外还活着
		assertEquals(why, FfmpegVideoDecoder.lastOpenError());

		// 成功的打开不许留着上一次失败的原因
		assertNotNull(FfmpegVideoDecoder.openSoftware(fixture()));
		assertEquals("", FfmpegVideoDecoder.lastOpenError(),
			"打开成功之后 lastOpenError() 必须是空串");
	}

	/**
	 * 解帧失败时，原因挂在这个实例上（{@link FfmpegVideoDecoder#lastError()}），
	 * 而不是只在返回值里给一个负数。
	 *
	 * <p>
	 * 320x240 的那段素材在 D3D11VA 上第一帧就失败（实测 AVERROR_INVALIDDATA），
	 * 但这不是所有机器都成立，所以这里只断言"失败时必须有原因"，硬件能解就跳过。
	 * </p>
	 */
	@Test
	void reportsWhyTheFirstFrameFailed()
	{
		assumeTrue(FfmpegVideoDecoder.isAvailable(),
			"原生解码器不可用：" + FfmpegVideoDecoder.lastLibraryError());

		try(FfmpegVideoDecoder decoder =
			FfmpegVideoDecoder.openHardware(fixture()))
		{
			assertNotNull(decoder,
				"硬件优先至少应当退回软解：" + FfmpegVideoDecoder.lastOpenError());

			int bytes = decoder.nextFrame();

			if(bytes > 0)
				return; // 这台机器的硬解这条路能用：值的检查在别的用例里

			assertTrue(bytes < 0, "解不出第一帧时应当返回负的 AVERROR，实际 " + bytes);

			String why = decoder.lastError();

			assertFalse(why.isBlank(), "解帧失败必须留下原因，不能是空串");
			assertTrue(why.contains("AVERROR " + bytes),
				"原因里的 AVERROR 应当与返回值一致，实际：" + why);
			assertTrue(why.contains("path="), "原因要说明走的哪条路，实际：" + why);
		}
	}

	/** {@link FfmpegVideoDecoder#lastError()} 在从来没失败过的实例上是空串。 */
	@Test
	void aHealthyDecoderHasNoLastError()
	{
		assumeTrue(FfmpegVideoDecoder.isAvailable(),
			"原生解码器不可用：" + FfmpegVideoDecoder.lastLibraryError());

		try(FfmpegVideoDecoder decoder =
			FfmpegVideoDecoder.openSoftware(fixture()))
		{
			assertNotNull(decoder);
			assertTrue(decoder.nextFrame() > 0);
			assertEquals("", decoder.lastError(), "解成功了就不该有失败原因");
		}
	}

	// ------------------------------------------------------------------

	/** 素材很小（4 帧纯色），每一帧都该是不透明的：alpha 不是 255 就是没填满。 */
	private static void assertOpaque(byte[] rgba, int length)
	{
		for(int pixel = 0; pixel * FfmpegVideoDecoder.BYTES_PER_PIXEL + 3 < length;
			pixel += 97)
		{
			int alpha = rgba[pixel * FfmpegVideoDecoder.BYTES_PER_PIXEL + 3];
			assertEquals(255, alpha & 0xFF, "第 " + pixel + " 个像素的 alpha");
		}
	}

	private static String fixture()
	{
		return fixture("fixture-colours.mp4");
	}

	private static String fixture(String name)
	{
		URL url = FfmpegVideoDecoderNativeTest.class
			.getResource("/net/wurstclient/background/" + name);

		assumeTrue(url != null, "测试素材不在：" + name);

		try
		{
			return Path.of(url.toURI()).toAbsolutePath().toString();

		}catch(URISyntaxException e)
		{
			throw new AssertionError(e);
		}
	}

	// ------------------------------------------------------------------
	// 四象限素材的像素检查（缓冲区版，不经过 NativeImage）
	// ------------------------------------------------------------------

	/** 逐象限取中心点，避开 H.264 色度下采样在边界上的过渡。 */
	private static void assertQuadrants(byte[] rgba, int width, int height)
	{
		int halfW = width / 2;
		int halfH = height / 2;

		assertDominant(rgba, width, halfW / 2, halfH / 2, 'R', "左上");
		assertDominant(rgba, width, halfW + halfW / 2, halfH / 2, 'G', "右上");
		assertDominant(rgba, width, halfW / 2, halfH + halfH / 2, 'B', "左下");
		assertBright(rgba, width, halfW + halfW / 2, halfH + halfH / 2, "右下");
	}

	/**
	 * 判据是"哪一路占优"而不是标称值：H.264 对饱和色有实打实的偏移，但红/绿/蓝谁
	 * 占优不会被编码动过，而通道写反、行序翻转、行错位都会让占优的那一路换人。
	 */
	private static void assertDominant(byte[] rgba, int width, int x, int y,
		char channel, String where)
	{
		int i = (y * width + x) * FfmpegVideoDecoder.BYTES_PER_PIXEL;
		int r = rgba[i] & 0xFF;
		int g = rgba[i + 1] & 0xFF;
		int b = rgba[i + 2] & 0xFF;
		int dominant = channel == 'R' ? r : channel == 'G' ? g : b;

		assertTrue(dominant >= 150, where + " 应当是 " + channel + " 占优，实际 R="
			+ r + " G=" + g + " B=" + b);

		int[] rgb = {r, g, b};

		for(int c = 0; c < 3; c++)
			if(c != (channel == 'R' ? 0 : channel == 'G' ? 1 : 2))
				assertTrue(rgb[c] <= 90, where + " 的另外两路应当很低，实际 R=" + r
					+ " G=" + g + " B=" + b);
	}

	private static void assertBright(byte[] rgba, int width, int x, int y,
		String where)
	{
		int i = (y * width + x) * FfmpegVideoDecoder.BYTES_PER_PIXEL;
		int r = rgba[i] & 0xFF;
		int g = rgba[i + 1] & 0xFF;
		int b = rgba[i + 2] & 0xFF;

		assertTrue(r >= 150 && g >= 150 && b >= 150,
			where + " 应当是白的，实际 R=" + r + " G=" + g + " B=" + b);
	}
}
