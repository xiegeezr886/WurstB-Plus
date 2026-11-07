/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
package net.wurstclient.hacks.scaffold;

import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BeaconBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.CartographyTableBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CommandBlock;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.DaylightDetectorBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DragonEggBlock;
import net.minecraft.world.level.block.EnchantmentTableBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.GrindstoneBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.LoomBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.SlimeBlock;
import net.minecraft.world.level.block.SmithingTableBlock;
import net.minecraft.world.level.block.StructureBlock;
import net.minecraft.world.level.block.StonecutterBlock;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WebBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.WurstClient;
import net.wurstclient.util.Rotation;

/**
 * Pure geometry, block-selection and raycast helpers shared by the Scaffold
 * modes. Ported from OpenOpal's {@code RotationUtility}, {@code RaycastUtility},
 * {@code MoveUtility} and {@code InventoryUtility}.
 */
public final class ScaffoldUtils
{
	public static final double REACH = 4.5D;
	private static final Minecraft MC = WurstClient.MC;

	private ScaffoldUtils()
	{}

	public static Rotation rotationTo(Vec3 from, Vec3 to)
	{
		double x = to.x - from.x;
		double y = to.y - from.y;
		double z = to.z - from.z;
		double horizontal = Math.sqrt(x * x + z * z);
		float yaw = (float)Math.toDegrees(Math.atan2(z, x)) - 90.0F;
		float pitch = (float)-Math.toDegrees(Math.atan2(y, horizontal));
		return Rotation.wrapped(yaw, Mth.clamp(pitch, -90.0F, 90.0F));
	}

	public static Rotation rotationFromBlock(BlockPos pos, Direction face)
	{
		Player player = MC.player;
		float xDiff = (float)(pos.getX() + 0.5D - player.getX()
			+ face.getStepX() * 0.5D);
		float yDiff = (float)(player.getY() + player.getEyeHeight()
			- pos.getY() - face.getStepY() * 0.5D);
		float zDiff = (float)(pos.getZ() + 0.5D - player.getZ()
			+ face.getStepZ() * 0.5D);
		double distance = Math.sqrt(xDiff * xDiff + zDiff * zDiff);
		float yaw = (float)Math.toDegrees(-Math.atan2(xDiff, zDiff));
		float pitch = (float)Math.toDegrees(Math.atan(yDiff / distance));
		return Rotation.wrapped(yaw, pitch);
	}

	public static Rotation getRotationFromPosition(Vec3 from, Vec3 to)
	{
		double xDiff = to.x - from.x;
		double yDiff = to.y - from.y;
		double zDiff = to.z - from.z;
		double distance = Math.sqrt(xDiff * xDiff + zDiff * zDiff);
		float yaw = (float)Math.toDegrees(-Math.atan2(xDiff, zDiff));
		float pitch = (float)-Math.toDegrees(Math.atan2(yDiff, distance));
		return Rotation.wrapped(yaw, pitch);
	}

	/**
	 * The rotation the server currently believes the player has. Wurst spoofs
	 * this through {@code RotationFaker}; OpenOpal reads it from the real
	 * player because its rotation handler changes the player itself.
	 */
	public static Rotation getRotation()
	{
		var faker = WurstClient.INSTANCE.getRotationFaker();
		if(faker.hasServerRotation())
			return new Rotation(faker.getServerYaw(), faker.getServerPitch());
		return new Rotation(MC.player.getYRot(), MC.player.getXRot());
	}

	/**
	 * OpenOpal's mouse-sensitivity GCD patch. Every returned rotation is an
	 * integer number of mouse pixels away from {@code previous}. Deliberately
	 * does not wrap/clamp: the callers apply those where the reference does.
	 */
	public static Rotation patchConstantRotation(Rotation rotation,
		Rotation previous)
	{
		double sensitivity = MC.options.sensitivity().get() * 0.6D + 0.2D;
		double multiplier = sensitivity * sensitivity * sensitivity * 8.0D;
		double divisor = multiplier * 0.15D;

		float yawDelta = rotation.yaw() - previous.yaw();
		float pitchDelta = rotation.pitch() - previous.pitch();
		float yaw = previous.yaw()
			+ (float)(Math.round(yawDelta / divisor) * divisor);
		float pitch = previous.pitch()
			+ (float)(Math.round(pitchDelta / divisor) * divisor);
		return new Rotation(yaw, pitch);
	}

	public static double getCursorDelta(double rotationDelta,
		double sensitivityMultiplier)
	{
		return (float)(rotationDelta / sensitivityMultiplier) / 0.15F;
	}

	public static float getSensitivityModifiedRotation(double original)
	{
		double sensitivity = MC.options.sensitivity().get() * 0.6D + 0.2D;
		double multiplier = sensitivity * sensitivity * sensitivity * 8.0D;
		return (float)(getCursorDelta(original, multiplier) * multiplier)
			* 0.15F;
	}

	public static Rotation getSentRotation(Rotation original)
	{
		Rotation patched = patchConstantRotation(original, getRotation());
		return new Rotation(
			getSensitivityModifiedRotation(patched.yaw()),
			getSensitivityModifiedRotation(patched.pitch()));
	}

	/**
	 * OpenOpal's {@code getVanillaRotation}: round-trips the rotation through
	 * the sensitivity grid and keeps the yaw in the same 360-degree range as
	 * the player's current yaw.
	 */
	public static Rotation getVanillaRotation(Rotation original)
	{
		Rotation sent = getSentRotation(original);
		return new Rotation(
			getDuplicateWrapped(sent.yaw(), getRotation().yaw()), sent.pitch());
	}

	public static float getDuplicateWrapped(float value, float target)
	{
		return target + Mth.wrapDegrees(value - target);
	}

	/**
	 * OpenOpal's {@code getRotationDifference}: Yarn's
	 * {@code MathHelper.angleBetween} is the absolute wrapped yaw difference.
	 */
	public static double getRotationDifference(Rotation a, Rotation b)
	{
		return Math.abs(Mth.wrapDegrees(b.yaw() - a.yaw()))
			+ Math.abs(a.pitch() - b.pitch());
	}

	public static Vec3 rotationVector(float pitch, float yaw)
	{
		float pitchRad = pitch * Mth.DEG_TO_RAD;
		float yawRad = -yaw * Mth.DEG_TO_RAD;
		float yawCos = Mth.cos(yawRad);
		float yawSin = Mth.sin(yawRad);
		float pitchCos = Mth.cos(pitchRad);
		float pitchSin = Mth.sin(pitchRad);
		return new Vec3(yawSin * pitchCos, -pitchSin, yawCos * pitchCos);
	}

	public static BlockHitResult raycastBlock(Rotation rotation, double range)
	{
		return raycastBlock(MC.player.getEyePosition(), rotation, range);
	}

	public static BlockHitResult raycastBlock(Vec3 start, Rotation rotation,
		double range)
	{
		Vec3 end = start.add(rotationVector(rotation.pitch(),
			rotation.yaw()).scale(range));
		return MC.level.clip(new ClipContext(start, end,
			ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, MC.player));
	}

	public static boolean isGoodBlock(Block block)
	{
		if(block == null || isBlockInteractable(block)
			|| block instanceof TntBlock || block instanceof FallingBlock
			|| block instanceof WebBlock)
			return false;

		BlockState state = block.defaultBlockState();
		return state.getFluidState().isEmpty()
			&& state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE,
				BlockPos.ZERO);
	}

	public static boolean isBlockInteractable(Block block)
	{
		return block instanceof TrapDoorBlock
			|| block instanceof AbstractFurnaceBlock
			|| block instanceof SignBlock || block instanceof AnvilBlock
			|| block instanceof BarrelBlock || block instanceof BeaconBlock
			|| block instanceof BedBlock || block instanceof BellBlock
			|| block instanceof BrewingStandBlock
			|| block instanceof ButtonBlock || block instanceof CakeBlock
			|| block instanceof CartographyTableBlock
			|| block instanceof ChestBlock || block instanceof CommandBlock
			|| block instanceof ComparatorBlock
			|| block instanceof ComposterBlock
			|| block instanceof CraftingTableBlock
			|| block instanceof DaylightDetectorBlock
			|| block instanceof DispenserBlock || block instanceof DoorBlock
			|| block instanceof DragonEggBlock
			|| block instanceof EnchantmentTableBlock
			|| block instanceof EnderChestBlock || block instanceof FenceBlock
			|| block instanceof FenceGateBlock || block instanceof FlowerBlock
			|| block instanceof FlowerPotBlock || block instanceof GrindstoneBlock
			|| block instanceof HopperBlock || block instanceof JigsawBlock
			|| block instanceof JukeboxBlock || block instanceof LecternBlock
			|| block instanceof LeverBlock || block instanceof LightBlock
			|| block instanceof LoomBlock || block instanceof NoteBlock
			|| block instanceof RedStoneWireBlock
			|| block instanceof RepeaterBlock
			|| block instanceof RespawnAnchorBlock
			|| block instanceof ShulkerBoxBlock
			|| block instanceof SlimeBlock
			|| block instanceof SmithingTableBlock
			|| block instanceof StonecutterBlock
			|| block instanceof StructureBlock || block instanceof WebBlock;
	}

	public static boolean isPlaceableStack(ItemStack stack)
	{
		return stack != null && !stack.isEmpty()
			&& stack.getItem() instanceof BlockItem blockItem
			&& isGoodBlock(blockItem.getBlock());
	}

	public static boolean isReplaceable(BlockPos pos)
	{
		return MC.level != null && MC.level.isInWorldBounds(pos)
			&& MC.level.getBlockState(pos).canBeReplaced();
	}

	public static boolean isValidSupportBlock(BlockPos pos)
	{
		if(MC.level == null || !MC.level.isInWorldBounds(pos))
			return false;

		BlockState state = MC.level.getBlockState(pos);
		return !state.isAir() && !state.canBeReplaced()
			&& state.getFluidState().isEmpty()
			&& !isBlockInteractable(state.getBlock())
			&& !state.getCollisionShape(MC.level, pos).isEmpty();
	}

	public static boolean isBoxEmpty(Player player, AABB box)
	{
		return !MC.level.getBlockCollisions(player, box).iterator().hasNext();
	}

	public static boolean isMoving()
	{
		if(MC.player == null || MC.player.input == null)
			return false;
		return Math.abs(MC.player.input.forwardImpulse) > 1.0E-4F
			|| Math.abs(MC.player.input.leftImpulse) > 1.0E-4F;
	}

	public static float moveDirectionDegrees()
	{
		return moveDirectionDegrees(MC.player.getYRot());
	}

	public static float moveDirectionDegrees(float yaw)
	{
		if(MC.player == null || MC.player.input == null)
			return yaw;

		float forward = MC.player.input.forwardImpulse;
		float sideways = MC.player.input.leftImpulse;
		return (float)Math.toDegrees(getDirection(yaw, forward, sideways));
	}

	public static double getDirection(float rotationYaw, double forward,
		double strafing)
	{
		if(forward < 0)
			rotationYaw += 180;

		float multiplier = forward < 0 ? -0.5F : forward > 0 ? 0.5F : 1.0F;
		if(strafing > 0)
			rotationYaw -= 90 * multiplier;
		if(strafing < 0)
			rotationYaw += 90 * multiplier;
		return Math.toRadians(rotationYaw);
	}

	/**
	 * OpenOpal's movement-intelligence sorting angle. Keeps the placement
	 * rotation close to a cardinal/diagonal movement yaw so the server sees a
	 * plausible mouse path.
	 */
	public static Rotation getPriorityAngle(Rotation current, int steps,
		boolean snap, boolean diagonal)
	{
		float targetYaw;
		if(snap)
		{
			float rounding = 45.0F / Math.max(1, steps);
			float roundedMoveDir = Math.round(moveDirectionDegrees() / rounding)
				* rounding;
			float yawRad = (float)Math.toRadians(roundedMoveDir);
			float offset = 10.0F;
			float dirX = -Mth.sin(yawRad) * offset;
			float dirZ = Mth.cos(yawRad) * offset;

			Vec3 playerPos = MC.player.position();
			double centerX = Math.floor(playerPos.x) + dirX + 0.5D;
			double centerZ = Math.floor(playerPos.z) + dirZ + 0.5D;
			targetYaw = (float)Math.toDegrees(
				Math.atan2(-(centerX - playerPos.x), centerZ - playerPos.z));
		}else
			targetYaw = moveDirectionDegrees();

		float endYaw = targetYaw
			+ ThreadLocalRandom.current().nextFloat() * 0.02F - 0.01F;
		float best = endYaw;
		float bestDifference = Math.abs(Mth.wrapDegrees(endYaw - current.yaw()));

		float[] candidates = diagonal
			? new float[]{45, -45, 90, -90, 135, -135}
			: new float[]{0, 180, 45, -45, 90, -90, 135, -135};
		for(float candidateOffset : candidates)
		{
			float candidate = endYaw + candidateOffset;
			float difference = Math
				.abs(Mth.wrapDegrees(candidate - current.yaw()));
			if(difference < bestDifference)
			{
				bestDifference = difference;
				best = candidate;
			}
		}
		return new Rotation(Mth.wrapDegrees(best), current.pitch());
	}
}
