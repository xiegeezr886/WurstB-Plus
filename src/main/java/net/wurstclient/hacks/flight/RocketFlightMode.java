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
 * Rocket：水平与 Vanilla 相同，但垂直速度乘 3（配合火箭/烟花式的冲刺感）。
 */
public final class RocketFlightMode implements FlightMode
{
	@Override
	public String getName()
	{
		return "Rocket";
	}
	
	@Override
	public Vec3 getHorizontalMovement(Vec3 delta, float forward,
		float sideways, float yRot, double horizontal)
	{
		return MovementPlanner.setHorizontal(delta, forward, sideways, yRot,
			horizontal);
	}
	
	@Override
	public double getVerticalMultiplier()
	{
		return 3;
	}
}
