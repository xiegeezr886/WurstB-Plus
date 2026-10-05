/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import org.jcodec.api.JCodecException;
import org.jcodec.api.PictureWithMetadata;
import org.jcodec.api.awt.AWTFrameGrab;
import org.jcodec.common.DemuxerTrack;
import org.jcodec.common.DemuxerTrackMeta;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.containers.mp4.demuxer.AbstractMP4DemuxerTrack;
import org.jcodec.containers.mp4.demuxer.MP4Demuxer;
import org.jcodec.scale.AWTUtil;

import com.mojang.blaze3d.platform.NativeImage;

/**
 * 视频背景：用 JCodec（纯 Java 的 H.264 解码器）把 mp4 一帧帧解出来。
 *
 * <p>
 * 与 GIF 那条路（{@link BackgroundClip}）最大的不同是<b>不能先全解完再放</b>：
 * 一段 10 秒的 720p30 视频解成位图是 300 帧 × 3.7 MB ≈ 1.1 GB。所以这里是流式的——
 * 解码线程按挂钟一次解一帧，解好的帧放进 3 个复用缓冲里，渲染线程取走最新的一帧
 * 拷进纹理。缓冲不会无限增长；渲染线程超过一秒不来取帧（标题界面不在显示）时
 * 解码线程直接停下，不白烧 CPU，重新显示时再按挂钟定位。
 * </p>
 *
 * <p>
 * 时间以挂钟为准，不以「解了多少帧」为准：每帧的 PTS 换算成绝对显示时刻，落后
 * 挂钟太多就跳一次（见 {@link VideoPacing}）。这样 60fps 的源在 30fps 上限下不会
 * 半速播放，界面隐藏一段时间回来也不会把落下的几百帧一帧帧解完。
 * </p>
 *
 * <p>
 * 解不出来的文件（没有 H.264 轨道、HEVC/VP9/AV1、空文件、坏文件）一律降级成
 * 「放不了」：探测阶段返回 {@link Probe} 说明原因，播放中途坏掉则由
 * {@link #failed()} 报告，两种情况都不会把异常丢到渲染线程上。
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
	/** 最长边超过这个尺寸就缩小；小文件保持原尺寸，不放大。 */
	public static final int MAX_WIDTH = 1280;
	public static final int MAX_HEIGHT = 720;

	/** 复用缓冲的个数：一个在解、一个等渲染线程取、一个在拷进纹理。 */
	private static final int BUFFER_COUNT = 3;

	/** 等待期间的分片长度：醒来看一眼有没有被 close()。 */
	private static final long SLEEP_CHUNK_MS = 250;

	/**
	 * 单帧最多等这么久。
	 *
	 * <p>
	 * 少数壁纸确实有长达数秒的静止段（PTS 上就是一个大空档），那种情况等下去是
	 * 对的；但坏掉的时间戳（比如跳到几小时后）会让画面永久冻住。等不满就按挂钟
	 * 重新定位一次——定位只是跳到关键帧，代价很小。
	 * </p>
	 */
	private static final long MAX_WAIT_MS = 5_000;

	/** 连续跳这么多次还没恢复播放，就认为这个文件没法放了。 */
	private static final int MAX_RESYNCS = 8;

	/** 等解码线程退出的上限，见 {@link #close()}。 */
	private static final long JOIN_TIMEOUT_MS = 2_000;
	/**
	 * 渲染线程多久不来取帧就认为背景没在显示。
	 *
	 * <p>
	 * 标题界面不在了（玩家进了游戏、开着别的界面）时没人来取帧，这时候继续解码
	 * 就是白烧 CPU：一段 1080p 的 H.264 光解码就能吃掉小半个核心。留 1 秒是给
	 * 标题界面偶尔的卡顿留余量，免得每次掉帧都停一次。
	 * </p>
	 */
	private static final long IDLE_TIMEOUT_MS = 1_000;

	/** 构造时等第一帧的上限：等到了就不会有那一帧黑屏，等不到也不影响播放。 */
	private static final long FIRST_FRAME_TIMEOUT_MS = 500;

	private static final long NO_PTS = Long.MIN_VALUE;
	private static final long NO_RESYNC = Long.MIN_VALUE;

	private final Path file;
	private final int width;
	private final int height;
	private final long initialPassMs;

	/** 全部缓冲，只增不减，{@link #close()} 按它逐个关。 */
	private final List<NativeImage> buffers = new ArrayList<>(BUFFER_COUNT);
	private final ArrayDeque<NativeImage> free = new ArrayDeque<>(BUFFER_COUNT);
	private final Object lock = new Object();

	/** 解码线程刚解好、等渲染线程取走的那一帧。 */
	private NativeImage published;

	/** 渲染线程最后一次来取帧的时刻，用来判断背景还在不在显示。 */
	private volatile long lastTakenMs;

	/** 缩放到目标尺寸用的暂存：每帧都新建一张的话，720p 就是每秒上百 MB 的垃圾。 */
	private BufferedImage scaled;
	private Graphics2D scaledGraphics;
	private int[] scaledArgb;

	private Thread thread;

	/** 解码用的通道与取帧器，只有解码线程碰。 */
	private SeekableByteChannel channel;
	private AWTFrameGrab grabber;

	private volatile boolean closed;
	private volatile boolean failed;
	private volatile String failure = "";

	/**
	 * 一个文件到底能不能放，以及为什么不能放。
	 *
	 * @param detail
	 *            给日志和状态栏用的补充信息：不能放时是编码 fourcc 或异常摘要
	 */
	public record Probe(boolean playable, Reason reason, String detail,
		int sourceWidth, int sourceHeight, int frameCount, long durationMs)
	{
		/** 目标尺寸：按最长边缩到 1280x720 以内，小文件不放大。 */
		int[] targetSize()
		{
			return fitSize(sourceWidth, sourceHeight, MAX_WIDTH, MAX_HEIGHT);
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

	private BackgroundVideo(Path file, Probe probe)
	{
		this.file = file;
		initialPassMs = Math.max(VideoPacing.MIN_PASS_MS, probe.durationMs());
		lastTakenMs = System.currentTimeMillis();

		int[] target = probe.targetSize();
		width = target[0];
		height = target[1];

		boolean ready = false;

		try
		{
			for(int i = 0; i < BUFFER_COUNT; i++)
				buffers.add(new NativeImage(NativeImage.Format.RGBA, width,
					height, false));

			free.addAll(buffers);

			scaled = new BufferedImage(width, height,
				BufferedImage.TYPE_INT_ARGB);
			scaledGraphics = scaled.createGraphics();
			scaledGraphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
				RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			scaledArgb = new int[width * height];

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
	 * 只做三件事：挡住明显不是 mp4 的文件、让 JCodec 建出取帧器（不是 H.264 的
	 * fourcc 在这里就会被它拒掉）、真的解出第一帧。最后一件事是必要的——一个
	 * fourcc 写着 avc1、样本却缺一半的文件，只有解一次才知道。
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

		SeekableByteChannel channel = null;

		try
		{
			channel = NIOUtils.readableChannel(file.toFile());
			AWTFrameGrab grabber = AWTFrameGrab.createAWTFrameGrab(channel);
			DemuxerTrack track = grabber.getVideoTrack();
			DemuxerTrackMeta meta = track.getMeta();
			String fourcc = fourccOf(track);

			PictureWithMetadata first = grabber.getNativeFrameWithMetadata();

			if(first == null)
				return unplayable(Reason.NO_FRAME, fourcc);

			// 解码出来的画面可能带着 H.264 的裁剪矩形（1080p 的编码高度往往是
			// 1088），旋转过的手机视频还要把宽高换过来——用 GetCodedSize() 会得到
			// 一个比实际画面大的尺寸，贴上去就是一条黑边
			int[] size = orientedSize(first.getPicture().getCroppedWidth(),
				first.getPicture().getCroppedHeight(),
				first.getOrientation());

			return new Probe(true, Reason.OK, fourcc, size[0], size[1],
				meta.getTotalFrames(), Math.round(meta.getTotalDuration() * 1000));

		}catch(IOException | JCodecException | RuntimeException | Error e)
		{
			// 走到这里说明取帧器没建起来或者第一帧没解出来：再解析一次文件头，
			// 把「不是 H.264」和「文件坏了」区分开。这条路径只在失败时走
			return diagnose(file, e);

		}finally
		{
			NIOUtils.closeQuietly(channel);
		}
	}

	/**
	 * 失败时的补充诊断：再看一眼 mp4 里到底有什么。
	 *
	 * <p>
	 * 只在 {@link #probe(Path)} 已经失败之后调用，所以正常文件不会多解析一遍。
	 * </p>
	 */
	private static Probe diagnose(Path file, Throwable cause)
	{
		SeekableByteChannel channel = null;

		try
		{
			channel = NIOUtils.readableChannel(file.toFile());
			MP4Demuxer demuxer = MP4Demuxer.createMP4Demuxer(channel);
			DemuxerTrack track = demuxer.getVideoTrack();

			if(track == null)
				return unplayable(Reason.NO_VIDEO_TRACK, cause.toString());

			DemuxerTrackMeta meta = track.getMeta();
			String fourcc = fourccOf(track);

			if(meta.getTotalFrames() <= 0 || meta.getTotalDuration() <= 0)
				return unplayable(Reason.NO_FRAME, fourcc);

			if(!isPlayableFourcc(fourcc))
				return unplayable(Reason.UNSUPPORTED_CODEC, fourcc);

			return unplayable(Reason.DECODE_FAILED, fourcc + " / " + cause);

		}catch(IOException | RuntimeException e)
		{
			return unplayable(Reason.DECODE_FAILED, cause.toString());

		}finally
		{
			NIOUtils.closeQuietly(channel);
		}
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
			return new Opened(new BackgroundVideo(file, probe), probe);

		}catch(RuntimeException | Error e)
		{
			// 显存/内存不够、或者 AWT 起不来：当作放不了，异常不许丢到渲染线程
			return new Opened(null,
				probe.failed(Reason.DECODE_FAILED, String.valueOf(e)));
		}
	}

	// ------------------------------------------------------------------
	// 纯判定（不碰解码器，可单测）
	// ------------------------------------------------------------------

	/**
	 * 目标尺寸：按最长边缩到 max 以内，保持宽高比，比 max 小的一律不放大。
	 *
	 * <p>
	 * 之所以要缩：解码本身的开销由源分辨率决定，但每帧的 YUV→RGB、缩放与纹理上传
	 * 都由这里的尺寸决定。4K 壁纸按原尺寸走，光上传一帧就是 33 MB。
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

	/** 带旋转的视频（手机竖拍）显示尺寸是反过来的。 */
	static int[] orientedSize(int width, int height,
		DemuxerTrackMeta.Orientation orientation)
	{
		if(orientation == DemuxerTrackMeta.Orientation.D_90
			|| orientation == DemuxerTrackMeta.Orientation.D_270)
			return new int[]{height, width};

		return new int[]{width, height};
	}

	/**
	 * 文件头看着像不像 mp4。
	 *
	 * <p>
	 * 先自己看一眼的意义在于错误信息：JCodec 对 WebM / 随机字节抛的都只是「解析
	 * 失败」，而用户需要知道的是「这个文件根本不是 mp4」。判据是第一个 box 的
	 * 类型（偏移 4 起的 4 个 ASCII 字符）以及它的长度字段是否合法。
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
	 * JCodec 0.2.5 里能把 fourcc 认成 H.264 的只有 {@code avc1}
	 * （见 {@code Codec.codecByFourcc}）。{@code avc3} 虽然也是 H.264，但参数集
	 * 在码流里而不在 avcC 盒子里，这套解码器读不到，所以同样按放不了处理。
	 */
	static boolean isPlayableFourcc(String fourcc)
	{
		return "avc1".equals(fourcc);
	}

	/** 把 fourcc 翻成看得懂的名字，用在不支持时的提示里。 */
	public static String describeFourcc(String fourcc)
	{
		if(fourcc == null || fourcc.isBlank())
			return "unknown";

		return switch(fourcc.toLowerCase(Locale.ROOT))
		{
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
	 * AWT 的整数像素是 ARGB，而 {@code NativeImage} 的整数像素接口是 ABGR：红蓝要
	 * 交换，写错了就是一张颜色反过来的壁纸。与 {@link BackgroundClip#toImage} 里对
	 * GIF 帧做的是同一件事（那边是内联写法）。
	 */
	static int argbToAbgr(int argb)
	{
		return (argb & 0xFF00FF00) | (argb & 0xFF) << 16
			| (argb >> 16 & 0xFF);
	}

	// ------------------------------------------------------------------
	// 渲染线程这一侧
	// ------------------------------------------------------------------

	int width()
	{
		return width;
	}

	int height()
	{
		return height;
	}

	/** 解码线程是不是已经放弃了（文件被删、样本损坏、定位不出来）。 */
	boolean failed()
	{
		return failed;
	}

	String failure()
	{
		return failure;
	}

	/**
	 * 取走最新解好的那一帧，没有新帧时返回 null。
	 *
	 * <p>
	 * 调用方用完必须 {@link #recycle(NativeImage)} 还回来：缓冲只有三个，不还的话
	 * 解码线程会一直等在空闲队列上。
	 * </p>
	 */
	NativeImage takeFrame()
	{
		synchronized(lock)
		{
			// 记下来给解码线程判断「背景还在不在显示」，再叫醒它接着解
			lastTakenMs = System.currentTimeMillis();
			NativeImage frame = published;
			published = null;
			lock.notifyAll();
			return frame;
		}
	}

	void recycle(NativeImage frame)
	{
		if(frame == null)
			return;

		synchronized(lock)
		{
			free.add(frame);
			lock.notifyAll();
		}
	}

	// ------------------------------------------------------------------
	// 解码线程
	// ------------------------------------------------------------------

	private void run()
	{
		long startedAt = System.currentTimeMillis();
		long epochMs = 0;
		long passMs = initialPassMs;
		long firstPtsMs = NO_PTS;
		long lastShownMs = VideoPacing.NO_FRAME;
		long resyncMs = NO_RESYNC;
		int minIntervalMs =
			VideoPacing.minFrameIntervalMs(VideoPacing.CAP_FPS);
		int resyncs = 0;

		try
		{
			openStream();

			while(!closed)
			{
				// 背景没在显示就先别解：界面上没人看，CPU 却是实打实在烧
				if(!waitWhileIdle())
					return;

				PictureWithMetadata frame = grabber.getNativeFrameWithMetadata();

				if(frame == null)
				{
					// 放完一轮：把实测的一轮时长记下来（元数据里的时长未必准），
					// 回到第 0 帧接着放
					epochMs += passMs;
					firstPtsMs = NO_PTS;
					lastShownMs = VideoPacing.NO_FRAME;
					resyncMs = NO_RESYNC;
					rewind();
					continue;
				}

				long ptsMs = Math.round(frame.getTimestamp() * 1000.0);

				if(firstPtsMs == NO_PTS)
					firstPtsMs = ptsMs;

				long dueMs = epochMs + (ptsMs - firstPtsMs);
				passMs = Math.max(passMs, dueMs - epochMs
					+ Math.round(frame.getDuration() * 1000.0));

				if(resyncMs != NO_RESYNC)
				{
					// 刚跳到一个关键帧上：关键帧到目标之间的帧必须解掉（H.264 不能
					// 从中间接上），但不该显示，否则画面会先倒回去一段
					if(dueMs < resyncMs)
						continue;

					resyncMs = NO_RESYNC;
				}

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
					// 落后太多（界面隐藏过一段时间、或者解码跟不上实时）或时间轴
					// 不可信：按挂钟跳一次。绝不把落下的几百帧一帧帧解完
					if(++resyncs > MAX_RESYNCS)
					{
						failed = true;
						failure = "定位 " + resyncs + " 次仍然没有恢复播放";
						return;
					}

					long targetMs = VideoPacing.loopedTime(nowMs, passMs);
					epochMs = VideoPacing.loopBase(nowMs, passMs);
					resyncMs = nowMs;
					lastShownMs = VideoPacing.NO_FRAME;
					grabber.seekToSecondSloppy(targetMs / 1000.0);
					continue;
				}

				// DROP：帧率上限以内该丢的帧，解了但不往上送
				if(action != VideoPacing.Action.SHOW)
					continue;

				if(!publish(frame))
					return;

				lastShownMs = dueMs;
				resyncs = 0;
			}

		}catch(InterruptedException e)
		{
			// close() 打断的，正常收工

		}catch(IOException | JCodecException | RuntimeException | Error e)
		{
			// 文件被删、样本坏了、解码器内部出错：都不许丢给渲染线程，
			// 标记失败让 BackgroundManager 退回内置背景
			failed = true;
			failure = String.valueOf(e);

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
	 * 重新显示时不用补帧：挂钟已经走过去了，回到主循环后
	 * {@link VideoPacing#isBehind} 会判成脱节，直接按挂钟定位到该放的位置。
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
	 * 转换在锁外做：那是这一路里最慢的一步（YUV→RGB、缩放、写像素），拿着锁做会
	 * 让渲染线程的 {@link #takeFrame()} 一起卡住。
	 * </p>
	 *
	 * @return false 表示视频已经关了，解码线程该收工
	 */
	private boolean publish(PictureWithMetadata frame) throws InterruptedException
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
				free.add(published);
				published = null;
			}

			buffer = free.poll();
		}

		convert(frame, buffer);

		synchronized(lock)
		{
			if(closed)
				return false;

			published = buffer;
			lock.notifyAll();
		}

		return true;
	}

	/**
	 * 一帧 JCodec 画面 → 目标尺寸的复用缓冲。
	 *
	 * <p>
	 * 颜色转换交给 JCodec 自己的 {@link AWTUtil}：那套 YUV→RGB 矩阵是跟着它的
	 * 解码器调的（视频范围、chroma 上下采样、1080p 的裁剪矩形、旋转），自己写一遍
	 * 只能省一次拷贝，却很容易把颜色或者边缘搞错。代价是每个显示的帧会多一张源
	 * 尺寸的 {@code BufferedImage} 垃圾，这是那个接口决定的。
	 * </p>
	 */
	private void convert(PictureWithMetadata frame, NativeImage target)
	{
		BufferedImage source = AWTUtil.toBufferedImage(frame.getPicture(),
			frame.getOrientation());

		if(source.getWidth() != width || source.getHeight() != height)
			scaledGraphics.drawImage(source, 0, 0, width, height, null);

		BufferedImage pixels =
			source.getWidth() == width && source.getHeight() == height ? source
				: scaled;
		pixels.getRGB(0, 0, width, height, scaledArgb, 0, width);

		for(int y = 0; y < height; y++)
			for(int x = 0; x < width; x++)
				target.setPixelRGBA(x, y,
					argbToAbgr(scaledArgb[y * width + x]));
	}

	private void openStream() throws IOException, JCodecException
	{
		closeStream();
		channel = NIOUtils.readableChannel(file.toFile());
		grabber = AWTFrameGrab.createAWTFrameGrab(channel);
	}

	private void closeStream()
	{
		NIOUtils.closeQuietly(channel);
		channel = null;
		grabber = null;
	}

	/**
	 * 回到第 0 帧。
	 *
	 * <p>
	 * 用精确定位而不是重开文件：{@code seekToSecondPrecise(0)} 就是把解复用器拨回
	 * 第 0 个样本再解一次首帧之前的前导帧，比重新解析一遍 moov 便宜。万一它失败
	 * （文件在这一轮里被换掉了），就重开整条流。
	 * </p>
	 */
	private void rewind() throws IOException, JCodecException
	{
		try
		{
			grabber.seekToSecondPrecise(0);
		}catch(IOException | JCodecException | RuntimeException e)
		{
			openStream();
		}
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

		// 解码线程可能正卡在一次很长的解码里，而 close() 是从**渲染线程**调的
		// （切换背景、删除、资源重载）：在这里 join 两秒就是画面卡两秒。所以交给
		// 一个后台线程收尾——它等解码线程退出之后再关缓冲。缓冲那几 MB 显存晚
		// 几毫秒释放没有任何影响。
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
			// 解码线程还卡在 JCodec / AWT 里没退出来。这时候关掉 NativeImage
			// 会和它正在写的像素撞车（直接崩客户端），宁可漏掉这几 MB 显存
			System.out.println("[Background] 视频解码线程在 " + JOIN_TIMEOUT_MS
				+ "ms 内没有退出，缓冲不再回收：" + file);
			return;
		}

		releaseResources();
	}

	/** 关缓冲与暂存。调用前必须保证解码线程已经退出，或者根本还没起来。 */
	private void releaseResources()
	{
		synchronized(lock)
		{
			published = null;
			free.clear();

			for(NativeImage image : buffers)
				image.close();

			buffers.clear();
		}

		if(scaledGraphics != null)
		{
			scaledGraphics.dispose();
			scaledGraphics = null;
		}

		scaled = null;
		scaledArgb = null;
		closeStream();
	}

	// ------------------------------------------------------------------
	// 小工具
	// ------------------------------------------------------------------

	private static Probe unplayable(Reason reason, String detail)
	{
		return new Probe(false, reason, detail, 0, 0, 0, 0);
	}

	private static String fourccOf(DemuxerTrack track)
	{
		if(track instanceof AbstractMP4DemuxerTrack mp4)
			return mp4.getFourcc();

		return null;
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
