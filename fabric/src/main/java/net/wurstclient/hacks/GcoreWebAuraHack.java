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
 * Simplified port of IMG's {@code GcoreWebAura}.
 *
 * <p>Teleports next to the target and places cobwebs around its feet so it
 * cannot escape. The anchor/trap stages of the original module are not ported.
 */
@SearchTags({"tp web aura", "web aura", "天罗地网", "蜘蛛网光环"})
public final class GcoreWebAuraHack extends Hack implements UpdateListener
{
	private final SliderSetting targetRange = new SliderSetting(
		"Target range", "Maximum distance to search for a target.", 100, 1,
		300, 1, ValueDisplay.INTEGER.withSuffix(" blocks"));

	private final SliderSetting placeInterval = new SliderSetting(
		"Place interval", "Delay between two web placements.", 50, 1, 2000,
		10, ValueDisplay.INTEGER.withSuffix("ms"));

	private final SliderSetting webCount = new SliderSetting("Web count",
		"Maximum number of cobwebs placed per action.", 1, 1, 8, 1,
		ValueDisplay.INTEGER);

	private final CheckboxSetting swingHand = new CheckboxSetting("Swing hand",
		"Play the hand swing animation when placing.", true);

	private final EntityFilterList entityFilters =
		EntityFilterList.genericCombat();

	private long nextAction;

	public GcoreWebAuraHack()
	{
		super("GcoreWebAura");
		setCategory(Category.COMBAT);
		addConflictGroup(HackConflictGroup.COMBAT_TARGETING);
		addSetting(targetRange);
		addSetting(placeInterval);
		addSetting(webCount);
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
			&& !TpCombatUtil.teleportNear("GcoreWebAura", target.position(),
				1.5, 3.5))
			return;

		nextAction = now + placeInterval.getValueI();

		Vec3 targetPos = target.position();
		List<BlockPos> spots =
			TpCombatUtil.findFreeSpots(targetPos, 1, 2.2);
		if(spots.isEmpty())
			return;

		spots.sort(Comparator.comparingDouble(
			pos -> Vec3.atCenterOf(pos).distanceToSqr(targetPos)));

		int placed = 0;
		for(BlockPos pos : spots)
		{
			if(placed >= webCount.getValueI())
				break;

			// Do not web our own feet.
			if(MC.player.getBoundingBox().intersects(new AABB(pos)))
				continue;

			if(TpCombatUtil.placeBlock(pos, Items.COBWEB))
			{
				placed++;
				if(swingHand.isChecked())
					TpCombatUtil.swing();
			}
		}
	}
}
