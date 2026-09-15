/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks.flight;

import net.minecraft.world.phys.Vec3;
import net.wurstclient.util.MovementPlanner;

/**
 * Boost：先把速度往输入方向平滑（blend 0.1），再夹到设置的水平速度上限，
 * 所以起步柔、不会瞬间到达设定速度。
 */
public final class BoostFlightMode implements FlightMode
{
	@Override
	public String getName()
	{
		return "Boost";
	}
	
	@Override
	public Vec3 getHorizontalMovement(Vec3 delta, float forward,
		float sideways, float yRot, double horizontal)
	{
		return MovementPlanner.clampHorizontal(MovementPlanner.blendHorizontal(
			delta, forward, sideways, yRot, horizontal, 0.1), horizontal);
	}
}
