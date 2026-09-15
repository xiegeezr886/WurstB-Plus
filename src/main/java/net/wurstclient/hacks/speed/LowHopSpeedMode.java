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

/**
 * LowHop：落地给一个很小的向上速度（0.2），空中限制下沉速度。
 * {@code lowHopActive} 是本模式自己的状态，所以由本类持有并在 {@link #reset()} 里清掉。
 */
public final class LowHopSpeedMode implements SpeedMode
{
	private boolean lowHopActive;
	
	@Override
	public String getName()
	{
		return "LowHop";
	}
	
	@Override
	public void apply(SpeedHackHack hack, LocalPlayer player, float forward,
		float sideways, double targetSpeed)
	{
		Vec3 current = player.getDeltaMovement();
		Vec3 movement = MovementPlanner.blendHorizontal(current, forward,
			sideways, player.getYRot(), targetSpeed,
			player.onGround() ? 1 : hack.getStrafeSpeed());
		if(player.onGround())
		{
			lowHopActive = hack.isAutoJump();
			if(lowHopActive)
				movement = new Vec3(movement.x, 0.2, movement.z);
		}else if(!hack.isAutoJump())
			lowHopActive = false;
		else if(lowHopActive && movement.y < -0.08)
			movement = new Vec3(movement.x, -0.08, movement.z);
		player.setDeltaMovement(MovementPlanner.clampControlledHorizontal(current,
			movement, targetSpeed));
	}
	
	@Override
	public void reset()
	{
		lowHopActive = false;
	}
}
