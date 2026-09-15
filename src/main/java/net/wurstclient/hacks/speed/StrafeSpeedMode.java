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

/** Strafe：地面直接给满，空中按 Strafe speed 强度平滑。 */
public final class StrafeSpeedMode implements SpeedMode
{
	@Override
	public String getName()
	{
		return "Strafe";
	}
	
	@Override
	public void apply(SpeedHackHack hack, LocalPlayer player, float forward,
		float sideways, double targetSpeed)
	{
		Vec3 current = player.getDeltaMovement();
		Vec3 movement = MovementPlanner.blendHorizontal(current, forward,
			sideways, player.getYRot(), targetSpeed,
			player.onGround() ? 1 : hack.getStrafeSpeed());
		if(player.onGround() && hack.isAutoJump())
			movement = new Vec3(movement.x, 0.42, movement.z);
		player.setDeltaMovement(MovementPlanner.clampControlledHorizontal(current,
			movement, targetSpeed));
	}
}
