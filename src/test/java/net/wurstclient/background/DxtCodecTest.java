/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * {@link DxtCodec}：手工构造的块 + 逐像素断言。
 *
 * <p>
 * 这些块是照 BC1/BC2/BC3 规范手工搭出来的，所以断言的是"规范说什么"而不是"我的
 * 实现输出什么" —— 拿实现自己的输出当基准等于没测。
 * </p>
 */
final class DxtCodecTest
{
	/** RGB565 的纯红。 */
	private static final int RED565 = 0xF800;

	/** RGB565 的纯白。 */
	private static final int WHITE565 = 0xFFFF;

	@Test
	void knowsHowMuchDataEachFormatNeeds()
	{
		assertEquals(8, DxtCodec.dxt1Size(4, 4));
		assertEquals(16, DxtCodec.dxt35Size(4, 4));

		// 4x4 是一块；8x4 与 4x8 是两块；5x5 向上取整成 2x2 块
		assertEquals(16, DxtCodec.dxt1Size(8, 4));
		assertEquals(16, DxtCodec.dxt1Size(4, 8));
		assertEquals(32, DxtCodec.dxt1Size(8, 8));
		assertEquals(8 * 4, DxtCodec.dxt1Size(5, 5));

		assertEquals(-1, DxtCodec.dxt1Size(0, 4));
		assertEquals(-1, DxtCodec.dxt35Size(4, -1));
	}

	@Test
	void refusesDataThatIsTooShort()
	{
		assertThrows(IllegalArgumentException.class,
			() -> DxtCodec.decodeDxt1(new byte[7], 4, 4));
		assertThrows(IllegalArgumentException.class,
			() -> DxtCodec.decodeDxt1(null, 4, 4));
		assertThrows(IllegalArgumentException.class,
			() -> DxtCodec.decodeDxt1(new byte[8], 0, 4));
	}

	/** 一块纯色：c0 = 红、c1 = 0、16 个索引全是 0 → 整块红且不透明。 */
	@Test
	void decodesAFlatDxt1Block()
	{
		byte[] block = block(RED565, 0x0000, 0L);
		byte[] rgba = DxtCodec.decodeDxt1(block, 4, 4);

		assertEquals(4 * 4 * 4, rgba.length);

		for(int y = 0; y < 4; y++)
			for(int x = 0; x < 4; x++)
				assertPixel(rgba, 4, x, y, 255, 255, 0, 0);
	}

	/**
	 * 四色模式的插值：c0 = 白 > c1 = 黑，索引 2 应当是 1/3、索引 3 是 2/3。
	 *
	 * <p>
	 * 顺带钉住**行优先**的索引顺序：索引字按 texel 0..15 从低位开始排，texel 0 是
	 * 块的左上角。
	 * </p>
	 */
	@Test
	void interpolatesTheTwoMiddleColours()
	{
		long indices = 0;

		for(int texel = 0; texel < 16; texel++)
			indices |= (long)(texel < 8 ? 2 : 3) << 2 * texel;

		byte[] rgba =
			DxtCodec.decodeDxt1(block(WHITE565, 0x0000, indices), 4, 4);

		// 上半块（texel 0..7）是索引 2 = 1/3 白；下半块是索引 3 = 2/3 白
		assertPixel(rgba, 4, 0, 0, 255, 85, 85, 85);
		assertPixel(rgba, 4, 3, 1, 255, 85, 85, 85);
		assertPixel(rgba, 4, 0, 2, 255, 170, 170, 170);
		assertPixel(rgba, 4, 3, 3, 255, 170, 170, 170);
	}

	/** c0 &lt;= c1 时是**三色模式**：索引 3 是透明黑，不是插值色。 */
	@Test
	void treatsIndexThreeAsTransparentInThreeColourMode()
	{
		long indices = 0;

		for(int texel = 0; texel < 16; texel++)
			indices |= 3L << 2 * texel;

		byte[] rgba = DxtCodec.decodeDxt1(block(0x0000, WHITE565, indices), 4,
			4);

		assertPixel(rgba, 4, 0, 0, 0, 0, 0, 0);
		assertPixel(rgba, 4, 3, 3, 0, 0, 0, 0);
	}

	/** DXT5 的 alpha：端点 255/0，索引 0 -> 255、1 -> 0、2 -> (8-2)*255/7 = 218。 */
	@Test
	void decodesInterpolatedDxt5Alpha()
	{
		byte[] data = new byte[16];
		data[0] = (byte)255; // a0
		data[1] = 0; // a1

		// 每个 texel 3 bit：texel 0 -> 0, 1 -> 1, 2 -> 2, 其余 0
		int[] alphaIndex = {0, 1, 2, 0};
		long bits = 0;

		for(int texel = 0; texel < 16; texel++)
			bits |= (long)alphaIndex[texel % 4] << 3 * texel;

		for(int i = 0; i < 6; i++)
			data[2 + i] = (byte)(bits >> 8 * i);

		// 颜色段：c0 = c1 = 红，索引全 0
		data[8] = (byte)(RED565 & 0xFF);
		data[9] = (byte)(RED565 >> 8);
		data[10] = (byte)(RED565 & 0xFF);
		data[11] = (byte)(RED565 >> 8);

		byte[] rgba = DxtCodec.decodeDxt5(data, 4, 4);

		assertPixel(rgba, 4, 0, 0, 255, 255, 0, 0);
		assertPixel(rgba, 4, 1, 0, 0, 255, 0, 0);
		assertPixel(rgba, 4, 2, 0, 218, 255, 0, 0);
		assertPixel(rgba, 4, 3, 0, 255, 255, 0, 0);
	}

	/** DXT3 的 alpha 是显式 4 bit：0xF -> 255、0x8 -> 136、0x0 -> 0。 */
	@Test
	void decodesExplicitDxt3Alpha()
	{
		byte[] data = new byte[16];

		// 每 texel 4 bit，texel 0 在最低位
		data[0] = (byte)0x8F; // texel0 = F, texel1 = 8

		data[8] = (byte)(RED565 & 0xFF);
		data[9] = (byte)(RED565 >> 8);
		data[10] = (byte)(RED565 & 0xFF);
		data[11] = (byte)(RED565 >> 8);

		byte[] rgba = DxtCodec.decodeDxt3(data, 4, 4);

		assertPixel(rgba, 4, 0, 0, 255, 255, 0, 0);
		assertPixel(rgba, 4, 1, 0, 136, 255, 0, 0);
		assertPixel(rgba, 4, 2, 0, 0, 255, 0, 0);
	}

	/** 8x4 = 左右两块，右边的块不该被写到左边的像素上。 */
	@Test
	void tilesBlocksLeftToRight()
	{
		byte[] data = new byte[16];
		System.arraycopy(block(RED565, 0, 0), 0, data, 0, 8);
		System.arraycopy(block(0x07E0, 0, 0), 0, data, 8, 8); // 纯绿

		byte[] rgba = DxtCodec.decodeDxt1(data, 8, 4);

		assertPixel(rgba, 8, 0, 0, 255, 255, 0, 0);
		assertPixel(rgba, 8, 3, 3, 255, 255, 0, 0);
		assertPixel(rgba, 8, 4, 0, 255, 0, 255, 0);
		assertPixel(rgba, 8, 7, 3, 255, 0, 255, 0);
	}

	/** 不是 4 的整数倍时，多出来的 texel 要被丢掉而不是越界写。 */
	@Test
	void clipsToTheRequestedSize()
	{
		byte[] rgba = DxtCodec.decodeDxt1(block(RED565, 0, 0), 3, 2);

		assertEquals(3 * 2 * 4, rgba.length);
		assertPixel(rgba, 3, 0, 0, 255, 255, 0, 0);
		assertPixel(rgba, 3, 2, 1, 255, 255, 0, 0);
	}

	// ------------------------------------------------------------------

	/** 搭一个 DXT1 颜色块：c0、c1、16 个 2 bit 索引。 */
	private static byte[] block(int c0, int c1, long indices)
	{
		byte[] out = new byte[8];
		out[0] = (byte)(c0 & 0xFF);
		out[1] = (byte)(c0 >> 8);
		out[2] = (byte)(c1 & 0xFF);
		out[3] = (byte)(c1 >> 8);

		for(int i = 0; i < 4; i++)
			out[4 + i] = (byte)(indices >> 8 * i);

		return out;
	}

	private static void assertPixel(byte[] rgba, int width, int x, int y,
		int a, int r, int g, int b)
	{
		int at = (y * width + x) * 4;
		assertArrayEquals(new byte[]{(byte)r, (byte)g, (byte)b, (byte)a},
			new byte[]{rgba[at], rgba[at + 1], rgba[at + 2], rgba[at + 3]},
			"像素 (" + x + "," + y + ")");
	}

	/** 只是为了让上面的断言在失败时更容易读，顺带确认通道顺序是 R,G,B,A。 */
	@Test
	void writesChannelsInRgbaOrder()
	{
		byte[] rgba = DxtCodec.decodeDxt1(block(RED565, 0, 0), 1, 1);

		assertArrayEquals(new byte[]{(byte)255, 0, 0, (byte)255}, rgba,
			"红在最低字节、alpha 在最高字节");
	}
}
