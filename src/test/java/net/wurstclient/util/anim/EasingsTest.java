package net.wurstclient.util.anim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

final class EasingsTest
{
	private static final float EPSILON = 0.0001F;

	/**
	 * 不满足 f(0)=0 —— 照搬参考，不是笔误，见 {@link Easings#SIGMOID}。
	 */
	private static final Set<Easings> NOT_ZERO_AT_ORIGIN =
		EnumSet.of(Easings.SIGMOID);

	/**
	 * 不精确落在 1 —— 照搬参考，见 {@link Easings#DYNAMIC_ISLAND}。
	 */
	private static final Set<Easings> NOT_EXACTLY_ONE_AT_END =
		EnumSet.of(Easings.SIGMOID, Easings.DYNAMIC_ISLAND);

	/** 会刻意冲出 [0,1] 的曲线（回拉 / 弹性），不是缺陷。 */
	private static final Set<Easings> OVERSHOOTING = EnumSet
		.of(Easings.EASE_IN_BACK, Easings.EASE_OUT_ELASTIC);

	/**
	 * 参考侧 EASE_IN_OUT_SINE 与 EASE_IN_SINE 是同一个函数（只差乘法书写
	 * 顺序，而浮点乘法可交换），这里必须已经是两条不同的曲线。
	 */
	@Test
	void easeInOutSineIsNoLongerADuplicateOfEaseInSine()
	{
		// 参考那一条在中点给的是 1 - cos(π/4) = 0.29289，而不是 0.5
		assertEquals(0.29289F, Easings.EASE_IN_SINE.apply(0.5F), 0.0001F);

		// 真正的 ease-in-out 必须在中点给出 0.5
		assertEquals(0.5F, Easings.EASE_IN_OUT_SINE.apply(0.5F), EPSILON);
		assertNotEquals(Easings.EASE_IN_SINE.apply(0.5F),
			Easings.EASE_IN_OUT_SINE.apply(0.5F),
			"EASE_IN_OUT_SINE 又被写回成了 EASE_IN_SINE");
	}

	/**
	 * 参考的 EASE_IN_OUT_CIRC 第二段写成 {@code sqrt(1 - 4(x-1)x)}，在
	 * {@code x = 0.5} 处跳变到 1.20711（第一段的极限是 0.5），并且整段大于 1。
	 */
	@Test
	void easeInOutCircStaysContinuousAndInsideTheUnitInterval()
	{
		assertEquals(0.5F, Easings.EASE_IN_OUT_CIRC.apply(0.5F), EPSILON,
			"参考在这一点的值是 1.20711");

		float previous = -1;
		for(int i = 0; i <= 100; i++)
		{
			float x = i / 100F;
			float y = Easings.EASE_IN_OUT_CIRC.apply(x);

			assertTrue(y >= -EPSILON && y <= 1 + EPSILON,
				"EASE_IN_OUT_CIRC(" + x + ") = " + y + " escaped [0,1]");
			assertTrue(y >= previous - EPSILON,
				"EASE_IN_OUT_CIRC is not monotonic at x=" + x);
			previous = y;
		}
	}

	/**
	 * 所有 EASE_IN_OUT_* 都应当关于中点对称：{@code f(0.5)=0.5} 且
	 * {@code f(x) + f(1-x) = 1}。
	 */
	@Test
	void inOutCurvesAreSymmetricAboutTheMidpoint()
	{
		for(Easings easing : Easings.values())
		{
			if(!easing.name().startsWith("EASE_IN_OUT_"))
				continue;

			assertEquals(0.5F, easing.apply(0.5F), EPSILON,
				easing + " is not 0.5 at the midpoint");

			for(int i = 0; i <= 20; i++)
			{
				float x = i / 20F;
				assertEquals(1F, easing.apply(x) + easing.apply(1 - x),
					EPSILON, easing + " is not symmetric at x=" + x);
			}
		}
	}

	@Test
	void everyCurveIsFiniteAcrossTheUnitInterval()
	{
		for(Easings easing : Easings.values())
			for(int i = 0; i <= 100; i++)
			{
				float y = easing.apply(i / 100F);
				assertTrue(Float.isFinite(y),
					easing + " produced " + y + " at x=" + (i / 100F));
			}
	}

	@Test
	void curvesHitTheirEndpointsUnlessTheReferenceDoesNot()
	{
		for(Easings easing : Easings.values())
		{
			if(!NOT_ZERO_AT_ORIGIN.contains(easing))
				assertEquals(0F, easing.apply(0F), EPSILON,
					easing + " should start at 0");

			if(!NOT_EXACTLY_ONE_AT_END.contains(easing))
				assertEquals(1F, easing.apply(1F), EPSILON,
					easing + " should end at 1");
		}

		// 把这两条例外本身也钉住，免得以后有人「顺手修好」而不知道是故意的
		assertEquals(0.5F, Easings.SIGMOID.apply(0F), EPSILON);
		assertEquals(0.7311F, Easings.SIGMOID.apply(1F), 0.001F);
		assertEquals(1.0040F, Easings.DYNAMIC_ISLAND.apply(1F), 0.001F);
	}

	@Test
	void overshootingCurvesAreStillAllowedToLeaveTheUnitInterval()
	{
		assertTrue(Easings.EASE_IN_BACK.apply(0.5F) < 0F,
			"EASE_IN_BACK should dip below 0");
		assertTrue(Easings.EASE_OUT_ELASTIC.apply(0.5F) > 1F,
			"EASE_OUT_ELASTIC should overshoot 1");

		// 但它们在两端仍必须精确落点，否则补间收不了尾
		for(Easings easing : OVERSHOOTING)
		{
			assertEquals(0F, easing.apply(0F), EPSILON);
			assertEquals(1F, easing.apply(1F), EPSILON);
		}
	}

	@Test
	void knownValuesMatchTheReferenceFormulas()
	{
		assertEquals(0.5F, Easings.LINEAR.apply(0.5F), EPSILON);
		assertEquals(0.25F, Easings.EASE_IN_QUAD.apply(0.5F), EPSILON);
		assertEquals(0.75F, Easings.EASE_OUT_QUAD.apply(0.5F), EPSILON);
		assertEquals(0.125F, Easings.EASE_IN_CUBIC.apply(0.5F), EPSILON);
		assertEquals(0.875F, Easings.EASE_OUT_CUBIC.apply(0.5F), EPSILON);
		assertEquals(0.75F, Easings.DECELERATE.apply(0.5F), EPSILON);
		assertEquals(0.5F, Easings.SMOOTH_STEP.apply(0.5F), EPSILON);
		assertEquals(0.0625F, Easings.EASE_IN_QUART.apply(0.5F), EPSILON);
		assertEquals(0.9375F, Easings.EASE_OUT_QUART.apply(0.5F), EPSILON);
		assertEquals(0.03125F, Easings.EASE_IN_QUINT.apply(0.5F), EPSILON);
		assertEquals(0.96875F, Easings.EASE_OUT_QUINT.apply(0.5F), EPSILON);

		// 1 - cos(π/4) 与 sin(π/4)
		assertEquals(0.29289F, Easings.EASE_IN_SINE.apply(0.5F), 0.0001F);
		assertEquals(0.70711F, Easings.EASE_OUT_SINE.apply(0.5F), 0.0001F);
		assertEquals(0.5F, Easings.EASE_IN_OUT_EXPO.apply(0.5F), EPSILON);
	}

	@Test
	void theCurveCountMatchesTheReference()
	{
		// 参考 Easing.java 共 28 条；数目变了说明有人加/删了曲线，需要同步文档
		assertEquals(28, Easings.values().length);
	}

	@Test
	void applyIsUsableThroughTheCurveInterface()
	{
		Curve curve = Easings.EASE_OUT_QUAD;

		assertEquals(Easings.EASE_OUT_QUAD.apply(0.3F), curve.apply(0.3F),
			EPSILON);
	}
}
