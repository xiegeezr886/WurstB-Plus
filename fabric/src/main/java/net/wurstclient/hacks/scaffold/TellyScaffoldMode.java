/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
package net.wurstclient.hacks.scaffold;

import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.wurstclient.WurstClient;
import net.wurstclient.events.MoveInputListener;
import net.wurstclient.hacks.ScaffoldWalkHack;
import net.wurstclient.hacks.ScaffoldWalkHack.BlockData;
import net.wurstclient.mixin.LivingEntityAccessor;
import net.wurstclient.util.Rotation;

/**
 * Ported from OpenOpal's {@code TellyScaffold}: target Y level tracking, the
 * ground/air rotation rules, the sensitivity patch and auto jump.
 */
public final class TellyScaffoldMode implements ScaffoldMode
{
	private final ScaffoldWalkHack hack;

	private int targetYLevel;
	private int groundTicks;
	private int airTicks;
	private Rotation lastRotation;

	public TellyScaffoldMode(ScaffoldWalkHack hack)
	{
		this.hack = hack;
	}

	@Override
	public boolean handlesTick()
	{
		return true;
	}

	@Override
	public void onEnable()
	{
		targetYLevel = WurstClient.MC.player == null ? -1
			: Mth.floor(WurstClient.MC.player.getY()) - 1;
		groundTicks = 0;
		airTicks = 0;
		lastRotation = null;
	}

	@Override
	public void onDisable()
	{
		lastRotation = null;
	}

	@Override
	public void onMoveInput(MoveInputListener.MoveInputEvent event)
	{
		var player = WurstClient.MC.player;
		if(player == null || WurstClient.MC.level == null
			|| player.hasEffect(MobEffects.JUMP))
			return;
		if(!ScaffoldUtils.isMoving()
			&& Math.abs(event.getForward()) < 1.0E-4F
			&& Math.abs(event.getSideways()) < 1.0E-4F)
			return;
		if(canAutoJump(event))
		{
			((LivingEntityAccessor)player).wurst_setNoJumpDelay(0);
			event.setJump(true);
		}
	}

	@Override
	public void onTick()
	{
		if(WurstClient.MC.player == null || WurstClient.MC.level == null)
			return;

		if(WurstClient.MC.player.onGround())
		{
			groundTicks++;
			airTicks = 0;
		}else
		{
			airTicks++;
			groundTicks = 0;
		}

		boolean manualJump = WurstClient.MC.options.keyJump.isDown();
		if(targetYLevel == -1 || WurstClient.MC.player.onGround()
			|| !ScaffoldUtils.isMoving() || manualJump)
			targetYLevel = Mth.floor(WurstClient.MC.player.getY()) - 1;

		BlockData data = hack.findBestTellyBlockData(targetYLevel);
		if(data == null)
		{
			hack.setBlockCache(null);
			hack.setRotation(null);
			return;
		}

		hack.setBlockCache(data);
		Rotation targetRotation = legalizeRotation(
			getTargetRotation(data.rotation()));
		targetRotation = targetRotation.withPitch(
			Mth.clamp(targetRotation.pitch(), -89.5F, 89.5F));
		hack.setRotation(targetRotation);
		hack.applyModeRotation(targetRotation,
			hack.tellyHeypixel.isChecked() ? ScaffoldRotationModel.HEYPIXEL
				: ScaffoldRotationModel.INSTANT,
			180.0F);
		lastRotation = targetRotation;
	}

	private boolean canAutoJump(MoveInputListener.MoveInputEvent event)
	{
		var player = WurstClient.MC.player;
		if(player == null || !player.onGround() || player.isShiftKeyDown()
			|| event.isSneak() || !hack.hasHotbarPlaceableBlock())
			return false;
		if(player.isInWater() || player.isInLava() || player.onClimbable())
			return false;
		return ScaffoldUtils.isBoxEmpty(player,
			player.getBoundingBox().move(0.0D, 0.42D, 0.0D));
	}

	private Rotation getTargetRotation(Rotation blockRotation)
	{
		var player = WurstClient.MC.player;
		if(groundTicks > 0)
		{
			if(!WurstClient.MC.options.keyJump.isDown())
				return new Rotation(ScaffoldUtils.getRotation().yaw(), 75.5F);
			if(groundTicks == 1 && lastRotation != null)
			{
				float yawDelta = Mth.wrapDegrees(
					blockRotation.yaw() - lastRotation.yaw());
				float halfDelta = Math.max(1.0F,
					Math.abs(yawDelta) * 0.5F);
				return new Rotation(lastRotation.yaw()
					+ Mth.clamp(yawDelta, -halfDelta, halfDelta), 75.5F);
			}
			return new Rotation(ScaffoldUtils.getRotation().yaw(), 75.5F);
		}

		if(lastRotation == null)
			return blockRotation;
		float limit = airTicks == 1 ? 90.0F : 50.0F;
		float yawDelta = Mth.wrapDegrees(
			blockRotation.yaw() - lastRotation.yaw());
		return new Rotation(lastRotation.yaw()
			+ Mth.clamp(yawDelta, -limit, limit), blockRotation.pitch());
	}

	private Rotation legalizeRotation(Rotation rotation)
	{
		Rotation clamped = new Rotation(rotation.yaw(),
			Mth.clamp(rotation.pitch(), -89.5F, 89.5F));
		Rotation reference =
			lastRotation != null ? lastRotation : ScaffoldUtils.getRotation();
		Rotation patched = ScaffoldUtils.patchConstantRotation(clamped,
			reference);
		return new Rotation(patched.yaw(),
			Mth.clamp(patched.pitch(), -89.5F, 89.5F));
	}
}
