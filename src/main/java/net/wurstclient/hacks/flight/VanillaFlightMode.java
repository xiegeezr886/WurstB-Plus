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

/** Vanilla：水平速度直接按输入设置（由服务端/原版飞行逻辑处理）。 */
public final class VanillaFlightMode implements FlightMode
{
	@Override
	public String getName()
	{
		return "Vanilla";
	}
	
	@Override
	public Vec3 getHorizontalMovement(Vec3 delta, float forward,
		float sideways, float yRot, double horizontal)
	{
		return MovementPlanner.setHorizontal(delta, forward, sideways, yRot,
			horizontal);
	}
}
