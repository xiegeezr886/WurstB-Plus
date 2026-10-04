package net.wurstclient.twilight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 校验拼贴卡浮动的姿态，数值取自参考的 {@code collage-float-a/b/c}。
 */
final class TwilightHomeLayoutCollageTest
{
	private static final float CARD_0 = 218F;

	/** keyframes 的 0%：基础倾斜、不抬起、不缩放。 */
	@Test
	void startsAtTheBasePose()
	{
		for(int index = 0; index < 3; index++)
		{
			float[] pose = TwilightHomeLayout.collageTransform(index, CARD_0, 0,
				false);

			assertEquals(TwilightHomeLayout.COLLAGE_ROTATION_DEG[index],
				pose[0], 1e-4F, "第 " + index + " 张的起始角度");
			assertEquals(0F, pose[1], 1e-4F, "第 " + index + " 张的起始位移");
			assertEquals(1F, pose[2], 1e-4F);
		}
	}

	/** keyframes 的 50%：抬起最多、旋转偏到另一端。 */
	@Test
	void reachesTheLiftedPoseAtHalfPeriod()
	{
		float[] pose = TwilightHomeLayout.collageTransform(1, CARD_0,
			(long)TwilightHomeLayout.COLLAGE_FLOAT_PERIOD_MS[1] / 2, false);

		assertEquals(5F + TwilightHomeLayout.COLLAGE_FLOAT_ROTATION_DEG[1],
			pose[0], 1e-3F, "第 1 张在半个周期应转到 6 度");
		assertEquals(-8F, pose[1], 1e-3F, "第 1 张在半个周期应抬起 8px");
	}

	/** 第 0 张的抬起量是自身高度的 4%（-54% → -58%）。 */
	@Test
	void theFirstCardLiftsByAShareOfItsHeight()
	{
		float[] pose = TwilightHomeLayout.collageTransform(0, CARD_0,
			(long)TwilightHomeLayout.COLLAGE_FLOAT_PERIOD_MS[0] / 2, false);

		assertEquals(-CARD_0 * TwilightHomeLayout.COLLAGE_LIFT_RATIO, pose[1],
			1e-3F);
		assertEquals(-2F, pose[0], 1e-3F, "第 0 张在半个周期应转到 -2 度");
	}

	/** 悬停只改第 0 张：放大到 1.03、角度回到 -1.5 度。 */
	@Test
	void hoverOnlyAffectsTheFirstCard()
	{
		float[] hovered = TwilightHomeLayout.collageTransform(0, CARD_0, 1234L,
			true);

		assertEquals(TwilightHomeLayout.COLLAGE_HOVER_ROTATION_DEG, hovered[0],
			1e-4F);
		assertEquals(TwilightHomeLayout.COLLAGE_HOVER_SCALE, hovered[2], 1e-4F);
		assertEquals(0F, hovered[1], 1e-4F);

		float[] second = TwilightHomeLayout.collageTransform(1, CARD_0, 1234L,
			true);
		float[] secondIdle = TwilightHomeLayout.collageTransform(1, CARD_0,
			1234L, false);

		assertEquals(secondIdle[0], second[0], 1e-6F, "第 1 张不受悬停影响");
		assertEquals(secondIdle[1], second[1], 1e-6F);
	}

	/** 动画是往返循环的：一个周期后回到起点。 */
	@Test
	void returnsToTheBasePoseAfterOnePeriod()
	{
		for(int index = 0; index < 3; index++)
		{
			long period = (long)TwilightHomeLayout.COLLAGE_FLOAT_PERIOD_MS[index];
			float[] start = TwilightHomeLayout.collageTransform(index, CARD_0, 0,
				false);
			float[] next = TwilightHomeLayout.collageTransform(index, CARD_0,
				period, false);

			assertEquals(start[0], next[0], 1e-3F, "第 " + index + " 张应循环");
			assertEquals(start[1], next[1], 1e-3F);
		}
	}

	/** 位移始终是向上的（keyframes 只往负方向走）。 */
	@Test
	void neverPushesTheCardDown()
	{
		for(int index = 0; index < 3; index++)
			for(int step = 0; step <= 20; step++)
			{
				long now = step * 400L;
				float[] pose = TwilightHomeLayout.collageTransform(index, CARD_0,
					now, false);

				assertTrue(pose[1] <= 1e-4F, "第 " + index + " 张不该向下移动");
				assertTrue(pose[2] == 1F, "未悬停时不应缩放");
			}
	}
}
