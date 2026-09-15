/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks.velocity;

import net.minecraft.world.phys.Vec3;
import net.wurstclient.WurstClient;
import net.wurstclient.events.PacketInputListener.PacketInputEvent;
import net.wurstclient.hacks.NoVelocityHack;
import net.wurstclient.util.VelocityPlanner;
import net.wurstclient.util.VelocityPlanner.JumpResetDecision;

/**
 * JumpReset：不动速度包，而是记下"刚被击退"，然后在接下来几 tick 里挑一个合适的时机
 * 按跳跃键，用原版的跳跃重置把自己拉回来（对击退有减免的服务器很有效）。
 * 待跳计数是本模式自己的状态（原来挂在 hack 上）。
 */
public final class JumpResetVelocityMode implements VelocityMode
{
	private int pendingJumpTicks = -1;
	private int pendingJumpAge;
	private int pendingJumpMaximumAge;
	
	@Override
	public String getName()
	{
		return "JumpReset";
	}
	
	@Override
	public boolean onEntityVelocity(NoVelocityHack hack, PacketInputEvent event,
		Vec3 incoming)
	{
		if(!VelocityPlanner.isFallDamageVelocity(incoming))
		{
			pendingJumpTicks = hack.getJumpDelay();
			pendingJumpAge = 0;
			pendingJumpMaximumAge = pendingJumpTicks + 2;
		}
		
		// JumpReset 不改写速度包，所以不取消它。
		return false;
	}
	
	@Override
	public void onUpdate(NoVelocityHack hack)
	{
		if(pendingJumpTicks < 0)
			return;
		if(WurstClient.MC.player == null)
		{
			reset();
			return;
		}
		
		boolean moving =
			WurstClient.MC.player.input.getMoveVector().length() > 1.0E-5F;
		JumpResetDecision decision = VelocityPlanner.evaluateJumpReset(
			pendingJumpTicks, pendingJumpAge, pendingJumpMaximumAge,
			WurstClient.MC.player.onGround(), moving, hack.isOnlyMoving(),
			WurstClient.MC.player.isSprinting(), hack.isRequireSprint());
		if(decision == JumpResetDecision.WAIT)
		{
			pendingJumpTicks--;
			pendingJumpAge++;
			return;
		}
		if(decision == JumpResetDecision.JUMP)
			WurstClient.MC.player.jumpFromGround();
		reset();
	}
	
	@Override
	public void reset()
	{
		pendingJumpTicks = -1;
		pendingJumpAge = 0;
		pendingJumpMaximumAge = 0;
	}
}
