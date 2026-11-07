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
import java.lang.reflect.Field;
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

		// 1.21.6+ hands the final movement vector out directly on ClientInput.
		// Reading it is what vanilla movement itself uses.
		Vec2 vanilla = callGetMoveVector(input);
		if(vanilla != null)
			return vanilla;

		// Legacy inputs (1.20.x - 1.21.5) expose impulse floats instead of
		// key booleans.
		Float forwardImpulse = readFloat(input, "forwardImpulse");
		Float leftImpulse = readFloat(input, "leftImpulse");
		if(forwardImpulse != null || leftImpulse != null)
			return new Vec2(leftImpulse == null ? 0F : leftImpulse,
				forwardImpulse == null ? 0F : forwardImpulse);

		// Otherwise the key states live either on the input itself or in its
		// keyPresses record (1.21.6+ keeps them in
		// net.minecraft.world.entity.player.Input).
		Object keys = readField(input, "keyPresses");
		if(keys == null)
			keys = input;

		float sideways = (isPressed(keys, "left") ? 1F : 0F)
			- (isPressed(keys, "right") ? 1F : 0F);
		float forward = (isPressed(keys, "forward") ? 1F : 0F)
			- (isPressed(keys, "backward") ? 1F : 0F);
		return new Vec2(sideways, forward);
	}

	public static boolean isMoving(Object input)
	{
		return getMoveVector(input).length() > 1.0E-5F;
	}

	private static Vec2 callGetMoveVector(Object input)
	{
		try
		{
			Method method = input.getClass().getMethod("getMoveVector");
			if(method.getReturnType() != Vec2.class)
				return null;

			method.trySetAccessible();
			return (Vec2)method.invoke(input);
		}catch(ReflectiveOperationException e)
		{
			return null;
		}
	}

	private static Object readField(Object target, String name)
	{
		try
		{
			Field field = target.getClass().getField(name);
			field.trySetAccessible();
			return field.get(target);
		}catch(ReflectiveOperationException e)
		{
			return null;
		}
	}

	private static Float readFloat(Object target, String name)
	{
		Object value = readField(target, name);
		return value instanceof Number number ? number.floatValue() : null;
	}

	private static boolean isPressed(Object target, String name)
	{
		try
		{
			Method method = target.getClass().getMethod(name);
			method.trySetAccessible();
			Object value = method.invoke(target);
			if(value instanceof Boolean pressed)
				return pressed;
			if(value instanceof Number number)
				return number.floatValue() > 1.0E-5F;
		}catch(NoSuchMethodException e)
		{
			// fall through to the field lookup
		}catch(ReflectiveOperationException e)
		{
			return false;
		}

		Object value = readField(target, name);
		if(value instanceof Boolean pressed)
			return pressed;
		if(value instanceof Number number)
			return number.floatValue() > 1.0E-5F;

		return false;
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
