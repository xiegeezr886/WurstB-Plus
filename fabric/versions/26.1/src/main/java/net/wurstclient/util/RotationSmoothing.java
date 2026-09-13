/*
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

import net.minecraft.util.Mth;

public enum RotationSmoothing
{
	LINEAR("Linear")
	{
		@Override
		public float apply(float current, float target,
			float maxDegreesPerTick)
		{
			return RotationUtils.limitAngleChange(current, target,
				maxDegreesPerTick);
		}
	},
	EASE_IN_OUT("EaseInOut")
	{
		@Override
		public float apply(float current, float target,
			float maxDegreesPerTick)
		{
			float rawChange = Mth.wrapDegrees(
				Mth.wrapDegrees(target) - Mth.wrapDegrees(current));
			float absChange = Math.abs(rawChange);
			float threshold = maxDegreesPerTick * 3;

			if(absChange < maxDegreesPerTick)
				return current + rawChange;

			float progress = absChange < threshold
				? (float)Math.pow(absChange / threshold, 0.5)
				: 1.0F;

			float easeSpeed = maxDegreesPerTick * progress
				* (absChange < threshold ? 0.3F + 0.7F * progress : 1.0F);

			return RotationUtils.limitAngleChange(current, target,
				easeSpeed);
		}
	},
	FACTOR("Factor")
	{
		@Override
		public float apply(float current, float target,
			float maxDegreesPerTick)
		{
			float rawChange = Mth.wrapDegrees(
				Mth.wrapDegrees(target) - Mth.wrapDegrees(current));
			float absChange = Math.abs(rawChange);

			if(absChange < 0.1F)
				return current + rawChange;

			float speed = absChange * 0.4F;
			speed = Mth.clamp(speed, maxDegreesPerTick * 0.3F,
				maxDegreesPerTick);

			return RotationUtils.limitAngleChange(current, target, speed);
		}
	},
	INSTANT("Instant")
	{
		@Override
		public float apply(float current, float target,
			float maxDegreesPerTick)
		{
			return RotationUtils.limitAngleChange(current, target);
		}
	};

	private final String displayName;

	RotationSmoothing(String displayName)
	{
		this.displayName = displayName;
	}

	public String getDisplayName()
	{
		return displayName;
	}

	public abstract float apply(float current, float target,
		float maxDegreesPerTick);

	public static Rotation smooth(Rotation start, Rotation end,
		float maxChange, RotationSmoothing mode)
	{
		float nextYaw = mode.apply(start.yaw(), end.yaw(), maxChange);
		float nextPitch = mode.apply(start.pitch(), end.pitch(),
			maxChange * 0.7F);
		return new Rotation(nextYaw, nextPitch);
	}
}
