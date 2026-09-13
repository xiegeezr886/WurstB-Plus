/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.twilight;

/**
 * The motion vocabulary of the Twilight Echo interface.
 *
 * <p>
 * Every transition of the reference is a CSS {@code cubic-bezier} with one of
 * five curves and one of six durations - see {@code base.css} lines 26-46 for
 * the normal values and lines 421-426 for the reduced motion overrides. Java has
 * no bezier evaluation, so this class solves the curve the way a browser does:
 * the x axis is the progress, the y axis is the eased value, and the y for a
 * given x is found by Newton iteration with a bisection fallback.
 *
 * <p>
 * Deliberately free of Minecraft types so that the curves can be unit tested.
 */
public final class TwilightEasing
{
	// ---- 曲线（base.css 行 26-37）----
	
	/** {@code --te-ease-enter}, 行 26. */
	public static final double[] ENTER = {0.4D, 0D, 0.2D, 1D};
	
	/** {@code --te-ease-spring}, 行 33 - overshoots on purpose. */
	public static final double[] SPRING = {0.22D, 1.14D, 0.36D, 1D};
	
	/** {@code --te-ease-out-quint}, 行 34 (and {@code --te-ease-soft}). */
	public static final double[] OUT_QUINT = {0.22D, 1D, 0.36D, 1D};
	
	/** {@code --te-ease-out-expo}, 行 35. */
	public static final double[] OUT_EXPO = {0.16D, 1D, 0.3D, 1D};
	
	/** {@code --te-ease-out-strong}, 行 37. */
	public static final double[] OUT_STRONG = {0.23D, 1D, 0.32D, 1D};
	
	// ---- 时长与位移（base.css 行 38-46）----
	
	public static final int PRESS_MS = 90;
	public static final int HOVER_MS = 160;
	public static final int PANEL_MS = 280;
	public static final int PAGE_MS = 400;
	public static final int SETTLE_MS = 500;
	public static final int RETURN_MS = 220;
	
	/** {@code --te-motion-press-scale}, 行 45. */
	public static final float PRESS_SCALE = 0.97F;
	
	/** {@code --te-motion-hover-translate}, 行 46. */
	public static final float HOVER_TRANSLATE = -1F;
	
	private static final int NEWTON_ITERATIONS = 8;
	private static final double NEWTON_EPSILON = 1E-6D;
	private static final double BISECTION_EPSILON = 1E-7D;
	private static final int BISECTION_ITERATIONS = 32;
	
	private TwilightEasing()
	{
		
	}
	
	/** {@code --te-ease-enter}. */
	public static double enter(double progress)
	{
		return cubicBezier(progress, ENTER);
	}
	
	/** {@code --te-ease-soft}, i.e. {@code --te-ease-out-quint}. */
	public static double soft(double progress)
	{
		return cubicBezier(progress, OUT_QUINT);
	}
	
	/** {@code --te-ease-spring}, which overshoots above 1 in the middle. */
	public static double spring(double progress)
	{
		return cubicBezier(progress, SPRING);
	}
	
	/** {@code --te-ease-out-quint}. */
	public static double outQuint(double progress)
	{
		return cubicBezier(progress, OUT_QUINT);
	}
	
	/** {@code --te-ease-out-expo}. */
	public static double outExpo(double progress)
	{
		return cubicBezier(progress, OUT_EXPO);
	}
	
	/** {@code --te-ease-out-strong}. */
	public static double outStrong(double progress)
	{
		return cubicBezier(progress, OUT_STRONG);
	}
	
	/**
	 * CSS {@code cubic-bezier(x1, y1, x2, y2)} evaluated at the given progress.
	 *
	 * @param progress
	 *            the x axis, clamped to {@code [0, 1]} like the browser does.
	 * @return the y axis, which can leave {@code [0, 1]} for an overshooting
	 *         curve such as {@link #SPRING}.
	 */
	public static double cubicBezier(double progress, double[] curve)
	{
		if(curve == null || curve.length != 4)
			throw new IllegalArgumentException("curve must be x1,y1,x2,y2");
		
		double x = clamp(progress, 0D, 1D);
		double x1 = curve[0];
		double y1 = curve[1];
		double x2 = curve[2];
		double y2 = curve[3];
		
		// a straight line needs no solving
		if(x1 == y1 && x2 == y2)
			return x;
		
		double t = solveForX(x, x1, x2);
		return bezier(t, y1, y2);
	}
	
	/**
	 * @return the bezier parameter {@code t} whose x equals {@code x}, by Newton
	 *         iteration with a bisection fallback for flat regions.
	 */
	static double solveForX(double x, double x1, double x2)
	{
		double t = x;
		
		for(int i = 0; i < NEWTON_ITERATIONS; i++)
		{
			double error = bezier(t, x1, x2) - x;
			
			if(Math.abs(error) < NEWTON_EPSILON)
				return t;
			
			double slope = bezierSlope(t, x1, x2);
			
			if(Math.abs(slope) < 1E-6D)
				break;
			
			double next = t - error / slope;
			
			if(next < 0D || next > 1D || Double.isNaN(next))
				break;
			
			t = next;
		}
		
		double low = 0D;
		double high = 1D;
		t = x;
		
		for(int i = 0; i < BISECTION_ITERATIONS; i++)
		{
			double value = bezier(t, x1, x2);
			
			if(Math.abs(value - x) < BISECTION_EPSILON)
				return t;
			
			if(value < x)
				low = t;
			else
				high = t;
			
			t = (low + high) / 2D;
		}
		
		return t;
	}
	
	/** The cubic bezier with the first control point at 0 and the last at 1. */
	private static double bezier(double t, double p1, double p2)
	{
		double inverse = 1D - t;
		return 3D * inverse * inverse * t * p1 + 3D * inverse * t * t * p2
			+ t * t * t;
	}
	
	private static double bezierSlope(double t, double p1, double p2)
	{
		double inverse = 1D - t;
		return 3D * inverse * inverse * p1
			+ 6D * inverse * t * (p2 - p1) + 3D * t * t * (1D - p2);
	}
	
	/**
	 * Eases a 0..1 progress with one of the reference curves.
	 *
	 * @param elapsedMs
	 *            milliseconds since the animation started.
	 * @param durationMs
	 *            total duration; 0 means "already finished".
	 */
	public static double animate(long elapsedMs, int durationMs,
		double[] curve)
	{
		if(durationMs <= 0)
			return 1D;
		
		return cubicBezier(elapsedMs / (double)durationMs, curve);
	}
	
	public static float lerp(float from, float to, double progress)
	{
		return (float)(from + (to - from) * progress);
	}
	
	/**
	 * The press animation of a button: the reference scales to 0.97 over 90ms.
	 */
	public static float pressScale(long elapsedMs)
	{
		return lerp(1F, PRESS_SCALE, animate(elapsedMs, PRESS_MS, OUT_STRONG));
	}
	
	/**
	 * The hover animation: the reference lifts the control by 1px over 160ms.
	 */
	public static float hoverTranslate(long elapsedMs)
	{
		return lerp(0F, HOVER_TRANSLATE,
			animate(elapsedMs, HOVER_MS, OUT_STRONG));
	}
	
	private static double clamp(double value, double min, double max)
	{
		return value < min ? min : value > max ? max : value;
	}
}
