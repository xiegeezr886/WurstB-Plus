/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
package net.wurstclient.hud2;

/**
 * Time-based exponential animation.
 *
 * <p>Ported from Soar Client v7.1.2
 * ({@code me.eldodebug.soar.utils.animation.simple.SimpleAnimation} and
 * {@code AnimationUtils}).
 */
public final class HudAnimation
{
	private float value;
	private long lastMs = System.currentTimeMillis();

	public HudAnimation() {}

	public HudAnimation(float value)
	{
		this.value = value;
	}

	public void setAnimation(float target, double speed)
	{
		long now = System.currentTimeMillis();
		long delta = Math.max(1, now - lastMs);
		lastMs = now;

		if(speed > 28)
			speed = 28;

		double deltaValue = speed == 0 ? 0
			: Math.abs(target - value) * 0.35F / (10.0 / speed);
		value = compensate(target, value, deltaValue, delta);
	}

	private static float compensate(float target, float current, double speed,
		long delta)
	{
		float diff = current - target;
		double add = delta * (speed / 50);

		if(diff > speed)
		{
			if(current - add > target)
				current -= add;
			else
				current = target;
		}else if(diff < -speed)
		{
			if(current + add < target)
				current += add;
			else
				current = target;
		}else if(Math.abs(current - target) < 0.03)
			current = target;

		return current;
	}

	public float getValue()
	{
		return value;
	}

	public void setValue(float value)
	{
		this.value = value;
	}
}
