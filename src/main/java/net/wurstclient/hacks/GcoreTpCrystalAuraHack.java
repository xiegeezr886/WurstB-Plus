/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from InvincibleMachineGun v1.9.0 (GPL-3.0).
package net.wurstclient.hacks;

import java.util.Comparator;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.hack.HackConflictGroup;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.filterlists.EntityFilterList;
import net.wurstclient.util.ServerEquivTp;
import net.wurstclient.util.TpCombatUtil;

/**
 * Simplified port of IMG's {@code GcoreTpCrystalAura}.
 *
 * <p>Teleports next to the target, places obsidian and an end crystal, then
 * attacks the crystal. The full gcore placement planner is not ported.
 */
@SearchTags({"tp crystal aura", "tp crystal", "魔术水晶", "水晶光环"})
public final class GcoreTpCrystalAuraHack extends Hack
	implements UpdateListener
{
	private final SliderSetting targetRange = new SliderSetting(
		"Target range", "Maximum distance to search for a target.", 100, 1,
		300, 1, ValueDisplay.INTEGER.withSuffix(" blocks"));

	private final SliderSetting searchRadius = new SliderSetting(
		"Search radius",
		"Radius around the target in which obsidian is placed.", 5, 1, 8, 1,
		ValueDisplay.INTEGER);

	private final SliderSetting attackInterval = new SliderSetting(
		"Attack interval", "Delay between crystal attacks.", 100, 1, 1000, 1,
		ValueDisplay.INTEGER.withSuffix("ms"));

	private final SliderSetting hitDistance = new SliderSetting(
		"Hit distance", "Maximum distance from the target to a crystal.", 5, 1,
		6, 0.1, ValueDisplay.DECIMAL.withSuffix(" blocks"));

	private final SliderSetting placeInterval = new SliderSetting(
		"Place interval", "Delay between two obsidian placements.", 180, 1,
		2000, 10, ValueDisplay.INTEGER.withSuffix("ms"));

	private final SliderSetting minTargetDamage = new SliderSetting(
		"Min target damage", "Minimum predicted damage to the target.", 0.3, 0,
		20, 0.1, ValueDisplay.DECIMAL);

	private final SliderSetting maxSelfDamage = new SliderSetting(
		"Max self damage", "Maximum predicted damage to yourself.", 6, 0, 20,
		0.1, ValueDisplay.DECIMAL);

	private final CheckboxSetting swingHand = new CheckboxSetting("Swing hand",
		"Play the hand swing animation when placing or attacking.", true);

	private final EntityFilterList entityFilters =
		EntityFilterList.genericCombat();

	private BlockPos obsidianPos;
	private int phase;
	private long nextAction;
	private long nextAttack;

	public GcoreTpCrystalAuraHack()
	{
		super("GcoreTpCrystalAura");
		setCategory(Category.COMBAT);
		addConflictGroup(HackConflictGroup.COMBAT_TARGETING);
		addSetting(targetRange);
		addSetting(searchRadius);
		addSetting(attackInterval);
		addSetting(hitDistance);
		addSetting(placeInterval);
		addSetting(minTargetDamage);
		addSetting(maxSelfDamage);
		addSetting(swingHand);
		entityFilters.forEach(this::addSetting);
	}

	@Override
	protected void onEnable()
	{
		resetPlacement();
		nextAction = 0;
		nextAttack = 0;
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		resetPlacement();
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null || MC.gameMode == null)
			return;

		long now = System.currentTimeMillis();

		Entity target =
			TpCombatUtil.findTarget(targetRange.getValue(), entityFilters);
		if(target == null)
		{
			resetPlacement();
			return;
		}

		if(now >= nextAttack)
		{
			EndCrystal crystal = TpCombatUtil.findCrystal(target.position(),
				hitDistance.getValue());
			if(crystal != null && attackCrystal(crystal))
			{
				nextAttack = now + attackInterval.getValueI();
				return;
			}
		}

		if(obsidianPos != null)
		{
			processPlacement(now);
			return;
		}

		if(now < nextAction)
			return;

		BlockPos spot = findSpot(target);
		if(spot == null)
			return;

		Vec3 spotCenter = Vec3.atCenterOf(spot);
		if(!TpCombatUtil.teleportNear("GcoreTpCrystalAura", spotCenter, 1.5,
			4))
			return;

		if(!TpCombatUtil.placeBlock(spot, Items.OBSIDIAN))
		{
			nextAction = now + 200;
			return;
		}

		if(swingHand.isChecked())
			TpCombatUtil.swing();

		obsidianPos = spot;
		phase = 1;
		nextAction = now + 100;
	}

	private void processPlacement(long now)
	{
		if(MC.level.getBlockState(obsidianPos).getBlock() != Blocks.OBSIDIAN)
		{
			resetPlacement();
			return;
		}

		if(phase == 1)
		{
			if(!TpCombatUtil.teleportNear("GcoreTpCrystalAura",
				Vec3.atCenterOf(obsidianPos), 1.5, 4))
				return;

			if(TpCombatUtil.placeCrystal(obsidianPos))
			{
				if(swingHand.isChecked())
					TpCombatUtil.swing();
				phase = 2;
				nextAction = now + 100;
			}
			return;
		}

		if(phase == 2)
		{
			EndCrystal crystal = TpCombatUtil.findCrystal(
				Vec3.atCenterOf(obsidianPos), hitDistance.getValue());
			if(crystal != null && attackCrystal(crystal))
			{
				if(swingHand.isChecked())
					TpCombatUtil.swing();
				nextAttack = now + attackInterval.getValueI();
			}

			resetPlacement();
			nextAction = now + placeInterval.getValueI();
		}
	}

	private boolean attackCrystal(EndCrystal crystal)
	{
		Vec3 center = crystal.position();
		if(MC.player.position().distanceToSqr(center) > 3 * 3
			&& !TpCombatUtil.teleportNear("GcoreTpCrystalAura", center, 1.5,
				3))
			return false;

		MC.gameMode.attack(MC.player, crystal);
		return true;
	}

	private BlockPos findSpot(Entity target)
	{
		Vec3 targetPos = target.position();
		List<BlockPos> spots = TpCombatUtil.findFreeSpots(targetPos,
			searchRadius.getValueI(), searchRadius.getValue());
		if(spots.isEmpty())
			return null;

		spots.sort(Comparator.comparingDouble(
			pos -> Vec3.atCenterOf(pos).distanceToSqr(targetPos)));

		double minTarget = minTargetDamage.getValue();
		double maxSelf = maxSelfDamage.getValue();
		BlockPos best = null;
		double bestScore = -Double.MAX_VALUE;

		for(int i = 0; i < Math.min(spots.size(), 32); i++)
		{
			BlockPos pos = spots.get(i);
			if(!TpCombatUtil.isReplaceable(pos.above()))
				continue;

			Vec3 stand = TpCombatUtil.findStandPos(Vec3.atCenterOf(pos), 1.5,
				4);
			if(stand == null)
				continue;

			Vec3 crystalPos = Vec3.atCenterOf(pos).add(0, 1, 0);
			float targetDamage =
				TpCombatUtil.targetDamage(crystalPos, target, 6);
			if(targetDamage < minTarget)
				continue;

			float selfDamage =
				TpCombatUtil.selfDamageAt(crystalPos, stand, 6);
			if(selfDamage > maxSelf)
				continue;

			double score = targetDamage - selfDamage * 0.25;
			if(score > bestScore)
			{
				bestScore = score;
				best = pos;
			}
		}

		return best;
	}

	private void resetPlacement()
	{
		obsidianPos = null;
		phase = 0;
	}
}
