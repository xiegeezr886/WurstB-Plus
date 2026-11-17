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

public enum VelocityPlanner
{
	;

	public static Vec3 modify(Vec3 incoming, Vec3 current, double horizontal,
		double vertical, double retainHorizontal, double retainVertical)
	{
		double x = horizontal == 0 ? current.x * retainHorizontal
			: incoming.x * horizontal;
		double y = vertical == 0 ? current.y * retainVertical
			: incoming.y * vertical;
		double z = horizontal == 0 ? current.z * retainHorizontal
			: incoming.z * horizontal;
		return new Vec3(x, y, z);
	}

	public static boolean shouldApply(int chance, int roll, boolean onlyMoving,
		boolean moving, Trigger trigger, boolean onGround, boolean inFluid,
		boolean allowInFluid, boolean fallFlying, boolean allowWhileFlying)
	{
		if(chance <= 0 || roll >= chance || onlyMoving && !moving
			|| inFluid && !allowInFluid
			|| fallFlying && !allowWhileFlying)
			return false;

		return switch(trigger)
		{
			case ALWAYS -> true;
			case ON_GROUND -> onGround;
			case IN_AIR -> !onGround;
		};
	}

	public static boolean isFallDamageVelocity(Vec3 velocity)
	{
		return velocity.x == 0 && velocity.z == 0 && velocity.y < 0;
	}

	public enum Trigger
	{
		ALWAYS("Always"),
		ON_GROUND("On ground"),
		IN_AIR("In air");

		private final String name;

		Trigger(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}
}
