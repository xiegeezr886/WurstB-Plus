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
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
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
import net.wurstclient.util.TpCombatUtil;

/**
 * Simplified port of IMG's {@code GcoreGTrap}.
 *
 * <p>Teleports next to the target and surrounds it with obsidian. The original
 * gcore trap planner and respawn-anchor stage are not ported.
 */
@SearchTags({"g trap", "gtrap", "trap", "困阵", "传送困阵"})
public final class GcoreGTrapHack extends Hack implements UpdateListener
{
	private final SliderSetting targetRange = new SliderSetting(
		"Target range", "Maximum distance to search for a target.", 100, 1,
		300, 1, ValueDisplay.INTEGER.withSuffix(" blocks"));

	private final SliderSetting placeInterval = new SliderSetting(
		"Place interval", "Delay between two trap placements.", 50, 1, 2000,
		10, ValueDisplay.INTEGER.withSuffix("ms"));

	private final SliderSetting placeCount = new SliderSetting(
		"Place count", "Maximum number of blocks placed per action.", 5, 1, 12,
		1, ValueDisplay.INTEGER);

	private final CheckboxSetting swingHand = new CheckboxSetting("Swing hand",
		"Play the hand swing animation when placing.", true);

	private final EntityFilterList entityFilters =
		EntityFilterList.genericCombat();

	private long nextAction;

	public GcoreGTrapHack()
	{
		super("GcoreGTrap");
		setCategory(Category.COMBAT);
		addConflictGroup(HackConflictGroup.COMBAT_TARGETING);
		addSetting(targetRange);
		addSetting(placeInterval);
		addSetting(placeCount);
		addSetting(swingHand);
		entityFilters.forEach(this::addSetting);
	}

	@Override
	protected void onEnable()
	{
		nextAction = 0;
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
		if(MC.player == null || MC.level == null || MC.gameMode == null)
			return;

		long now = System.currentTimeMillis();
		if(now < nextAction)
			return;

		Entity target =
			TpCombatUtil.findTarget(targetRange.getValue(), entityFilters);
		if(target == null)
			return;

		if(MC.player.position().distanceToSqr(target.position()) > 4.2 * 4.2
			&& !TpCombatUtil.teleportNear("GcoreGTrap", target.position(), 1.5,
				3.5))
			return;

		nextAction = now + placeInterval.getValueI();

		Vec3 targetPos = target.position();
		List<BlockPos> spots =
			TpCombatUtil.findFreeSpots(targetPos, 2, 3.2);
		if(spots.isEmpty())
			return;

		spots.sort(Comparator.comparingDouble(
			pos -> Vec3.atCenterOf(pos).distanceToSqr(targetPos)));

		int placed = 0;
		for(BlockPos pos : spots)
		{
			if(placed >= placeCount.getValueI())
				break;

			AABB box = new AABB(pos);
			if(box.intersects(target.getBoundingBox())
				|| box.intersects(MC.player.getBoundingBox()))
				continue;

			if(TpCombatUtil.placeBlock(pos, Items.OBSIDIAN))
			{
				placed++;
				if(swingHand.isChecked())
					TpCombatUtil.swing();
			}
		}
	}
}
