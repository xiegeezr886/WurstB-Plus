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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.hack.HackConflictGroup;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.filterlists.EntityFilterList;
import net.wurstclient.util.ChatUtils;
import net.wurstclient.util.TpCombatUtil;

/**
 * Simplified port of IMG's {@code TpAnchorAura} / {@code GLightTpAnchorAura}.
 *
 * <p>Teleports next to the target, places a respawn anchor, charges it with
 * glowstone and detonates it. The full gcore placement planner is not ported;
 * candidate spots are evaluated with the regular damage calculator instead.
 */
public abstract class AbstractTpAnchorAuraHack extends Hack
	implements UpdateListener
{
	private final String moduleName;
	private final SliderSetting targetRange;
	private final SliderSetting searchRadius;
	private final SliderSetting placeDelay;
	private final SliderSetting minTargetDamage;
	private final SliderSetting maxSelfDamage;
	private final CheckboxSetting ignite;
	private final CheckboxSetting swingHand;
	private final EntityFilterList entityFilters;

	private BlockPos anchorPos;
	private int phase;
	private long nextAction;

	protected AbstractTpAnchorAuraHack(String name, int defaultPlaceDelay)
	{
		super(name);
		moduleName = name;
		setCategory(Category.COMBAT);
		addConflictGroup(HackConflictGroup.COMBAT_TARGETING);

		targetRange = new SliderSetting("Target range",
			"Maximum distance to search for a target.", 100, 1, 300, 1,
			ValueDisplay.INTEGER.withSuffix(" blocks"));
		searchRadius = new SliderSetting("Search radius",
			"Radius around the target in which anchors are placed.", 6, 1, 8,
			1, ValueDisplay.INTEGER);
		placeDelay = new SliderSetting("Place delay",
			"Delay between two anchors.", defaultPlaceDelay, 0, 2000, 10,
			ValueDisplay.INTEGER.withSuffix("ms"));
		minTargetDamage = new SliderSetting("Min target damage",
			"Minimum predicted damage to the target.", 0.3, 0, 20, 0.1,
			ValueDisplay.DECIMAL);
		maxSelfDamage = new SliderSetting("Max self damage",
			"Maximum predicted damage to yourself.", 12, 0, 20, 0.1,
			ValueDisplay.DECIMAL);
		ignite = new CheckboxSetting("Ignite",
			"Charge the anchor with glowstone and detonate it.", true);
		swingHand = new CheckboxSetting("Swing hand",
			"Play the hand swing animation when placing or igniting.", true);
		entityFilters = EntityFilterList.genericCombat();

		addSetting(targetRange);
		addSetting(searchRadius);
		addSetting(placeDelay);
		addSetting(minTargetDamage);
		addSetting(maxSelfDamage);
		addSetting(ignite);
		addSetting(swingHand);
		entityFilters.forEach(this::addSetting);
	}

	@Override
	protected void onEnable()
	{
		resetAnchor();
		nextAction = 0;
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		resetAnchor();
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null || MC.gameMode == null)
			return;

		if(MC.level.dimensionType().respawnAnchorWorks())
		{
			ChatUtils.error("Respawn anchors do not explode in this dimension.");
			setEnabled(false);
			return;
		}

		long now = System.currentTimeMillis();
		if(now < nextAction)
			return;

		if(anchorPos != null)
		{
			processAnchor(now);
			return;
		}

		Entity target =
			TpCombatUtil.findTarget(targetRange.getValue(), entityFilters);
		if(target == null)
			return;

		BlockPos spot = findSpot(target);
		if(spot == null)
			return;

		Vec3 spotCenter = Vec3.atCenterOf(spot);
		if(!TpCombatUtil.teleportNear(moduleName, spotCenter, 1.5, 4))
			return;

		if(!TpCombatUtil.placeBlock(spot, Items.RESPAWN_ANCHOR))
		{
			nextAction = now + 200;
			return;
		}

		if(swingHand.isChecked())
			TpCombatUtil.swing();

		anchorPos = spot;
		phase = 1;
		nextAction = now + 100;
	}

	private void processAnchor(long now)
	{
		if(!(MC.level.getBlockState(anchorPos).getBlock() instanceof RespawnAnchorBlock))
		{
			resetAnchor();
			return;
		}

		if(!ignite.isChecked())
		{
			resetAnchor();
			nextAction = now + placeDelay.getValueI();
			return;
		}

		if(phase == 1)
		{
			if(!TpCombatUtil.selectItem(Items.GLOWSTONE))
			{
				resetAnchor();
				return;
			}

			if(TpCombatUtil.interact(anchorPos))
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
			if(!TpCombatUtil
				.selectItem(stack -> !stack.is(Items.GLOWSTONE)))
			{
				resetAnchor();
				return;
			}

			if(TpCombatUtil.interact(anchorPos))
			{
				if(swingHand.isChecked())
					TpCombatUtil.swing();
				resetAnchor();
				nextAction = now + placeDelay.getValueI();
			}
		}
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

		for(int i = 0; i < Math.min(spots.size(), 40); i++)
		{
			BlockPos pos = spots.get(i);
			Vec3 center = Vec3.atCenterOf(pos);
			Vec3 stand = TpCombatUtil.findStandPos(center, 1.5, 4);
			if(stand == null)
				continue;

			float targetDamage = TpCombatUtil.targetDamage(center, target, 5);
			if(targetDamage < minTarget)
				continue;

			float selfDamage = TpCombatUtil.selfDamageAt(center, stand, 5);
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

	private void resetAnchor()
	{
		anchorPos = null;
		phase = 0;
	}
}
