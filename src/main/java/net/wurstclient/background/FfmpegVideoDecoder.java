/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

import java.util.Locale;

/**
 * 到本仓库自带的精简版 FFmpeg（LGPL、仅解码器）的 JNI 桥。
 *
 * <p>
 * 视频背景（{@link BackgroundVideo}）就是通过它解码的：解码线程按挂钟取帧、把帧
 * 交给 3 个复用缓冲，渲染线程再拷进纹理。这个类本身只做"薄封装"——不排队、不计时、
 * 不管循环，节奏全在调用方（{@code VideoPacing}）。
 * </p>
 *
 * <h2>像素格式</h2>
 * <p>
 * {@link #nextFrame} 写出来的是 <b>紧密排列的 8 位 RGBA，内存字节序为 R,G,B,A</b>
 * （即 FFmpeg 的 {@code AV_PIX_FMT_RGBA}），stride 恒等于 {@code width * 4}，
 * 行序是从上到下的自然图像顺序。
 * </p>
 *
 * <p>
 * 这一点很关键：它正好就是 Minecraft
 * {@code com.mojang.blaze3d.platform.NativeImage.Format.RGBA} 的内存字节序，
 * 所以调用方可以把这块 buffer 直接交给 {@code NativeImage} 上传，不需要任何通道交换，
 * 也不需要翻转行序。注意它<b>不等于</b> {@code BufferedImage.getRGB()} 返回的那个打包
 * int（那个是 ARGB）：旧的 JCodec 路径要交换红蓝是因为那个接口，而不是因为行序。
 * </p>
 *
 * <h2>返回值约定</h2>
 * <ul>
 * <li>{@link #open} 失败返回 {@code null}，不抛异常。</li>
 * <li>{@link #nextFrame} 返回值 {@code > 0} 表示本次写入的字节数，正好是
 * {@code width * height * 4}；返回 {@code 0} 表示这一遍已经放到结尾（循环的判定权在调用方）；
 * 返回 {@code < 0} 是 FFmpeg 的 AVERROR 负值。</li>
 * <li>{@link #seekToStart} 返回 {@code 0} 成功，负值失败。</li>
 * </ul>
 *
 * <h2>线程模型</h2>
 * <p>
 * 本类不是线程安全的，一个实例只能被一条线程使用。native 侧没有自己的线程、队列或节奏控制，
 * 计时与循环都由 Java 调用方决定——{@link BackgroundVideo} 自己开工作线程，
 * {@code VideoPacing} 决定哪一帧真的显示。
 * </p>
 *
 * <p>
 * 硬件解码（d3d11va）是可选的、绝不是硬性要求：{@link #openHardware} 失败会自动落回软解，
 * 用 {@link #isHardware()} 可以查询实际走的是哪条路。解码中途硬件出问题时会返回负值，
 * 调用方应该重新用 {@link #openSoftware} 打开同一个文件。
 * </p>
 */
public final class FfmpegVideoDecoder implements AutoCloseable
{
	/** 本类产出的像素格式，见类文档。 */
	public static final String PIXEL_FORMAT = "RGBA";

	/** 每像素字节数。 */
	public static final int BYTES_PER_PIXEL = 4;

	private static volatile boolean libraryReady;
	private static volatile String libraryFailure = "";

	/**
	 * 最近一次 {@link #open} 失败的原因，成功时是空串。
	 *
	 * <p>
	 * 之所以要在 Java 侧也留一份：打开失败的 native 句柄在上面那个方法里当场就
	 * {@code nativeClose} 掉了，之后再也读不到它身上的原因，而模组的日志行
	 * （{@code [Background] … 不能播放：<Reason> / <fourcc> / <detail>}）正是在那之后
	 * 才拼出来的。原因里带的是「哪一步 / AVERROR 值 / av_strerror 文字 / 走的是硬解
	 * 还是软解」，见 native/ffmpeg/README.md 第 8 节。
	 * </p>
	 *
	 * <p>
	 * 是 {@link ThreadLocal} 而不是静态字段：选择界面会把所有视频背景<b>并发</b>探测
	 * 一遍，用一个共享字段的话，"A 打开失败"与"B 打开成功"会互相覆盖——那正好又变成
	 * 这个类要修的那个"原因莫名其妙是空的"的老问题。打开它的线程读它，语义也最直白。
	 * </p>
	 */
	private static final ThreadLocal<String> lastOpenFailure = new ThreadLocal<>();

	/** native 句柄，0 表示未打开或已关闭。 */
	private long handle;

	/** 这个实例上最近一次失败的完整原因，成功时是空串。 */
	private volatile String lastError = "";

	/** {@link #nextFrame} 用的复用缓冲，长度 = width*height*4 + JNI 要求的尾部余量。 */
	private byte[] frame;

	private int width;
	private int height;
	private int outWidth;
	private int outHeight;
	private double frameRate;
	private long durationMs;
	private boolean hardware;

	private FfmpegVideoDecoder(long handle, int width, int height,
		double frameRate, long durationMs, boolean hardware)
	{
		this.handle = handle;
		this.width = width;
		this.height = height;
		this.outWidth = width;
		this.outHeight = height;
		this.frameRate = frameRate;
		this.durationMs = durationMs;
		this.hardware = hardware;
		this.frame = new byte[width * height * BYTES_PER_PIXEL
			+ FfmpegNatives.PADDING];
	}

	// ------------------------------------------------------------------ open

	/**
	 * 优先硬件解码打开；硬件不可用时自动改用软件解码。
	 *
	 * @return 打不开就返回 {@code null}（文件不存在、没有视频流、格式不支持……），
	 *         失败原因见 {@link #lastOpenError()}（natives 没起来时见
	 *         {@link #lastLibraryError()}）与标准错误输出。
	 */
	public static FfmpegVideoDecoder openHardware(String path)
	{
		return open(path, true);
	}

	/** 纯软件解码打开（不碰硬件栈，用于对照测试或硬件路径出问题时的退路）。 */
	public static FfmpegVideoDecoder openSoftware(String path)
	{
		return open(path, false);
	}

	/** 按 {@code wantHardware} 打开，见 {@link #openHardware}。 */
	public static FfmpegVideoDecoder open(String path, boolean wantHardware)
	{
		if(path == null || path.isEmpty())
		{
			lastOpenFailure.set("路径为空");
			return null;
		}

		if(!FfmpegNatives.ensure())
		{
			// natives 都没起来：库级错误就是全部原因（DLL 缺失、被占用、架构不对……）
			lastOpenFailure.set("natives 未就绪：" + lastLibraryError());
			return null;
		}

		long h = nativeOpen(path, wantHardware);
		if(h == 0 || !nativeIsOpen(h))
		{
			// 失败也要拿到 native 的说明文字，然后必须把句柄放掉，否则泄漏。
			String why = lastNativeError(h);
			if(h != 0)
				nativeClose(h);
			lastOpenFailure.set(why);
			System.err.println("[ffmpeg] cannot open " + path + ": " + why);
			return null;
		}

		lastOpenFailure.set("");
		return new FfmpegVideoDecoder(h, nativeWidth(h), nativeHeight(h),
			nativeFrameRate(h), nativeDurationMs(h), nativeIsHardware(h));
	}

	// ------------------------------------------------------------- accessors

	/** 解码输出宽度（像素）。 */
	public int width()
	{
		return width;
	}

	/** 解码输出高度（像素）。 */
	public int height()
	{
		return height;
	}

	/** 容器/流里报告的帧率，可能为 0（未知）。 */
	public double frameRate()
	{
		return frameRate;
	}

	/** 时长（毫秒），未知时为 0。 */
	public long durationMs()
	{
		return durationMs;
	}

	/** 实际生效的是不是硬件解码。 */
	public boolean isHardware()
	{
		return hardware;
	}

	/** 转换输出宽度，见 {@link #setOutputSize}。 */
	public int outputWidth()
	{
		return outWidth;
	}

	/** 转换输出高度，见 {@link #setOutputSize}。 */
	public int outputHeight()
	{
		return outHeight;
	}

	/**
	 * 让 swscale 直接把解码结果缩放到指定尺寸，省掉调用方自己缩放。
	 *
	 * <p>
	 * 传 0/0（或任何非正数）恢复成解码原生尺寸。这个不是可选的性能优化：4K 源按原生
	 * 尺寸输出每帧要 {@code 3840*2160*4 = 33 MB}，而模组只画屏幕那么大。
	 * {@link BackgroundVideo} 按**窗口的帧缓冲尺寸**（再被源尺寸与 2560x1440 的上限
	 * 夹住）调用它，窗口缩放时会重新调一次。
	 * </p>
	 *
	 * <p>
	 * 代价要说清楚：让 swscale 缩放<b>不是免费的</b>。实测同一段 4K 素材，输出
	 * 3840x2160（只做 YUV→RGBA 转换）是 252 fps，缩到 2560x1440 反而只有 95.5 fps
	 * ——多抽头滤波比转换本身贵。详见 docs/title-background.md 第 6 节的实测表。
	 * </p>
	 *
	 * @return 新的每帧字节数；参数非法或未打开时返回负值。
	 */
	public int setOutputSize(int outW, int outH)
	{
		if(handle == 0)
			return -1;

		int needed = nativeSetOutputSize(handle, outW, outH);
		if(needed < 0)
		{
			lastError = lastNativeError(handle);
			return needed;
		}

		outWidth = outW > 0 ? outW : width;
		outHeight = outH > 0 ? outH : height;

		int length = needed + FfmpegNatives.PADDING;
		if(frame.length < length)
			frame = new byte[length];

		return needed;
	}

	/** 本实例要求的 buffer 长度，含尾部余量。 */
	public int bufferSize()
	{
		return frame.length;
	}

	/**
	 * 复用的帧缓冲：内容在每次 {@link #nextFrame} 后被覆盖，不要长期持有。
	 */
	public byte[] frameBuffer()
	{
		return frame;
	}

	// --------------------------------------------------------------- decoding

	/**
	 * 解下一帧到 {@link #frameBuffer()}。
	 *
	 * @return 写入的字节数（{@code width*height*4}）；{@code 0} 表示已到结尾，
	 *         循环与否由调用方决定；负值为 FFmpeg AVERROR。
	 */
	public int nextFrame()
	{
		if(handle == 0)
			return -1;

		int bytes = nativeNextFrame(handle, frame, frame.length);

		// 失败时必须把 native 记下的原因捞出来：冷冰冰一个 -1094995529 没法排查
		if(bytes < 0)
			lastError = lastNativeError(handle);

		return bytes;
	}

	/**
	 * 回到文件开头。循环播放时用这个，不要重新 {@link #open}：重开要重新解析容器、
	 * 重新建解码器，而且会重建整块缓冲。
	 *
	 * @return {@code 0} 成功，负值失败。
	 */
	public int seekToStart()
	{
		if(handle == 0)
			return -1;

		int rc = nativeSeekToStart(handle);

		if(rc < 0)
			lastError = lastNativeError(handle);

		return rc;
	}

	/** 幂等关闭。关闭后再调 {@link #nextFrame} 返回 {@code -1}。 */
	@Override
	public void close()
	{
		long h = handle;
		handle = 0;
		if(h != 0)
			nativeClose(h);
	}

	public boolean isOpen()
	{
		return handle != 0;
	}

	// ------------------------------------------------------------------ misc

	/** 链接进来的 FFmpeg 版本串，例如 {@code "7.1.1"}。 */
	public static String ffmpegVersion()
	{
		if(!FfmpegNatives.ensure())
			return "unavailable";
		return nativeVersion();
	}

	/** natives 是否已经就绪；没就绪时返回 false 而不是抛异常。 */
	public static boolean isAvailable()
	{
		return FfmpegNatives.ensure();
	}

	/** natives 加载失败的原因，成功时是空串。 */
	public static String lastLibraryError()
	{
		return libraryFailure.isEmpty() ? FfmpegNatives.failure()
			: libraryFailure;
	}

	/**
	 * 最近一次打开失败的原因，成功时是空串。
	 *
	 * <p>
	 * 格式固定，能直接进日志、能 grep、能断言：
	 * </p>
	 *
	 * <pre>
	 * &lt;阶段&gt;: AVERROR &lt;数值&gt; (&lt;av_strerror 文字&gt;) path=&lt;硬件/软件&gt;
	 *         hw=&lt;device-created|device-unavailable|not-attempted-yet&gt;
	 *         [file=&lt;路径&gt;] [&lt;该阶段的上下文&gt;] [&lt;后备说明&gt;]
	 * </pre>
	 *
	 * <p>
	 * 实测例子（见 native/ffmpeg/README.md 第 8 节）：
	 * </p>
	 *
	 * <pre>
	 * avformat_open_input: AVERROR -2 (No such file or directory) path=software
	 *   hw=not-requested file=C:\...\missing.mp4
	 *
	 * first avcodec_send_packet: AVERROR -1094995529 (Invalid data found when
	 *   processing input) path=hardware hw=device-created codec=h264
	 *   frame=320x240 already_out=0
	 * </pre>
	 *
	 * <p>
	 * 打开失败的句柄当场就被 {@link #close()} 掉了，所以这个原因必须活在类上而不是
	 * 句柄上（native 侧同样留了一份，见 {@code nativeLastOpenError}）。读的是<b>当前
	 * 线程</b>最近一次 {@link #open} 的结果。
	 * </p>
	 */
	public static String lastOpenError()
	{
		String failure = lastOpenFailure.get();
		return failure == null ? "" : failure;
	}

	/**
	 * 这个实例上最近一次失败的完整原因（阶段 + AVERROR + av_strerror + 走的哪条路），
	 * 从没失败过时是空串。
	 *
	 * <p>
	 * 与 {@link #lastOpenError()} 的分工：打开失败看那个（句柄都没了），
	 * 解帧/换尺寸失败看这个（句柄还在，原因在它身上）。
	 * </p>
	 */
	public String lastError()
	{
		return lastError;
	}

	private static String lastNativeError(long h)
	{
		try
		{
			String s = nativeLastError(h);

			// 句柄已释放（h==0）时 native 只能给"全局最后一次错误"；打开失败那条
			// 路还留了一份静态的，拿它兜底比"(no detail)"有用得多
			if(s == null || s.isEmpty())
				s = nativeLastOpenError();

			return s == null || s.isEmpty() ? "(no detail)" : s;

		}catch(Throwable t)
		{
			return "(native error unavailable: " + t + ")";
		}
	}

	@Override
	public String toString()
	{
		return String.format(Locale.ROOT,
			"FfmpegVideoDecoder[%dx%d, %.3f fps, %d ms, %s, open=%s]", width,
			height, frameRate, durationMs, hardware ? "d3d11va" : "software",
			handle != 0);
	}

	// ------------------------------------------------------------- JNI surface
	// 这些方法与 native/ffmpeg/src/vf_ffmpeg_jni.c 里的 JNIEXPORT 一一对应，
	// 全名必须保持 Java_net_wurstclient_background_FfmpegVideoDecoder_<name>。

	private static native long nativeOpen(String path, boolean wantHardware);

	private static native boolean nativeIsOpen(long handle);

	private static native String nativeLastError(long handle);

	/** 最近一次「打开失败」的原因；不需要句柄，因为它要活得比句柄久。 */
	private static native String nativeLastOpenError();

	private static native int nativeWidth(long handle);

	private static native int nativeHeight(long handle);

	private static native double nativeFrameRate(long handle);

	private static native long nativeDurationMs(long handle);

	private static native boolean nativeIsHardware(long handle);

	private static native int nativeSetOutputSize(long handle, int outW,
		int outH);

	private static native int nativeOutputWidth(long handle);

	private static native int nativeOutputHeight(long handle);

	private static native int nativeNextFrame(long handle, byte[] dst,
		int capacity);

	private static native int nativeSeekToStart(long handle);

	private static native void nativeClose(long handle);

	private static native String nativeVersion();
}
