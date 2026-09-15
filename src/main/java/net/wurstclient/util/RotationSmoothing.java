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
	},
	/**
	 * 与 Linear 的区别：把"这一步最多转多少度"当成**两轴合起来的步长**，
	 * 按两轴各自占总转角的比例分配，因此 yaw 与 pitch 会同时到位
	 * （OpenOpal 的 {@code LinearRotationModel} 就是这个分配方式）。
	 *
	 * <p>其余模式（以及 Linear 自己）是逐轴限速、且俯仰只拿到
	 * {@code 0.7 * maxChange}，两轴不会同时收敛：目标偏航 90°、俯仰 30°、每 tick 30° 时，
	 * Linear 一 tick 走 (30, 21)，偏航先到位而俯仰还在走，轨迹是一条折线。
	 */
	PROPORTIONAL("Proportional")
	{
		@Override
		public float apply(float current, float target,
			float maxDegreesPerTick)
		{
			// 逐轴调用时没有"合起来"的概念，等同于 Linear
			return RotationUtils.limitAngleChange(current, target,
				maxDegreesPerTick);
		}

		@Override
		public Rotation applyVector(Rotation current, Rotation target,
			float maxDegreesPerTick)
		{
			float deltaYaw = Mth.wrapDegrees(
				Mth.wrapDegrees(target.yaw()) - Mth.wrapDegrees(current.yaw()));
			float deltaPitch = target.pitch() - current.pitch();
			float distance = (float)Math.sqrt(
				deltaYaw * deltaYaw + deltaPitch * deltaPitch);

			if(distance == 0 || distance <= maxDegreesPerTick)
				return target;

			float scale = maxDegreesPerTick / distance;
			return new Rotation(current.yaw() + deltaYaw * scale,
				current.pitch() + deltaPitch * scale);
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

	/**
	 * 一次算出两个轴。默认实现就是逐轴 {@link #apply}（偏航用 {@code maxDegreesPerTick}、
	 * 俯仰用 {@code 0.7 * maxDegreesPerTick}），与加这个方法之前的行为逐位相同；
	 * 需要"两轴同步"的模型覆盖它。
	 */
	public Rotation applyVector(Rotation current, Rotation target,
		float maxDegreesPerTick)
	{
		float yaw = apply(current.yaw(), target.yaw(), maxDegreesPerTick);
		float pitch = apply(current.pitch(), target.pitch(),
			maxDegreesPerTick * 0.7F);
		return new Rotation(yaw, pitch);
	}

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
		Rotation next = mode.applyVector(safeStart, safeEnd, safeMaxChange);
		float nextYaw = next.yaw();
		float nextPitch = next.pitch();
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
