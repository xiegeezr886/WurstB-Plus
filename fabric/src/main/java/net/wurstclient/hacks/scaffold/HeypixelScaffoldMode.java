/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
package net.wurstclient.hacks.scaffold;

import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.WaterlilyBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.WurstClient;
import net.wurstclient.events.MoveInputListener;
import net.wurstclient.hacks.ScaffoldWalkHack;
import net.wurstclient.hacks.ScaffoldWalkHack.BlockData;
import net.wurstclient.mixin.LivingEntityAccessor;
import net.wurstclient.util.Rotation;

/**
 * Ported from OpenOpal's {@code HeypixelScaffold}. All of the target scanning,
 * rotation search, duplicate-rotation jitter and placement flow is preserved;
 * the only omitted subsystem is OpenOpal's Stuck/SkipTick rescue (Wurst has no
 * equivalent module).
 */
public class HeypixelScaffoldMode implements ScaffoldMode
{
	private static final Direction[] DIRECTIONS = Direction.values();
	private static final int MAX_RESCUE_ATTEMPTS = 8;

	private static final Set<Block> BLACKLISTED_BLOCKS = Set.of(Blocks.AIR,
		Blocks.WATER, Blocks.LAVA, Blocks.ENCHANTING_TABLE, Blocks.GLASS_PANE,
		Blocks.IRON_BARS, Blocks.SNOW, Blocks.COAL_ORE, Blocks.DIAMOND_ORE,
		Blocks.EMERALD_ORE, Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.TORCH,
		Blocks.ANVIL, Blocks.NOTE_BLOCK, Blocks.JUKEBOX, Blocks.TNT,
		Blocks.GOLD_ORE, Blocks.IRON_ORE, Blocks.LAPIS_ORE,
		Blocks.STONE_PRESSURE_PLATE, Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE,
		Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE, Blocks.STONE_BUTTON,
		Blocks.LEVER, Blocks.TALL_GRASS, Blocks.TRIPWIRE,
		Blocks.TRIPWIRE_HOOK, Blocks.RAIL, Blocks.CORNFLOWER,
		Blocks.RED_MUSHROOM, Blocks.BROWN_MUSHROOM, Blocks.VINE,
		Blocks.SUNFLOWER, Blocks.LADDER, Blocks.FURNACE, Blocks.SAND,
		Blocks.CACTUS, Blocks.DISPENSER, Blocks.DROPPER,
		Blocks.CRAFTING_TABLE, Blocks.COBWEB, Blocks.PUMPKIN,
		Blocks.COBBLESTONE_WALL, Blocks.OAK_FENCE, Blocks.REDSTONE_TORCH,
		Blocks.FLOWER_POT);

	private final ScaffoldWalkHack hack;
	private final boolean hypixel;

	protected int airTick;
	protected int yLevel;
	private BlockPos blockPos;
	private Direction enumFacing;
	private int oldSlot = -1;
	protected float baseYaw;
	protected float forwardYaw;
	private Rotation lastTargetRotation;
	private Rotation lastValidPlaceRotation;
	private BlockHitResult lastHitResult;
	private int rotateCount;
	private boolean checkedBlock;
	private int stuckTicks;
	private int searchYTop;
	private int duplicateRotNonce;
	private int upTellyRotateTick;
	private int upTellyJumpTick;

	private boolean upTellyBypassActive;
	private boolean upTellyBypassRecovering;
	private boolean upTellyBypassPendingGroundJump;

	public HeypixelScaffoldMode(ScaffoldWalkHack hack)
	{
		this(hack, false);
	}

	protected HeypixelScaffoldMode(ScaffoldWalkHack hack, boolean hypixel)
	{
		this.hack = hack;
		this.hypixel = hypixel;
	}

	@Override
	public boolean handlesTick()
	{
		return true;
	}

	@Override
	public void onEnable()
	{
		if(WurstClient.MC.player != null)
		{
			oldSlot = WurstClient.MC.player.getInventory().selected;
			baseYaw = WurstClient.MC.player.getYRot();
			forwardYaw = resolveBaseYaw();
			stuckTicks = 0;
			lastValidPlaceRotation = null;
			duplicateRotNonce = 0;
			upTellyRotateTick = 0;
			upTellyJumpTick = 0;
			rotateCount = 0;
			checkedBlock = false;
			lastHitResult = null;
				airTick = 0;
			blockPos = null;
			enumFacing = null;
		}
		upTellyBypassActive = false;
		upTellyBypassRecovering = false;
		upTellyBypassPendingGroundJump = false;
	}

	@Override
	public void onDisable()
	{
		hack.resetSlot();
		var player = WurstClient.MC.player;
		if(player != null && oldSlot != -1
			&& player.getInventory().selected != oldSlot)
		{
			player.getInventory().selected = oldSlot;
			player.connection.send(
				new ServerboundSetCarriedItemPacket(oldSlot));
		}
		oldSlot = -1;
		rotateCount = 0;
		checkedBlock = false;
		lastHitResult = null;
		upTellyRotateTick = 0;
		upTellyJumpTick = 0;
	}

	@Override
	public void onMoveInput(MoveInputListener.MoveInputEvent event)
	{
		var player = WurstClient.MC.player;
		if(player == null)
			return;

		if(upTellyBypassRecovering)
		{
			event.setForward(0.0F);
			event.setSideways(0.0F);
			if(upTellyBypassPendingGroundJump && player.onGround())
			{
				((LivingEntityAccessor)player).wurst_setNoJumpDelay(0);
				event.setJump(true);
				upTellyBypassPendingGroundJump = false;
			}
			return;
		}

		// OpenOpal's Heypixel onMoveInput jump logic: auto jump while telly
		// bridging, alternating the jump input when the key is held.
		if(!isTellyEnabled() || !ScaffoldUtils.isMoving()
			|| !player.onGround())
		{
			upTellyJumpTick = 0;
			return;
		}

		if(WurstClient.MC.options.keyJump.isDown())
		{
			upTellyJumpTick++;
			event.setJump(upTellyJumpTick % 2 == 0);
			return;
		}

		upTellyJumpTick = 0;
		event.setJump(true);
	}

	@Override
	public void onTick()
	{
		var player = WurstClient.MC.player;
		if(player == null || WurstClient.MC.level == null)
			return;

		updateUpTellyBypassState();
		if(upTellyBypassRecovering || upTellyBypassActive)
			return;

		selectBlockSlot();
		if(player.onGround())
			yLevel = Mth.floor(player.getY()) - 1;

		getBlockInfo();

		if(isSafeWalkEnabled() && !isTellyEnabled())
			hack.requestSneak(player.onGround() && isOnBlockEdge(0.3F));

		if(isTellyEnabled())
			tickTelly();
		else
			tickBridge();
	}

	private void tickTelly()
	{
		var player = WurstClient.MC.player;
		if(player.onGround())
		{
			airTick = 0;
			blockPos = null;
			enumFacing = null;

			float clientYaw = resolveBaseYaw();
			float clientPitch = player.getXRot();
			forwardYaw = clientYaw;
			Rotation target = new Rotation(clientYaw, clientPitch);
			lastTargetRotation = target;
			rotateTo(target, getRotateBackSpeed(), false);
			return;
		}

		if(shouldTrackMovementYawDuringTelly())
			forwardYaw = resolveBaseYaw();

		int baseTellyTick = getTellyTick();
		int dynamicTellyTick = baseTellyTick;
		if(shouldAllowUpTelly())
			dynamicTellyTick = Math.max(1, baseTellyTick
				- (isDiagonalYaw(forwardYaw) ? 3 : 2));

		if(airTick < dynamicTellyTick)
		{
			Rotation forward = new Rotation(forwardYaw,
				ScaffoldUtils.getRotation().pitch());
			if(shouldAllowUpTelly())
			{
				upTellyRotateTick++;
				if(upTellyRotateTick % 2 == 0)
				{
					lastTargetRotation = forward;
					rotateTo(forward, getRotateBackSpeed(), false);
				}
			}else
			{
				upTellyRotateTick = 0;
				lastTargetRotation = forward;
				rotateTo(forward, getRotateBackSpeed(), false);
			}

			if(shouldAllowUpTelly() && isDiagonalYaw(forwardYaw)
				&& airTick >= 1 && blockPos != null && enumFacing != null)
			{
				Rotation early = getRotation(blockPos, enumFacing);
				if(early != null)
				{
					lastTargetRotation = early;
					rotateTo(early, getRotateSpeed(), true);
					place();
				}
			}
		}else
		{
			Rotation rotation = getRotation(blockPos, enumFacing);
			if(rotation != null)
			{
				lastTargetRotation = rotation;
				rotateTo(rotation, getRotateSpeed(), true);
				place();
			}
		}
		airTick++;
	}

	private void tickBridge()
	{
		var player = WurstClient.MC.player;
		if(blockPos == null)
		{
			Rotation target = new Rotation(
				Mth.wrapDegrees(baseYaw - 180.0F), 89.64F);
			lastTargetRotation = target;
			hack.setPacketRotation(target, 2);
			rotateTo(target, getRotateSpeed(), false);
		}
		if(onAir() || !isSnapEnabled())
		{
			Rotation rotation = getRotation(blockPos, enumFacing);
			if(rotation != null)
			{
				lastTargetRotation = rotation;
				hack.setPacketRotation(rotation, 2);
				rotateTo(rotation, getRotateSpeed(), false);
			}
		}
		place();
	}

	protected void rotateTo(Rotation target, float speed, boolean instant)
	{
		if(target == null)
			return;
		hack.applyModeRotation(target,
			instant ? ScaffoldRotationModel.INSTANT
				: ScaffoldRotationModel.HEYPIXEL,
			speed);
	}

	private void selectBlockSlot()
	{
		var player = WurstClient.MC.player;
		int slot = -1;
		for(int i = 0; i < 9; i++)
			if(isValidStack(player.getInventory().getItem(i)))
			{
				slot = i;
				break;
			}
		if(slot == -1)
			return;
		if(player.getInventory().selected != slot)
		{
			player.getInventory().selected = slot;
			player.connection.send(new ServerboundSetCarriedItemPacket(slot));
		}
	}

	private boolean place()
	{
		return place(true);
	}

	private boolean place(boolean checkRotation)
	{
		if(!onAir() || blockPos == null || enumFacing == null)
			return false;

		if(!checkRotation)
			return performPlace(new BlockHitResult(getVec3(blockPos, enumFacing),
				enumFacing, blockPos, false));

		Rotation current = lastValidPlaceRotation != null
			? lastValidPlaceRotation
			: hack.getLastRotation() != null ? hack.getLastRotation()
				: ScaffoldUtils.getRotation();
		BlockHitResult hitResult = ScaffoldUtils.raycastBlock(current,
			hack.getRange());
		if(hitResult != null && hitResult.getBlockPos().equals(blockPos)
			&& hitResult.getDirection() == enumFacing)
		{
			lastValidPlaceRotation = current;
			return performPlace(hitResult);
		}

		if((isTellyEnabled() || hack.overrideRaycast.isChecked())
			&& lastHitResult != null
			&& lastHitResult.getBlockPos().equals(blockPos)
			&& lastHitResult.getDirection() == enumFacing)
		{
			lastValidPlaceRotation = lastTargetRotation;
			return performPlace(lastHitResult);
		}
		return false;
	}

	private boolean performPlace(BlockHitResult hitResult)
	{
		var player = WurstClient.MC.player;
		ItemStack stack = player.getMainHandItem();
		if(!(stack.getItem() instanceof BlockItem))
			return false;
		if(!hack.canPlaceThisTick())
			return false;

		if(useInteractBeforePlace())
			WurstClient.MC.gameMode.useItem(player, InteractionHand.MAIN_HAND);

		hack.swingHand.swing(InteractionHand.MAIN_HAND);
		boolean success = WurstClient.MC.gameMode
			.useItemOn(player, InteractionHand.MAIN_HAND, hitResult)
			.consumesAction();
		if(!success)
			return false;

		stuckTicks = 0;
		hack.notePlaced(
			hitResult.getBlockPos().relative(hitResult.getDirection()));
		return true;
	}

	private boolean overBlock(BlockPos pos, Direction facing)
	{
		BlockHitResult hitResult = ScaffoldUtils
			.raycastBlock(ScaffoldUtils.getRotation(), hack.getRange());
		return hitResult != null && hitResult.getBlockPos().equals(pos)
			&& hitResult.getDirection() == facing;
	}

	private int getYLevel()
	{
		var player = WurstClient.MC.player;
		if(!WurstClient.MC.options.keyJump.isDown()
			&& ScaffoldUtils.isMoving() && player.fallDistance <= 0.25
			&& isTellyEnabled() && player.getDeltaMovement().y <= 0.02)
			return yLevel;
		return Mth.floor(player.getY()) - 1;
	}

	private boolean shouldAllowUpTelly()
	{
		var player = WurstClient.MC.player;
		if(player.horizontalCollision)
			return false;
		return isTellyEnabled() && ScaffoldUtils.isMoving()
			&& !player.onGround()
			&& (WurstClient.MC.options.keyJump.isDown()
				|| player.getDeltaMovement().y > -0.08);
	}

	private boolean isDiagonalYaw(float yaw)
	{
		double radians = Math.toRadians(Mth.wrapDegrees(yaw));
		return Math.abs(Math.sin(radians * 2.0D)) > 0.58;
	}

	private void getBlockInfo()
	{
		var player = WurstClient.MC.player;
		Vec3 baseVec = player.getEyePosition();
		int baseY = getYLevel();
		if(shouldAllowUpTelly())
			baseY++;
		searchYTop = baseY;

		BlockPos base = BlockPos.containing(baseVec.x, baseY, baseVec.z);
		int baseX = base.getX();
		int baseZ = base.getZ();

		if(isSolidAndNonInteractive(
			WurstClient.MC.level.getBlockState(base), base))
		{
			checkedBlock = false;
			return;
		}

		if(checkBlock(baseVec, base))
			return;

		for(int d = 1; d <= 6; d++)
		{
			if(checkBlock(baseVec, new BlockPos(baseX, baseY - d, baseZ)))
				return;
			for(int x = 0; x <= d; x++)
				for(int z = 0; z <= d - x; z++)
				{
					int y = d - x - z;
					for(int rev1 = 0; rev1 <= 1; rev1++)
						for(int rev2 = 0; rev2 <= 1; rev2++)
							if(checkBlock(baseVec,
								new BlockPos(baseX + (rev1 == 0 ? x : -x),
									baseY - y,
									baseZ + (rev2 == 0 ? z : -z))))
								return;
				}
		}
		checkedBlock = false;
	}

	private boolean isSolidAndNonInteractive(BlockState state, BlockPos pos)
	{
		return !state.getCollisionShape(WurstClient.MC.level, pos).isEmpty()
			&& state.getMenuProvider(WurstClient.MC.level, pos) == null;
	}

	private boolean checkBlock(Vec3 baseVec, BlockPos pos)
	{
		BlockState state = WurstClient.MC.level.getBlockState(pos);
		if(!(state.getBlock() instanceof AirBlock)
			&& !(state.getBlock() instanceof WaterlilyBlock))
		{
			checkedBlock = false;
			return false;
		}
		if(pos.getY() > searchYTop)
		{
			checkedBlock = false;
			return false;
		}

		Vec3 center = new Vec3(pos.getX() + 0.5D, pos.getY(),
			pos.getZ() + 0.5D);
		for(Direction direction : DIRECTIONS)
		{
			Vec3 hit = center.add(direction.getStepX() * 0.5D,
				direction.getStepY() * 0.5D, direction.getStepZ() * 0.5D);
			BlockPos support = pos.relative(direction);
			if(!isSolidAndNonInteractive(
				WurstClient.MC.level.getBlockState(support), support))
				continue;

			Vec3 relative = hit.subtract(baseVec);
			if(relative.lengthSqr() <= 4.5D * 4.5D
				&& relative.dot(new Vec3(direction.getStepX(),
					direction.getStepY(), direction.getStepZ())) >= 0)
			{
				if(direction.getOpposite() == Direction.UP
					&& ScaffoldUtils.isMoving()
					&& !WurstClient.MC.options.keyJump.isDown())
					continue;
				blockPos = support;
				enumFacing = direction.getOpposite();
				checkedBlock = true;
				return true;
			}
		}
		checkedBlock = false;
		return false;
	}

	private boolean isValidStack(ItemStack stack)
	{
		if(stack == null || !(stack.getItem() instanceof BlockItem blockItem)
			|| stack.getCount() <= 0)
			return false;
		Block block = blockItem.getBlock();
		if(block instanceof FlowerBlock || block instanceof MushroomBlock
			|| block instanceof CropBlock || block instanceof SlabBlock)
			return false;
		return !BLACKLISTED_BLOCKS.contains(block);
	}

	private Vec3 getVec3(BlockPos pos, Direction face)
	{
		double x = pos.getX() + 0.5D;
		double y = pos.getY() + 0.5D;
		double z = pos.getZ() + 0.5D;
		if(face != Direction.UP && face != Direction.DOWN)
			y += 0.08D;
		else
		{
			x += (Math.random() - 0.5D) * 0.4D;
			z += (Math.random() - 0.5D) * 0.4D;
		}
		if(face == Direction.WEST || face == Direction.EAST)
			z += (Math.random() - 0.5D) * 0.4D;
		if(face == Direction.SOUTH || face == Direction.NORTH)
			x += (Math.random() - 0.5D) * 0.4D;
		return new Vec3(x, y, z);
	}

	private Rotation getRotation(BlockPos pos, Direction face)
	{
		if(pos == null || face == null)
			return null;
		lastHitResult = null;
		Rotation direct = ScaffoldUtils.rotationFromBlock(pos, face);
		Rotation reverseYaw = new Rotation(
			Mth.wrapDegrees(WurstClient.MC.player.getYRot() - 180.0F),
			direct.pitch());

		if(onAir())
		{
			BlockHitResult reverse = raycastBlockWithRotation(reverseYaw,
				pos, face);
			if(reverse != null)
			{
				lastHitResult = reverse;
				return withDuplicateRotJitter(reverseYaw, pos, face);
			}
		}

		BlockHitResult directHit = raycastBlockWithRotation(direct, pos,
			face);
		if(directHit != null)
		{
			lastHitResult = directHit;
			return withDuplicateRotJitter(direct, pos, face);
		}

		float[] yawOffsets = {-2F, 2F, -4F, 4F, -7F, 7F, -12F, 12F};
		float[] pitchOffsets = {0F, -1F, 1F, -2F, 2F, -4F, 4F};
		for(float yawOffset : yawOffsets)
			for(float pitchOffset : pitchOffsets)
			{
				Rotation trial = new Rotation(
					Mth.wrapDegrees(direct.yaw() + yawOffset),
					Mth.clamp(direct.pitch() + pitchOffset, -90.0F, 90.0F));
				BlockHitResult result = raycastBlockWithRotation(trial, pos,
					face);
				if(result != null)
				{
					lastHitResult = result;
					return withDuplicateRotJitter(trial, pos, face);
				}
			}

		Rotation raytraced = hack.getRotationFromRaycastedBlock(pos, face,
			direct, WurstClient.MC.player.getEyePosition());
		if(raytraced != null)
			return withDuplicateRotJitter(raytraced, pos, face);

		return withDuplicateRotJitter(direct, pos, face);
	}

	private Rotation withDuplicateRotJitter(Rotation base, BlockPos pos,
		Direction face)
	{
		if(!hack.duplicateRotPlace.isChecked() || base == null)
			return base;

		duplicateRotNonce++;
		int index = duplicateRotNonce % 6;
		float yawJitter = switch(index)
		{
			case 0 -> 0.032F;
			case 1 -> -0.037F;
			case 2 -> 0.021F;
			case 3 -> -0.026F;
			case 4 -> 0.014F;
			default -> -0.018F;
		};
		float pitchJitter = index % 2 == 0 ? 0.012F : -0.009F;
		Rotation jittered = new Rotation(
			Mth.wrapDegrees(base.yaw() + yawJitter),
			Mth.clamp(base.pitch() + pitchJitter, -89.9F, 89.9F));
		return raycastBlockWithRotation(jittered, pos,
			face) != null ? jittered : base;
	}

	private BlockHitResult raycastBlockWithRotation(Rotation rotation,
		BlockPos pos, Direction face)
	{
		BlockHitResult hitResult = ScaffoldUtils.raycastBlock(rotation,
			hack.getRange());
		if(hitResult == null)
			return null;
		if(hitResult.getBlockPos().equals(pos)
			&& hitResult.getDirection() == face)
			return hitResult;
		return hitResult.getBlockPos().equals(pos) ? hitResult : null;
	}

	private boolean onAir()
	{
		Vec3 baseVec = WurstClient.MC.player.getEyePosition();
		BlockPos base = BlockPos.containing(baseVec.x, getYLevel(),
			baseVec.z);
		Block block = WurstClient.MC.level.getBlockState(base).getBlock();
		return block instanceof AirBlock || block instanceof WaterlilyBlock;
	}

	private boolean isOnBlockEdge(float sensitivity)
	{
		var player = WurstClient.MC.player;
		AABB box = player.getBoundingBox().move(0.0D, -0.5D, 0.0D)
			.inflate(-sensitivity, 0.0D, -sensitivity);
		return !WurstClient.MC.level.getBlockCollisions(player, box)
			.iterator().hasNext();
	}

	protected float resolveBaseYaw()
	{
		return WurstClient.MC.player == null ? 0
			: WurstClient.MC.player.getYRot();
	}

	protected boolean shouldTrackMovementYawDuringTelly()
	{
		return false;
	}

	protected boolean isTellyEnabled()
	{
		return hack.uitemsTelly.isChecked();
	}

	protected boolean isSafeWalkEnabled()
	{
		return hack.safeWalk.isChecked();
	}

	protected boolean isSnapEnabled()
	{
		return hack.snap.isChecked();
	}

	protected boolean useInteractBeforePlace()
	{
		return hack.interactBeforePlace.isChecked();
	}

	protected int getTellyTick()
	{
		return hack.tellyTick.getValueI();
	}

	protected float getRotateSpeed()
	{
		return hack.rotateSpeed.getValueF();
	}

	protected float getRotateBackSpeed()
	{
		return hack.rotateBackSpeed.getValueF();
	}

	protected ScaffoldWalkHack hack()
	{
		return hack;
	}

	protected void rotateTo(Rotation target)
	{
		rotateTo(target, getRotateSpeed(), false);
	}

	protected boolean isHypixel()
	{
		return hypixel;
	}

	public boolean isUpTellyBypassActive()
	{
		return upTellyBypassActive || upTellyBypassRecovering;
	}

	private void updateUpTellyBypassState()
	{
		var player = WurstClient.MC.player;
		boolean shouldBypass = hack.upTellyBypass.isChecked()
			&& hack.getMode() == ScaffoldWalkHack.Mode.HEYPIXEL
			&& hack.rotationModel
				.getSelected() == ScaffoldRotationModel.HEYPIXEL
			&& isTellyEnabled() && WurstClient.MC.options.keyJump.isDown()
			&& (WurstClient.MC.options.keyUp.isDown()
				|| WurstClient.MC.options.keyDown.isDown()
				|| WurstClient.MC.options.keyLeft.isDown()
				|| WurstClient.MC.options.keyRight.isDown());

		if(shouldBypass)
		{
			upTellyBypassActive = true;
			upTellyBypassRecovering = false;
			upTellyBypassPendingGroundJump = false;
		}else if(upTellyBypassActive)
		{
			upTellyBypassActive = false;
			upTellyBypassRecovering = true;
			upTellyBypassPendingGroundJump = true;
		}

		if(upTellyBypassRecovering && !upTellyBypassPendingGroundJump
			&& player != null && !player.onGround())
			upTellyBypassRecovering = false;
	}

	@Override
	public String toString()
	{
		return getModeName();
	}

	protected String getModeName()
	{
		return hypixel ? "Hypixel" : "Heypixel";
	}

	// Kept for parity with the reference; Wurst has no stuck/skip-tick module.
	protected boolean handleSkipTickRecovery()
	{
		return false;
	}

	protected void runSkidRescueRecursive(boolean tellyMode, float rotateSpeed,
		int depth)
	{
		if(depth >= MAX_RESCUE_ATTEMPTS || blockPos == null
			|| enumFacing == null)
			return;
		Rotation rotation = getRotation(blockPos, enumFacing);
		if(rotation == null)
			return;
		rotateTo(rotation, rotateSpeed, true);
		place();
	}
}
