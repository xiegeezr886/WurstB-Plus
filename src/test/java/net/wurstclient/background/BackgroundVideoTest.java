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

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mojang.blaze3d.platform.NativeImage;

import net.wurstclient.background.BackgroundVideo.Reason;

/**
 * 视频背景的判定与解码。
 *
 * <p>
 * 分三层：不碰解码器的纯算术（尺寸策略、文件头、编码判定）、不许抛异常的失败路径
 * （文件不存在 / 空的 / 根本不是 mp4 / 只有 ftyp 的 mp4），以及走真实解码路径的
 * 端到端用例。
 * </p>
 *
 * <p>
 * 端到端用的是随仓库发布的**素材**（{@code src/test/resources/.../fixture-*.mp4}，
 * 三段共约 180 KB，是旧版本用 JCodec 的编码器现编出来之后存下来的——换成 FFmpeg
 * 解码之后就再也编不出新素材了，因为这套构建只带解码器）。素材是为了在没有
 * Minecraft 的情况下也能验「颜色对不对、行序对不对」：四象限素材（左上红、右上绿、
 * 左下蓝、右下白）逐象限比对，红蓝写反或上下翻转都会红。
 * </p>
 *
 * <p>
 * 解码用例需要两样东西：随仓库发布的原生解码器（{@code native/ffmpeg/dll}，测试
 * 通过 {@code wurst.ffmpeg.dir} 指过去）与 LWJGL 的本地内存（{@code NativeImage}）。
 * 两样任何一个在这台机器上没有，用例自己跳过——那缺的是环境，不是被测代码。
 * </p>
 */
final class BackgroundVideoTest
{
	@AfterEach
	void clearDrawnSizeOverride()
	{
		BackgroundVideo.drawnSizeOverride = null;
	}

	// ------------------------------------------------------------------
	// 纯算术：尺寸策略
	// ------------------------------------------------------------------

	/** 4K 与 1080p 都缩到上限以内，小文件保持原尺寸（永不放大）。 */
	@Test
	void largeVideosAreScaledDown()
	{
		assertSameSize(new int[]{2560, 1440},
			BackgroundVideo.fitSize(3840, 2160, 2560, 1440));
		assertSameSize(new int[]{1920, 1080},
			BackgroundVideo.fitSize(3840, 2160, 1920, 1080));
		// 宽高比必须保住：拉伸过的壁纸一眼就能看出来
		assertSameSize(new int[]{1280, 534},
			BackgroundVideo.fitSize(2560, 1068, 1280, 720));
	}

	@Test
	void smallVideosKeepTheirSize()
	{
		assertSameSize(new int[]{640, 360},
			BackgroundVideo.fitSize(640, 360, 2560, 1440));
		assertSameSize(new int[]{320, 240},
			BackgroundVideo.fitSize(320, 240, 1920, 1080));

		// 正方形：长边受上限约束，短边跟着同一个比例；源比上限小就原样
		assertSameSize(new int[]{1000, 1000},
			BackgroundVideo.fitSize(1000, 1000, 2560, 1440));
		assertSameSize(new int[]{720, 720},
			BackgroundVideo.fitSize(1000, 1000, 1280, 720));
	}

	/** 坏掉的尺寸不能让后面的 new NativeImage 抛异常。 */
	@Test
	void degenerateSizesBecomeOnePixel()
	{
		assertSameSize(new int[]{1, 1},
			BackgroundVideo.fitSize(0, 0, 2560, 1440));
		assertSameSize(new int[]{1, 1},
			BackgroundVideo.fitSize(-100, 50, 2560, 1440));
	}

	/**
	 * 这一次要修的那个问题：上传尺寸按**绘制尺寸**来，不再是写死的 1280x720。
	 *
	 * <p>
	 * 旧那版固定按 720p 上传，在 1080p / 1440p 的窗口里就是被放大着画的，看起来
	 * 就是糊的。
	 * </p>
	 */
	@Test
	void uploadSizeFollowsTheDrawnSize()
	{
		// 1920x1080 的窗口：4K 源解到 1080p，一像素对一像素
		assertSameSize(new int[]{1920, 1080},
			BackgroundVideo.chooseSize(new int[]{1920, 1080}, 3840, 2160));

		// 1440p 的窗口：解到 1440p
		assertSameSize(new int[]{2560, 1440},
			BackgroundVideo.chooseSize(new int[]{2560, 1440}, 3840, 2160));

		// 小窗口：解小一点，省下来的是显存与每帧的拷贝
		assertSameSize(new int[]{1280, 720},
			BackgroundVideo.chooseSize(new int[]{1280, 720}, 3840, 2160));

		// 21:9 的窗口：两个轴一起约束，宽高比不变
		assertSameSize(new int[]{2560, 1440},
			BackgroundVideo.chooseSize(new int[]{3440, 1440}, 3840, 2160));
	}

	/** 上限是 2560x1440：比它更大的窗口也只用这么大（见常量上的实测数据）。 */
	@Test
	void uploadSizeIsCappedByTheCeiling()
	{
		assertSameSize(new int[]{2560, 1440},
			BackgroundVideo.chooseSize(new int[]{3840, 2160}, 3840, 2160));
		assertSameSize(new int[]{2560, 1440},
			BackgroundVideo.chooseSize(new int[]{7680, 4320}, 7680, 4320));
	}

	/** 源比窗口小就按源尺寸：解码出来的东西不该被放大。 */
	@Test
	void uploadSizeNeverUpscalesTheSource()
	{
		assertSameSize(new int[]{1280, 720},
			BackgroundVideo.chooseSize(new int[]{3840, 2160}, 1280, 720));
		assertSameSize(new int[]{640, 480},
			BackgroundVideo.chooseSize(new int[]{1920, 1080}, 640, 480));
	}

	/** 问不到窗口尺寸（无客户端、客户端还没起窗口）时按默认尺寸算。 */
	@Test
	void uploadSizeFallsBackWhenThereIsNoWindow()
	{
		assertSameSize(new int[]{1280, 720},
			BackgroundVideo.chooseSize(new int[]{0, 0}, 3840, 2160));
		assertSameSize(new int[]{1280, 720},
			BackgroundVideo.chooseSize(null, 3840, 2160));
		assertSameSize(new int[]{1280, 720},
			BackgroundVideo.chooseSize(new int[]{-5, 10}, 3840, 2160));
	}

	// ------------------------------------------------------------------
	// 纯算术：文件头与编码
	// ------------------------------------------------------------------

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

	/**
	 * 现在是哪几种编码能放：与 {@code native/ffmpeg/build-ffmpeg.sh} 的 configure
	 * 行一一对应。HEVC / VP9 / AV1 从"放不了"变成了"能放"，VP8 与 ProRes 仍然不行。
	 */
	@Test
	void knowsWhichCodecsTheBuildHas()
	{
		assertTrue(BackgroundVideo.isPlayableFourcc("avc1"));
		assertTrue(BackgroundVideo.isPlayableFourcc("avc3"));
		assertTrue(BackgroundVideo.isPlayableFourcc("hvc1"));
		assertTrue(BackgroundVideo.isPlayableFourcc("hev1"));
		assertTrue(BackgroundVideo.isPlayableFourcc("vp09"));
		assertTrue(BackgroundVideo.isPlayableFourcc("av01"));
		assertTrue(BackgroundVideo.isPlayableFourcc("mp4v"));
		assertTrue(BackgroundVideo.isPlayableFourcc("JPEG"));

		assertFalse(BackgroundVideo.isPlayableFourcc("vp08"));
		assertFalse(BackgroundVideo.isPlayableFourcc("ap4h"));
		assertFalse(BackgroundVideo.isPlayableFourcc(null));
		assertFalse(BackgroundVideo.isPlayableFourcc(""));
		assertFalse(BackgroundVideo.isPlayableFourcc("xyz9"));
	}

	/** AV1 只有硬解：软解这条路在 FFmpeg 里根本不存在。 */
	@Test
	void av1NeedsHardwareDecoding()
	{
		assertTrue(BackgroundVideo.needsHardwareDecoder("av01"));
		assertTrue(BackgroundVideo.needsHardwareDecoder("AV01"));
		assertFalse(BackgroundVideo.needsHardwareDecoder("avc1"));
		assertFalse(BackgroundVideo.needsHardwareDecoder("hvc1"));
		assertFalse(BackgroundVideo.needsHardwareDecoder(null));
	}

	@Test
	void namesTheCodecForTheMessage()
	{
		assertEquals("H.264", BackgroundVideo.describeFourcc("avc1"));
		assertEquals("HEVC/H.265", BackgroundVideo.describeFourcc("hvc1"));
		assertEquals("HEVC/H.265", BackgroundVideo.describeFourcc("HEV1"));
		assertEquals("VP9", BackgroundVideo.describeFourcc("vp09"));
		assertEquals("AV1", BackgroundVideo.describeFourcc("av01"));
		assertEquals("unknown", BackgroundVideo.describeFourcc(null));
		// 不认识的 fourcc 原样带出去，比"未知编码"有用
		assertEquals("xyz9", BackgroundVideo.describeFourcc("xyz9"));
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
	 * 「解不出来」上，而不是把异常漏给调用方。
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

	/**
	 * 一个"看着像 mp4、其实解不开"的文件，失败原因必须一路走到
	 * {@link BackgroundVideo.Probe#detail()} 里。
	 *
	 * <p>
	 * 这一条钉的就是游戏里那行日志：
	 * {@code [Background] 视频背景 <id> 不能播放：<Reason> / <fourcc> / <detail>}。
	 * 之前 detail 会是空的（日志里就是 {@code DECODE_FAILED / avc1 / }），于是"为什么
	 * 放不了"完全无从查起。现在它必须带上 shim 记下的阶段、AVERROR 与走的是哪条路。
	 * </p>
	 */
	@Test
	void failedProbeCarriesTheNativeReason(@TempDir Path folder)
		throws IOException
	{
		assumeTrue(decoderAvailable(), decoderUnavailable());

		Path file = folder.resolve("broken.mp4");

		// 头 16 字节是合法的 ftyp box（所以能过 looksLikeMp4），后面全是垃圾：
		// 这类文件正是"探测说 avc1、真解却解不出来"的那种
		byte[] bytes = new byte[4096];
		bytes[3] = 16;
		System.arraycopy("ftyp".getBytes(), 0, bytes, 4, 4);
		System.arraycopy("isom".getBytes(), 0, bytes, 8, 4);
		for(int i = 16; i < bytes.length; i++)
			bytes[i] = (byte)(i * 7);
		Files.write(file, bytes);

		BackgroundVideo.Probe probe = BackgroundVideo.probe(file);

		assertFalse(probe.playable());

		String detail = probe.detail();

		assertFalse(detail == null || detail.isBlank(),
			"探测失败必须带上原因，不能是空的（日志里那行就是 Reason / fourcc / detail）");
		assertTrue(detail.contains("AVERROR"),
			"原因要带上 AVERROR 数值与文字，实际：" + detail);
		assertTrue(detail.contains("path="),
			"原因要说明硬解还是软解被尝试过，实际：" + detail);

		// 日志那行的样子：Reason / fourcc / detail
		System.out.println("[test] " + probe.reason() + " / " + probe.detail());
	}

	// ------------------------------------------------------------------
	// 端到端：随仓库发布的素材
	// ------------------------------------------------------------------
	@Test
	void probesTheBundledH264Fixture()
	{
		assumeTrue(decoderAvailable(), decoderUnavailable());
		Path file = fixture("fixture-colours.mp4");

		BackgroundVideo.Probe probe = BackgroundVideo.probe(file);

		assertTrue(probe.playable(), "仓库里的素材应当能放：" + probe.reason()
			+ " / " + probe.detail());

		assertEquals("avc1", probe.detail());
		assertEquals(320, probe.sourceWidth());
		assertEquals(240, probe.sourceHeight());
		assertEquals(4, probe.frameCount());

		// 30fps 的 4 帧 = 133ms；时间基换算难免有一两毫秒的舍入
		assertTrue(probe.durationMs() >= 100 && probe.durationMs() <= 170,
			"时长应当是 133ms 上下，实际 " + probe.durationMs() + "ms");

		// 目标尺寸：源本来就比上限小，所以原样保留
		assertSameSize(new int[]{320, 240}, probe.targetSize());
	}

	/**
	 * 回归：**第一次尝试（硬解优先）就成功**时，探测必须返回成功。
	 *
	 * <p>
	 * {@code probe()} 里原来只有 {@code if(probe == null){…软解重试…}}，之后不管
	 * 成败都落到 {@code diagnose(track, attempt.failure())}——成功那条路没有返回，
	 * 于是成功时原因串是空的，卡片和日志一律显示 {@code DECODE_FAILED / avc1 / }，
	 * 看起来像"解码器全是坏的"。
	 * </p>
	 *
	 * <p>
	 * 上面那条 320x240 的用例抓不到这个：那个尺寸的 D3D11VA 在第一帧就失败（见
	 * {@link BackgroundVideo#probe} 的注释与 native/ffmpeg/README.md 第 8 节），
	 * 测试实际跑的是"硬解失败 → 软解重试成功"这条<b>能</b>正确返回的分支。这里换
	 * 720p 素材，把硬解成功那条路真正跑一遍——实测这份素材在这台机器上是
	 * {@code hardware: d3d11va (ACTIVE)}。
	 * </p>
	 *
	 * <p>
	 * 没有 d3d11va 的机器上它会退化成"软解成功"，断言照样成立：只覆盖不到那条分支，
	 * 不会假失败。
	 * </p>
	 */
	@Test
	void probesAnHdFixtureThatHardwareCanDecode()
	{
		assumeTrue(decoderAvailable(), decoderUnavailable());
		Path file = fixture("fixture-hd.mp4");

		BackgroundVideo.Probe probe = BackgroundVideo.probe(file);

		assertTrue(probe.playable(),
			"硬解能吃下的素材被判成放不了：" + probe.reason() + " / "
				+ probe.detail());
		assertEquals(Reason.OK, probe.reason());
		assertEquals("avc1", probe.detail());
		assertEquals(1280, probe.sourceWidth());
		assertEquals(720, probe.sourceHeight());
		assertEquals(30, probe.frameCount());
	}

	/**
	 * 端到端验颜色与行序：走的是 {@link BackgroundVideo} 真实那条路
	 * （FFmpeg → RGBA 缓冲 → NativeImage）。
	 *
	 * <p>
	 * 素材是四象限（左上红、右上绿、左下蓝、右下白），所以这一条同时钉住三件事：
	 * 通道顺序（红蓝写反会让左上变蓝）、行序（上下翻转会让左上变蓝、右上变白）、
	 * 以及"帧不是空的"。H.264 是有损的，但纯色块只是 DC 系数，给 24 的余量足够。
	 * </p>
	 */
	@Test
	void decodesTheBundledFixtureInColour() throws Exception
	{
		assumeTrue(decoderAvailable(), decoderUnavailable());
		assumeTrue(canUseNativeImages(), "这个环境里用不了 NativeImage，跳过");
		Path file = fixture("fixture-quadrants.mp4");

		BackgroundVideo.Opened opened = BackgroundVideo.open(file);
		assumeTrue(opened.playable(),
			"打不开仓库里的素材：" + opened.probe().reason());

		BackgroundVideo video = opened.video();

		try
		{
			assertEquals(160, video.width());
			assertEquals(120, video.height());

			NativeImage frame = takeFrame(video, 2_000);
			assertTrue(frame != null, "两秒里一帧都没解出来");

			try
			{
				// 逐象限取中心点，避开 H.264 的色度下采样在边界上的过渡
				assertDominant(frame, 40, 30, 'R', "左上");
				assertDominant(frame, 120, 30, 'G', "右上");
				assertDominant(frame, 40, 90, 'B', "左下");
				assertBright(frame, 120, 90, "右下");

				// 每一帧都该是不透明的：透明会被画成黑，看不出是"没解出来"
				assertEquals(255, alphaAt(frame, 40, 30));

			}finally
			{
				video.recycle(frame);
			}

		}finally
		{
			video.close();
		}
	}

	/**
	 * 真的把播放器跑起来：解码线程按挂钟一帧帧交出来，渲染线程取走再还回来。
	 *
	 * <p>
	 * 这条用例挡的是「一帧都出不来」这一类错误——交接队列写错、解码线程一起步就死、
	 * 缓冲不回收把线程堵死、close() 卡住不返回——同时钉住 **30fps 上限**：素材是
	 * 30fps 的源，1.5 秒里应当收到 30~45 帧；上限要是没生效（解码完就往外倒），
	 * 这个数会远远超过 60。
	 * </p>
	 */
	@Test
	void playsTheBundledFixtureInRealTime() throws Exception
	{
		assumeTrue(decoderAvailable(), decoderUnavailable());
		assumeTrue(canUseNativeImages(), "这个环境里用不了 NativeImage，跳过");
		Path file = fixture("fixture-playback.mp4");

		BackgroundVideo.Opened opened = BackgroundVideo.open(file);
		assumeTrue(opened.playable(),
			"打不开仓库里的素材：" + opened.probe().reason());

		BackgroundVideo video = opened.video();

		// 源比上限小，所以按原尺寸放，不放大
		assertEquals(320, video.width());
		assertEquals(240, video.height());

		int received = 0;
		long deadline = System.currentTimeMillis() + 1_500;

		try
		{
			while(System.currentTimeMillis() < deadline)
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

		// 30fps 的源在 1.5 秒里 45 帧上下：给到 20 帧就足以证明帧真的在流动，
		// 超过 60 帧则说明 30fps 上限根本没生效
		assertTrue(received >= 20, "1.5 秒只收到 " + received + " 帧");
		assertTrue(received <= 60,
			"1.5 秒收到 " + received + " 帧，30fps 上限没生效");
		assertFalse(video.failed(), "解码线程不该失败：" + video.failure());
	}

	/**
	 * 背景没在显示时解码线程要停下来（不是空转）。
	 *
	 * <p>
	 * 渲染线程超过 1 秒不来取帧就认为标题界面不在了：不显示的那段时间继续解码
	 * 就是白烧 CPU（一段 4K 的 H.264 能吃掉小半个核心），而玩家正在游戏里。
	 * 判据是"解了多少帧"而不是"收到多少帧"——停下来这件事在画面上看不出来。
	 * </p>
	 */
	@Test
	void stopsDecodingWhileNobodyTakesFrames() throws Exception
	{
		assumeTrue(decoderAvailable(), decoderUnavailable());
		assumeTrue(canUseNativeImages(), "这个环境里用不了 NativeImage，跳过");
		Path file = fixture("fixture-playback.mp4");

		BackgroundVideo.Opened opened = BackgroundVideo.open(file);
		assumeTrue(opened.playable(),
			"打不开仓库里的素材：" + opened.probe().reason());

		BackgroundVideo video = opened.video();

		try
		{
			// 先正常看几帧，让解码线程真的跑起来
			for(int i = 0; i < 5; i++)
			{
				NativeImage frame = takeFrame(video, 2_000);

				if(frame != null)
					video.recycle(frame);
			}

			// 然后 2.5 秒不取帧（模拟玩家进了游戏、标题界面不在）
			Thread.sleep(2_500);

			long before = video.decodedFrames();
			Thread.sleep(2_000);
			long decodedWhileHidden = video.decodedFrames() - before;

			// 30fps 的源，没停的话这 2 秒会解 60 帧上下；停了就只多出个位数
			assertTrue(decodedWhileHidden <= 5,
				"背景没在显示时还在解码：" + decodedWhileHidden + " 帧");

			// 而且必须还能接着放（不是卡死了、也没被判成放不了）
			NativeImage frame = takeFrame(video, 2_000);
			assertTrue(frame != null, "重新显示之后一帧都没解出来");
			video.recycle(frame);
			assertFalse(video.failed(), "解码线程不该失败：" + video.failure());

		}finally
		{
			video.close();
		}
	}

	/**
	 * 窗口变大变小要跟着换解码尺寸。
	 *
	 * <p>
	 * 单测里没有窗口，所以把"绘制尺寸"换成固定值（
	 * {@link BackgroundVideo#drawnSizeOverride}）。这条用例覆盖的是这次新增里最容易
	 * 出错的一段：解码线程换缓冲池、渲染线程拿到的帧与 {@code width()} 一致、旧池
	 * 里的缓冲被安全关掉（{@code recycle} 会碰到"不属于当前池"的帧）。
	 * </p>
	 */
	@Test
	void followsTheWindowSize() throws Exception
	{
		assumeTrue(decoderAvailable(), decoderUnavailable());
		assumeTrue(canUseNativeImages(), "这个环境里用不了 NativeImage，跳过");
		Path file = fixture("fixture-playback.mp4");

		BackgroundVideo.drawnSizeOverride = new int[]{1280, 720};

		BackgroundVideo.Opened opened = BackgroundVideo.open(file);
		assumeTrue(opened.playable(),
			"打不开仓库里的素材：" + opened.probe().reason());

		BackgroundVideo video = opened.video();

		try
		{
			// 源是 320x240，比"窗口"小，所以目标是源尺寸
			assertEquals(320, video.width());
			assertEquals(240, video.height());

			// 窗口变小：目标尺寸跟着变小
			BackgroundVideo.drawnSizeOverride = new int[]{160, 120};
			assertTrue(waitForSize(video, 160, 120, 4_000),
				"窗口变小之后没有跟着换尺寸，还是 " + video.width() + "x"
					+ video.height());

			// 换完之后交出来的帧必须是新尺寸（旧池的帧不许再往外发）
			NativeImage small = takeFrame(video, 2_000);
			assertTrue(small != null, "换尺寸之后一帧都没解出来");
			try
			{
				assertEquals(160, small.getWidth());
				assertEquals(120, small.getHeight());
			}finally
			{
				video.recycle(small);
			}

			// 再放大回去
			BackgroundVideo.drawnSizeOverride = new int[]{1280, 720};
			assertTrue(waitForSize(video, 320, 240, 4_000),
				"窗口放大回去之后没有跟着换尺寸，还是 " + video.width() + "x"
					+ video.height());

		}finally
		{
			video.close();
		}
	}

	// ------------------------------------------------------------------
	// 测试用的素材与工具
	// ------------------------------------------------------------------

	private static Path fixture(String name)
	{
		URL url = BackgroundVideoTest.class
			.getResource("/net/wurstclient/background/" + name);

		assumeTrue(url != null, "测试素材不在：" + name);

		try
		{
			return Path.of(url.toURI());

		}catch(URISyntaxException e)
		{
			throw new AssertionError(e);
		}
	}

	private static boolean decoderAvailable()
	{
		return FfmpegVideoDecoder.isAvailable();
	}

	private static String decoderUnavailable()
	{
		return "原生解码器不可用（native/ffmpeg/dll）："
			+ FfmpegVideoDecoder.lastLibraryError();
	}

	/** 取一帧，等不到返回 null；还回来是调用方的事。 */
	private static NativeImage takeFrame(BackgroundVideo video, long timeoutMs)
		throws InterruptedException
	{
		long deadline = System.currentTimeMillis() + timeoutMs;

		while(System.currentTimeMillis() < deadline)
		{
			NativeImage frame = video.takeFrame();

			if(frame != null)
				return frame;

			Thread.sleep(2);
		}

		return null;
	}

	/** 一边取帧（换尺寸是渲染线程取帧时才发起的）一边等它换成目标尺寸。 */
	private static boolean waitForSize(BackgroundVideo video, int width,
		int height, long timeoutMs) throws InterruptedException
	{
		long deadline = System.currentTimeMillis() + timeoutMs;

		while(System.currentTimeMillis() < deadline)
		{
			NativeImage frame = video.takeFrame();

			if(frame != null)
				video.recycle(frame);

			if(video.width() == width && video.height() == height)
				return true;

			Thread.sleep(10);
		}

		return video.width() == width && video.height() == height;
	}

	/**
	 * {@code NativeImage} 的整数像素是 ABGR（低字节是红），而 FFmpeg 交出来的是
	 * 内存里的 R,G,B,A —— 所以从整数里取红要用最低字节。这里刻意按 ABGR 解，
	 * 写反了这条用例就会红。
	 *
	 * <p>
	 * 判据用的是"哪一路占优"而不是生成素材时的标称值：H.264 对饱和色有实打实的
	 * 偏移（实测素材里的纯蓝 (30,30,220) 解出来是 (6,2,220)），但红/绿/蓝谁占优
	 * 不会被编码动过，而通道写反、行序翻转都会让占优的那一路换人。
	 * </p>
	 */
	private static void assertDominant(NativeImage image, int x, int y,
		char channel, String where)
	{
		int[] rgb = rgbAt(image, x, y);
		int dominant = channel == 'R' ? rgb[0] : channel == 'G' ? rgb[1] : rgb[2];

		assertTrue(dominant >= 150,
			where + " 应当是" + channel + " 占优，实际 R=" + rgb[0] + " G="
				+ rgb[1] + " B=" + rgb[2]);

		for(int i = 0; i < 3; i++)
			if(i != (channel == 'R' ? 0 : channel == 'G' ? 1 : 2))
				assertTrue(rgb[i] <= 90,
					where + " 的另外两路应当很低，实际 R=" + rgb[0] + " G="
						+ rgb[1] + " B=" + rgb[2]);
	}

	private static void assertBright(NativeImage image, int x, int y,
		String where)
	{
		int[] rgb = rgbAt(image, x, y);

		for(int value : rgb)
			assertTrue(value >= 150,
				where + " 应当是亮的，实际 R=" + rgb[0] + " G=" + rgb[1] + " B="
					+ rgb[2]);
	}

	private static int[] rgbAt(NativeImage image, int x, int y)
	{
		int pixel = image.getPixelRGBA(x, y);
		return new int[]{pixel & 0xFF, pixel >> 8 & 0xFF, pixel >> 16 & 0xFF};
	}

	private static int alphaAt(NativeImage image, int x, int y)
	{
		return image.getPixelRGBA(x, y) >>> 24;
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
