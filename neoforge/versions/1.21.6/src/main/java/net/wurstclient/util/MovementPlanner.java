/*
 * Copyright (c) 2015-2026 CCBlueX
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import java.lang.reflect.Method;

public enum MovementPlanner
{
	;

	public static boolean isMoving(float forward, float sideways)
	{
		return Math.abs(forward) > 1.0E-5F || Math.abs(sideways) > 1.0E-5F;
	}

	public static Vec2 getMoveVector(Object input)
	{
		if(input == null)
			return Vec2.ZERO;

		float sideways = (isPressed(input, "left") ? 1F : 0F)
			- (isPressed(input, "right") ? 1F : 0F);
		float forward = (isPressed(input, "forward") ? 1F : 0F)
			- (isPressed(input, "backward") ? 1F : 0F);
		return new Vec2(sideways, forward);
	}

	public static boolean isMoving(Object input)
	{
		return getMoveVector(input).length() > 1.0E-5F;
	}

	private static boolean isPressed(Object input, String name)
	{
		try
		{
			Method method = null;
			try
			{
				method = input.getClass().getMethod(name);
			}catch(NoSuchMethodException e)
			{
				if("forward".equals(name))
					method = input.getClass()
						.getMethod("forwardImpulse");
			}
			if(method == null)
				return false;
			Object value = method.invoke(input);
			return value instanceof Boolean b && b;
		}catch(ReflectiveOperationException e)
		{
			return false;
		}
	}

	public static Vec3 horizontalMotion(float forward, float sideways,
		float yaw, double speed)
	{
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
		double factor = Math.max(0, Math.min(1, strength));
		return new Vec3(current.x + (desired.x - current.x) * factor,
			current.y, current.z + (desired.z - current.z) * factor);
	}

	public static Vec3 clampHorizontal(Vec3 movement, double maximum)
	{
		if(maximum <= 0)
			return new Vec3(0, movement.y, 0);

		double horizontal = Math.hypot(movement.x, movement.z);
		if(horizontal <= maximum || horizontal < 1.0E-5)
			return movement;

		double scale = maximum / horizontal;
		return new Vec3(movement.x * scale, movement.y, movement.z * scale);
	}
}
