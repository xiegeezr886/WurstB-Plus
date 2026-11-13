/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 *
 * Ported from InvincibleMachineGun (GPL-3.0).
 */
package net.wurstclient.hacks;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.InventoryUtils;

/**
 * Scrapes oxidation off nearby copper blocks with an axe.
 *
 * <p>
 * Unlike the upstream module, this port does not react to manual right-clicks.
 * It scans around the player and only targets blocks that are already at least
 * one oxidation stage away from normal copper.
 */
@SearchTags({"auto deoxidizer", "copper scraper", "deoxidize", "scrape copper",
	"oxidized copper", "weathering"})
public final class AutoDeoxidizerHack extends Hack implements UpdateListener
{
	private final SliderSetting range = new SliderSetting("Radius",
		"How far to search for oxidized copper blocks.", 4, 1, 6, 0.5,
		ValueDisplay.DECIMAL.withSuffix(" blocks"));

	private final SliderSetting delay = new SliderSetting("Delay",
		"Ticks to wait between scraping actions.", 4, 1, 20, 1,
		ValueDisplay.INTEGER.withSuffix(" ticks"));

	private final CheckboxSetting onlyFullyOxidized = new CheckboxSetting(
		"Only fully oxidized",
		"Only scrape blocks in the final oxidation stage. Disable this to also"
			+ " scrape exposed and weathered copper.",
		true);

	private final CheckboxSetting useInventoryAxe = new CheckboxSetting(
		"Use inventory axe",
		"Also takes an axe out of the main inventory when the hotbar has none.",
		true);

	private int timer;

	public AutoDeoxidizerHack()
	{
		super("AutoDeoxidizer");
		setCategory(Category.ITEMS);
		addSetting(range);
		addSetting(delay);
		addSetting(onlyFullyOxidized);
		addSetting(useInventoryAxe);
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

		if(MC.screen != null)
			return;

		if(timer > 0)
		{
			timer--;
			return;
		}

		BlockPos target = findTarget();
		if(target == null)
			return;

		if(!selectAxe())
		{
			timer = delay.getValueI();
			return;
		}

		IMC.getInteractionManager().rightClickBlock(target, Direction.UP,
			Vec3.atCenterOf(target));
		MC.player.swing(InteractionHand.MAIN_HAND);
		timer = delay.getValueI();
	}

	private BlockPos findTarget()
	{
		double rangeSq = range.getValueSq();
		int r = range.getValueCeil();
		BlockPos center = MC.player.blockPosition();
		BlockPos min = center.offset(-r, -r, -r);
		BlockPos max = center.offset(r, r, r);

		BlockPos best = null;
		double bestDistSq = Double.MAX_VALUE;

		for(BlockPos pos : BlockPos.betweenClosed(min, max))
		{
			BlockState state = MC.level.getBlockState(pos);
			if(!(state.getBlock() instanceof WeatheringCopper))
				continue;

			Optional<BlockState> previous =
				WeatheringCopper.getPrevious(state);
			if(previous.isEmpty())
				continue;

			if(onlyFullyOxidized.isChecked()
				&& WeatheringCopper.getNext(state.getBlock()).isPresent())
				continue;

			double distSq = MC.player.distanceToSqr(Vec3.atCenterOf(pos));
			if(distSq > rangeSq || distSq >= bestDistSq)
				continue;

			best = pos.immutable();
			bestDistSq = distSq;
		}

		return best;
	}

	private boolean selectAxe()
	{
		if(MC.player.getMainHandItem().is(ItemTags.AXES))
			return true;

		int slot = InventoryUtils.indexOf(
			stack -> stack.is(ItemTags.AXES), 9, false);

		if(slot == -1 && useInventoryAxe.isChecked())
			slot = InventoryUtils.indexOf(
				stack -> stack.is(ItemTags.AXES), 36, false);

		if(slot == -1)
			return false;

		InventoryUtils.selectItem(slot);
		return MC.player.getMainHandItem().is(ItemTags.AXES);
	}
}
