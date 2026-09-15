package net.wurstclient.hud2.elements;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class TargetHudElementTest
{
	@Test
	void usesRiseModernDimensions()
	{
		TargetHudElement element = new TargetHudElement();
		assertEquals(145, element.getWidth());
		assertEquals(48, element.getHeight());
	}

	@Test
	void widthFollowsRiseModernTextFormula()
	{
		assertEquals(65, TargetHudElement.healthBarWidth(40, 20));
		assertEquals(75, TargetHudElement.healthBarWidth(60, 20));
		assertEquals(145, TargetHudElement.panelWidth(40, 20));
		assertEquals(155, TargetHudElement.panelWidth(60, 20));
	}

	@Test
	void healthIsRoundedToOneDecimal()
	{
		assertEquals("19.9", TargetHudElement.formatHealth(19.94F));
		assertEquals("20.0", TargetHudElement.formatHealth(20));
	}

	@Test
	void healthAnimationUsesRiseQuintCurve()
	{
		assertEquals(10.3125F, TargetHudElement.interpolateHealth(20, 10,
			TargetHudElement.HEALTH_ANIMATION_NANOS / 2), 0.001F);
		assertEquals(10, TargetHudElement.interpolateHealth(20, 10,
			TargetHudElement.HEALTH_ANIMATION_NANOS), 0.001F);
	}

	@Test
	void openingAnimationHasElasticOvershoot()
	{
		assertEquals(0, TargetHudElement.easeOutElastic(0));
		assertTrue(TargetHudElement.easeOutElastic(0.15F) > 1);
		assertEquals(1, TargetHudElement.easeOutElastic(1));
	}

	@Test
	void closingAnimationUsesBackOvershoot()
	{
		assertEquals(1, TargetHudElement.exitScale(0));
		assertTrue(TargetHudElement.exitScale(0.2F) > 1);
		assertEquals(0, TargetHudElement.exitScale(1));
	}

	// ------------------------------------------------------------------
	// 血条：参考把吸收值同时算进分子与分母
	// ------------------------------------------------------------------

	@Test
	void healthRatioMatchesThePlainCaseWithoutAbsorption()
	{
		assertEquals(1F, TargetHudElement.healthRatio(20, 20, 0), 1E-4F);
		assertEquals(0.5F, TargetHudElement.healthRatio(10, 20, 0), 1E-4F);
		assertEquals(0F, TargetHudElement.healthRatio(0, 20, 0), 1E-4F);
	}

	/**
	 * 这是参考那条式子的要点：吸收进的是<b>分母</b>，所以满血满吸收才是满条，
	 * 只满血（20/24）不该显示成满。
	 */
	@Test
	void absorptionRaisesTheDenominatorSoFullHealthIsNotAFullBar()
	{
		assertEquals(1F, TargetHudElement.healthRatio(24, 20, 4), 1E-4F);
		assertEquals(20F / 24F, TargetHudElement.healthRatio(20, 20, 4), 1E-4F);
	}

	@Test
	void healthRatioIsClampedToOne()
	{
		assertEquals(1F, TargetHudElement.healthRatio(30, 20, 0), 1E-4F);
		assertEquals(1F, TargetHudElement.healthRatio(20, 20, -5), 1E-4F);
	}

	@Test
	void healthRatioNeverDividesByZeroOrReturnsNaN()
	{
		assertEquals(0F, TargetHudElement.healthRatio(5, 0, 0), 1E-4F);
		assertEquals(0F, TargetHudElement.healthRatio(5, 0, -10), 1E-4F);

		float ratio = TargetHudElement.healthRatio(Float.NaN, 20, 0);
		assertTrue(Float.isFinite(ratio), "NaN health must not leak out");
		assertEquals(0F, ratio, 1E-4F);
	}

	/**
	 * 底层那根滞后条用的是动画值，上层压的是真实值；掉血时动画值更大，
	 * 于是露出一截暗色拖尾——所以两个比例必须能分开算。
	 */
	@Test
	void animatedAndTrueRatiosDifferWhileTheBarIsCatchingUp()
	{
		float animated = TargetHudElement.healthRatio(20, 20, 0);
		float trueValue = TargetHudElement.healthRatio(8, 20, 0);

		assertTrue(animated > trueValue,
			"the lagging bar should be longer than the true bar right after a hit");
	}
}
