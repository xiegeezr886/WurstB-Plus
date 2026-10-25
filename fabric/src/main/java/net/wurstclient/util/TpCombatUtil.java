/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from InvincibleMachineGun v1.9.0 (GPL-3.0).
package net.wurstclient.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.WurstClient;
import net.wurstclient.mixinterface.IMinecraftClient;
import net.wurstclient.settings.filterlists.EntityFilterList;

/**
 * Shared helpers for the simplified IMG TP combat modules.
 */
public final class TpCombatUtil
{
	private static final WurstClient WURST = WurstClient.INSTANCE;
	private static final IMinecraftClient IMC = WurstClient.IMC;

	private TpCombatUtil() {}

	public static Entity findTarget(double range, EntityFilterList filters)
	{
		if(WurstClient.MC.player == null || WurstClient.MC.level == null)
			return null;

		double rangeSq = range * range;
		return filters.applyTo(EntityUtils.getAttackableEntities())
			.filter(e -> WurstClient.MC.player.distanceToSqr(e) <= rangeSq)
			.min(Comparator.comparingDouble(
				e -> WurstClient.MC.player.distanceToSqr(e)))
			.orElse(null);
	}

	/**
	 * Finds a position where the player can stand, between {@code minDistance}
	 * and {@code maxDistance} away from {@code center}.
	 */
	public static Vec3 findStandPos(Vec3 center, double minDistance,
		double maxDistance)
	{
		BlockPos origin = BlockPos.containing(center);
		Vec3 best = null;
		double bestDistance = Double.MAX_VALUE;

		for(int dx = -3; dx <= 3; dx++)
			for(int dy = -1; dy <= 2; dy++)
				for(int dz = -3; dz <= 3; dz++)
				{
					Vec3 pos = new Vec3(origin.getX() + dx + 0.5,
						origin.getY() + dy, origin.getZ() + dz + 0.5);
					double distance = pos.distanceTo(center);
					if(distance < minDistance || distance > maxDistance)
						continue;
					if(!ServerEquivTp.canStand(pos))
						continue;
					if(distance < bestDistance)
					{
						bestDistance = distance;
						best = pos;
					}
				}

		return best;
	}

	public static List<BlockPos> findFreeSpots(Vec3 center, int radius,
		double maxDistance)
	{
		List<BlockPos> result = new ArrayList<>();
		if(WurstClient.MC.level == null)
			return result;

		BlockPos origin = BlockPos.containing(center);
		double maxSq = maxDistance * maxDistance;

		for(BlockPos pos : BlockPos.betweenClosed(
			origin.offset(-radius, -radius, -radius),
			origin.offset(radius, radius, radius)))
		{
			if(Vec3.atCenterOf(pos).distanceToSqr(center) > maxSq)
				continue;
			if(!WurstClient.MC.level.getBlockState(pos).canBeReplaced())
				continue;
			if(!hasClickableNeighbor(pos))
				continue;
			result.add(pos.immutable());
		}

		return result;
	}

	public static boolean hasClickableNeighbor(BlockPos pos)
	{
		if(WurstClient.MC.level == null)
			return false;

		for(Direction side : Direction.values())
		{
			BlockPos neighbor = pos.relative(side);
			if(!WurstClient.MC.level.getBlockState(neighbor)
				.getCollisionShape(WurstClient.MC.level, neighbor).isEmpty())
				return true;
		}
		return false;
	}

	public static boolean selectItem(Item item)
	{
		return InventoryUtils.selectItem(item, 9);
	}

	public static boolean selectItem(Predicate<ItemStack> predicate)
	{
		return InventoryUtils.selectItem(predicate, 9);
	}

	public static boolean isHolding(Item item)
	{
		return WurstClient.MC.player != null
			&& WurstClient.MC.player.getMainHandItem().is(item);
	}

	/** Selects {@code item} and places it at {@code pos}. */
	public static boolean placeBlock(BlockPos pos, Item item)
	{
		if(WurstClient.MC.player == null || WurstClient.MC.level == null)
			return false;
		if(!selectItem(item) || !isHolding(item))
			return false;
		return BlockPlacer.place(pos, true, false);
	}

	/** Right-clicks an existing block with the currently held item. */
	public static boolean interact(BlockPos pos)
	{
		if(WurstClient.MC.player == null || WurstClient.MC.level == null
			|| WurstClient.MC.gameMode == null)
			return false;

		Vec3 center = Vec3.atCenterOf(pos);
		Vec3 eyes = RotationUtils.getEyesPos();
		Direction side =
			Direction.getNearest(eyes.x - center.x, eyes.y - center.y,
				eyes.z - center.z);
		Vec3 hitVec = center
			.add(Vec3.atLowerCornerOf(side.getNormal()).scale(0.5));

		if(eyes.distanceToSqr(hitVec) > 4.5 * 4.5)
			return false;

		Rotation rotation = RotationUtils.getNeededRotations(hitVec);
		rotation.applyToClientPlayer();
		rotation.sendPlayerLookPacket(false);

		InteractionResult result = IMC.getInteractionManager()
			.rightClickBlock(pos, side, hitVec);
		return result.consumesAction();
	}

	/** Places an end crystal on top of {@code support}. */
	public static boolean placeCrystal(BlockPos support)
	{
		if(WurstClient.MC.player == null || WurstClient.MC.level == null)
			return false;
		if(!selectItem(Items.END_CRYSTAL) || !isHolding(Items.END_CRYSTAL))
			return false;

		Vec3 hitVec = Vec3.atCenterOf(support).add(0, 0.5, 0);
		Rotation rotation = RotationUtils.getNeededRotations(hitVec);
		rotation.applyToClientPlayer();
		rotation.sendPlayerLookPacket(false);

		InteractionResult result = IMC.getInteractionManager()
			.rightClickBlock(support, Direction.UP, hitVec);
		return result.consumesAction();
	}

	public static EndCrystal findCrystal(Vec3 center, double radius)
	{
		if(WurstClient.MC.level == null)
			return null;

		AABB box = new AABB(center.x - radius, center.y - radius,
			center.z - radius, center.x + radius, center.y + radius,
			center.z + radius);

		return WurstClient.MC.level.getEntitiesOfClass(EndCrystal.class, box)
			.stream().filter(crystal -> !crystal.isRemoved())
			.min(Comparator.comparingDouble(
				crystal -> crystal.position().distanceToSqr(center)))
			.orElse(null);
	}

	/**
	 * Calculates the explosion damage the player would take while standing at
	 * {@code simulatedPos}. The client position is restored immediately.
	 */
	public static float selfDamageAt(Vec3 explosionPos, Vec3 simulatedPos,
		float power)
	{
		if(WurstClient.MC.player == null)
			return Float.MAX_VALUE;

		Vec3 oldPos = WurstClient.MC.player.position();
		try
		{
			WurstClient.MC.player.setPos(simulatedPos.x, simulatedPos.y,
				simulatedPos.z);
			return DamageUtils.calculateDamage(explosionPos,
				WurstClient.MC.player, power);
		}finally
		{
			WurstClient.MC.player.setPos(oldPos.x, oldPos.y, oldPos.z);
		}
	}

	public static float targetDamage(Vec3 explosionPos, Entity target,
		float power)
	{
		if(!(target instanceof LivingEntity living))
			return 0;
		return DamageUtils.calculateDamage(explosionPos, living, power);
	}

	public static void swing()
	{
		if(WurstClient.MC.player != null)
			WurstClient.MC.player.swing(InteractionHand.MAIN_HAND);
	}

	public static boolean isReplaceable(BlockPos pos)
	{
		return WurstClient.MC.level != null
			&& WurstClient.MC.level.getBlockState(pos).canBeReplaced();
	}

	public static BlockState getState(BlockPos pos)
	{
		return WurstClient.MC.level == null ? null
			: WurstClient.MC.level.getBlockState(pos);
	}

	/** Teleports to a standable position near {@code center}. */
	public static boolean teleportNear(String module, Vec3 center,
		double minDistance, double maxDistance)
	{
		Vec3 stand = findStandPos(center, minDistance, maxDistance);
		if(stand == null)
			return false;
		ServerEquivTp.teleport(module, stand, 10);
		return true;
	}
}
