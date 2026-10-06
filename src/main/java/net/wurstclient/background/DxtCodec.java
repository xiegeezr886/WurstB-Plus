/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

/**
 * DXT1 / DXT3 / DXT5（= BC1 / BC2 / BC3）的解码。
 *
 * <p>
 * Wallpaper Engine 的贴图里，磁盘 {@code format} 3 / 4 / 5 分别是
 * {@code CompressedDXT5} / {@code CompressedDXT3} / {@code CompressedDXT1}（映射来自
 * RePKG 的 {@code MipmapFormat} 枚举：1-based，磁盘值 = 枚举值 − 1；见
 * docs/wallpaper-engine-scene.md 第 6.1 节）。这些格式按 4×4 像素一块编码，每块字节数
 * 固定：DXT1 是 8 字节（每像素 4 bit），DXT3/DXT5 是 16 字节（每像素 8 bit）。
 * </p>
 *
 * <p>
 * 写它的原因很实际：不做这一步，那类贴图会被整层跳过 —— 实测「4K 动态音乐壁纸」
 * （工坊 1646702957）52 层里只画出 1 层。
 * </p>
 *
 * <p>
 * 输出是**紧密排列的 RGBA8888、行序自上而下**，与 {@code NativeImage.Format.RGBA}
 * 一致，也就是 FFmpeg 那条路交出来的同一套约定。
 * </p>
 */
final class DxtCodec
{
	private DxtCodec()
	{
	}

	/** 一块覆盖的边长。 */
	static final int BLOCK = 4;

	static final int DXT1_BLOCK_BYTES = 8;
	static final int DXT35_BLOCK_BYTES = 16;

	/** DXT1 编码数据需要多少字节；尺寸非法时返回 -1。 */
	static long dxt1Size(int width, int height)
	{
		return encodedSize(width, height, DXT1_BLOCK_BYTES);
	}

	/** DXT3 / DXT5 编码数据需要多少字节；尺寸非法时返回 -1。 */
	static long dxt35Size(int width, int height)
	{
		return encodedSize(width, height, DXT35_BLOCK_BYTES);
	}

	static long encodedSize(int width, int height, int blockBytes)
	{
		if(width <= 0 || height <= 0)
			return -1;

		long blocksX = (width + BLOCK - 1) / BLOCK;
		long blocksY = (height + BLOCK - 1) / BLOCK;
		return blocksX * blocksY * blockBytes;
	}

	/**
	 * 解出一张 DXT1 图。
	 *
	 * @param data
	 *            编码数据，长度至少 {@link #dxt1Size(int, int)}
	 * @return 紧密排列的 RGBA8888（{@code width * height * 4} 字节）
	 * @throws IllegalArgumentException
	 *             尺寸非法或数据不够长
	 */
	static byte[] decodeDxt1(byte[] data, int width, int height)
	{
		return decode(data, width, height, false, false);
	}

	static byte[] decodeDxt3(byte[] data, int width, int height)
	{
		return decode(data, width, height, true, false);
	}

	static byte[] decodeDxt5(byte[] data, int width, int height)
	{
		return decode(data, width, height, true, true);
	}

	/**
	 * @param withAlpha
	 *            每块前面有 8 字节 alpha 段
	 * @param dxt5Alpha
	 *            alpha 段是 DXT5 的端点插值（否则是 DXT3 的显式 4 bit）
	 */
	private static byte[] decode(byte[] data, int width, int height,
		boolean withAlpha, boolean dxt5Alpha)
	{
		if(width <= 0 || height <= 0)
			throw new IllegalArgumentException(
				"贴图尺寸非法：" + width + "x" + height);

		int blockBytes = withAlpha ? DXT35_BLOCK_BYTES : DXT1_BLOCK_BYTES;
		long needed = encodedSize(width, height, blockBytes);

		if(data == null || data.length < needed)
			throw new IllegalArgumentException("DXT 数据不够长：需要 " + needed
				+ " 字节，只有 " + (data == null ? 0 : data.length));

		byte[] rgba = new byte[width * height * 4];
		int blocksX = (width + BLOCK - 1) / BLOCK;
		int blocksY = (height + BLOCK - 1) / BLOCK;
		int[] colours = new int[4];

		for(int by = 0; by < blocksY; by++)
			for(int bx = 0; bx < blocksX; bx++)
			{
				int block = (by * blocksX + bx) * blockBytes;
				int colourAt = block;
				long alphaBits = 0;
				int a0 = 0;
				int a1 = 0;

				if(withAlpha)
				{
					if(dxt5Alpha)
					{
						a0 = data[block] & 0xFF;
						a1 = data[block + 1] & 0xFF;

						for(int i = 0; i < 6; i++)
							alphaBits |= (long)(data[block + 2 + i] & 0xFF)
								<< (8 * i);
					}else
						alphaBits = readU64(data, block);

					colourAt = block + 8;
				}

				int c0 = readU16(data, colourAt);
				int c1 = readU16(data, colourAt + 2);
				long indices = readU32(data, colourAt + 4);

				colours[0] = toRgba(c0);
				colours[1] = toRgba(c1);

				if(c0 > c1 || withAlpha)
				{
					colours[2] = mix(colours[0], colours[1], 1, 2);
					colours[3] = mix(colours[0], colours[1], 2, 1);

				}else
				{
					// 三色模式：第三色是中值，索引 3 是透明
					colours[2] = mix(colours[0], colours[1], 1, 1);
					colours[3] = 0;
				}

				for(int y = 0; y < BLOCK; y++)
				{
					int py = by * BLOCK + y;

					if(py >= height)
						break;

					for(int x = 0; x < BLOCK; x++)
					{
						int px = bx * BLOCK + x;

						if(px >= width)
							break;

						int texel = 4 * y + x;
						int pixel = (int)(indices >> 2 * texel & 0b11);
						int value = colours[pixel];
						int alpha = 255;

						// BC1 的三色模式里索引 3 是**透明黑**：颜色与 alpha 都要归零。
						// 只把颜色置 0 的话那一格会变成不透明黑（实测就是这里漏了）。
						if(!withAlpha && c0 <= c1 && pixel == 3)
						{
							value = 0;
							alpha = 0;
						}

						if(withAlpha)
							alpha = dxt5Alpha
								? alpha5(a0, a1, (int)(alphaBits >> 3 * texel
									& 0b111))
								: (int)(alphaBits >> 4 * texel & 0xF) * 17;

						int out = (py * width + px) * 4;
						rgba[out] = (byte)(value >> 16 & 0xFF);
						rgba[out + 1] = (byte)(value >> 8 & 0xFF);
						rgba[out + 2] = (byte)(value & 0xFF);
						rgba[out + 3] = (byte)alpha;
					}
				}
			}

		return rgba;
	}

	/** DXT5 的 3 bit alpha 索引 -> 8 bit。规则见 BC3 规范。 */
	private static int alpha5(int a0, int a1, int index)
	{
		if(index == 0)
			return a0;

		if(index == 1)
			return a1;

		if(a0 > a1)
			return ((8 - index) * a0 + (index - 1) * a1) / 7;

		if(index == 6)
			return 0;

		if(index == 7)
			return 255;

		return ((6 - index) * a0 + (index - 1) * a1) / 5;
	}

	private static int toRgba(int rgb565)
	{
		int r = (rgb565 >> 11 & 0x1F) * 255 / 31;
		int g = (rgb565 >> 5 & 0x3F) * 255 / 63;
		int b = (rgb565 & 0x1F) * 255 / 31;
		return r << 16 | g << 8 | b;
	}

	private static int mix(int a, int b, int wa, int wb)
	{
		int r = ((a >> 16 & 0xFF) * wa + (b >> 16 & 0xFF) * wb) / (wa + wb);
		int g = ((a >> 8 & 0xFF) * wa + (b >> 8 & 0xFF) * wb) / (wa + wb);
		int bl = ((a & 0xFF) * wa + (b & 0xFF) * wb) / (wa + wb);
		return r << 16 | g << 8 | bl;
	}

	private static int readU16(byte[] data, int at)
	{
		return data[at] & 0xFF | (data[at + 1] & 0xFF) << 8;
	}

	private static long readU32(byte[] data, int at)
	{
		return readU16(data, at) | (long)readU16(data, at + 2) << 16;
	}

	private static long readU64(byte[] data, int at)
	{
		return readU32(data, at) | readU32(data, at + 4) << 32;
	}
}
