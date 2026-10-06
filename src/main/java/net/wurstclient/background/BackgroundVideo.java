/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Window;

import net.minecraft.client.Minecraft;

/**
 * 视频背景：用随模组发布的精简版 FFmpeg（LGPL、仅解码器，见
 * {@code native/ffmpeg/README.md}）把 mp4 一帧帧解出来。
 *
 * <p>
 * 与 GIF 那条路（{@link BackgroundClip}）最大的不同是<b>不能先全解完再放</b>：
 * 一段 10 秒的 720p30 视频解成位图是 300 帧 × 3.7 MB ≈ 1.1 GB。所以这里是流式的——
 * 解码线程按挂钟一次解一帧，解好的帧放进 3 个复用缓冲里，渲染线程取走最新的一帧
 * 拷进纹理。缓冲不会无限增长；渲染线程超过一秒不来取帧（标题界面不在显示）时
 * 解码线程直接停下，不白烧 CPU，重新显示时接着往下放。
 * </p>
 *
 * <p>
 * 时间以挂钟为准，不以「解了多少帧」为准：每帧按容器帧率换算成绝对显示时刻，
 * 落后挂钟太多就重新对齐一次（见 {@link VideoPacing}）。这样 60fps 的源在 30fps
 * 上限下不会半速播放，界面隐藏一段时间回来也不会把落下的几百帧一帧帧解完。
 * </p>
 *
 * <p>
 * <b>时间轴是自己算的，不是容器里的 PTS。</b>shim 只交出「下一帧的图像」，没有
 * 逐帧时间戳，所以第 n 帧的时刻是 {@code n * 1000 / fps}（fps 取容器帧率，
 * 拿不到就用「帧数 / 时长」推，再拿不到按 30）。代价是可变帧率的源会有累积偏差；
 * 收益是时间轴一定单调向前——旧那条路上「坏时间戳跳到几小时后」的失败模式不存在了。
 * </p>
 *
 * <p>
 * 解不出来的文件一律降级成「放不了」：探测阶段返回 {@link Probe} 说明原因，
 * 播放中途坏掉则由 {@link #failed()} 报告，两种情况都不会把异常丢到渲染线程上。
 * 硬件解码（D3D11VA）是可选的：开不出来就软解，中途坏了也用软解重开接着放。
 * </p>
 *
 * <p>
 * 解码线程是每实例一个的守护线程：{@link #close()} 中断它之后就把收尾交给一个
 * 后台线程（缓冲要等解码线程真的退出才能关，见 {@link #close()}）；客户端退出时
 * 守护线程随 JVM 一起结束，所以不需要全局的 shutdown 钩子。
 * </p>
 */
public final class BackgroundVideo implements AutoCloseable
{
	/**
	 * 上传尺寸的上限（另一侧的约束是窗口与源尺寸，见 {@link #chooseSize}）。
	 *
	 * <p>
	 * 取 2560x1440：2K 屏按原尺寸、4K 屏按 1.5 倍画。本机实测（4K/16fps 的
	 * H.264 源，369 帧/轮）这套尺寸下解码是软解 87.5fps、硬解 65.9fps，再加每帧
	 * 3.7ms 的 Java 侧拷贝，端到端仍有 55~66fps，是 30fps 上限的两倍上下；
	 * 内存/显存代价是 3 个复用缓冲 44MB（按 4K 尺寸要 150MB 以上）。
	 * </p>
	 *
	 * <p>
	 * 「上传尺寸变大几乎不要钱」这个说法<b>不成立</b>：解码本身由源分辨率决定，
	 * 但 swscale 的缩放与显存带宽是按输出像素算的。同一台机器上 720p→1440p
	 * 软解从 196.7fps 掉到 87.5fps、硬解从 113.3fps 掉到 65.9fps（实测）。所以上限
	 * 不是越高越好，这里取的是「画质够、且留两倍余量」的那个点。
	 * </p>
	 */
	public static final int MAX_UPLOAD_WIDTH = 2560;
	public static final int MAX_UPLOAD_HEIGHT = 1440;

	/** 拿不到窗口尺寸时（无客户端的单测、或客户端还没起窗口）按这个算。 */
	static final int DEFAULT_WIDTH = 1280;
	static final int DEFAULT_HEIGHT = 720;

	/** 复用缓冲的个数：一个在解、一个等渲染线程取、一个在拷进纹理。 */
	private static final int BUFFER_COUNT = 3;

	/** 等待期间的分片长度：醒来看一眼有没有被 close()。 */
	private static final long SLEEP_CHUNK_MS = 250;

	/**
	 * 单帧最多等这么久。
	 *
	 * <p>
	 * 少数壁纸确实有长达数秒的静止段（时间戳上就是一个大空档），那种情况等下去
	 * 是对的；但系统时钟被往回调（NTP 校时）会让画面永久冻住。等不满就重新对齐
	 * 一次挂钟，代价只是丢掉那一小段等待。
	 * </p>
	 */
	private static final long MAX_WAIT_MS = 5_000;

	/** 等解码线程退出的上限，见 {@link #close()}。 */
	private static final long JOIN_TIMEOUT_MS = 2_000;

	/**
	 * 渲染线程多久不来取帧就认为背景没在显示。
	 *
	 * <p>
	 * 标题界面不在了（玩家进了游戏、开着别的界面）时没人来取帧，这时候继续解码
	 * 就是白烧 CPU：一段 4K 的 H.264 光解码就能吃掉小半个核心。留 1 秒是给标题
	 * 界面偶尔的卡顿留余量，免得每次掉帧都停一次。
	 * </p>
	 */
	private static final long IDLE_TIMEOUT_MS = 1_000;

	/** 构造时等第一帧的上限：等到了就不会有那一帧黑屏，等不到也不影响播放。 */
	private static final long FIRST_FRAME_TIMEOUT_MS = 500;

	/**
	 * 两次换解码尺寸之间至少隔这么久。
	 *
	 * <p>
	 * 拖动窗口边缘时每帧尺寸都不一样，跟着换就是一秒几十次「3 张 NativeImage
	 * 的分配 + 释放」。半秒的间隔足够跟上用户的动作，又不会抖。
	 * </p>
	 */
	private static final long RETARGET_MIN_INTERVAL_MS = 500;

	/** 硬件解码中途坏掉时，最多重开软解这么多次。 */
	private static final int MAX_SOFTWARE_RETRIES = 2;

	/** 容器里既没有帧率、也算不出帧率时的兜底帧率。 */
	private static final double FALLBACK_FPS = 30.0;

	/** 测试用：把「绘制尺寸」钉住，见 {@link #drawnSize()}。 */
	static volatile int[] drawnSizeOverride;

	private final Path file;
	private final int sourceWidth;
	private final int sourceHeight;

	/** 盒子扫描出来的帧数，只用来兜底推算帧率。 */
	private final int frameCount;

	private final long initialPassMs;

	private volatile int width;
	private volatile int height;

	/** 全部缓冲，只增不减，{@link #close()} 按它逐个关。 */
	private final List<NativeImage> buffers = new ArrayList<>(BUFFER_COUNT);
	private final ArrayDeque<NativeImage> free = new ArrayDeque<>(BUFFER_COUNT);

	/**
	 * 上一套尺寸留下的缓冲，等渲染线程还回来之后才能关。
	 *
	 * <p>
	 * 换尺寸时渲染线程可能正拿着旧池里的一帧在拷（{@code takeFrame()} 与
	 * {@code recycle()} 之间），那几张不能立刻关，否则撞上它正在写的像素就是
	 * 直接崩客户端。还回来的时候按「不属于当前池」处理，当场关掉。
	 * </p>
	 */
	private final List<NativeImage> retired = new ArrayList<>(2);

	private final Object lock = new Object();

	/** 解码线程刚解好、等渲染线程取走的那一帧。 */
	private NativeImage published;

	/** 渲染线程已经取走、还没还回来的那一帧。 */
	private NativeImage checkedOut;

	/** 渲染线程最后一次来取帧的时刻，用来判断背景还在不在显示。 */
	private volatile long lastTakenMs;

	/** 渲染线程算出来的目标尺寸，解码线程按它换缓冲。 */
	private int requestedWidth;
	private int requestedHeight;

	private long lastRetargetMs;

	private Thread thread;

	/** 解码器、每帧字节数与帧间隔：只有解码线程碰。 */
	private FfmpegVideoDecoder decoder;
	private int frameBytes;
	private double frameMs;

	/** 当前这一轮里的第几帧（解码线程专用，时间轴就是从它算出来的）。 */
	private long frameIndex;

	/** 硬件解码中途坏掉后已经重开软解的次数（解码线程专用）。 */
	private int softwareRetries;

	/**
	 * 到目前为止解了多少帧（含丢掉不上传的）。
	 *
	 * <p>
	 * 只给诊断与单测看：它是"解码线程还在不在干活"的唯一直接证据——背景不在显示
	 * 时应当停住，而画面本身看不出来这件事。
	 * </p>
	 */
	private volatile long decodedFrames;

	private volatile boolean closed;
	private volatile boolean failed;
	private volatile String failure = "";

	/**
	 * 一个文件到底能不能放，以及为什么不能放。
	 *
	 * @param detail
	 *            给日志和状态栏用的补充信息：不能放时是编码 fourcc 或原因摘要
	 */
	public record Probe(boolean playable, Reason reason, String detail,
		int sourceWidth, int sourceHeight, int frameCount, long durationMs)
	{
		/** 这个源最多会用到多大：按最长边缩到上限以内，小文件不放大。 */
		int[] targetSize()
		{
			return fitSize(sourceWidth, sourceHeight, MAX_UPLOAD_WIDTH,
				MAX_UPLOAD_HEIGHT);
		}

		Probe failed(Reason newReason, String newDetail)
		{
			return new Probe(false, newReason, newDetail, sourceWidth,
				sourceHeight, frameCount, durationMs);
		}
	}

	/** 放不了的原因。文案在选择界面里按这个枚举查翻译，所以不在这里拼字符串。 */
	public enum Reason
	{
		OK,
		MISSING,
		EMPTY_FILE,
		NOT_MP4,
		NO_VIDEO_TRACK,
		UNSUPPORTED_CODEC,
		NO_FRAME,
		DECODE_FAILED
	}

	/**
	 * {@link #open(Path)} 的结果：成功时带上播放器，失败时带上原因。
	 *
	 * @param video
	 *            打不开时为 null
	 */
	record Opened(BackgroundVideo video, Probe probe)
	{
		boolean playable()
		{
			return video != null;
		}
	}

	private BackgroundVideo(Path file, Probe probe, int[] target)
	{
		this.file = file;
		sourceWidth = probe.sourceWidth();
		sourceHeight = probe.sourceHeight();
		frameCount = probe.frameCount();
		width = target[0];
		height = target[1];
		requestedWidth = target[0];
		requestedHeight = target[1];
		initialPassMs = Math.max(VideoPacing.MIN_PASS_MS, probe.durationMs());
		lastTakenMs = System.currentTimeMillis();
		lastRetargetMs = System.currentTimeMillis();

		boolean ready = false;

		try
		{
			for(int i = 0; i < BUFFER_COUNT; i++)
				buffers.add(new NativeImage(NativeImage.Format.RGBA, width,
					height, false));

			free.addAll(buffers);

			thread = new Thread(this::run, "WurstB-BackgroundVideo");
			thread.setDaemon(true);
			thread.start();

			// 先等第一帧出来再交回去：这段时间花的是一条后台线程，换掉的是纹理
			// 头几帧的黑屏
			waitForFirstFrame(FIRST_FRAME_TIMEOUT_MS);
			ready = true;

		}finally
		{
			if(!ready)
				releaseResources();
		}
	}

	// ------------------------------------------------------------------
	// 探测与打开
	// ------------------------------------------------------------------

	/** 在后台线程探测，供选择界面在允许选中之前问一次。 */
	public static CompletableFuture<Probe> probeAsync(Path file)
	{
		return CompletableFuture.supplyAsync(() -> probe(file));
	}

	/**
	 * 这个文件能不能放。
	 *
	 * <p>
	 * 只做三件事：挡住明显不是 mp4 的文件、真的打开解码器、真的解出第一帧。
	 * 最后一件事是必要的——一个 fourcc 写着 avc1、样本却缺一半的文件，只有解一次
	 * 才知道。打不开时再扫一眼盒子结构（{@link Mp4Probe}），把「没有视频轨道」
	 * 「编码这台机器放不了」和「文件坏了」区分开。
	 * </p>
	 *
	 * <p>
	 * 硬件优先那条路失败会再用纯软件试一次，两次都解不出才算放不了。这不是多余的
	 * 客气：实测 320x240 的 baseline H.264 在 D3D11VA 上第一帧就返回
	 * AVERROR_INVALIDDATA，而同一段文件软解完全正常——只试硬件的话，一个能放的
	 * 文件会被判成"放不了"。
	 * </p>
	 *
	 * <p>
	 * 任何异常都在这里被吃掉并翻译成 {@link Reason}：调用方里有渲染线程
	 * （选择界面），不能让它看见异常。
	 * </p>
	 */
	public static Probe probe(Path file)
	{
		if(file == null || !Files.isRegularFile(file))
			return unplayable(Reason.MISSING, String.valueOf(file));

		try
		{
			if(Files.size(file) <= 0)
				return unplayable(Reason.EMPTY_FILE, file.getFileName()
					.toString());

			if(!looksLikeMp4(readHeader(file)))
				return unplayable(Reason.NOT_MP4, file.getFileName().toString());

		}catch(IOException | RuntimeException e)
		{
			return unplayable(Reason.MISSING, String.valueOf(e));
		}

		// 视频源的编码与帧数只有盒子扫描知道，解码器只说得出宽高与时长
		Mp4Probe.Result track = Mp4Probe.read(file);

		Attempt attempt = decodeFirstFrame(file, false, track);

		// 成功那条路必须显式返回。这里原来写成 `if(probe == null){…软解重试…}` 之后
		// 不管成败都落到 diagnose()，于是「硬解第一次就成功」的文件也带着空原因进
		// 诊断，卡片和日志一律显示 `DECODE_FAILED / avc1 / ` —— 解码器明明是好的，
		// 看起来却像全坏（实机卡了很久就是这个）。别再把它改回单分支：
		// BackgroundVideoTest.probesAnHdFixtureThatHardwareCanDecode 会红。
		if(attempt.probe() != null)
			return attempt.probe();

		Attempt software = decodeFirstFrame(file, true, track);

		if(software.probe() != null)
			return software.probe();

		return diagnose(track,
			attempt.failure() + "；软解也不行：" + software.failure());
	}

	/**
	 * 一次「打开 + 解第一帧」的尝试。
	 *
	 * @param probe
	 *            解出来了就是它，失败时是 null
	 * @param failure
	 *            失败原因，成功时是空串
	 */
	private record Attempt(Probe probe, String failure)
	{
	}

	/**
	 * 一次「打开解码器」的结果。
	 *
	 * @param decoder
	 *            成功时非 null
	 * @param failure
	 *            失败原因（硬解优先那条路与软解那条路各试了一次的话，两条都在），
	 *            成功时是空串
	 */
	private record OpenAttempt(FfmpegVideoDecoder decoder, String failure)
	{
	}

	private static Attempt decodeFirstFrame(Path file, boolean forceSoftware,
		Mp4Probe.Result track)
	{
		OpenAttempt opening =
			forceSoftware ? openSoftwareOnly(file) : openDecoder(file);

		if(opening.decoder() == null)
		{
			// 打开失败必须无条件落一行日志：只挂在选择界面的卡片上，用户报"视频
			// 放不了"时就只剩一句"不能播放"，原因根本没进过日志文件
			String reason = describeOpenFailure(opening.failure());
			System.err.println("[Background] 视频解码器打开失败（"
				+ (forceSoftware ? "软解" : "硬解优先") + "）：" + file + " —— "
				+ reason);
			return new Attempt(null, reason);
		}

		FfmpegVideoDecoder decoder = opening.decoder();

		try
		{
			int bytes = decoder.nextFrame();

			if(bytes <= 0)
				return new Attempt(null,
					(decoder.isHardware() ? "d3d11va" : "软件")
						+ "解码的第一帧解不出来（AVERROR " + bytes + "）"
						+ nativeReason(decoder));

			return new Attempt(new Probe(true, Reason.OK, fourccOf(track),
				decoder.width(), decoder.height(),
				track == null ? 0 : track.sampleCount(),
				decoder.durationMs()), "");

		}catch(RuntimeException | Error e)
		{
			return new Attempt(null, String.valueOf(e));

		}finally
		{
			decoder.close();
		}
	}

	/**
	 * 失败时那句给日志和卡片看的补充信息。
	 *
	 * <p>
	 * 优先用打开路径记下来的具体原因（阶段 + AVERROR + av_strerror + 走的是硬解还是
	 * 软解，见 {@link FfmpegVideoDecoder#lastOpenError()}），只有 natives 压根没起
	 * 来时才退回库级错误。
	 * </p>
	 */
	private static String describeOpenFailure(String failure)
	{
		if(failure != null && !failure.isEmpty())
			return failure;

		// 打开这一步自己失败（句柄都没拿到）时，native 的原因在**静态槽**里，不在
		// 句柄上——原来这里只回落到 libraryFailure()，而那是"natives 没起来"专用的，
		// natives 正常时它是空串，于是日志出现 `… DECODE_FAILED / avc1 / ` 这种
		// 什么都不说的尾巴（实机就是这个现象，卡了很久）。
		String nativeReason = FfmpegVideoDecoder.lastOpenError();
		String library = libraryFailure();

		if(nativeReason != null && !nativeReason.isEmpty())
			return library.isEmpty() ? nativeReason
				: nativeReason + "（" + library + "）";

		return library;
	}

	/** native 记下的解帧失败原因，拼成 {@code "：<原因>"}；没有就返回空串。 */
	private static String nativeReason(FfmpegVideoDecoder decoder)
	{
		String reason = decoder.lastError();
		return reason.isEmpty() ? "" : "：" + reason;
	}

	/**
	 * 失败时的补充诊断：再看一眼 mp4 里到底有什么。
	 *
	 * <p>
	 * 只在 {@link #probe(Path)} 已经失败之后调用，所以正常文件不会多解析一遍。
	 * </p>
	 */
	private static Probe diagnose(Mp4Probe.Result track, String cause)
	{
		if(track == null || !track.parsed())
			return unplayable(Reason.DECODE_FAILED, cause);

		if(!track.hasVideoTrack())
			return unplayable(Reason.NO_VIDEO_TRACK, cause);

		String fourcc = track.fourcc();

		if(track.sampleCount() <= 0)
			return unplayable(Reason.NO_FRAME, fourcc);

		// 这套解码器里没有的编码（VP8、ProRes……），以及只能靠硬件解的 AV1：
		// 都归到「这个编码放不了」，卡片上写明是哪种编码。AV1 要显卡支持，
		// 没有 AV1 硬解时 av1dec 直接返回 ENOSYS，软解这条路根本不存在
		if(!isPlayableFourcc(fourcc) || needsHardwareDecoder(fourcc))
			return unplayable(Reason.UNSUPPORTED_CODEC, fourcc);

		return unplayable(Reason.DECODE_FAILED, fourcc + " / " + cause);
	}

	/** 后台打开：探测 + 建缓冲 + 起解码线程。打不开时 video 为 null，原因在 probe 里。 */
	static CompletableFuture<Opened> openAsync(Path file)
	{
		return CompletableFuture.supplyAsync(() -> open(file));
	}

	static Opened open(Path file)
	{
		Probe probe = probe(file);

		if(!probe.playable())
			return new Opened(null, probe);

		try
		{
			int[] target =
				chooseSize(drawnSize(), probe.sourceWidth(), probe.sourceHeight());

			return new Opened(new BackgroundVideo(file, probe, target), probe);

		}catch(RuntimeException | Error e)
		{
			// 显存/内存不够、解码器打不开：当作放不了，异常不许丢到渲染线程
			return new Opened(null,
				probe.failed(Reason.DECODE_FAILED, String.valueOf(e)));
		}
	}

	/**
	 * 先按硬件优先开一次（shim 自己在设备建不出来时会退回软解），开不出来再明确
	 * 按软解开一次；两条路的原因都带回去，不成功返回的 {@code decoder} 是 null。
	 */
	private static OpenAttempt openDecoder(Path file)
	{
		FfmpegVideoDecoder decoder =
			FfmpegVideoDecoder.open(file.toString(), true);

		if(decoder != null)
			return new OpenAttempt(decoder, "");

		String hardware = FfmpegVideoDecoder.lastOpenError();

		decoder = FfmpegVideoDecoder.openSoftware(file.toString());

		if(decoder != null)
			return new OpenAttempt(decoder, "");

		return new OpenAttempt(null, "硬解优先：" + hardware + "；软解："
			+ FfmpegVideoDecoder.lastOpenError());
	}

	/** 只按软解开一次，失败原因一并带回去。 */
	private static OpenAttempt openSoftwareOnly(Path file)
	{
		FfmpegVideoDecoder decoder =
			FfmpegVideoDecoder.openSoftware(file.toString());

		if(decoder != null)
			return new OpenAttempt(decoder, "");

		return new OpenAttempt(null, FfmpegVideoDecoder.lastOpenError());
	}

	/**
	 * 库级错误，只在真的读不到时才当成原因。
	 *
	 * <p>
	 * 兜底文案特意写成「原因不明（解码库正常）」，而不是原来那句「解码器打不开」：
	 * 后者会在<b>库其实好好的</b>时候也印进日志，把排查方向整个带偏（实机日志里那句
	 * {@code library=[解码器打不开]} 让人以为是 native 没加载，查了很久，真正的原因
	 * 是 {@link #probe} 成功那条路漏了返回）。这里说清楚"库没问题、是别的原因"，
	 * 下一个看日志的人不用再走一遍。
	 * </p>
	 */
	private static String libraryFailure()
	{
		String reason = FfmpegVideoDecoder.lastLibraryError();
		return reason.isEmpty() ? "原因不明（解码库正常）" : reason;
	}

	// ------------------------------------------------------------------
	// 纯判定（不碰解码器，可单测）
	// ------------------------------------------------------------------

	/**
	 * 解码/上传尺寸：绘制尺寸、源尺寸与上限三者取最小。
	 *
	 * <p>
	 * 三个约束各有各的理由。**绘制尺寸**：壁纸是整屏画的，纹理比屏幕大没有意义，
	 * 比屏幕小就是糊——旧那版固定按 1280x720 上传，在 1080p 及以上的窗口里就是
	 * 被放大着画的，这正是"模糊"的来源。**源尺寸**：解码出来的东西不该被放大
	 * （放开这个约束只是把糊换成另一种糊，还多花显存）。**上限**：见
	 * {@link #MAX_UPLOAD_WIDTH}。
	 * </p>
	 *
	 * @param drawn
	 *            绘制尺寸（窗口的帧缓冲尺寸）；非法时按
	 *            {@link #DEFAULT_WIDTH}x{@link #DEFAULT_HEIGHT}
	 */
	static int[] chooseSize(int[] drawn, int sourceWidth, int sourceHeight)
	{
		boolean usable = drawn != null && drawn.length == 2 && drawn[0] > 0
			&& drawn[1] > 0;
		int drawnWidth = usable ? drawn[0] : DEFAULT_WIDTH;
		int drawnHeight = usable ? drawn[1] : DEFAULT_HEIGHT;

		return fitSize(sourceWidth, sourceHeight,
			Math.min(drawnWidth, MAX_UPLOAD_WIDTH),
			Math.min(drawnHeight, MAX_UPLOAD_HEIGHT));
	}

	/**
	 * 目标尺寸：按最长边缩到 max 以内，保持宽高比，比 max 小的一律不放大。
	 *
	 * <p>
	 * 之所以要缩：解码本身的开销由源分辨率决定，但 YUV→RGB、缩放与纹理上传都由
	 * 这里的尺寸决定。4K 壁纸按原尺寸走，光每帧的缓冲就是 33 MB。
	 * </p>
	 */
	static int[] fitSize(int sourceWidth, int sourceHeight, int maxWidth,
		int maxHeight)
	{
		if(sourceWidth <= 0 || sourceHeight <= 0)
			return new int[]{1, 1};

		float scale = Math.min(1F, Math.min(maxWidth / (float)sourceWidth,
			maxHeight / (float)sourceHeight));

		return new int[]{Math.max(1, Math.round(sourceWidth * scale)),
			Math.max(1, Math.round(sourceHeight * scale))};
	}

	/**
	 * 文件头看着像不像 mp4。
	 *
	 * <p>
	 * 先自己看一眼的意义在于错误信息：解码器对 WebM / 随机字节只会说"打不开"，
	 * 而用户需要知道的是「这个文件根本不是 mp4」。判据是第一个 box 的类型
	 * （偏移 4 起的 4 个 ASCII 字符）以及它的长度字段是否合法。
	 * </p>
	 */
	static boolean looksLikeMp4(byte[] header)
	{
		if(header == null || header.length < 8)
			return false;

		long size = (header[0] & 0xFFL) << 24 | (header[1] & 0xFFL) << 16
			| (header[2] & 0xFFL) << 8 | header[3] & 0xFFL;

		// 1 表示后面还有 64 位的长度，其它情况至少要装得下 box 头
		if(size != 1 && size < 8)
			return false;

		for(String type : new String[]{"ftyp", "moov", "mdat", "free", "skip",
			"wide", "pnot", "styp", "sidx"})
			if(matches(header, 4, type))
				return true;

		return false;
	}

	/**
	 * 这套 native 构建里带了哪些解码器。
	 *
	 * <p>
	 * 与 {@code native/ffmpeg/build-ffmpeg.sh} 的 configure 行一一对应：H.264
	 * （{@code avc1} / {@code avc3}）、HEVC（{@code hvc1} / {@code hev1}）、
	 * VP9（{@code vp09}）、AV1（{@code av01}）、MPEG-4 Part 2（{@code mp4v}）与
	 * Motion JPEG（{@code jpeg}）。VP8、ProRes 这些没有编进来，所以仍然放不了。
	 * </p>
	 */
	static boolean isPlayableFourcc(String fourcc)
	{
		if(fourcc == null)
			return false;

		return switch(fourcc.toLowerCase(Locale.ROOT))
		{
			case "avc1", "avc3", "hvc1", "hev1", "vp09", "av01", "mp4v",
				"jpeg" -> true;
			default -> false;
		};
	}

	/**
	 * 这个编码只能硬件解。
	 *
	 * <p>
	 * 只有 AV1：FFmpeg 里没有软件 AV1 解码器（{@code libavcodec/av1dec.c} 在没有
	 * hwaccel 时直接返回 ENOSYS），所以没有 AV1 硬解的机器上它一律打不开。
	 * </p>
	 */
	static boolean needsHardwareDecoder(String fourcc)
	{
		return fourcc != null && "av01".equals(fourcc.toLowerCase(Locale.ROOT));
	}

	/** 把 fourcc 翻成看得懂的名字，用在不支持时的提示里。 */
	public static String describeFourcc(String fourcc)
	{
		if(fourcc == null || fourcc.isBlank())
			return "unknown";

		return switch(fourcc.toLowerCase(Locale.ROOT))
		{
			case "avc1", "avc3" -> "H.264";
			case "hvc1", "hev1" -> "HEVC/H.265";
			case "vp09" -> "VP9";
			case "vp08" -> "VP8";
			case "av01" -> "AV1";
			case "mp4v" -> "MPEG-4 Part 2";
			case "apch", "apcn", "apcs", "apco", "ap4h" -> "ProRes";
			case "jpeg" -> "Motion JPEG";
			default -> fourcc;
		};
	}

	/**
	 * 绘制尺寸：壁纸铺满整屏，所以它就是窗口的**帧缓冲**尺寸。
	 *
	 * <p>
	 * 不是 GUI 缩放后的逻辑尺寸（{@code Window.getGuiScaledWidth()}）：那张图会被
	 * 放大 {@code guiScale} 倍画到屏幕上，按逻辑尺寸解码等于自己先糊一道，正是这次
	 * 要修的那个问题。{@code Window.getWidth()} 拿到的就是物理像素。
	 * </p>
	 */
	static int[] drawnSize()
	{
		int[] override = drawnSizeOverride;

		if(override != null && override.length == 2)
			return override;

		try
		{
			Minecraft mc = Minecraft.getInstance();
			Window window = mc == null ? null : mc.getWindow();

			if(window != null && window.getWidth() > 0
				&& window.getHeight() > 0)
				return new int[]{window.getWidth(), window.getHeight()};

		}catch(Throwable t)
		{
			// 单测里没有客户端，客户端启动早期也可能还没有窗口。这里连 Throwable
			// 一起接住：链接错误是"这个功能现在问不了"，不是"这个文件放不了"
		}

		return new int[]{DEFAULT_WIDTH, DEFAULT_HEIGHT};
	}

	// ------------------------------------------------------------------
	// 渲染线程这一侧
	// ------------------------------------------------------------------

	int width()
	{
		return width;
	}

	int height()	{
		return height;
	}

	/** 解码线程是不是已经放弃了（文件被删、样本损坏、解码器挂了）。 */
	boolean failed()
	{
		return failed;
	}

	String failure()
	{
		return failure;
	}

	/** 解码线程一共解了多少帧（诊断与单测用，见字段说明）。 */
	long decodedFrames()
	{
		return decodedFrames;
	}

	/**
	 * 取走最新解好的那一帧，没有新帧时返回 null。
	 *
	 * <p>
	 * 调用方用完必须 {@link #recycle(NativeImage)} 还回来：缓冲只有三个，不还的话
	 * 解码线程会一直等在空闲队列上。
	 * </p>
	 *
	 * <p>
	 * 顺便看一眼窗口尺寸：这个方法是渲染线程每帧都会来的地方，而窗口变大变小就是
	 * 「该按多大解码」变了。真正的换缓冲由解码线程做（缓冲是它的）。
	 * </p>
	 */
	NativeImage takeFrame()
	{
		synchronized(lock)
		{
			// 记下来给解码线程判断「背景还在不在显示」，再叫醒它接着解
			lastTakenMs = System.currentTimeMillis();

			int[] target = chooseSize(drawnSize(), sourceWidth, sourceHeight);
			requestedWidth = target[0];
			requestedHeight = target[1];

			NativeImage frame = published;
			published = null;
			checkedOut = frame;
			lock.notifyAll();
			return frame;
		}
	}

	void recycle(NativeImage frame)
	{
		if(frame == null)
			return;

		boolean close = false;

		synchronized(lock)
		{
			if(frame == checkedOut)
				checkedOut = null;

			if(buffers.contains(frame))
				free.add(frame);
			else
			{
				// 换尺寸之前的旧池：还回来的时候才能关
				retired.remove(frame);
				close = true;
			}

			lock.notifyAll();
		}

		if(close)
			frame.close();
	}

	// ------------------------------------------------------------------
	// 解码线程
	// ------------------------------------------------------------------

	private void run()
	{
		long startedAt = System.currentTimeMillis();
		long epochMs = 0;
		long passMs = initialPassMs;
		long lastShownMs = VideoPacing.NO_FRAME;
		int resyncs = 0;
		int minIntervalMs =
			VideoPacing.minFrameIntervalMs(VideoPacing.CAP_FPS);
		boolean restartPass = true;

		try
		{
			openStream(false);

			while(!closed)
			{
				// 背景没在显示就先别解：界面上没人看，CPU 却是实打实在烧
				if(!waitWhileIdle())
					return;

				retargetIfRequested();

				if(restartPass)
				{
					// 重开解码器（或者刚刚从硬件退回软件）之后，解码位置又在
					// 第 0 帧上了：这一轮的起点重新算
					restartPass = false;
					epochMs = System.currentTimeMillis() - startedAt;
					frameIndex = 0;
					lastShownMs = VideoPacing.NO_FRAME;
				}

				int bytes = decoder.nextFrame();

				if(bytes < 0)
				{
					// 硬件解码中途坏了：重开软解接着放（硬件本来就是可选的）
					if(!recoverFromDecodeError(bytes))
						return;

					restartPass = true;
					continue;
				}

				if(bytes == 0)
				{
					// 放完一轮：把实测的一轮时长记下来（元数据里的时长未必准），
					// 回到第 0 帧接着放
					epochMs += passMs;
					frameIndex = 0;
					lastShownMs = VideoPacing.NO_FRAME;
					resyncs = 0;

					if(decoder.seekToStart() < 0)
					{
						// 回不到开头（文件在这期间被换掉了）：重开整条流
						openStream(false);
						restartPass = true;
					}

					continue;
				}

				if(bytes != frameBytes)
				{
					// shim 说好的每帧字节数与实际写出来的不一致：宁可判放不了，
					// 也不要按错的尺寸去读那块缓冲
					failed = true;
					failure = "一帧的字节数不对（" + bytes + " != " + frameBytes
						+ "）";
					return;
				}

				decodedFrames++;

				long ptsMs = Math.round(frameIndex * frameMs);
				frameIndex++;
				passMs = Math.max(passMs, ptsMs + Math.round(frameMs));
				long dueMs = epochMs + ptsMs;
				long nowMs = System.currentTimeMillis() - startedAt;

				VideoPacing.Action action = VideoPacing.decide(nowMs, dueMs,
					lastShownMs, minIntervalMs,
					VideoPacing.SEEK_TOLERANCE_MS, MAX_WAIT_MS);

				if(action == VideoPacing.Action.WAIT)
				{
					// 还没到显示时刻：分片睡过去，醒着检查有没有被 close()
					waitUntil(startedAt + dueMs);

					if(closed)
						return;

					nowMs = System.currentTimeMillis() - startedAt;
					action = VideoPacing.decide(nowMs, dueMs, lastShownMs,
						minIntervalMs, VideoPacing.SEEK_TOLERANCE_MS,
						MAX_WAIT_MS);
				}

				if(action == VideoPacing.Action.RESYNC)
				{
					// 落后挂钟太多：界面隐藏过一段时间，或者解码跟不上实时。
					// 这套 native 接口只有"回第 0 帧"，没有任意定位，所以这里把
					// 这一轮的起点挪到当下——画面继续往下放，只是把落后的时间
					// 丢掉。既不会跳回片头，也不会把落下的几百帧一帧帧解完
					epochMs = nowMs - ptsMs;
					dueMs = nowMs;
					lastShownMs = VideoPacing.NO_FRAME;
					resyncs++;

					if(resyncs == 1 || resyncs % 120 == 0)
						System.out.println("[Background] 视频解码落后挂钟，已重新对齐 "
							+ resyncs + " 次：" + file);

					action = VideoPacing.Action.SHOW;
				}

				// DROP：帧率上限以内该丢的帧，解了但不往上送
				if(action != VideoPacing.Action.SHOW)
					continue;

				if(!publish())
					return;

				lastShownMs = dueMs;
				resyncs = 0;
			}

		}catch(InterruptedException e)
		{
			// close() 打断的，正常收工

		}catch(Throwable t)
		{
			// 文件被删、样本坏了、解码器内部出错、显存不够：都不许丢给渲染线程，
			// 标记失败让 BackgroundManager 退回内置背景
			failed = true;
			failure = String.valueOf(t);

		}finally
		{
			closeStream();
		}
	}

	/**
	 * 渲染线程一段时间没来取帧就在这里等，见 {@link #IDLE_TIMEOUT_MS}。
	 *
	 * <p>
	 * 判据是「距上一次取帧已经超过一秒」（<b>不是</b>「一秒内取过帧」——写反了
	 * 就是解码线程一开始先睡一秒，而渲染线程每来取一次帧又把计时器推后，
	 * 两边互相等，一帧都出不来）。刚构造时 {@code lastTakenMs} 是当前时间，
	 * 所以第一帧不会被这里挡住。
	 * </p>
	 *
	 * <p>
	 * 重新显示时不用补帧：这一轮直接往下放，挂钟那边由主循环的
	 * {@link VideoPacing#isBehind} 判成脱节并重新对齐一次。旧那版是定位到挂钟
	 * 位置，现在没有任意定位，效果差别是"接着放"与"跳到该放的位置"。
	 * </p>
	 *
	 * @return false 表示视频已经关了，解码线程该收工
	 */
	private boolean waitWhileIdle() throws InterruptedException
	{
		synchronized(lock)
		{
			while(!closed
				&& System.currentTimeMillis() - lastTakenMs >= IDLE_TIMEOUT_MS)
				lock.wait(SLEEP_CHUNK_MS);
		}

		return !closed;
	}

	/**
	 * 睡到指定的挂钟时刻（毫秒，绝对时间）。
	 *
	 * <p>
	 * 分片睡是为了两件事：被 {@link #close()} 打断时能立刻收工，以及一个坏掉的
	 * 时间戳不至于让线程永远醒不过来——等多久 {@link VideoPacing#decide} 已经
	 * 判过了，这里只负责睡觉。
	 * </p>
	 */
	private void waitUntil(long deadline) throws InterruptedException
	{
		while(!closed)
		{
			long remaining = deadline - System.currentTimeMillis();

			if(remaining <= 0)
				return;

			Thread.sleep(Math.min(remaining, SLEEP_CHUNK_MS));
		}
	}

	/**
	 * 转换并发布一帧。
	 *
	 * <p>
	 * 转换在锁外做：那是这一路里最慢的一步（4K 源、1440p 输出时逐像素写一张
	 * 3.7M 像素的图要几毫秒），拿着锁做会让渲染线程的 {@link #takeFrame()} 一起
	 * 卡住。
	 * </p>
	 *
	 * @return false 表示视频已经关了，解码线程该收工
	 */
	private boolean publish() throws InterruptedException
	{
		NativeImage buffer;

		synchronized(lock)
		{
			while(!closed && free.isEmpty())
				lock.wait();

			if(closed)
				return false;

			// 渲染线程还没取走上一帧：直接丢掉它，屏幕上只该有最新的那一帧
			if(published != null)
			{
				putBackOrRetire(published);
				published = null;
			}

			buffer = free.poll();
		}

		copyInto(buffer);

		synchronized(lock)
		{
			if(closed)
				return false;

			published = buffer;
			lock.notifyAll();
		}

		return true;
	}

	/** 锁内调用：当前池里的还回空闲队列，旧池里的记进 retired。 */
	private void putBackOrRetire(NativeImage image)
	{
		if(buffers.contains(image))
			free.add(image);
		else if(!retired.contains(image))
			retired.add(image);
	}

	/**
	 * 一帧 native RGBA → 复用缓冲。
	 *
	 * <p>
	 * <b>不交换通道、不翻转行序。</b>shim 交出来的是紧密排列的 8 位 RGBA（内存
	 * 字节序 R,G,B,A）、行序自上而下，与 {@code NativeImage.Format.RGBA} 完全一致；
	 * 这两点都实测过（同一段 mp4 用 JCodec 与 FFmpeg 各解一遍逐像素比对：正着放
	 * 的平均差 2.6，上下翻转 450.8、左右翻转 461.6、红蓝互换 217.7）。旧那条
	 * JCodec 路径要做 ARGB→ABGR 的换算，是因为 {@code BufferedImage.getRGB}
	 * 给的是打包的 ARGB，而不是因为哪一边需要翻转。
	 * </p>
	 *
	 * <p>
	 * 逐像素写：{@code NativeImage} 没有公开底层指针，唯一的批量入口
	 * {@code applyToPixelsInPlace} 也是逐像素回调，所以这就是最快的公开写法。
	 * 实测 1920x1080 是 2.3ms/帧、2560x1440 是 3.7ms/帧。
	 * </p>
	 */
	private void copyInto(NativeImage target)
	{
		byte[] source = decoder.frameBuffer();
		int width = target.getWidth();
		int height = target.getHeight();

		for(int y = 0; y < height; y++)
			for(int x = 0; x < width; x++)
			{
				int i = (y * width + x) * 4;
				int r = source[i] & 0xFF;
				int g = source[i + 1] & 0xFF;
				int b = source[i + 2] & 0xFF;
				int a = source[i + 3] & 0xFF;

				// NativeImage 的整数像素是 ABGR：低字节是红
				target.setPixelRGBA(x, y, a << 24 | b << 16 | g << 8 | r);
			}
	}

	// ------------------------------------------------------------------
	// 尺寸变化与解码器的生死
	// ------------------------------------------------------------------

	/** 渲染线程记下的目标尺寸变了就换一套缓冲，并让 swscale 按新尺寸输出。 */
	private void retargetIfRequested() throws IOException
	{
		int newWidth;
		int newHeight;

		synchronized(lock)
		{
			if(requestedWidth == width && requestedHeight == height)
				return;

			// 拖窗口边缘时尺寸每帧都变，隔一会儿再跟
			if(System.currentTimeMillis() - lastRetargetMs < RETARGET_MIN_INTERVAL_MS)
				return;

			newWidth = requestedWidth;
			newHeight = requestedHeight;
		}

		if(newWidth <= 0 || newHeight <= 0)
			return;

		retarget(newWidth, newHeight);
	}

	/**
	 * 换解码尺寸：解码器的输出尺寸与三个复用缓冲一起换。
	 *
	 * <p>
	 * 旧池里那几张不能立刻关——渲染线程可能正拿着其中一张在拷。空闲的直接关，
	 * 在 {@link #published} / {@link #checkedOut} 上的记进 {@link #retired}，
	 * 等 {@link #recycle(NativeImage)} 还回来时再关。
	 * </p>
	 */
	private void retarget(int newWidth, int newHeight) throws IOException
	{
		int needed = decoder.setOutputSize(newWidth, newHeight);

		if(needed <= 0)
			throw new IOException("setOutputSize(" + newWidth + "x" + newHeight
				+ ") 失败：" + needed + nativeReason(decoder));

		List<NativeImage> fresh = new ArrayList<>(BUFFER_COUNT);

		for(int i = 0; i < BUFFER_COUNT; i++)
			fresh.add(new NativeImage(NativeImage.Format.RGBA, newWidth,
				newHeight, false));

		List<NativeImage> closing = new ArrayList<>(BUFFER_COUNT);

		synchronized(lock)
		{
			for(NativeImage image : buffers)
				if(image == published || image == checkedOut)
					retired.add(image);
				else
					closing.add(image);

			buffers.clear();
			free.clear();
			buffers.addAll(fresh);
			free.addAll(fresh);
			published = null;
			checkedOut = null;

			width = newWidth;
			height = newHeight;
			lastRetargetMs = System.currentTimeMillis();
		}

		frameBytes = needed;

		for(NativeImage image : closing)
			image.close();
	}

	/** 打开解码器并设好输出尺寸。解码线程专用。 */
	private void openStream(boolean forceSoftware) throws IOException
	{
		closeStream();

		OpenAttempt opening =
			forceSoftware ? openSoftwareOnly(file) : openDecoder(file);

		if(opening.decoder() == null)
			throw new IOException(
				"解码器打不开：" + describeOpenFailure(opening.failure()));

		decoder = opening.decoder();
		applyOutputSize();

		// 时间轴按帧率算：容器里的帧率先用，没有就用「帧数 / 时长」推，
		// 再没有就按 30fps——宁可时间轴粗一点，也不要一帧都放不出来
		double fps = decoder.frameRate();

		if(fps <= 0 && frameCount > 0 && decoder.durationMs() > 0)
			fps = frameCount * 1000.0 / decoder.durationMs();

		frameMs = 1000.0 / (fps > 0 ? fps : FALLBACK_FPS);
	}

	private void applyOutputSize() throws IOException
	{
		frameBytes = decoder.setOutputSize(width, height);

		if(frameBytes <= 0)
			throw new IOException("setOutputSize(" + width + "x" + height
				+ ") 失败：" + frameBytes + nativeReason(decoder));
	}

	/**
	 * 解到一半出错：硬件这条路挂掉时改用软解接着放（硬件只是可选的），软解也
	 * 挂掉就把这个文件判成放不了。
	 */
	private boolean recoverFromDecodeError(int error)
	{
		String reason = nativeReason(decoder);

		if(decoder.isHardware() && softwareRetries < MAX_SOFTWARE_RETRIES)
		{
			softwareRetries++;
			System.out.println("[Background] 硬件解码出错（AVERROR " + error
				+ "）" + reason + "，改用软件解码继续：" + file);

			try
			{
				openStream(true);
				return true;

			}catch(IOException | RuntimeException | Error e)
			{
				failed = true;
				failure = String.valueOf(e);
				return false;
			}
		}

		failed = true;
		failure = "解码失败（AVERROR " + error + "）" + reason;
		return false;
	}

	private void closeStream()
	{
		if(decoder != null)
		{
			decoder.close();
			decoder = null;
		}

		frameBytes = 0;
	}

	/** 等第一帧解好，见构造函数的说明。 */
	private void waitForFirstFrame(long timeoutMs)
	{
		long deadline = System.currentTimeMillis() + timeoutMs;

		synchronized(lock)
		{
			while(published == null && !failed && !closed
				&& System.currentTimeMillis() < deadline)
				try
				{
					lock.wait(Math.max(1L,
						deadline - System.currentTimeMillis()));
				}catch(InterruptedException e)
				{
					Thread.currentThread().interrupt();
					return;
				}
		}
	}

	@Override
	public void close()
	{
		closed = true;

		synchronized(lock)
		{
			lock.notifyAll();
		}

		Thread worker = thread;

		if(worker == null || worker == Thread.currentThread())
		{
			releaseResources();
			return;
		}

		worker.interrupt();

		if(!worker.isAlive())
		{
			releaseResources();
			return;
		}

		// 解码线程可能正卡在一次很长的解码里（4K 一帧就是十几毫秒，硬件路径上
		// 还要等 GPU 回读），而 close() 是从**渲染线程**调的（切换背景、删除、
		// 资源重载）：在这里 join 两秒就是画面卡两秒。所以交给一个后台线程收尾
		// ——它等解码线程退出之后再关缓冲。缓冲那几十 MB 晚几毫秒释放没有影响。
		Thread reaper = new Thread(() -> reap(worker),
			"WurstB-BackgroundVideoReaper");
		reaper.setDaemon(true);
		reaper.start();
	}

	/** 等解码线程退出，然后关掉缓冲；等不到就宁可漏掉这几 MB。 */
	private void reap(Thread worker)
	{
		try
		{
			worker.join(JOIN_TIMEOUT_MS);
		}catch(InterruptedException e)
		{
			Thread.currentThread().interrupt();
			return;
		}

		if(worker.isAlive())
		{
			// 解码线程还卡在解码器里没退出来。这时候关掉 NativeImage 会和它
			// 正在写的像素撞车（直接崩客户端），宁可漏掉这几 MB 显存
			System.out.println("[Background] 视频解码线程在 " + JOIN_TIMEOUT_MS
				+ "ms 内没有退出，缓冲不再回收：" + file);
			return;
		}

		releaseResources();
	}

	/** 关缓冲与解码器。调用前必须保证解码线程已经退出，或者根本还没起来。 */
	private void releaseResources()
	{
		List<NativeImage> closing = new ArrayList<>(BUFFER_COUNT + 2);

		synchronized(lock)
		{
			published = null;
			checkedOut = null;
			closing.addAll(buffers);
			buffers.clear();
			free.clear();
			closing.addAll(retired);
			retired.clear();
		}

		for(NativeImage image : closing)
			image.close();

		closeStream();
	}

	// ------------------------------------------------------------------
	// 小工具
	// ------------------------------------------------------------------

	private static Probe unplayable(Reason reason, String detail)
	{
		return new Probe(false, reason, detail, 0, 0, 0, 0);
	}

	private static String fourccOf(Mp4Probe.Result track)
	{
		return track == null ? "" : track.fourcc();
	}

	private static byte[] readHeader(Path file) throws IOException
	{
		try(InputStream in = Files.newInputStream(file))
		{
			return in.readNBytes(16);
		}
	}

	private static boolean matches(byte[] data, int offset, String ascii)
	{
		if(data.length < offset + ascii.length())
			return false;

		for(int i = 0; i < ascii.length(); i++)
			if((data[offset + i] & 0xFF) != ascii.charAt(i))
				return false;

		return true;
	}
}
