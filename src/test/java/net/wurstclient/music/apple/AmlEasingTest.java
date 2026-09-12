package net.wurstclient.music.apple;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 校验 {@link AmlEasing} 与 AMLL 的 cubic-bezier 缓动一致。
 */
final class AmlEasingTest
{
	@Test
	void empEasingIsAPulsePeakingAtMidpoint()
	{
		// AMLL 的 makeEmpEasing 是一条脉冲：中点为峰值 1，首尾归零
		assertEquals(0, AmlEasing.empEasing(0), 1e-6);
		assertEquals(1, AmlEasing.empEasing(0.5), 1e-4);
		assertEquals(0, AmlEasing.empEasing(1), 1e-6);
	}

	@Test
	void empEasingRisesThenFalls()
	{
		double previous = -1;
		for(int i = 0; i <= 16; i++)
		{
			double value = AmlEasing.empEasing(i / 32D);
			assertTrue(value >= previous,
				"empEasing 前半段在 x=" + i / 32D + " 处回退");
			previous = value;
		}
		previous = 2;
		for(int i = 16; i <= 32; i++)
		{
			double value = AmlEasing.empEasing(i / 32D);
			assertTrue(value <= previous,
				"empEasing 后半段在 x=" + i / 32D + " 处上升");
			previous = value;
		}
	}

	@Test
	void easeOutSpansZeroToOne()
	{
		assertEquals(0, AmlEasing.easeOut(0), 1e-6);
		assertEquals(1, AmlEasing.easeOut(1), 1e-6);
		// CSS ease-out 起步快于线性
		assertTrue(AmlEasing.easeOut(0.25) > 0.25, "ease-out 应快于线性");
		assertTrue(AmlEasing.easeOut(0.5) > 0.5, "ease-out 应在中点前过半");
	}

	@Test
	void cubicBezierClampsOutOfRangeInput()
	{
		assertEquals(0, AmlEasing.cubicBezier(0.2, 0.4, 0.58, 1, -1), 1e-9);
		assertEquals(1, AmlEasing.cubicBezier(0.2, 0.4, 0.58, 1, 2), 1e-9);
	}

	@Test
	void interludeEasingsMatchAmlEndpoints()
	{
		assertEquals(0, AmlEasing.easeOutExpo(0), 1e-9);
		assertEquals(1, AmlEasing.easeOutExpo(1), 1e-9);
		assertEquals(0, AmlEasing.easeInOutBack(0), 1e-6);
		assertEquals(1, AmlEasing.easeInOutBack(1), 1e-6);
	}
}
