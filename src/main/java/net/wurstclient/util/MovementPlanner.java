/*
 * Copyright (c) 2015-2026 CCBlueX
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

import net.minecraft.world.phys.Vec3;

public enum MovementPlanner
{
	;

	public static boolean isMoving(float forward, float sideways)
	{
		return Float.isFinite(forward) && Float.isFinite(sideways)
			&& (Math.abs(forward) > 1.0E-5F
				|| Math.abs(sideways) > 1.0E-5F);
	}

	public static Vec3 horizontalMotion(float forward, float sideways,
		float yaw, double speed)
	{
		if(!Float.isFinite(forward) || !Float.isFinite(sideways)
			|| !Float.isFinite(yaw) || !Double.isFinite(speed))
			return Vec3.ZERO;
		double length = Math.hypot(forward, sideways);
		if(length < 1.0E-5 || speed <= 0)
			return Vec3.ZERO;

		double normalizedForward = forward / Math.max(1, length);
		double normalizedSideways = sideways / Math.max(1, length);
		double radians = Math.toRadians(yaw);
		double sin = Math.sin(radians);
		double cos = Math.cos(radians);
		double x = normalizedSideways * cos - normalizedForward * sin;
		double z = normalizedForward * cos + normalizedSideways * sin;
		return new Vec3(x * speed, 0, z * speed);
	}

	public static Vec3 setHorizontal(Vec3 current, float forward,
		float sideways, float yaw, double speed)
	{
		Vec3 horizontal = horizontalMotion(forward, sideways, yaw, speed);
		return new Vec3(horizontal.x, current.y, horizontal.z);
	}

	public static Vec3 blendHorizontal(Vec3 current, float forward,
		float sideways, float yaw, double speed, double strength)
	{
		Vec3 desired = horizontalMotion(forward, sideways, yaw, speed);
		double factor = Double.isFinite(strength)
			? Math.max(0, Math.min(1, strength)) : 0;
		double currentX = Double.isFinite(current.x) ? current.x : 0;
		double currentZ = Double.isFinite(current.z) ? current.z : 0;
		return new Vec3(currentX + (desired.x - currentX) * factor,
			current.y, currentZ + (desired.z - currentZ) * factor);
	}

	public static Vec3 clampHorizontal(Vec3 movement, double maximum)
	{
		if(!Double.isFinite(maximum) || maximum <= 0)
			return new Vec3(0, movement.y, 0);
		if(!Double.isFinite(movement.x) || !Double.isFinite(movement.z))
			return new Vec3(0, movement.y, 0);

		double horizontal = Math.hypot(movement.x, movement.z);
		if(horizontal <= maximum || horizontal < 1.0E-5)
			return movement;

		double scale = maximum / horizontal;
		return new Vec3(movement.x * scale, movement.y, movement.z * scale);
	}

	public static Vec3 clampControlledHorizontal(Vec3 current, Vec3 proposed,
		double maximum)
	{
		double currentSpeed = Math.hypot(current.x, current.z);
		if(Double.isFinite(currentSpeed) && Double.isFinite(maximum)
			&& currentSpeed > Math.max(0, maximum) + 1.0E-5)
			return new Vec3(current.x, proposed.y, current.z);
		return clampHorizontal(proposed, maximum);
	}
}
