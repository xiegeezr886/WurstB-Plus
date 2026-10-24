/*
 * Copyright (c) 2025 LeavesHack contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from LeavesHack (AGPL-3.0).
package net.wurstclient.hacks;

import java.util.Comparator;
import java.util.stream.StreamSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.BlockPlacer;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.DamageUtils;
import net.wurstclient.util.FakePlayerEntity;
import net.wurstclient.util.InventoryUtils;
import net.wurstclient.util.RotationUtils;
import net.wurstclient.util.text.WText;

@SearchTags({"auto anchor", "respawn anchor", "anchor aura", "AutoAnchor"})
public final class AutoAnchorHack extends Hack implements UpdateListener
{
	private static final float ANCHOR_POWER = 5;

	private final SliderSetting targetRange = new SliderSetting(
		"Target range",
		WText.literal("How far to search for a target to anchor."), 8, 1, 20,
		0.5, ValueDisplay.DECIMAL);

	private final SliderSetting range = new SliderSetting("Range",
		WText.literal("How far away anchors can be placed and detonated."), 4.5,
		1, 6, 0.1, ValueDisplay.DECIMAL);

	private final SliderSetting delay =
		new SliderSetting("Delay", WText.literal(
			"Ticks to wait between placing another anchor."), 2, 0, 20, 1,
			ValueDisplay.INTEGER.withSuffix(" ticks"));

	private final SliderSetting chargeDelay =
		new SliderSetting("Charge delay",
			WText.literal("Ticks to wait between charging and detonating."), 2, 0,
			20, 1, ValueDisplay.INTEGER.withSuffix(" ticks"));

	private final SliderSetting minDamage = new SliderSetting("Min damage",
		WText.literal("Minimum damage the anchor must deal to the target."), 4,
		0, 36, 0.5, ValueDisplay.DECIMAL);

	private final SliderSetting maxSelfDamage =
		new SliderSetting("Max self damage",
			WText.literal("Maximum damage the anchor may deal to you."), 10, 0,
			36, 0.5, ValueDisplay.DECIMAL);

	private final CheckboxSetting antiSuicide = new CheckboxSetting(
		"Anti suicide", WText.literal("Rejects anchors that could kill you."),
		true);

	private int timer;

	public AutoAnchorHack()
	{
		super("AutoAnchor");
		setCategory(Category.COMBAT);
		addSetting(targetRange);
		addSetting(range);
		addSetting(delay);
		addSetting(chargeDelay);
		addSetting(minDamage);
		addSetting(maxSelfDamage);
		addSetting(antiSuicide);
	}

	@Override
	protected void onEnable()
	{
		timer = 0;
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null || MC.player.isSpectator())
			return;

		if(timer > 0)
		{
			timer--;
			return;
		}

		LivingEntity target = findTarget();
		if(target == null)
			return;

		BlockPos anchor = findBestAnchor(target);
		if(anchor != null)
		{
			actOnAnchor(anchor);
			return;
		}

		if(InventoryUtils.indexOf(Items.RESPAWN_ANCHOR, 36) == -1
			|| InventoryUtils.indexOf(Items.GLOWSTONE, 36) == -1)
			return;

		BlockPos placement = findBestPlacement(target);
		if(placement == null)
			return;

		int oldSlot = MC.player.getInventory().selected;
		InventoryUtils.selectItem(Items.RESPAWN_ANCHOR, 36);
		if(!MC.player.getMainHandItem().is(Items.RESPAWN_ANCHOR))
			return;

		if(BlockPlacer.placeOneBlock(placement))
			timer = delay.getValueI();
		MC.player.getInventory().selected = oldSlot;
	}

	private void actOnAnchor(BlockPos pos)
	{
		int charge = BlockUtils.getState(pos)
			.getOptionalValue(RespawnAnchorBlock.CHARGE).orElse(0);

		if(charge > 0)
		{
			int oldSlot = MC.player.getInventory().selected;
			MC.player.getInventory().selected = findNonGlowstoneSlot();

			if(!MC.player.getMainHandItem().is(Items.GLOWSTONE)
				&& rightClickAnchor(pos))
				timer = chargeDelay.getValueI();

			MC.player.getInventory().selected = oldSlot;
			return;
		}

		int oldSlot = MC.player.getInventory().selected;
		InventoryUtils.selectItem(Items.GLOWSTONE, 36);

		if(MC.player.getMainHandItem().is(Items.GLOWSTONE)
			&& rightClickAnchor(pos))
			timer = chargeDelay.getValueI();

		MC.player.getInventory().selected = oldSlot;
	}

	private LivingEntity findTarget()
	{
		double rangeSq = targetRange.getValueSq();

		return StreamSupport
			.stream(MC.level.entitiesForRendering().spliterator(), false)
			.filter(e -> e instanceof LivingEntity
				&& ((LivingEntity)e).getHealth() > 0)
			.filter(e -> e != MC.player)
			.filter(e -> !(e instanceof FakePlayerEntity))
			.filter(e -> !WURST.getFriends().contains(e.getScoreboardName()))
			.filter(e -> MC.player.distanceToSqr(e) <= rangeSq)
			.map(e -> (LivingEntity)e)
			.min(Comparator.comparingDouble(e -> MC.player.distanceToSqr(e)))
			.orElse(null);
	}

	private BlockPos findBestAnchor(LivingEntity target)
	{
		return BlockUtils.getAllInBoxStream(target.blockPosition(), 3)
			.filter(pos -> BlockUtils.getBlock(pos) == Blocks.RESPAWN_ANCHOR)
			.filter(this::inRange).filter(pos -> isDamageSafe(pos, target))
			.max(Comparator.comparingDouble(
				pos -> getTargetDamage(pos, target)))
			.orElse(null);
	}

	private BlockPos findBestPlacement(LivingEntity target)
	{
		return BlockUtils.getAllInBoxStream(target.blockPosition(), 2)
			.filter(pos -> BlockUtils.getState(pos).canBeReplaced())
			.filter(this::inRange).filter(this::hasClickableNeighbor)
			.filter(pos -> !hasEntity(pos))
			.filter(pos -> isDamageSafe(pos, target))
			.max(Comparator.comparingDouble(pos -> getTargetDamage(pos, target)
				- DamageUtils.calculateDamage(Vec3.atCenterOf(pos), MC.player,
					ANCHOR_POWER) * 0.25))
			.orElse(null);
	}

	private boolean isDamageSafe(BlockPos pos, LivingEntity target)
	{
		float targetDamage = getTargetDamage(pos, target);
		float selfDamage = DamageUtils.calculateDamage(Vec3.atCenterOf(pos),
			MC.player, ANCHOR_POWER);

		if(targetDamage < minDamage.getValueF()
			|| selfDamage > maxSelfDamage.getValueF())
			return false;

		return !antiSuicide.isChecked()
			|| selfDamage < MC.player.getHealth()
				+ MC.player.getAbsorptionAmount();
	}

	private float getTargetDamage(BlockPos pos, LivingEntity target)
	{
		return DamageUtils.calculateDamage(Vec3.atCenterOf(pos), target,
			ANCHOR_POWER);
	}

	private boolean inRange(BlockPos pos)
	{
		return RotationUtils.getEyesPos()
			.distanceToSqr(Vec3.atCenterOf(pos)) <= range.getValueSq();
	}

	private boolean hasClickableNeighbor(BlockPos pos)
	{
		for(Direction side : Direction.values())
		{
			BlockPos neighbor = pos.relative(side);
			if(BlockUtils.canBeClicked(neighbor)
				&& !BlockUtils.getState(neighbor).canBeReplaced())
				return true;
		}

		return false;
	}

	private boolean hasEntity(BlockPos pos)
	{
		return !MC.level.getEntities((Entity)null, new AABB(pos)).isEmpty();
	}

	private boolean rightClickAnchor(BlockPos pos)
	{
		Vec3 eyesPos = RotationUtils.getEyesPos();
		Vec3 posVec = Vec3.atCenterOf(pos);
		double distanceSqPosVec = eyesPos.distanceToSqr(posVec);

		for(Direction side : Direction.values())
		{
			Vec3 hitVec = posVec
				.add(Vec3.atLowerCornerOf(side.getNormal()).scale(0.5));
			double distanceSqHitVec = eyesPos.distanceToSqr(hitVec);

			if(distanceSqHitVec > range.getValueSq())
				continue;
			if(distanceSqHitVec >= distanceSqPosVec)
				continue;

			RotationUtils.getNeededRotations(hitVec).sendPlayerLookPacket();
			IMC.getInteractionManager().rightClickBlock(pos, side, hitVec);
			return true;
		}

		return false;
	}

	private int findNonGlowstoneSlot()
	{
		for(int slot = 0; slot < 9; slot++)
			if(!MC.player.getInventory().getItem(slot)
				.is(Items.GLOWSTONE))
				return slot;

		return MC.player.getInventory().selected;
	}
}
