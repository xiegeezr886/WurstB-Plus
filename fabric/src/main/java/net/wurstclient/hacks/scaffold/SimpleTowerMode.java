/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
package net.wurstclient.hacks.scaffold;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.WurstClient;
import net.wurstclient.hacks.ScaffoldWalkHack;
import net.wurstclient.hacks.ScaffoldWalkHack.Mode;

/**
 * Vanilla / Anti Gaming Chair / Watchdog / Bloxd. The only differences are the
 * tower jump action and Bloxd's extra interact packet + vertical rescue.
 */
public final class SimpleTowerMode implements ScaffoldMode
{
	private final ScaffoldWalkHack hack;
	private final Mode mode;

	public SimpleTowerMode(ScaffoldWalkHack hack, Mode mode)
	{
		this.hack = hack;
		this.mode = mode;
	}

	@Override
	public void onBlockPlaced(BlockHitResult hitResult)
	{
		if(!hack.tower.isChecked() || WurstClient.MC.player == null
			|| WurstClient.MC.player.hasEffect(MobEffects.JUMP)
			|| WurstClient.MC.options.keyUse.isDown()
			|| !WurstClient.MC.options.keyJump.isDown())
			return;

		switch(mode)
		{
			case VANILLA:
			((net.wurstclient.mixin.LivingEntityAccessor)WurstClient.MC.player)
				.wurst_jumpFromGround();
			break;

			case BLOXD:
			BlockPos above = hitResult.getBlockPos().above();
			BlockHitResult towerHit = new BlockHitResult(
				hitResult.getLocation().add(0, 1, 0),
				hitResult.getDirection(), above, false);
			WurstClient.MC.player.connection.send(
				new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND,
					towerHit, 0));
			break;

			case ANTI_GAMING_CHAIR:
			Vec3 velocity = WurstClient.MC.player.getDeltaMovement();
			WurstClient.MC.player.setDeltaMovement(velocity.x, 0.42D,
				velocity.z);
			break;

			case WATCHDOG:
			default:
			break;
		}
	}

	@Override
	public void onPostMotion()
	{
		if(mode != Mode.BLOXD || WurstClient.MC.player == null
			|| WurstClient.MC.level == null)
			return;

		var box = WurstClient.MC.player.getBoundingBox()
			.inflate(-0.005D, 0.0D, -0.005D);
		if(!ScaffoldUtils.isBoxEmpty(WurstClient.MC.player, box))
		{
			Vec3 pos = WurstClient.MC.player.position();
			WurstClient.MC.player.setPos(pos.x, pos.y + 2.0D, pos.z);
		}
	}

	// Kept so the shared tick can ask a mode for a preferred rotation; the
	// simple modes always use the shared placement rotation.
	@Override
	public ScaffoldWalkHack.RotationRequest getPacketRotation()
	{
		return null;
	}

	@Override
	public String toString()
	{
		return mode.toString();
	}
}
