/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
package net.wurstclient.hacks.scaffold;

import java.util.Random;

import net.minecraft.util.Mth;
import net.wurstclient.util.Rotation;

/**
 * The rotation models used by Scaffold, ported one-to-one from OpenOpal's
 * {@code IRotationModel} implementations. {@code Max angle} is the model speed
 * in degrees per tick; {@code timeDelta} is the fractional tick progress.
 */
public enum ScaffoldRotationModel
{
	INSTANT("Instant"),
	HEYPIXEL("Heypixel"),
	LINEAR("Linear"),
	ORGANIC("Organic"),
	SIDEWAYS("Sideways");

	private static final Random RANDOM = new Random(System.nanoTime());
	private static double organicTime;

	private final String name;

	ScaffoldRotationModel(String name)
	{
		this.name = name;
	}

	@Override
	public String toString()
	{
		return name;
	}

	public Rotation tick(Rotation from, Rotation to, float speed)
	{
		return tick(from, to, speed, 1.0F, 1.2D, 0.12D);
	}

	public Rotation tick(Rotation from, Rotation to, float speed,
		float timeDelta, double driftIntensity, double jitterIntensity)
	{
		switch(this)
		{
			case INSTANT:
			return new Rotation(from.yaw()
				+ Mth.wrapDegrees(to.yaw() - from.yaw()) * timeDelta,
				from.pitch() + (to.pitch() - from.pitch()) * timeDelta);

			case LINEAR:
			{
				float deltaYaw = Mth.wrapDegrees(to.yaw() - from.yaw())
					* timeDelta;
				float deltaPitch = (to.pitch() - from.pitch()) * timeDelta;
				double distance = Math
					.sqrt(deltaYaw * deltaYaw + deltaPitch * deltaPitch);
				if(distance == 0.0D)
					return new Rotation(from.yaw() + deltaYaw,
						from.pitch() + deltaPitch);

				double ratioYaw = Math.abs(deltaYaw / distance);
				double ratioPitch = Math.abs(deltaPitch / distance);
				float moveYaw = (float)Math.max(
					Math.min(deltaYaw, speed * ratioYaw),
					-speed * ratioYaw);
				float movePitch = (float)Math.max(
					Math.min(deltaPitch, speed * ratioPitch),
					-speed * ratioPitch);
				return new Rotation(from.yaw() + moveYaw,
					from.pitch() + movePitch);
			}

			case ORGANIC:
			{
				float rawYaw = Mth.wrapDegrees(to.yaw() - from.yaw());
				float rawPitch = to.pitch() - from.pitch();
				float deltaYaw = rawYaw * timeDelta;
				float deltaPitch = rawPitch * timeDelta;
				double distance = Math.hypot(deltaYaw, deltaPitch);

				if(distance >= driftIntensity && distance > 0)
				{
					double ratioYaw = Math.abs(deltaYaw) / distance;
					double ratioPitch = Math.abs(deltaPitch) / distance;
					double maxYaw = speed * ratioYaw * timeDelta;
					double maxPitch = speed * ratioPitch * timeDelta;
					deltaYaw = Mth.clamp(deltaYaw, (float)-maxYaw,
						(float)maxYaw);
					deltaPitch = Mth.clamp(deltaPitch, (float)-maxPitch,
						(float)maxPitch);
				}

				organicTime += timeDelta;
				double driftYaw = Math
					.sin(organicTime * 0.25D + RANDOM.nextDouble() * Math.PI
						* 2.0D)
					* driftIntensity * timeDelta;
				double driftPitch = Math
					.sin(organicTime * 0.55D + RANDOM.nextDouble() * Math.PI
						* 2.0D)
					* driftIntensity * timeDelta;
				double jitterYaw = (RANDOM.nextDouble() * 2.0D - 1.0D)
					* jitterIntensity * timeDelta;
				double jitterPitch = (RANDOM.nextDouble() * 2.0D - 1.0D)
					* jitterIntensity * timeDelta;
				Rotation rotation = new Rotation(
					from.yaw() + deltaYaw + (float)driftYaw
						+ (float)jitterYaw,
					from.pitch() + deltaPitch + (float)driftPitch
						+ (float)jitterPitch);
				return ScaffoldUtils.patchConstantRotation(rotation, from);
			}

			case HEYPIXEL:
			case SIDEWAYS:
			default:
			{
				float deltaYaw = Mth.wrapDegrees(to.yaw() - from.yaw());
				float deltaPitch = to.pitch() - from.pitch();
				double distance = Math
					.sqrt(deltaYaw * deltaYaw + deltaPitch * deltaPitch);
				if(distance <= 1.0E-6D)
					return from;

				double ratioYaw = Math.abs(deltaYaw / distance);
				double ratioPitch = Math.abs(deltaPitch / distance);
				float moveYaw = (float)Math.max(
					Math.min(deltaYaw, speed * ratioYaw),
					-speed * ratioYaw);
				float movePitch = (float)Math.max(
					Math.min(deltaPitch, speed * ratioPitch),
					-speed * ratioPitch);
				Rotation rotation = new Rotation(from.yaw() + moveYaw,
					Mth.clamp(from.pitch() + movePitch, -90.0F, 90.0F));
				return ScaffoldUtils.patchConstantRotation(rotation, from);
			}
		}
	}

	/**
	 * OpenOpal's {@code HypixelRotationModel}: dynamic speed based on the
	 * movement direction and air ticks.
	 */
	public static Rotation tickHypixel(Rotation from, Rotation to,
		float timeDelta, boolean diagonalYaw, int airTicks)
	{
		float deltaYaw = Mth.wrapDegrees(to.yaw() - from.yaw()) * timeDelta;
		float deltaPitch = (to.pitch() - from.pitch()) * timeDelta;
		double distance = Math.sqrt(deltaYaw * deltaYaw + deltaPitch * deltaPitch);
		if(distance == 0.0D)
			return new Rotation(from.yaw() + deltaYaw,
				from.pitch() + deltaPitch);

		float speed = diagonalYaw ? airTicks == 1 ? 65.0F : 36.0F : 35.0F;
		double ratioYaw = Math.abs(deltaYaw / distance);
		double ratioPitch = Math.abs(deltaPitch / distance);
		float moveYaw = (float)Math.max(Math.min(deltaYaw, speed * ratioYaw),
			-speed * ratioYaw);
		float movePitch = (float)Math.max(
			Math.min(deltaPitch, speed * ratioPitch), -speed * ratioPitch);
		return new Rotation(from.yaw() + moveYaw, from.pitch() + movePitch);
	}
}
