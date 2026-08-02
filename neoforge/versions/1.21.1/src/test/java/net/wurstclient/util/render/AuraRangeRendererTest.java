package net.wurstclient.util.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class AuraRangeRendererTest
{
	@Test
	void preservesConfiguredRadius()
	{
		assertEquals(3, AuraRangeRenderer.sanitizeRadius(3));
		assertEquals(8, AuraRangeRenderer.sanitizeRadius(8));
	}

	@Test
	void rejectsInvalidRadii()
	{
		assertEquals(0, AuraRangeRenderer.sanitizeRadius(-1));
		assertEquals(0,
			AuraRangeRenderer.sanitizeRadius(Double.POSITIVE_INFINITY));
		assertEquals(0, AuraRangeRenderer.sanitizeRadius(Double.NaN));
	}

	@Test
	void bandStaysInsideExactAttackRadius()
	{
		double smallInner = AuraRangeRenderer.innerRadius(3);
		double largeInner = AuraRangeRenderer.innerRadius(8);
		assertTrue(smallInner > 0 && smallInner < 3);
		assertTrue(largeInner > smallInner && largeInner < 8);
		assertTrue(AuraRangeRenderer.segmentCount(8)
			>= AuraRangeRenderer.segmentCount(3));
	}
}
