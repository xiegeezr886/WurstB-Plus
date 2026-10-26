/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
package net.wurstclient.hacks.scaffold;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.wurstclient.WurstClient;
import net.wurstclient.hacks.ScaffoldWalkHack;
import net.wurstclient.hacks.ScaffoldWalkHack.SilenceBlockSlotMode;
import net.wurstclient.hacks.ScaffoldWalkHack.SilenceJumpMode;
import net.wurstclient.hacks.ScaffoldWalkHack.SilenceTellyMode;
import net.wurstclient.settings.SwingHandSetting.SwingHand;
import net.wurstclient.util.Rotation;

/**
 * Ported from OpenOpal's {@code SilenceTellyScaffold}. It keeps the full
 * request/arm/place state machine: the placement rotation is written into the
 * outgoing movement packet, the block is placed after that packet has been
 * acknowledged, and the armed placement is executed on the next input phase.
 *
 * <p>OpenOpal's StuckModule / SkipTick rescue and outbound packet blockage are
 * not ported because Wurst has no equivalent subsystems; everything else
 * (target search, rescue rotation, eagle, parkour jump, block slot modes,
 * duplicate rotation, sensitivity fix, block fly and rotation abuse) is.
 */
public final class SilenceTellyScaffoldMode implements ScaffoldMode
{
	private static final int SEARCH_RADIUS = 5;
	private static final int MAX_RESCUE_ROTATE_TICKS = 8;

	private final ScaffoldWalkHack hack;

	private SlotData blockSlot;
	private TellyBlockData blockData;
	private TellyBlockData lastBlockData;
	private Rotation rotation;
	private Rotation lastRotation;
	private BlockPos lastPlacePosition;
	private int posY;
	private boolean canPlace;
	private int rotateCount;
	private int placeCount;
	private int tellyJumpTicks;
	private int ups;
	private int movementCancelTicks;
	private int lastPlaceAge = -1;
	private boolean waitingForEagleSneak;
	private boolean modulePressedSneak;
	private boolean blockFlyDesyncing;
	private int blockFlyTicks;
	private float lastForward;
	private float lastSideways;
	private float lastPlacedYawDelta = Float.NaN;
	private int lastSearchAge = -1;
	private BlockPos lastSearchOrigin;
	private TellyBlockData lastSearchData;
	private boolean placementRequested;
	private int placementRequestAge = -1;
	private TellyBlockData placementRequestData;
	private SlotData placementRequestSlot;
	private Rotation placementRequestRotation;
	private Vec3 placementRequestEyePos;
	private Rotation packetRotation;
	private boolean placementArmed;
	private int placementArmedAge = -1;
	private TellyBlockData placementArmedData;
	private SlotData placementArmedSlot;
	private Rotation placementArmedRotation;
	private Vec3 placementArmedEyePos;
	private float placementArmedYawDelta = Float.NaN;
	private Rotation serverRotation;
	private int originalSlot = -1;

	public SilenceTellyScaffoldMode(ScaffoldWalkHack hack)
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
		resetState();
		if(WurstClient.MC.player != null)
		{
			originalSlot = WurstClient.MC.player.getInventory().selected;
			posY = Mth.floor(WurstClient.MC.player.getY() - 1.0D);
			lastRotation = new Rotation(WurstClient.MC.player.getYRot(),
				WurstClient.MC.player.getXRot());
			serverRotation = lastRotation;
		}
	}

	@Override
	public void onDisable()
	{
		releaseModuleSneak();
		resetBlockFly();
		var player = WurstClient.MC.player;
		if(player != null && originalSlot != -1
			&& player.getInventory().selected != originalSlot)
		{
			player.getInventory().selected = originalSlot;
			player.connection
				.send(new ServerboundSetCarriedItemPacket(originalSlot));
		}
		originalSlot = -1;
		resetState();
	}

	@Override
	public void onTick()
	{
		if(!ready())
		{
			clearTarget();
			return;
		}

		var player = WurstClient.MC.player;
		lastForward = player.input.forwardImpulse;
		lastSideways = player.input.leftImpulse;

		if(movementCancelTicks > 0)
		{
			player.input.forwardImpulse = 0.0F;
			player.input.leftImpulse = 0.0F;
			movementCancelTicks--;
		}

		updateEagleSneak();

		if(placementRequestAge != player.tickCount)
			clearPlacementRequest();

		preparePlacementTarget();

		if(!hack.isSilenceTellyTellySetting()
			|| blockSlot != null && isSlotStillValid(blockSlot))
			tryJump();
		requestCurrentPlacement();
	}

	@Override
	public void onPostMotion()
	{
		if(!ready())
		{
			clearPlacementRequest();
			clearPlacementArmed();
			return;
		}

		Rotation previousServerRotation = serverRotation;
		var rotationFaker = WurstClient.INSTANCE.getRotationFaker();
		serverRotation = new Rotation(rotationFaker.getServerYaw(),
			rotationFaker.getServerPitch());

		if(!hasCurrentPlacementRequest() || packetRotation == null)
		{
			clearPlacementRequest();
			return;
		}

		TellyBlockData requestedData = placementRequestData;
		SlotData requestedSlot = placementRequestSlot;
		Rotation appliedRotation = serverRotation;
		Vec3 requestedEyePos = placementRequestEyePos;
		clearPlacementRequest();

		Vec3 eyePos = requestedEyePos != null ? requestedEyePos
			: WurstClient.MC.player.getEyePosition();
		BlockHitResult hitResult = SilenceTellyRaycast.getFacedBlock(eyePos,
			appliedRotation);
		if(!SilenceTellyRaycast.didHitBlockFace(eyePos, appliedRotation,
			requestedData.pos(), requestedData.facing(), true))
			return;
		if(!isSlotStillValid(requestedSlot)
			|| !canPlaceAt(requestedData, hitResult, requestedSlot))
			return;

		placementArmed = true;
		placementArmedAge = WurstClient.MC.player.tickCount;
		placementArmedData = requestedData;
		placementArmedSlot = requestedSlot;
		placementArmedRotation = appliedRotation;
		placementArmedEyePos = eyePos;
		placementArmedYawDelta = previousServerRotation == null ? Float.NaN
			: Math.abs(Mth.wrapDegrees(
				appliedRotation.yaw() - previousServerRotation.yaw()));

		// The movement packet is already on the wire at this point, so the
		// server has seen the placement rotation. Execute the armed placement
		// immediately (OpenOpal's next input phase is this same tick).
		if(tryPlaceArmed())
			invalidateSearchCache();
	}

	@Override
	public ScaffoldWalkHack.RotationRequest getPacketRotation()
	{
		if(!hasCurrentPlacementRequest() || packetRotation == null)
			return null;
		return new ScaffoldWalkHack.RotationRequest(packetRotation, false);
	}

	public BlockPos getMarkPosition()
	{
		return lastPlacePosition;
	}

	// ------------------------------------------------------------- placement
	private void preparePlacementTarget()
	{
		blockSlot = selectBlockSlot();
		if(blockSlot == null || !isSlotStillValid(blockSlot))
		{
			clearTarget();
			return;
		}

		updatePosY();
		updateBlockTarget();
		updateCanPlace();
		applySlot(blockSlot);
		boolean forceRotation = updateRescueState();
		rotation = getBlockRotation(forceRotation);
		rotation = applyDuplicateRotation(rotation);
		rotation = applyFixedSensitivity(rotation);

		if(rotation != null)
		{
			lastRotation = rotation;
			hack.requestRotation(rotation, true);
		}
	}

	private void requestCurrentPlacement()
	{
		if(blockData == null || blockSlot == null)
			return;
		if(!canPlace)
			return;

		Rotation appliedRotation = getAppliedRotation();
		Vec3 eyePos = WurstClient.MC.player.getEyePosition();
		if(!SilenceTellyRaycast.didHitBlockFace(eyePos, appliedRotation,
			blockData.pos(), blockData.facing(), true))
			return;

		Rotation target = avoidDuplicateYawDelta(appliedRotation, eyePos,
			blockData);
		if(!SilenceTellyRaycast.didHitBlockFace(eyePos, target,
			blockData.pos(), blockData.facing(), true))
			return;
		BlockHitResult hitResult = SilenceTellyRaycast.getFacedBlock(eyePos,
			target);
		if(!isSlotStillValid(blockSlot)
			|| !canPlaceAt(blockData, hitResult, blockSlot))
			return;

		placementRequested = true;
		placementRequestAge = WurstClient.MC.player.tickCount;
		placementRequestData = blockData;
		placementRequestSlot = blockSlot;
		placementRequestRotation = target;
		placementRequestEyePos = eyePos;
		packetRotation = target;
		hack.setPacketRotation(target, 1);
	}

	/**
	 * OpenOpal's duplicate-yaw-delta avoidance: if this placement would send
	 * exactly the same yaw delta as the previous one, nudge it to a nearby
	 * rotation that still hits the same face.
	 */
	private Rotation avoidDuplicateYawDelta(Rotation target, Vec3 eyePos,
		TellyBlockData data)
	{
		if(target == null || eyePos == null || data == null
			|| serverRotation == null
			|| !hack.silenceDuplicateRotPlace.isChecked()
			|| Float.isNaN(lastPlacedYawDelta))
			return target;

		float yawDelta = Math.abs(Mth.wrapDegrees(
			target.yaw() - serverRotation.yaw()));
		if(yawDelta <= 2.0F
			|| Math.abs(yawDelta - lastPlacedYawDelta) >= 1.0E-4F)
			return target;

		float[] offsets = {0.15F, -0.15F, 0.3F, -0.3F, 0.6F, -0.6F, 1.0F,
			-1.0F};
		for(float offset : offsets)
		{
			Rotation candidate = new Rotation(
				Mth.wrapDegrees(target.yaw() + offset), target.pitch());
			if(hack.silenceFixRotation.isChecked())
				candidate = SilenceTellyRotation.patchSensitivity(candidate,
					serverRotation);

			float candidateDelta = Math.abs(Mth.wrapDegrees(
				candidate.yaw() - serverRotation.yaw()));
			if(Math.abs(candidateDelta - lastPlacedYawDelta) < 1.0E-4F)
				continue;
			if(SilenceTellyRaycast.didHitBlockFace(eyePos, candidate,
				data.pos(), data.facing(), true))
				return candidate;
		}
		return target;
	}

	private boolean tryPlaceArmed()
	{
		if(!placementArmed)
			return false;
		if(placementArmedAge != WurstClient.MC.player.tickCount
			|| lastPlaceAge == WurstClient.MC.player.tickCount)
		{
			clearPlacementArmed();
			return false;
		}

		TellyBlockData armedData = placementArmedData;
		SlotData armedSlot = placementArmedSlot;
		Rotation armedRotation = placementArmedRotation;
		Vec3 armedEyePos = placementArmedEyePos;
		float armedYawDelta = placementArmedYawDelta;
		Vec3 eyePos = armedEyePos != null ? armedEyePos
			: WurstClient.MC.player.getEyePosition();
		BlockHitResult hitResult = SilenceTellyRaycast.getFacedBlock(eyePos,
			armedRotation);
		if(!SilenceTellyRaycast.didHitBlockFace(eyePos, armedRotation,
			armedData.pos(), armedData.facing(), true))
		{
			clearPlacementArmed();
			return false;
		}
		if(!isSlotStillValid(armedSlot)
			|| !canPlaceAt(armedData, hitResult, armedSlot))
		{
			clearPlacementArmed();
			return false;
		}

		blockData = armedData;
		blockSlot = armedSlot;
		applySlot(armedSlot);

		var player = WurstClient.MC.player;
		boolean spoof = hack.silenceSpoofItem.isChecked()
			&& armedSlot.hand() == InteractionHand.MAIN_HAND;
		int previousSlot = player.getInventory().selected;
		if(spoof && previousSlot != armedSlot.slot())
			player.getInventory().selected = armedSlot.slot();

		try
		{
			if(hack.silenceAbuseRotation.isChecked())
				abuseRotation(30.0F, armedRotation.yaw());
			if(hack.silenceBlockFly.isChecked())
				updateBlockFlyBeforePlace();
			if(hack.silenceInteractBeforePlace.isChecked())
				interactItemBeforePlace();

			clearPlacementArmed();
			if(!hack.canPlaceThisTick())
				return false;
			InteractionResult result = WurstClient.MC.gameMode.useItemOn(
				player, armedSlot.hand(), hitResult);
			if(!result.consumesAction())
				return false;

			if(hack.silenceNoSwing.isChecked())
				SwingHand.SERVER.swing(armedSlot.hand());
			else
				player.swing(armedSlot.hand());
		}finally
		{
			if(spoof && player.getInventory().selected != previousSlot)
			{
				player.getInventory().selected = previousSlot;
				player.connection.send(
					new ServerboundSetCarriedItemPacket(previousSlot));
			}
		}

		lastPlaceAge = player.tickCount;
		if(!Float.isNaN(armedYawDelta))
			lastPlacedYawDelta = armedYawDelta;
		placeCount++;
		lastPlacePosition = armedData.placePos();
		hack.notePlaced(lastPlacePosition);
		return true;
	}

	// ----------------------------------------------------------- target search
	private void updateBlockTarget()
	{
		TellyBlockData possible = null;
		var player = WurstClient.MC.player;
		BlockPos playerBlock = BlockPos.containing(player.getX(), player.getY(),
			player.getZ());
		if(SilenceTellyRaycast.isIgnoredBlock(
			WurstClient.MC.level.getBlockState(playerBlock)))
			possible = getBlockData(new BlockPos(Mth.floor(player.getX()),
				posY, Mth.floor(player.getZ())));
		if(possible != null)
			blockData = possible;
		lastBlockData = possible;
		if(!isBlockDataUsable(blockData))
			blockData = possible;
	}

	private boolean updateRescueState()
	{
		boolean reachable = true;
		boolean forceRotation = false;
		SilenceTellyFallingPlayer fallingPlayer =
			new SilenceTellyFallingPlayer(lastForward, lastSideways);
		fallingPlayer.calculate(1);
		Vec3 nextEyePos = fallingPlayer.getEyePos();
		fallingPlayer.calculate(1);

		var player = WurstClient.MC.player;
		TellyBlockData placement = getBlockData(
			new BlockPos(Mth.floor(player.getX()),
				player.blockPosition().getY() - 1,
				Mth.floor(player.getZ())));
		if(placement != null)
		{
			if(hack.silenceSafeMode.isChecked()
				&& hack.silenceTestOnGround.isChecked()
				&& hack.getGroundTicks() == 1
				&& WurstClient.MC.options.keyJump.isDown())
				forceRotation = true;
			double distance = nextEyePos
				.distanceTo(Vec3.atCenterOf(placement.pos()));
			if(distance >= hack.silenceSafeDistance.getValue()
				|| placement.pos().getY() > fallingPlayer.getY())
			{
				canPlace = true;
				reachable = false;
				blockData = lastBlockData = placement;
			}
		}

		if(blockData != null)
		{
			AABB blockBox = new AABB(blockData.pos().getX(),
				blockData.pos().getY() - 1.0D, blockData.pos().getZ(),
				blockData.pos().getX() + 1.0D,
				blockData.pos().getY() + 1.0D,
				blockData.pos().getZ() + 1.0D);
			if(blockData.pos().getY() > fallingPlayer.getY()
				&& !blockBox.contains(player.position()))
			{
				canPlace = true;
				reachable = false;
				posY = player.blockPosition().getY() - 1;
				blockData = lastBlockData = getBlockData(
					new BlockPos(Mth.floor(player.getX()), posY,
						Mth.floor(player.getZ())));
			}
		}

		if(!reachable && rotateCount < MAX_RESCUE_ROTATE_TICKS)
		{
			movementCancelTicks = Math.max(movementCancelTicks, 1);
			rotateCount++;
		}else if(reachable)
			rotateCount = 0;
		return forceRotation;
	}

	private Rotation getBlockRotation(boolean forceRotation)
	{
		if(blockData == null)
			return null;

		Rotation reference = getReferenceRotation();
		Rotation target = SilenceTellyRotation.getClosestToBlockFace(
			blockData.pos(), blockData.facing(), reference);
		if(target == null)
		{
			float plus = WurstClient.MC.player.getYRot() + 100.0F;
			float minus = WurstClient.MC.player.getYRot() - 100.0F;
			target = Math.abs(Mth.wrapDegrees(
				plus - reference.yaw())) < Math.abs(Mth.wrapDegrees(
					minus - reference.yaw()))
						? new Rotation(plus, reference.pitch())
						: new Rotation(minus, reference.pitch());
		}

		if(movementCancelTicks > 0)
			return SilenceTellyRotation.getClosestToBlockFace(
				blockData.pos(), blockData.facing(), reference);

		int airTicks = hack.getAirTicks();
		int groundTicks = hack.getGroundTicks();
		float diff = SilenceTellyRotation.yawDiffDirectly(target.yaw(),
			reference.yaw());

		if(hack.silenceTellyMode
			.getSelected() == SilenceTellyMode.TELLY)
		{
			if(WurstClient.MC.options.keyJump.isDown()
				&& hack.silenceNoUptelly.isChecked())
				return rememberUsableRotation(target);
			if(WurstClient.MC.options.keyJump.isDown()
				&& hack.silenceSlowUpTelly.isChecked())
			{
				ups++;
				if(ups % 2 == 0)
					return rememberUsableRotation(target);
			}

			if(hack.silenceHeypixelUpTelly.isChecked()
				&& (airTicks < hack.silenceRotationTick.getValueI()
					|| hack.silenceSafeMode.isChecked()))
			{
				if(groundTicks > 0)
				{
					if(hack.silenceSafeMode.isChecked()
						&& (!hack.silenceTestOnGround.isChecked()
							|| WurstClient.MC.options.keyJump.isDown()))
					{
						if(groundTicks == 1)
						{
							if(!forceRotation)
								target = new Rotation(reference.yaw()
									+ SilenceTellyRotation.smooth(diff,
										diff / 2.0F),
									75.5F);
							else
								target = SilenceTellyRotation
									.getClosestToBlockFace(blockData.pos(),
										blockData.facing(),
										new Rotation(
											WurstClient.MC.player.getYRot(),
											reference.pitch()));
						}else if(groundTicks == 2)
							return new Rotation(
								WurstClient.MC.player.getYRot(), 75.5F);
					}else
						return new Rotation(
							WurstClient.MC.player.getYRot(), 75.5F);
				}else
				{
					float smooth = airTicks == 1 ? 80.0F : 50.0F;
					smooth -= randomFloat(0.001F, 0.005F);
					target = new Rotation(reference.yaw()
						+ SilenceTellyRotation.smooth(diff, smooth),
						target.pitch());
				}
			}else if(airTicks < hack.silenceRotationTick.getValueI())
			{
				if(!hack.silenceSnap.isChecked()
					|| WurstClient.MC.options.keyJump.isDown())
					return new Rotation(
						WurstClient.MC.player.getYRot(),
						85.0F + ThreadLocalRandom.current().nextFloat());
				if(lastBlockData == null)
					return new Rotation(
						WurstClient.MC.player.getYRot(),
						85.0F + ThreadLocalRandom.current().nextFloat());
			}
		}else if(hack.silenceTellyMode
			.getSelected() == SilenceTellyMode.NORMAL
			&& hack.silenceGodBridge.isChecked())
			target = new Rotation(ScaffoldUtils.moveDirectionDegrees(),
				75.5F);

		return rememberUsableRotation(target);
	}

	private Rotation rememberUsableRotation(Rotation target)
	{
		if(target == null || blockData == null)
			return target;
		Vec3 eyePos = WurstClient.MC.player.getEyePosition();
		if(lastRotation != null && SilenceTellyRaycast.didHitBlockFace(eyePos,
			lastRotation, blockData.pos(), blockData.facing(), true))
			return lastRotation;
		if(!hack.silenceAlwaysUpdateRotation.isChecked()
			&& hack.getAirTicks() >= hack.silenceRotationTick.getValueI()
			&& !SilenceTellyRaycast.didHitBlockFace(eyePos, target,
				blockData.pos(), blockData.facing(), true)
			&& lastRotation != null)
		{
			lastRotation = new Rotation(
				lastRotation.yaw()
					+ ThreadLocalRandom.current().nextFloat(),
				lastRotation.pitch());
			return lastRotation;
		}
		lastRotation = target;
		return target;
	}

	private Rotation applyDuplicateRotation(Rotation target)
	{
		if(target == null || !hack.silenceDuplicateRotPlace.isChecked())
			return target;
		float yaw = target.yaw() - randomFloat(0.0001F, 0.0003F);
		float pitch = target.pitch() - randomFloat(0.001F, 0.003F);
		while(pitch > 90.0F)
			pitch -= randomFloat(0.001F, 0.003F);
		return new Rotation(yaw, Mth.clamp(pitch, -90.0F, 90.0F));
	}

	private Rotation applyFixedSensitivity(Rotation target)
	{
		if(target == null || !hack.silenceFixRotation.isChecked())
			return target;
		return SilenceTellyRotation.patchSensitivity(target,
			getReferenceRotation());
	}

	private void updateCanPlace()
	{
		switch(hack.silenceTellyMode.getSelected())
		{
			case NORMAL:
			canPlace = true;
			break;
			case SNAP:
			canPlace = doesNotContainBlock(1);
			break;
			case TELLY:
			canPlace = hack.getAirTicks() >= hack.silencePlaceTick
				.getValueI();
			if(hack.silenceSafeMode.isChecked()
				&& hack.silenceTestOnGround.isChecked() && !canPlace
				&& WurstClient.MC.options.keyJump.isDown())
				canPlace = hack.getGroundTicks() == 1;
			break;
		}
	}

	private void tryJump()
	{
		var player = WurstClient.MC.player;
		if(!player.onGround() || player.isShiftKeyDown()
			|| !ScaffoldUtils.isMoving())
			return;
		if(WurstClient.MC.options.keyJump.isDown()
			|| WurstClient.MC.options.keyUse.isDown())
			return;
		int groundThreshold = hack.silenceHeypixelUpTelly.isChecked()
			&& hack.silenceSafeMode.isChecked()
			&& !hack.silenceTestOnGround.isChecked() ? 1 : 0;
		if(hack.getGroundTicks() <= groundThreshold)
			return;

		boolean jump = switch(hack.silenceJumpMode.getSelected())
		{
			case NONE -> false;
			case NORMAL -> true;
			case PARKOUR -> shouldParkourJump();
		};
		if(jump)
		{
			hack.requestJump();
			if(hack.silenceEagle.isChecked())
			{
				waitingForEagleSneak = true;
				tellyJumpTicks = 0;
			}
		}
	}

	private boolean shouldParkourJump()
	{
		var player = WurstClient.MC.player;
		double yaw = Math.toRadians(player.getYRot());
		double forwardX = -Math.sin(yaw);
		double forwardZ = Math.cos(yaw);
		BlockPos frontOne = BlockPos.containing(player.getX() + forwardX,
			player.getY() - 0.1D, player.getZ() + forwardZ);
		BlockPos frontTwo = BlockPos.containing(player.getX() + forwardX * 2D,
			player.getY() - 0.1D, player.getZ() + forwardZ * 2D);
		return WurstClient.MC.level.getBlockState(frontOne).isAir()
			|| WurstClient.MC.level.getBlockState(frontTwo).isAir();
	}

	private void updateEagleSneak()
	{
		if(!waitingForEagleSneak)
			return;
		tellyJumpTicks++;
		int start = hack.silenceEagleTick.getValueI();
		int end = start + hack.silenceKeepEagleTick.getValueI();
		if(tellyJumpTicks == start
			&& !WurstClient.MC.options.keyShift.isDown())
		{
			hack.requestSneak(true);
			modulePressedSneak = true;
		}
		if(tellyJumpTicks >= end)
		{
			releaseModuleSneak();
			waitingForEagleSneak = false;
			tellyJumpTicks = 0;
		}
	}

	private TellyBlockData getBlockData(BlockPos pos)
	{
		if(pos == null || WurstClient.MC.player == null
			|| WurstClient.MC.level == null)
			return null;
		if(lastSearchAge == WurstClient.MC.player.tickCount
			&& pos.equals(lastSearchOrigin))
			return lastSearchData;

		TellyBlockData data = getPos(pos);
		if(data == null)
		{
			BlockPos support = getBlockPos();
			if(support == null)
				return cacheSearch(pos, null);
			Direction face = getPlaceSide(support);
			if(face == null)
				return cacheSearch(pos, null);
			data = new TellyBlockData(support, face);
		}
		if(isReplaceableForPlacement(data.placePos()))
			return cacheSearch(pos, data);
		return cacheSearch(pos, null);
	}

	private TellyBlockData cacheSearch(BlockPos origin, TellyBlockData data)
	{
		lastSearchAge = WurstClient.MC.player.tickCount;
		lastSearchOrigin = origin;
		lastSearchData = data;
		return data;
	}

	private TellyBlockData getPos(BlockPos pos)
	{
		if(isPosSolid(pos.offset(-1, 0, 0)))
			return new TellyBlockData(pos.offset(-1, 0, 0),
				Direction.EAST);
		if(isPosSolid(pos.offset(1, 0, 0)))
			return new TellyBlockData(pos.offset(1, 0, 0),
				Direction.WEST);
		if(isPosSolid(pos.offset(0, 0, 1)))
			return new TellyBlockData(pos.offset(0, 0, 1),
				Direction.NORTH);
		if(isPosSolid(pos.offset(0, 0, -1)))
			return new TellyBlockData(pos.offset(0, 0, -1),
				Direction.SOUTH);
		if(isPosSolid(pos.below()))
			return new TellyBlockData(pos.below(), Direction.UP);
		return null;
	}

	private BlockPos getBlockPos()
	{
		var player = WurstClient.MC.player;
		BlockPos playerPos = BlockPos.containing(player.getX(), player.getY(),
			player.getZ());
		List<BlockPos> positions = new ArrayList<>();
		for(int x = SEARCH_RADIUS; x >= -SEARCH_RADIUS + 1; x--)
			for(int y = SEARCH_RADIUS; y >= -SEARCH_RADIUS + 1; y--)
				for(int z = SEARCH_RADIUS; z >= -SEARCH_RADIUS + 1; z--)
				{
					BlockPos pos = playerPos.offset(x, y, z);
					if(isPosSolid(pos) && pos.getY() < playerPos.getY())
						positions.add(pos);
				}
		if(positions.isEmpty())
			return null;
		positions.sort(
			Comparator.comparingDouble(pos -> pos.distSqr(playerPos)));
		return positions.get(0);
	}

	private Direction getPlaceSide(BlockPos support)
	{
		BlockPos playerBlock = WurstClient.MC.player.blockPosition();
		List<TellyBlockData> candidates = new ArrayList<>();
		addPlaceSide(candidates, support, Direction.EAST, playerBlock);
		addPlaceSide(candidates, support, Direction.NORTH, playerBlock);
		addPlaceSide(candidates, support, Direction.SOUTH, playerBlock);
		addPlaceSide(candidates, support, Direction.WEST, playerBlock);
		if(candidates.isEmpty())
			return null;
		candidates.sort(Comparator.comparingDouble(
			data -> data.placePos().distSqr(playerBlock)));
		return candidates.get(0).facing();
	}

	private void addPlaceSide(List<TellyBlockData> candidates,
		BlockPos support, Direction face, BlockPos playerBlock)
	{
		BlockPos placePos = support.relative(face);
		if(!placePos.equals(playerBlock)
			&& isReplaceableForPlacement(placePos))
			candidates.add(new TellyBlockData(support, face));
	}

	private boolean isPosSolid(BlockPos pos)
	{
		if(WurstClient.MC.level == null
			|| !WurstClient.MC.level.isInWorldBounds(pos))
			return false;
		BlockState state = WurstClient.MC.level.getBlockState(pos);
		Block block = state.getBlock();
		return !state.isAir() && !state.canBeReplaced()
			&& state.getFluidState().isEmpty()
			&& !state.getCollisionShape(WurstClient.MC.level, pos).isEmpty()
			&& !ScaffoldUtils.isBlockInteractable(block)
			&& !SilenceTellyRaycast.isIgnoredBlock(state)
			&& !(block instanceof TrapDoorBlock)
			&& !(block instanceof DoorBlock)
			&& !(block instanceof FenceGateBlock) && block != Blocks.COBWEB
			&& block != Blocks.FIRE;
	}

	private boolean isReplaceableForPlacement(BlockPos pos)
	{
		return WurstClient.MC.level != null
			&& WurstClient.MC.level.isInWorldBounds(pos)
			&& (WurstClient.MC.level.getBlockState(pos).canBeReplaced()
				|| SilenceTellyRaycast.isIgnoredBlock(
					WurstClient.MC.level.getBlockState(pos)));
	}

	private boolean canPlaceAt(TellyBlockData data, BlockHitResult hitResult,
		SlotData slot)
	{
		if(data == null || hitResult == null || slot == null
			|| !isPosSolid(data.pos())
			|| !isReplaceableForPlacement(data.placePos()))
			return false;
		ItemStack stack = getStack(slot);
		if(!(stack.getItem() instanceof BlockItem blockItem))
			return false;
		UseOnContext useOnContext = new UseOnContext(WurstClient.MC.player,
			slot.hand(), hitResult);
		BlockPlaceContext placeContext = blockItem
			.updatePlacementContext(new BlockPlaceContext(useOnContext));
		if(placeContext == null)
			return false;
		BlockState placementState = blockItem.getBlock()
			.getStateForPlacement(placeContext);
		if(placementState == null
			|| !placementState.canSurvive(WurstClient.MC.level,
				data.placePos()))
			return false;
		VoxelShape shape = placementState
			.getCollisionShape(WurstClient.MC.level, data.placePos());
		if(!shape.isEmpty())
		{
			AABB blockBox = shape.bounds().move(data.placePos().getX(),
				data.placePos().getY(), data.placePos().getZ());
			if(WurstClient.MC.player.getBoundingBox().intersects(blockBox)
				&& !isLandingPlacementCollisionAllowed(blockBox))
				return false;
		}
		return true;
	}

	private boolean isLandingPlacementCollisionAllowed(AABB blockBox)
	{
		return WurstClient.MC.player.getDeltaMovement().y <= 0.0D
			&& WurstClient.MC.player.getBoundingBox().minY >= blockBox.maxY
				- 1.0E-3D;
	}

	private SlotData selectBlockSlot()
	{
		var player = WurstClient.MC.player;
		if(isValidBlockStack(player.getOffhandItem()))
			return new SlotData(-1, InteractionHand.OFF_HAND);
		int selected = player.getInventory().selected;
		if(hack.silenceBlockSlotMode
			.getSelected() != SilenceBlockSlotMode.MOST_BLOCKS
			&& isValidBlockStack(player.getMainHandItem()))
			return new SlotData(selected, InteractionHand.MAIN_HAND);

		int bestSlot = -1;
		int bestCount = -1;
		if(hack.silenceBlockSlotMode
			.getSelected() == SilenceBlockSlotMode.MOST_BLOCKS)
		{
			for(int slot = 0; slot < 9; slot++)
			{
				ItemStack stack = player.getInventory().getItem(slot);
				if(isValidBlockStack(stack) && stack.getCount() > bestCount)
				{
					bestSlot = slot;
					bestCount = stack.getCount();
				}
			}
		}else
			for(int slot = 0; slot < 9; slot++)
				if(isValidBlockStack(player.getInventory().getItem(slot)))
					bestSlot = slot;
		return bestSlot == -1 ? null
			: new SlotData(bestSlot, InteractionHand.MAIN_HAND);
	}

	private void applySlot(SlotData slot)
	{
		if(slot.hand() != InteractionHand.MAIN_HAND)
			return;
		var player = WurstClient.MC.player;
		boolean changed = player.getInventory().selected != slot.slot();
		if(changed && !hack.silenceSpoofItem.isChecked())
			player.getInventory().selected = slot.slot();
		if(changed)
			player.connection
				.send(new ServerboundSetCarriedItemPacket(slot.slot()));
	}

	private boolean isSlotStillValid(SlotData slot)
	{
		return slot != null && isValidBlockStack(getStack(slot));
	}

	private ItemStack getStack(SlotData slot)
	{
		if(slot.hand() == InteractionHand.OFF_HAND)
			return WurstClient.MC.player.getOffhandItem();
		return WurstClient.MC.player.getInventory().getItem(slot.slot());
	}

	private boolean isValidBlockStack(ItemStack stack)
	{
		return stack != null && !stack.isEmpty()
			&& stack.getItem() instanceof BlockItem blockItem
			&& ScaffoldUtils.isGoodBlock(blockItem.getBlock());
	}

	private boolean isBlockDataUsable(TellyBlockData data)
	{
		return data != null && isPosSolid(data.pos())
			&& isReplaceableForPlacement(data.placePos());
	}

	private boolean doesNotContainBlock(int down)
	{
		var player = WurstClient.MC.player;
		BlockPos pos = BlockPos.containing(player.getX(),
			player.getY() - down, player.getZ());
		BlockState state = WurstClient.MC.level.getBlockState(pos);
		return state.isAir() || state.canBeReplaced()
			|| state.getCollisionShape(WurstClient.MC.level, pos).isEmpty();
	}

	private void updatePosY()
	{
		var player = WurstClient.MC.player;
		if(player.onGround())
			posY = Mth.floor(player.getY() - 1.0D);
		if(WurstClient.MC.options.keyJump.isDown())
			posY = player.blockPosition().getY() - 1;
	}

	private Rotation getReferenceRotation()
	{
		if(serverRotation != null)
			return serverRotation;
		return new Rotation(WurstClient.MC.player.getYRot(),
			WurstClient.MC.player.getXRot());
	}

	private Rotation getAppliedRotation()
	{
		if(rotation != null)
			return rotation;
		return new Rotation(WurstClient.MC.player.getYRot(),
			WurstClient.MC.player.getXRot());
	}

	private boolean hasCurrentPlacementRequest()
	{
		if(!placementRequested || placementRequestData == null
			|| placementRequestSlot == null
			|| placementRequestRotation == null)
			return false;
		int delta = WurstClient.MC.player.tickCount - placementRequestAge;
		return delta >= 0 && delta <= 1
			&& isBlockDataUsable(placementRequestData)
			&& isSlotStillValid(placementRequestSlot);
	}

	private void clearPlacementRequest()
	{
		placementRequested = false;
		placementRequestAge = -1;
		placementRequestData = null;
		placementRequestSlot = null;
		placementRequestRotation = null;
		placementRequestEyePos = null;
		packetRotation = null;
	}

	private void clearPlacementArmed()
	{
		placementArmed = false;
		placementArmedAge = -1;
		placementArmedData = null;
		placementArmedSlot = null;
		placementArmedRotation = null;
		placementArmedEyePos = null;
		placementArmedYawDelta = Float.NaN;
	}

	private void invalidateSearchCache()
	{
		lastSearchAge = -1;
		lastSearchOrigin = null;
		lastSearchData = null;
	}

	private void interactItemBeforePlace()
	{
		if(blockSlot.hand() != InteractionHand.MAIN_HAND)
			return;
		WurstClient.MC.gameMode.useItem(WurstClient.MC.player,
			InteractionHand.MAIN_HAND);
	}

	private void abuseRotation(float step, float targetYaw)
	{
		if(lastRotation == null)
			return;
		float change = SilenceTellyRotation.yawDiffDirectly(targetYaw,
			lastRotation.yaw());
		int times = (int)(Math.abs(change) / step);
		for(int i = 0; i < times; i++)
			WurstClient.MC.gameMode.useItem(WurstClient.MC.player,
				InteractionHand.MAIN_HAND);
	}

	private void updateBlockFlyBeforePlace()
	{
		if(blockFlyDesyncing || WurstClient.MC.player == null)
			return;
		WurstClient.MC.player.connection
			.send(new ServerboundPlayerActionPacket(
				ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND,
				BlockPos.ZERO, Direction.DOWN));
		blockFlyDesyncing = true;
		blockFlyTicks = 0;
	}

	private void resetBlockFly()
	{
		blockFlyDesyncing = false;
		blockFlyTicks = 0;
	}

	private boolean ready()
	{
		return WurstClient.MC.player != null && WurstClient.MC.level != null
			&& WurstClient.MC.gameMode != null && hack.isEnabled();
	}

	private void clearTarget()
	{
		clearPlacementRequest();
		clearPlacementArmed();
		blockData = null;
		rotation = null;
		canPlace = false;
		rotateCount = 0;
		movementCancelTicks = 0;
	}

	private void resetState()
	{
		blockSlot = null;
		blockData = null;
		lastBlockData = null;
		rotation = null;
		lastRotation = null;
		lastPlacePosition = null;
		canPlace = false;
		rotateCount = 0;
		placeCount = 0;
		tellyJumpTicks = 0;
		ups = 0;
		movementCancelTicks = 0;
		lastPlaceAge = -1;
		waitingForEagleSneak = false;
		modulePressedSneak = false;
		lastForward = 0.0F;
		lastSideways = 0.0F;
		lastPlacedYawDelta = Float.NaN;
		lastSearchAge = -1;
		lastSearchOrigin = null;
		lastSearchData = null;
		clearPlacementRequest();
		clearPlacementArmed();
		serverRotation = null;
	}

	private void releaseModuleSneak()
	{
		if(modulePressedSneak)
		{
			hack.requestSneak(false);
			modulePressedSneak = false;
		}
	}

	private float randomFloat(float min, float max)
	{
		return min + ThreadLocalRandom.current().nextFloat() * (max - min);
	}

	private record TellyBlockData(BlockPos pos, Direction facing)
	{
		private BlockPos placePos()
		{
			return pos.relative(facing);
		}
	}

	private record SlotData(int slot, InteractionHand hand)
	{}
}
