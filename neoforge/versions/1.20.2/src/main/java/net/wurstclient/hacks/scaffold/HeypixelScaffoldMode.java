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
	private int skipRecoveryAttempts, skipRecoveryActiveTicks, skipRecoverySkipTicks, skipRecoveryNoPlaceTicks;
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
			baseYaw = net.wurstclient.opal.rotation.RotationHelper.getClientHandler().getYawOr(WurstClient.MC.player.getYRot());
			forwardYaw = resolveBaseYaw();
			stuckTicks = 0;
            skipRecoveryAttempts = skipRecoveryActiveTicks = skipRecoverySkipTicks = skipRecoveryNoPlaceTicks = 0;
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
        if(player != null) {
            var client = net.wurstclient.opal.rotation.RotationHelper.getClientHandler();
            net.wurstclient.opal.rotation.RotationHelper.getHandler().rotate(new Rotation(client.getYawOr(player.getYRot()),
                client.getPitchOr(player.getXRot())), new net.wurstclient.opal.rotation.HeypixelRotationModel(getRotateBackSpeed()));
            net.wurstclient.opal.rotation.RotationHelper.getHandler().reverse();
        } else net.wurstclient.opal.rotation.RotationHelper.getHandler().reset();
        skipRecoveryActiveTicks = skipRecoverySkipTicks = skipRecoveryNoPlaceTicks = 0;
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

		if(upTellyBypassRecovering || upTellyBypassActive)
			return;

        if(hack.recovery.isSkipTickRecoveryActive()) {
            stuckTicks++;
            boolean hasBlock = false;
            for(int slot = 0; slot < 9; slot++)
                if(ScaffoldUtils.isPlaceableStack(player.getInventory().getItem(slot))) { hasBlock = true; break; }
            if(!hasBlock || stuckTicks > 10 && blockPos == null) {
                hack.recovery.setSkipTickRecoveryActive(false);
                stuckTicks = skipRecoveryAttempts = rotateCount = 0;
                skipRecoveryActiveTicks = skipRecoverySkipTicks = skipRecoveryNoPlaceTicks = 0;
            }
        } else {
            stuckTicks = skipRecoveryAttempts = rotateCount = 0;
            skipRecoveryActiveTicks = skipRecoverySkipTicks = skipRecoveryNoPlaceTicks = 0;
        }
		selectBlockSlot();
		if(player.onGround())
			yLevel = Mth.floor(player.getY()) - 1;

		getBlockInfo();
		if(handleSkipTickRecovery())
			return;

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
			float clientPitch = net.wurstclient.opal.rotation.RotationHelper.getClientHandler().getPitchOr(player.getXRot());
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
		hack.selectBlockSlot(slot, false);
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
		{
			return performPlace(new BlockHitResult(getVec3(blockPos, enumFacing),
				enumFacing, blockPos, false));
		}

        Rotation current = net.wurstclient.opal.rotation.RotationHelper.getHandler().isActive()
            ? net.wurstclient.opal.rotation.RotationHelper.getClientHandler().getRotation() : null;
        if(current == null) current = lastValidPlaceRotation != null
            ? lastValidPlaceRotation : ScaffoldUtils.getRotation();
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

	@Override
	public void cancelPendingPlacement()
	{
	}

	private boolean performPlace(BlockHitResult hitResult)
	{
		var player = WurstClient.MC.player;
		ItemStack stack = player.getMainHandItem();
		if(!(stack.getItem() instanceof BlockItem))
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
		skipRecoveryAttempts = 0;
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
		// Round-trip through the mouse-sensitivity grid, like OpenOpal's
		// getVanillaRotation and ScaffoldWalkHack.getFastRotation do. Without
		// this the rotation sent for Hypixel is an arbitrary fraction that
		// Grim-style checks can flag.
		Rotation direct = ScaffoldUtils
			.getVanillaRotation(ScaffoldUtils.rotationFromBlock(pos, face));
		Rotation reverseYaw = new Rotation(
			Mth.wrapDegrees(net.wurstclient.opal.rotation.RotationHelper.getClientHandler().getYawOr(WurstClient.MC.player.getYRot()) - 180.0F),
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
			: net.wurstclient.opal.rotation.RotationHelper.getClientHandler().getYawOr(WurstClient.MC.player.getYRot());
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

	public void updateUpTellyBypassState()
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

    private boolean handleSkipTickRecovery() {
        if (!hack.recovery.isSkipTickRecoveryActive()) {
            return false;
        }

        skipRecoveryActiveTicks++;
        if (skipRecoveryActiveTicks > 26) {
            hack.recovery.markSkipTickRecoveryFailed();
            skipRecoveryAttempts = 0;
            skipRecoverySkipTicks = 0;
            skipRecoveryNoPlaceTicks = 0;
            return true;
        }

        if (skipRecoverySkipTicks > 0 && skipRecoveryNoPlaceTicks > 6) {
            hack.recovery.markSkipTickRecoveryFailed();
            skipRecoveryAttempts = 0;
            skipRecoverySkipTicks = 0;
            skipRecoveryNoPlaceTicks = 0;
            return true;
        }

        if (!onAir()) {
            hack.recovery.setSkipTickRecoveryActive(false);
            skipRecoveryAttempts = 0;
            rotateCount = 0;
            skipRecoveryActiveTicks = 0;
            skipRecoverySkipTicks = 0;
            skipRecoveryNoPlaceTicks = 0;
            return true;
        }

        final BlockPos recoveryBlockPos = hack.recovery.getSkipTickRecoveryBlockPos();
        final Direction recoveryFace = hack.recovery.getSkipTickRecoveryFace();
        if (recoveryBlockPos != null && recoveryFace != null) {
            this.blockPos = recoveryBlockPos;
            this.enumFacing = recoveryFace;
        }

        final boolean hasTarget = blockPos != null && enumFacing != null;
        if (!hasTarget) {
            skipRecoveryAttempts++;
            skipRecoveryNoPlaceTicks++;
            if (skipRecoveryAttempts > 10) {
                hack.recovery.markSkipTickRecoveryFailed();
                skipRecoveryAttempts = 0;
                skipRecoverySkipTicks = 0;
                skipRecoveryNoPlaceTicks = 0;
            }
            return true;
        }

        if (skipRecoveryAttempts > 12) {
            hack.recovery.markSkipTickRecoveryFailed();
            skipRecoveryAttempts = 0;
            rotateCount = 0;
            skipRecoverySkipTicks = 0;
            skipRecoveryNoPlaceTicks = 0;
            return true;
        }

        final Rotation rotation = getRotation(blockPos, enumFacing);
        if (rotation == null) {
            skipRecoveryAttempts++;
            skipRecoveryNoPlaceTicks++;
            return true;
        }

        applyPlacementRotation(rotation, this.getRotateSpeed());
        sendRescueRotationPacket(rotation);
        final boolean placed = performPlace(new BlockHitResult(getVec3(blockPos, enumFacing), enumFacing, blockPos, false));
        if (placed) {
            skipRecoveryNoPlaceTicks = 0;
            skipRecoveryAttempts = 0;
            hack.recovery.setSkipTickRecoveryActive(false);
            skipRecoveryActiveTicks = 0;
            skipRecoverySkipTicks = 0;
        } else {
            net.wurstclient.opal.SkipTickUtility.addSkipTicks(1);
            skipRecoverySkipTicks++;
            skipRecoveryNoPlaceTicks++;
        }
        skipRecoveryAttempts++;

        return true;
    }

    private void applyPlacementRotation(Rotation rotation, float rotateSpeed) {
        if (rotation == null) {
            return;
        }
        this.lastTargetRotation = rotation;
        hack.setPacketRotation(rotation, 2);
        rotateTo(rotation, rotateSpeed, false);
    }

    private void runSkidRescueRecursive(boolean tellyMode, float rotateSpeed, int depth) {
        if (depth >= MAX_RESCUE_ATTEMPTS || WurstClient.MC.player == null || WurstClient.MC.level == null) {
            return;
        }

        getBlockInfo();
        if (blockPos == null || enumFacing == null) {
            return;
        }

        Rotation rotation = getRotation(blockPos, enumFacing);
        if (rotation == null) {
            return;
        }

        boolean reachable = computeReachable(tellyMode ? true : blockPos != null);
        if (checkedBlock && !reachable && rotateCount < MAX_RESCUE_ATTEMPTS) {
            rotateCount++;
            skipRecoveryAttempts++;
            net.wurstclient.opal.SkipTickUtility.addSkipTicks(1);
            sendRescueRotationPacket(rotation);
            performPlace(new BlockHitResult(getVec3(blockPos, enumFacing), enumFacing, blockPos, false));
            runSkidRescueRecursive(tellyMode, rotateSpeed, depth + 1);
        } else {
            applyPlacementRotation(rotation, rotateSpeed);
            place();
            rotateCount = Math.max(0, rotateCount - 1);
        }
    }

    private boolean computeReachable(boolean defaultReachable) {
        boolean reachable = defaultReachable;
        if (WurstClient.MC.player != null && blockPos != null && WurstClient.MC.player.getDeltaMovement().y < -0.1D) {
            if (blockPos.getY() > predictYAfterTicks(2)) {
                reachable = false;
            }
        }
        return reachable;
    }

    private double predictYAfterTicks(int ticks) {
        double y = WurstClient.MC.player.getY();
        double motionY = WurstClient.MC.player.getDeltaMovement().y;
        for (int i = 0; i < ticks; i++) {
            motionY = (motionY - 0.08D) * 0.98D;
            y += motionY;
        }
        return y;
    }

    private void sendRescueRotationPacket(Rotation rotation) {
        if (rotation == null || WurstClient.MC.player == null) {
            return;
        }
        hack.setPacketRotation(rotation, 2);
        rotation.sendPlayerLookPacket();
    }

    public boolean shouldSuppressSkipTickRecoveryTrigger() {
        if (WurstClient.MC.player == null || WurstClient.MC.level == null) {
            return false;
        }

        if (!this.isTellyEnabled() || WurstClient.MC.player.onGround() || !ScaffoldUtils.isMoving()) {
            return false;
        }

        final int baseTellyTick = this.getTellyTick();
        int dynamicTellyTick = baseTellyTick;
        if (shouldAllowUpTelly()) {
            dynamicTellyTick = Math.max(1, baseTellyTick - (isDiagonalYaw(forwardYaw) ? 3 : 2));
        }

        if (airTick > dynamicTellyTick + 1) {
            return false;
        }

        return WurstClient.MC.player.getDeltaMovement().y > -0.16D && WurstClient.MC.player.fallDistance < 1.6F;
    }
}
