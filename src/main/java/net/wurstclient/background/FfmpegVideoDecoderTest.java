/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

import java.util.Arrays;
import java.util.Locale;

/**
 * 不依赖 Minecraft 的命令行自测，用来核对走 JNI 的真实解码速度与像素是否正确。
 *
 * <p>
 * 它测的是 <b>经过 shim 的</b> 速度，不是 {@code ffmpeg.exe} 的速度——两者差别很大，
 * 因为 shim 每帧还多做一次 swscale 缩放和一次 RGBA 打包。
 * </p>
 *
 * <h2>跑法</h2>
 * <pre>
 * java -Dwurst.ffmpeg.dir=&lt;含 5 个 DLL 的目录&gt; \
 *      -cp &lt;编译输出&gt; net.wurstclient.background.FfmpegVideoDecoderTest \
 *      &lt;视频文件&gt; [循环遍数]
 * </pre>
 *
 * <p>
 * 这里刻意<b>不</b>加载 {@link FfmpegNatives} 里走 Minecraft 资源解压的那条路径：
 * 设了 {@code wurst.ffmpeg.dir} 就会直接从该目录加载 DLL，于是不需要 Minecraft 在类路径上。
 * 不过这个类所在的包会引用 {@code net.minecraft.*}，所以要么整个 mod 类路径一起给它，
 * 要么用 native 侧的 {@code vf_ffmpeg_test.exe}（见 native/ffmpeg/README.md）。
 * </p>
 */
public final class FfmpegVideoDecoderTest
{
	private FfmpegVideoDecoderTest()
	{
	}

	public static void main(String[] args)
	{
		if(args.length < 1)
		{
			System.err.println(
				"usage: FfmpegVideoDecoderTest <file> [loops] [hw] [outW outH]");
			System.err.println(
				"  hw: 1 = try d3d11va first (default), 0 = software only");
			System.err.println(
				"  outW/outH: output size (default = decode native size)");
			System.exit(2);
		}

		String path = args[0];
		int loops = args.length > 1 ? Integer.parseInt(args[1]) : 1;
		boolean hw = args.length <= 2 || !"0".equals(args[2]);
		int outW = args.length > 4 ? Integer.parseInt(args[3]) : 0;
		int outH = args.length > 4 ? Integer.parseInt(args[4]) : 0;

		System.out.println("ffmpeg   : " + FfmpegVideoDecoder.ffmpegVersion());

		double fps = run(path, loops, hw, outW, outH);
		if(fps < 0)
			System.exit(1);
	}

	/** @return 实测 fps，失败返回 -1。 */
	private static double run(String path, int loops, boolean hw, int outW,
		int outH)
	{
		System.out.println("mode     : " + (hw ? "hardware-preferred"
			: "software-only"));

		FfmpegVideoDecoder dec = hw ? FfmpegVideoDecoder.openHardware(path)
			: FfmpegVideoDecoder.openSoftware(path);

		if(dec == null)
		{
			System.err.println("open failed: "
				+ FfmpegVideoDecoder.lastLibraryError());
			return -1;
		}

		try
		{
			if(outW > 0 && outH > 0)
				dec.setOutputSize(outW, outH);

			int w = dec.outputWidth();
			int h = dec.outputHeight();
			System.out.printf(Locale.ROOT, "file     : %s%n", path);
			System.out.printf(Locale.ROOT, "size     : %dx%d%n", dec.width(),
				dec.height());
			System.out.printf(Locale.ROOT, "output   : %dx%d%n", w, h);
			System.out.printf(Locale.ROOT, "fps (meta): %.3f%n",
				dec.frameRate());
			System.out.printf(Locale.ROOT, "duration : %d ms%n",
				dec.durationMs());
			System.out.printf(Locale.ROOT, "active   : %s%n",
				dec.isHardware() ? "d3d11va" : "software");

			byte[] buf = dec.frameBuffer();
			int expected = w * h * FfmpegVideoDecoder.BYTES_PER_PIXEL;

			Stat stat = new Stat();
			int frames = 0;
			long nano = 0;
			int firstFrameBytes = -1;

			for(int loop = 0; loop < loops; loop++)
			{
				while(true)
				{
					long t0 = System.nanoTime();
					int n = dec.nextFrame();
					nano += System.nanoTime() - t0;

					if(n < 0)
					{
						System.err.printf(Locale.ROOT,
							"decode error (AVERROR %d) on frame %d%n", n,
							frames);
						printPixelSanity(stat, frames);
						return frames > 0 ? frames / (nano / 1e9) : -1;
					}

					if(n == 0)
						break;

					if(frames == 0)
						firstFrameBytes = n;

					sample(buf, Math.min(n, expected), stat);
					frames++;
				}

				if(loop + 1 < loops && dec.seekToStart() < 0)
					System.err.println("seekToStart failed on loop " + loop);
			}

			System.out.printf(Locale.ROOT, "bytes/frm: %d (expect %d)%n",
				firstFrameBytes, expected);
			System.out.printf(Locale.ROOT, "frames   : %d%n", frames);
			System.out.printf(Locale.ROOT, "elapsed  : %.3f s%n",
				nano / 1e9);

			double fps = nano > 0 ? frames / (nano / 1e9) : 0.0;
			System.out.printf(Locale.ROOT, "decode fps: %.1f%n", fps);

			printPixelSanity(stat, frames);
			return fps;

		}finally
		{
			dec.close();
		}
	}

	/**
	 * 抽稀采样：每 97 个像素取一个，统计各通道取值范围与均值。够用来证明画面
	 * 不是黑的/全零的，也够看出通道有没有整体错位。
	 */
	private static void sample(byte[] buf, int len, Stat s)
	{
		for(int p = 0; p * 4 + 3 < len; p += 97)
		{
			int r = buf[p * 4] & 0xFF;
			int g = buf[p * 4 + 1] & 0xFF;
			int b = buf[p * 4 + 2] & 0xFF;
			int a = buf[p * 4 + 3] & 0xFF;

			s.sumR += r;
			s.sumG += g;
			s.sumB += b;
			s.sumA += a;
			s.n++;
			s.minR = Math.min(s.minR, r);
			s.maxR = Math.max(s.maxR, r);
			s.minG = Math.min(s.minG, g);
			s.maxG = Math.max(s.maxG, g);
			s.minB = Math.min(s.minB, b);
			s.maxB = Math.max(s.maxB, b);
			s.allZero |= (r | g | b) == 0;
			s.allOpaque &= a == 255;
		}
	}

	private static void printPixelSanity(Stat s, int frames)
	{
		if(s.n == 0)
		{
			System.out.println("pixels   : no samples");
			return;
		}

		System.out.printf(Locale.ROOT,
			"channels : R[%d..%d] G[%d..%d] B[%d..%d]%n", s.minR, s.maxR,
			s.minG, s.maxG, s.minB, s.maxB);
		System.out.printf(Locale.ROOT, "mean     : R=%.1f G=%.1f B=%.1f A=%.1f%n",
			s.sumR / (double)s.n, s.sumG / (double)s.n, s.sumB / (double)s.n,
			s.sumA / (double)s.n);
		System.out.printf(Locale.ROOT,
			"sanity   : samples=%d seenAllZeroPixel=%s alphaAlways255=%s%n", s.n,
			s.allZero, s.allOpaque);
		System.out.printf(Locale.ROOT, "frames   : %d%n", frames);
	}

	private static final class Stat
	{
		long sumR, sumG, sumB, sumA, n;
		int minR = 255, maxR = 0, minG = 255, maxG = 0, minB = 255, maxB = 0;
		boolean allZero;
		boolean allOpaque = true;
	}

	/** 未被 main 使用，保留给交互式排查：打印一帧的少量像素。 */
	static String dumpTopLeft(byte[] buf, int width, int rows)
	{
		StringBuilder sb = new StringBuilder();
		for(int y = 0; y < rows; y++)
		{
			int[] px = new int[8];
			for(int x = 0; x < 8; x++)
			{
				int i = (y * width + x) * 4;
				px[x] = ((buf[i] & 0xFF) << 16) | ((buf[i + 1] & 0xFF) << 8)
					| (buf[i + 2] & 0xFF);
			}
			sb.append(Arrays.toString(px)).append('\n');
		}
		return sb.toString();
	}
}
