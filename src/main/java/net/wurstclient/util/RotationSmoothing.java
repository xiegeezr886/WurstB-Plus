/*
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

import java.util.Objects;
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
		Objects.requireNonNull(start, "start");
		Objects.requireNonNull(end, "end");
		Objects.requireNonNull(mode, "mode");
		Rotation safeStart = sanitize(start, new Rotation(0, 0));
		Rotation safeEnd = sanitize(end, safeStart);
		float safeMaxChange = Float.isFinite(maxChange)
			? Math.max(0, maxChange) : 0;
		float nextYaw = mode.apply(safeStart.yaw(), safeEnd.yaw(),
			safeMaxChange);
		float nextPitch = mode.apply(safeStart.pitch(), safeEnd.pitch(),
			safeMaxChange * 0.7F);
		if(!Float.isFinite(nextYaw))
			nextYaw = safeStart.yaw();
		if(!Float.isFinite(nextPitch))
			nextPitch = safeStart.pitch();
		return new Rotation(nextYaw, Mth.clamp(nextPitch, -90, 90));
	}

	public static Step smoothWithAcceleration(Rotation start, Rotation end,
		Rotation previousDelta, float maxChange, float maxAcceleration,
		RotationSmoothing mode)
	{
		Objects.requireNonNull(start, "start");
		Objects.requireNonNull(end, "end");
		Rotation safeStart = sanitize(start, new Rotation(0, 0));
		Rotation desired = smooth(safeStart, end, maxChange, mode);
		float desiredYaw = Mth.wrapDegrees(desired.yaw() - safeStart.yaw());
		float desiredPitch = desired.pitch() - safeStart.pitch();
		float previousYaw = previousDelta != null
			&& Float.isFinite(previousDelta.yaw()) ? previousDelta.yaw() : 0;
		float previousPitch = previousDelta != null
			&& Float.isFinite(previousDelta.pitch()) ? previousDelta.pitch() : 0;
		float acceleration = Float.isFinite(maxAcceleration)
			? Math.max(0, maxAcceleration) : 0;

		float yawDelta = approach(previousYaw, desiredYaw, acceleration);
		float pitchDelta = approach(previousPitch, desiredPitch,
			acceleration * 0.7F);
		float pitch = Mth.clamp(safeStart.pitch() + pitchDelta, -90, 90);
		Rotation rotation = new Rotation(safeStart.yaw() + yawDelta, pitch);
		return new Step(rotation,
			new Rotation(yawDelta, pitch - safeStart.pitch()));
	}

	private static Rotation sanitize(Rotation rotation, Rotation fallback)
	{
		float yaw = Float.isFinite(rotation.yaw()) ? rotation.yaw()
			: fallback.yaw();
		float pitch = Float.isFinite(rotation.pitch()) ? rotation.pitch()
			: fallback.pitch();
		return new Rotation(yaw, Mth.clamp(pitch, -90, 90));
	}

	private static float approach(float current, float target, float amount)
	{
		if(amount == 0)
			return target;
		return Mth.clamp(target, current - amount, current + amount);
	}

	public record Step(Rotation rotation, Rotation delta)
	{
	}
}
