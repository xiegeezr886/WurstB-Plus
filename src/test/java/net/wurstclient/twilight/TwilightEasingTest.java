/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.twilight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests the CSS easing solver.
 *
 * <p>
 * The reference animates everything with five {@code cubic-bezier} curves, and
 * the two things that can go wrong are an inaccurate solver (the whole interface
 * would feel wrong) and a wrong reading of the curves. The solver is therefore
 * checked against the one bezier value that is documented everywhere - CSS
 * {@code ease} at the half way point - and the curves are checked against the
 * properties the reference relies on: monotone, ending exactly at 1, and the
 * spring overshooting on purpose.
 */
final class TwilightEasingTest
{
	private static final double[][] CURVES = {TwilightEasing.ENTER,
		TwilightEasing.SPRING, TwilightEasing.OUT_QUINT,
		TwilightEasing.OUT_EXPO, TwilightEasing.OUT_STRONG};
	
	@Test
	void everyCurveStartsAtZeroAndEndsAtOne()
	{
		for(double[] curve : CURVES)
		{
			assertEquals(0D, TwilightEasing.cubicBezier(0D, curve), 1E-6D);
			assertEquals(1D, TwilightEasing.cubicBezier(1D, curve), 1E-6D);
		}
	}
	
	@Test
	void theProgressIsClampedLikeTheBrowser()
	{
		assertEquals(0D, TwilightEasing.outExpo(-0.5D), 1E-6D);
		assertEquals(1D, TwilightEasing.outExpo(1.5D), 1E-6D);
		assertEquals(0D, TwilightEasing.spring(-1D), 1E-6D);
		assertEquals(1D, TwilightEasing.spring(2D), 1E-6D);
	}
	
	@Test
	void theSolverMatchesTheDocumentedCssValue()
	{
		// CSS `ease` = cubic-bezier(0.25, 0.1, 0.25, 1), which is 0.8024 at 0.5
		double[] ease = {0.25D, 0.1D, 0.25D, 1D};
		
		assertEquals(0.8024D, TwilightEasing.cubicBezier(0.5D, ease), 0.01D);
	}
	
	@Test
	void aLinearCurveIsTheIdentity()
	{
		double[] linear = {0.5D, 0.5D, 0.5D, 0.5D};
		
		for(int i = 0; i <= 10; i++)
		{
			double progress = i / 10D;
			
			assertEquals(progress,
				TwilightEasing.cubicBezier(progress, linear), 1E-6D);
		}
	}
	
	@Test
	void theOutCurvesDecelerate()
	{
		double[][] outCurves = {TwilightEasing.OUT_QUINT,
			TwilightEasing.OUT_EXPO, TwilightEasing.OUT_STRONG};
		
		for(double[] curve : outCurves)
		{
			double quarter = TwilightEasing.cubicBezier(0.25D, curve);
			double threeQuarters = TwilightEasing.cubicBezier(0.75D, curve);
			
			// an ease-out is already ahead of the linear progress early on
			assertTrue(quarter > 0.25D, "was " + quarter);
			assertTrue(threeQuarters > 0.9D, "was " + threeQuarters);
		}
	}
	
	@Test
	void theSpringCurveOvershootsOnPurpose()
	{
		double highest = 0D;
		
		for(int i = 0; i <= 100; i++)
			highest = Math.max(highest,
				TwilightEasing.spring(i / 100D));
		
		// the overshoot of this curve is mild but real: about 1.0054
		assertTrue(highest > 1.001D, "highest was " + highest);
		assertEquals(1D, TwilightEasing.spring(1D), 1E-6D);
	}
	
	@Test
	void theCurvesAreMonotone()
	{
		double[][] monotone = {TwilightEasing.ENTER, TwilightEasing.OUT_QUINT,
			TwilightEasing.OUT_EXPO, TwilightEasing.OUT_STRONG};
		
		for(double[] curve : monotone)
		{
			double previous = -1D;
			
			for(int i = 0; i <= 200; i++)
			{
				double value = TwilightEasing.cubicBezier(i / 200D, curve);
				
				assertTrue(value >= previous - 1E-6D,
					"went back at " + i + ": " + value + " < " + previous);
				previous = value;
			}
		}
	}
	
	@Test
	void theValuesStayInAReasonableRange()
	{
		for(double[] curve : CURVES)
			for(int i = 0; i <= 100; i++)
			{
				double value = TwilightEasing.cubicBezier(i / 100D, curve);
				
				assertTrue(Double.isFinite(value));
				assertTrue(value >= -0.25D && value <= 1.25D,
					"value was " + value);
			}
	}
	
	@Test
	void animateRespectsTheDuration()
	{
		assertEquals(0D,
			TwilightEasing.animate(0, TwilightEasing.HOVER_MS,
				TwilightEasing.OUT_STRONG),
			1E-6D);
		assertEquals(1D,
			TwilightEasing.animate(TwilightEasing.HOVER_MS,
				TwilightEasing.HOVER_MS, TwilightEasing.OUT_STRONG),
			1E-6D);
		assertEquals(1D,
			TwilightEasing.animate(10 * TwilightEasing.HOVER_MS,
				TwilightEasing.HOVER_MS, TwilightEasing.OUT_STRONG),
			1E-6D);
		
		double half = TwilightEasing.animate(TwilightEasing.HOVER_MS / 2,
			TwilightEasing.HOVER_MS, TwilightEasing.OUT_STRONG);
		
		assertTrue(half > 0D && half < 1D, "was " + half);
	}
	
	@Test
	void aZeroDurationIsAlreadyFinished()
	{
		assertEquals(1D,
			TwilightEasing.animate(0, 0, TwilightEasing.OUT_QUINT), 1E-6D);
		assertEquals(1D,
			TwilightEasing.animate(50, -5, TwilightEasing.OUT_QUINT), 1E-6D);
	}
	
	@Test
	void thePressAnimationMatchesTheReference()
	{
		assertEquals(1F, TwilightEasing.pressScale(0), 1E-4F);
		assertEquals(TwilightEasing.PRESS_SCALE,
			TwilightEasing.pressScale(TwilightEasing.PRESS_MS), 1E-4F);
		
		float half = TwilightEasing.pressScale(TwilightEasing.PRESS_MS / 2);
		
		// the curve is a strong ease-out, so most of the shrink happens early
		assertTrue(half < 0.99F, "was " + half);
		assertTrue(half >= TwilightEasing.PRESS_SCALE, "was " + half);
	}
	
	@Test
	void theHoverAnimationLiftsTheControlByOnePixel()
	{
		assertEquals(0F, TwilightEasing.hoverTranslate(0), 1E-4F);
		assertEquals(TwilightEasing.HOVER_TRANSLATE,
			TwilightEasing.hoverTranslate(TwilightEasing.HOVER_MS), 1E-4F);
		
		float half = TwilightEasing.hoverTranslate(TwilightEasing.HOVER_MS / 2);
		
		assertTrue(half < 0F && half > TwilightEasing.HOVER_TRANSLATE,
			"was " + half);
	}
	
	@Test
	void theDurationsMatchTheReference()
	{
		assertEquals(90, TwilightEasing.PRESS_MS);
		assertEquals(160, TwilightEasing.HOVER_MS);
		assertEquals(280, TwilightEasing.PANEL_MS);
		assertEquals(400, TwilightEasing.PAGE_MS);
		assertEquals(500, TwilightEasing.SETTLE_MS);
		assertEquals(220, TwilightEasing.RETURN_MS);
		assertEquals(0.97F, TwilightEasing.PRESS_SCALE, 1E-6F);
		assertEquals(-1F, TwilightEasing.HOVER_TRANSLATE, 1E-6F);
	}
	
	@Test
	void lerpWorks()
	{
		assertEquals(0F, TwilightEasing.lerp(0F, 10F, 0D), 1E-6F);
		assertEquals(10F, TwilightEasing.lerp(0F, 10F, 1D), 1E-6F);
		assertEquals(5F, TwilightEasing.lerp(0F, 10F, 0.5D), 1E-6F);
		assertEquals(1F, TwilightEasing.lerp(2F, 0F, 0.5D), 1E-6F);
	}
	
	@Test
	void invalidCurvesAreRejected()
	{
		assertThrows(IllegalArgumentException.class,
			() -> TwilightEasing.cubicBezier(0.5D, null));
		assertThrows(IllegalArgumentException.class,
			() -> TwilightEasing.cubicBezier(0.5D, new double[]{0.1D, 0.2D,
				0.3D}));
	}
}
