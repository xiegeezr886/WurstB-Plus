/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

/**
 * LZ4 <b>块</b>格式的解压（没有帧头，就是 {@code LZ4_decompress_safe} 处理的那种）。
 *
 * <p>
 * Wallpaper Engine 的 {@code .tex} 里，mipmap 条目的 {@code compression == 1} 时像素
 * 数据就是一段 LZ4 块流（见 {@code linux-wallpaperengine} 的
 * {@code Data/Parsers/TextureParser.cpp}，它直接调 {@code LZ4_decompress_safe}）。
 * 实测用户库里 226 张贴图是这种，不做这一步它们只能被整层跳过。
 * </p>
 *
 * <p>
 * 自己写而不引依赖：块格式只有"字面量 + 回引"两种动作，几十行就够；多引一个依赖要多
 * 带一份库进 jar、还要多担一份许可。
 * </p>
 *
 * <p>
 * <b>边界一律当作错误，不猜。</b>长度不吻合、回引越界、输入截断都抛
 * {@link IllegalArgumentException} 并带上具体数字 —— 解错的像素比少画一层糟得多。
 * </p>
 */
final class Lz4Block
{
	private Lz4Block()
	{
	}

	/** 长度字段到达这个值就表示后面还有扩展字节。 */
	private static final int LONG_LENGTH = 15;

	/**
	 * 把一段 LZ4 块流解成指定的字节数。
	 *
	 * @param source
	 *            压缩数据
	 * @param sourceLength
	 *            压缩数据的有效长度
	 * @param targetLength
	 *            期望解出的字节数（{@code .tex} 里就是 {@code uncompressedSize}）
	 * @return 恰好 {@code targetLength} 字节
	 * @throws IllegalArgumentException
	 *             数据截断、回引越界，或解出来不是 {@code targetLength} 字节
	 */
	static byte[] decompress(byte[] source, int sourceLength, int targetLength)
	{
		return decompress(source, 0, sourceLength, targetLength);
	}

	/**
	 * 同 {@link #decompress(byte[], int, int)}，但压缩数据从 {@code offset} 开始 ——
	 * {@code .tex} 里 mip 数据就夹在文件中间。
	 */
	static byte[] decompress(byte[] source, int offset, int sourceLength,
		int targetLength)
	{
		if(source == null || offset < 0 || sourceLength < 0
			|| offset > source.length - sourceLength)
			throw new IllegalArgumentException("LZ4 源数据范围非法：offset="
				+ offset + " length=" + sourceLength + " 数组长 "
				+ (source == null ? 0 : source.length));

		if(targetLength < 0)
			throw new IllegalArgumentException("LZ4 目标长度非法：" + targetLength);

		Reader in = new Reader(source, offset, offset + sourceLength);
		byte[] output = new byte[targetLength];
		int out = 0;

		while(in.remaining() > 0)
		{
			int token = in.u8();

			// ---- 字面量段 ----
			int literals = token >>> 4;

			if(literals == LONG_LENGTH)
				literals += in.length();

			in.copyTo(output, out, literals, targetLength, "字面量");
			out += literals;

			// 最后一个序列只有字面量，没有回引
			if(in.remaining() <= 0)
				break;

			// ---- 回引段 ----
			int offset = in.u16();

			if(offset == 0 || offset > out)
				throw new IllegalArgumentException("LZ4 回引越界：offset=" + offset
					+ " 而当前只输出了 " + out + " 字节");

			int match = (token & 0x0F) + 4;

			if((token & 0x0F) == LONG_LENGTH)
				match += in.length();

			if(match > targetLength - out)
				throw new IllegalArgumentException("LZ4 解出的数据超出目标长度 "
					+ targetLength + "（回引段还要 " + match + " 字节）");

			// 逐字节拷：offset 小于 match 时源与目的重叠，这正是 LZ4 的重复展开
			int from = out - offset;

			for(int i = 0; i < match; i++)
				output[out++] = output[from++];
		}

		if(out != targetLength)
			throw new IllegalArgumentException(
				"LZ4 解出的长度不对：" + out + " != " + targetLength);

		return output;
	}

	/** 带边界检查的读指针。 */
	private static final class Reader
	{
		private final byte[] data;
		private final int end;
		private int at;

		Reader(byte[] data, int from, int end)
		{
			this.data = data;
			this.at = from;
			this.end = end;
		}

		int remaining()
		{
			return end - at;
		}

		int u8()
		{
			if(at >= end)
				throw new IllegalArgumentException("LZ4 数据被截断（读 1 字节时已到末尾）");

			return data[at++] & 0xFF;
		}

		int u16()
		{
			// 小端：低位在前
			return u8() | u8() << 8;
		}

		/** 长度扩展：每读到 0xFF 就继续，直到读到小于 0xFF 的那个。 */
		int length()
		{
			int total = 0;

			while(true)
			{
				int value = u8();
				total += value;

				if(value != 0xFF)
					return total;
			}
		}

		void copyTo(byte[] target, int targetAt, int count, int targetLength,
			String what)
		{
			if(count > remaining())
				throw new IllegalArgumentException("LZ4 " + what + "越界：要 "
					+ count + " 字节，只剩 " + remaining());

			if(count > targetLength - targetAt)
				throw new IllegalArgumentException("LZ4 解出的数据超出目标长度 "
					+ targetLength + "（" + what + "段要 " + count + " 字节）");

			System.arraycopy(data, at, target, targetAt, count);
			at += count;
		}
	}
}
