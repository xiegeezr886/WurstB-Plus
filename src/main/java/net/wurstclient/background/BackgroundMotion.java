/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

/**
 * The motion a still background is drawn with, so that a plain image still
 * feels alive.
 *
 * <p>
 * Everything here is deterministic: the pose is a pure function of the wall
 * clock, the pointer and the viewport, never of a frame counter. That keeps the
 * animation smooth when the frame rate dips, and makes it unit testable.
 */
public enum BackgroundMotion
{
	/** No movement at all. */
	NONE,

	/** A slow push in and out, drifting as it goes. */
	KEN_BURNS,

	/** A small counter-move that follows the pointer. */
	PARALLAX,

	/** A slow diagonal float. */
	DRIFT;

	/** One full cycle of the periodic motions. */
	private static final long CYCLE_MS = 40_000L;

	/** How far the pointer may push a parallax background, as a share of the
	 * viewport. */
	private static final float PARALLAX_RANGE = 0.02F;

	/** Scale a drifting background is drawn at, so the drift never exposes an
	 * edge. */
	private static final float DRIFT_OVERSCAN = 1.06F;

	/**
	 * Overscan a Ken Burns push keeps at its tightest, and how much more it
	 * gains at its widest. The base has to stay comfortably above twice the pan
	 * amounts below, otherwise the moment the push reaches its tightest would
	 * still be panning and the image would show its edge.
	 */
	private static final float KEN_BURNS_BASE_PUSH = 0.06F;
	private static final float KEN_BURNS_EXTRA_PUSH = 0.04F;
	private static final float KEN_BURNS_PAN_X = 0.012F;
	private static final float KEN_BURNS_PAN_Y = 0.010F;

	/**
	 * @param nowMs
	 *            the wall clock, in milliseconds
	 * @param strength
	 *            how pronounced the motion is, {@code 0..1}
	 * @param viewWidth
	 *            the viewport the background fills
	 * @param viewHeight
	 *            the viewport the background fills
	 * @param mouseX
	 *            the pointer, in the same space as the viewport
	 * @param mouseY
	 *            the pointer, in the same space as the viewport
	 * @return the transform to draw the background with
	 */
	public BackgroundPose compute(long nowMs, float strength, int viewWidth,
		int viewHeight, double mouseX, double mouseY)
	{
		float amount = clamp(strength, 0, 1);

		if(this == NONE || amount <= 0 || viewWidth <= 0 || viewHeight <= 0)
			return BackgroundPose.IDENTITY;

		// a phase in turns, so every motion shares the same clock shape
		double phase = (nowMs % CYCLE_MS) / (double)CYCLE_MS;
		double angle = phase * Math.PI * 2;

		return switch(this)
		{
			case KEN_BURNS -> kenBurns(angle, amount, viewWidth, viewHeight);
			case PARALLAX -> parallax(amount, viewWidth, viewHeight, mouseX,
				mouseY);
			case DRIFT -> drift(angle, amount, viewWidth, viewHeight);
			default -> BackgroundPose.IDENTITY;
		};
	}

	/**
	 * Pushes in and back out while panning a little. The push keeps a base
	 * overscan at its tightest so the pan always has room, which is why the
	 * scale never drops to 1.
	 */
	private static BackgroundPose kenBurns(double angle, float amount,
		int viewWidth, int viewHeight)
	{
		float push = KEN_BURNS_BASE_PUSH + KEN_BURNS_EXTRA_PUSH
			* (float)(0.5D + 0.5D * Math.sin(angle));
		float zoom = 1F + amount * push;
		float panX = (float)Math.sin(angle * 0.5D) * amount * KEN_BURNS_PAN_X;
		float panY = (float)Math.cos(angle * 0.35D) * amount
			* KEN_BURNS_PAN_Y;

		return new BackgroundPose(zoom, panX * viewWidth, panY * viewHeight);
	}

	/**
	 * Follows the pointer, bounded so the background never runs out of pixels.
	 */
	private static BackgroundPose parallax(float amount, int viewWidth,
		int viewHeight, double mouseX, double mouseY)
	{
		float range = PARALLAX_RANGE * amount;
		float normalizedX = (float)(mouseX / Math.max(1, viewWidth) - 0.5D);
		float normalizedY = (float)(mouseY / Math.max(1, viewHeight) - 0.5D);

		// drawn slightly larger so the shift cannot expose an edge
		float zoom = 1F + range;
		return new BackgroundPose(zoom, -normalizedX * range * viewWidth,
			-normalizedY * range * viewHeight);
	}

	/**
	 * A slow diagonal float, overscanned so the drift stays inside the image.
	 */
	private static BackgroundPose drift(double angle, float amount,
		int viewWidth, int viewHeight)
	{
		float zoom = 1F + (DRIFT_OVERSCAN - 1F) * amount;
		float panX = (float)Math.sin(angle) * amount * (DRIFT_OVERSCAN - 1F)
			* 0.5F;
		float panY = (float)Math.cos(angle * 0.7D) * amount
			* (DRIFT_OVERSCAN - 1F) * 0.5F;

		return new BackgroundPose(zoom, panX * viewWidth, panY * viewHeight);
	}

	/**
	 * Scales the motion down so that a small viewport does not get the same
	 * pixel travel as a large one, keeping the look consistent across window
	 * sizes.
	 */
	public static float strengthFor(float configured, int viewWidth)
	{
		if(viewWidth <= 0)
			return 0;

		return clamp(configured, 0, 1) * clamp(viewWidth / 1280F, 0.5F, 1.5F);
	}

	private static float clamp(float value, float min, float max)
	{
		return value < min ? min : value > max ? max : value;
	}

	@Override
	public String toString()
	{
		return switch(this)
		{
			case NONE -> "无";
			case KEN_BURNS -> "缓慢推拉";
			case PARALLAX -> "跟随鼠标";
			case DRIFT -> "缓慢漂移";
		};
	}
}
