package net.wurstclient.twilight;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

/**
 * 校验原版后端的两处格式转换。
 *
 * <p>
 * 圆角数组这块真出过事：外壳按 Skia 的 {@code RRect} 格式传 8 个元素（四角各
 * 一对 x/y），原版实现按紧凑的 4 个读，结果半径落到了错误的角上。
 */
final class TwilightVanillaTest
{
	@Test
	void convertsSkiaCornerPairsToFourRadii()
	{
		// 侧栏的 border-radius: 0 26px 26px 0（左上、右上、右下、左下）
		float[] skia = {0, 0, 26, 26, 26, 26, 0, 0};

		assertArrayEquals(new float[]{0, 26, 26, 0},
			TwilightVanilla.toFourCorners(skia), 1e-6F);
	}

	@Test
	void acceptsCompactArrays()
	{
		assertArrayEquals(new float[]{1, 2, 3, 4},
			TwilightVanilla.toFourCorners(new float[]{1, 2, 3, 4}), 1e-6F);
	}

	@Test
	void toleratesShortAndMissingArrays()
	{
		assertArrayEquals(new float[]{7, 0, 0, 0},
			TwilightVanilla.toFourCorners(new float[]{7}), 1e-6F);
		assertArrayEquals(new float[]{0, 0, 0, 0},
			TwilightVanilla.toFourCorners(new float[0]), 1e-6F);
		assertArrayEquals(new float[]{0, 0, 0, 0},
			TwilightVanilla.toFourCorners(null), 1e-6F);
	}
}
