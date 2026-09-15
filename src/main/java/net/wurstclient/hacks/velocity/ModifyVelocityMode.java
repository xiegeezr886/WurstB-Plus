/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks.velocity;

import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.WurstClient;
import net.wurstclient.events.PacketInputListener.PacketInputEvent;
import net.wurstclient.hacks.NoVelocityHack;
import net.wurstclient.mixin.ClientboundExplodePacketMixin;
import net.wurstclient.util.VelocityPlanner;

/**
 * Modify：把速度包里的值按 Horizontal / Vertical / Retain 设置改小，再取消原包；
 * 爆炸击退也一并按同样的比例缩放。
 */
public final class ModifyVelocityMode implements VelocityMode
{
	@Override
	public String getName()
	{
		return "Modify";
	}
	
	@Override
	public boolean onEntityVelocity(NoVelocityHack hack, PacketInputEvent event,
		Vec3 incoming)
	{
		Vec3 modified = VelocityPlanner.modify(incoming,
			WurstClient.MC.player.getDeltaMovement(),
			hack.getHorizontalMultiplier(), hack.getVerticalMultiplier(),
			hack.getRetainHorizontalMultiplier(),
			hack.getRetainVerticalMultiplier());
		WurstClient.MC.player.setDeltaMovement(modified);
		return true;
	}
	
	@Override
	public boolean handlesExplosions()
	{
		return true;
	}
	
	@Override
	public void onExplosion(ClientboundExplodePacket packet,
		double horizontalMultiplier, double verticalMultiplier)
	{
		ClientboundExplodePacketMixin accessor =
			(ClientboundExplodePacketMixin)(Object)packet;
		accessor.wurst_setKnockbackX(
			(float)(packet.getKnockbackX() * horizontalMultiplier));
		accessor.wurst_setKnockbackY(
			(float)(packet.getKnockbackY() * verticalMultiplier));
		accessor.wurst_setKnockbackZ(
			(float)(packet.getKnockbackZ() * horizontalMultiplier));
	}
}
