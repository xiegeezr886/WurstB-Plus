package net.wurstclient.twilight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 校验盒式模糊。
 *
 * <p>
 * 特意包含**非均匀**用例：滑动窗口如果原地改写，窗口里会混进中间结果，单一颜色
 * 的图看不出来，但对称的图形会被糊歪——所以这里专门检查对称性。
 */
final class CoverBlurTest
{
	private static final int OPAQUE_RED = 0xFFFF0000;
	private static final int OPAQUE_BLUE = 0xFF0000FF;

	@Test
	void uniformColourStaysExactlyTheSame()
	{
		int[] pixels = filled(8, 8, OPAQUE_RED);
		CoverBlur.boxBlur(pixels, 8, 8, 2);

		for(int pixel : pixels)
			assertEquals(OPAQUE_RED, pixel, "纯色图模糊后不该变");
	}

	/** 完全透明必须保持透明，不能糊出一层灰。 */
	@Test
	void transparentStaysTransparent()
	{
		int[] pixels = new int[8 * 8];
		CoverBlur.boxBlur(pixels, 8, 8, 2);

		for(int pixel : pixels)
			assertEquals(0, pixel);
	}

	/**
	 * 半透明与不透明相邻时，结果不能发暗——这是预乘 alpha 的意义所在。
	 */
	@Test
	void doesNotDarkenWhereAlphaVaries()
	{
		int[] pixels = filled(9, 1, OPAQUE_RED);
		// 只有最左侧是半透明，其余全不透明
		pixels[0] = 0x80FF0000;

		CoverBlur.boxBlur(pixels, 9, 1, 1);

		// 不透明区域模糊后仍是纯红，不能掺进透明一侧的黑色
		assertEquals(OPAQUE_RED, pixels[8], "不透明区域不能被透明邻居带暗");
		assertEquals(OPAQUE_RED, pixels[6], "不透明区域不能被透明邻居带暗");
	}

	/**
	 * 一条竖线的模糊必须左右对称——原地改写会让窗口读到已经糊过的值，
	 * 结果右侧被多糊一次，对称性就没了。
	 */
	@Test
	void aSingleColumnBlursSymmetrically()
	{
		int width = 15;
		int height = 5;
		int[] pixels = new int[width * height];

		for(int y = 0; y < height; y++)
			for(int x = 0; x < width; x++)
				pixels[y * width + x] =
					x == width / 2 ? 0xFFFFFFFF : 0xFF000000;

		CoverBlur.boxBlur(pixels, width, height, 2);

		int middle = width / 2;

		for(int offset = 1; offset <= 3; offset++)
			assertEquals(pixels[middle - offset], pixels[middle + offset],
				"距中线 " + offset + " 处应左右对称");
	}

	@Test
	void aBrightCentreSpreadsAndDims()
	{
		int width = 11;
		int[] pixels = new int[width * width];

		for(int i = 0; i < pixels.length; i++)
			pixels[i] = 0xFF000000;

		int middle = width / 2;
		pixels[middle * width + middle] = 0xFFFFFFFF;

		CoverBlur.boxBlur(pixels, width, width, 1);

		int centre = pixels[middle * width + middle];
		int neighbour = pixels[middle * width + middle + 1];

		assertTrue((centre & 0xFF) < 255, "中心应被摊薄，实为 " + (centre & 0xFF));
		assertTrue((neighbour & 0xFF) > 0, "相邻像素应被点亮");
	}

	@Test
	void rejectsDegenerateInput()
	{
		int[] pixels = filled(4, 4, OPAQUE_BLUE);

		CoverBlur.boxBlur(pixels, 4, 4, 0);
		assertEquals(OPAQUE_BLUE, pixels[0], "半径 0 应原样返回");

		CoverBlur.boxBlur(pixels, 0, 4, 2);
		assertEquals(OPAQUE_BLUE, pixels[0], "零宽度应原样返回");

		CoverBlur.boxBlur(null, 4, 4, 2);
		CoverBlur.boxBlur(new int[2], 4, 4, 2);
		assertEquals(OPAQUE_BLUE, pixels[0], "非法数组不该被改动");
	}

	private static int[] filled(int width, int height, int color)
	{
		int[] pixels = new int[width * height];

		for(int i = 0; i < pixels.length; i++)
			pixels[i] = color;

		return pixels;
	}
}
