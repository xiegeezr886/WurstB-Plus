/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks.speed;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.hacks.SpeedHackHack;
import net.wurstclient.util.MovementPlanner;

/** OnGround：只在地面那几 tick 把水平速度设成目标速度。 */
public final class OnGroundSpeedMode implements SpeedMode
{
	@Override
	public String getName()
	{
		return "OnGround";
	}
	
	@Override
	public void apply(SpeedHackHack hack, LocalPlayer player, float forward,
		float sideways, double targetSpeed)
	{
		if(!player.onGround())
			return;
		
		Vec3 current = player.getDeltaMovement();
		Vec3 proposed = MovementPlanner.setHorizontal(current, forward, sideways,
			player.getYRot(), targetSpeed);
		player.setDeltaMovement(MovementPlanner
			.clampControlledHorizontal(current, proposed, targetSpeed));
	}
}
