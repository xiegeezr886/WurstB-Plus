/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks.step;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.phys.AABB;
import net.wurstclient.WurstClient;
import net.wurstclient.hacks.StepHack;
import net.wurstclient.util.BlockUtils;

/**
 * Legit：不改 {@code maxUpStep}，而是在卡住墙、脚下有 0.5~1 格台阶时，
 * 连发两个位移动包再本地把自己抬上去（0.42 与 0.753 两个高度是 LiquidBounce
 * 原版的 MagicStep 系数），最后给 2 tick 冷却避免连发。
 *
 * <p>
 * 每一步都有前置检查（冷却、撞墙、在地面、不在梯子/水里、有移动输入、没按跳跃、
 * 头顶不卡、台阶高度在 0.5~1 之间），任一不满足就整 tick 不做任何事 —— 顺序与原实现一致。
 */
public final class LegitStepMode implements StepMode
{
	@Override
	public String getName()
	{
		return "Legit";
	}
	
	@Override
	public void onUpdate(StepHack hack, LocalPlayer player)
	{
		player.maxUpStep = hack.getPreviousStepHeight();
		
		if(hack.getStepCooldown() > 0 || !player.horizontalCollision)
			return;
		
		if(!player.onGround() || player.onClimbable() || player.isInWater()
			|| player.isInLava())
			return;
		
		if(player.input.forwardImpulse == 0 && player.input.leftImpulse == 0)
			return;
		
		if(player.input.jumping)
			return;
		
		AABB box = player.getBoundingBox().move(0, 0.05, 0).inflate(0.05);
		
		if(!WurstClient.MC.level.noCollision(player, box.move(0, 1, 0)))
			return;
		
		double stepHeight = BlockUtils.getBlockCollisions(box)
			.mapToDouble(bb -> bb.maxY).max().orElse(Double.NEGATIVE_INFINITY);
		
		stepHeight -= player.getY();
		
		if(stepHeight <= 0.5 || stepHeight > 1)
			return;
		
		ClientPacketListener netHandler = player.connection;
		
		netHandler.send(new ServerboundMovePlayerPacket.Pos(player.getX(),
			player.getY() + 0.42 * stepHeight, player.getZ(), false));
		
		netHandler.send(new ServerboundMovePlayerPacket.Pos(player.getX(),
			player.getY() + 0.753 * stepHeight, player.getZ(), false));
		
		player.setPos(player.getX(), player.getY() + stepHeight, player.getZ());
		hack.setStepCooldown(2);
	}
}
