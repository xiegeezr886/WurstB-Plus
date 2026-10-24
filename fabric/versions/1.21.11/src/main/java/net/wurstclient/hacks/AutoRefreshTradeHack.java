/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from LeavesHack (AGPL-3.0).
package net.wurstclient.hacks;

import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.BlockBreaker;
import net.wurstclient.util.BlockPlacer;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.ChatUtils;
import net.wurstclient.util.InventoryUtils;
import net.wurstclient.util.RotationUtils;

/**
 * Refreshes villager trades by breaking and replacing the villager's
 * workstation (lectern) while its trade screen is open.
 */
@SearchTags({"auto refresh trade", "trade refresh", "villager", "lectern",
	"librarian"})
public final class AutoRefreshTradeHack extends Hack implements UpdateListener
{
	private final SliderSetting range = new SliderSetting("Range",
		"How far away a villager can be.", 5, 1, 12, 0.5, ValueDisplay.DECIMAL);

	private final SliderSetting wallRange = new SliderSetting("Wall range",
		"How far away a villager can be without line of sight.", 5, 1, 12,
		0.5, ValueDisplay.DECIMAL);

	private final SliderSetting waitMine = new SliderSetting("Wait mine",
		"How long to wait after breaking the workstation before placing it"
			+ " again.", 5000, 0, 10000, 100,
		ValueDisplay.INTEGER.withSuffix(" ms"));

	private final CheckboxSetting onlyLibrarians = new CheckboxSetting(
		"Only librarians", "Only trade with librarian villagers.", true);

	private final CheckboxSetting stopOnBook = new CheckboxSetting(
		"Stop on book",
		"Stop refreshing once a trade offers an enchanted book at or above"
			+ " the minimum level.", true);

	private final SliderSetting minLevel = new SliderSetting("Min level",
		"Minimum enchantment level that counts as a wanted book.", 1, 1, 5, 1,
		ValueDisplay.INTEGER);

	private Phase phase = Phase.IDLE;
	private Villager target;
	private BlockPos workstation;
	private long waitUntil;
	private int cooldown;
	private int placeAttempts;

	public AutoRefreshTradeHack()
	{
		super("AutoRefreshTrade");
		setCategory(Category.ITEMS);
		addSetting(range);
		addSetting(wallRange);
		addSetting(waitMine);
		addSetting(onlyLibrarians);
		addSetting(stopOnBook);
		addSetting(minLevel);
	}

	@Override
	protected void onEnable()
	{
		reset();
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		if(MC.player != null && MC.screen instanceof MerchantScreen)
			MC.player.closeContainer();
		reset();
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null || MC.gameMode == null)
		{
			reset();
			return;
		}

		if(cooldown > 0)
			cooldown--;

		if(MC.screen != null)
		{
			if(MC.screen instanceof MerchantScreen screen)
				handleTradeScreen(screen);
			return;
		}

		switch(phase)
		{
			case IDLE -> openTradeScreen();
			case BREAKING -> breakWorkstation();
			case WAITING -> waitForWorkstation();
			case PLACING -> placeWorkstation();
		}
	}

	private void handleTradeScreen(MerchantScreen screen)
	{
		if(target == null || target.isRemoved())
			target = findVillager();

		if(stopOnBook.isChecked())
		{
			int level = findWantedBookLevel(screen.getMenu().getOffers());
			if(level > 0)
			{
				ChatUtils.message("Found a level " + level
					+ " enchanted book. Stopping AutoRefreshTrade.");
				setEnabled(false);
				return;
			}
		}

		MC.player.closeContainer();
		cooldown = 5;

		if(workstation == null || BlockUtils.getBlock(workstation) != Blocks.LECTERN)
			workstation = findWorkstation();

		if(workstation == null)
		{
			ChatUtils.warning(
				"Couldn't find the villager's lectern. Move closer to it.");
			setEnabled(false);
			return;
		}

		phase = Phase.BREAKING;
	}

	private void openTradeScreen()
	{
		if(cooldown > 0)
			return;

		if(target == null || target.isRemoved() || target.getHealth() <= 0)
			target = findVillager();

		if(target == null)
			return;

		if(MC.player.distanceToSqr(target) > range.getValueSq())
		{
			target = null;
			return;
		}

		WURST.getRotationFaker()
			.faceVectorPacket(target.getBoundingBox().getCenter());
		MC.gameMode.interact(MC.player, target, InteractionHand.MAIN_HAND);
		cooldown = 10;
	}

	private void breakWorkstation()
	{
		if(workstation == null
			|| BlockUtils.getBlock(workstation) != Blocks.LECTERN)
		{
			phase = Phase.WAITING;
			waitUntil = System.currentTimeMillis() + waitMine.getValueI();
			return;
		}

		double distanceSq = RotationUtils.getEyesPos()
			.distanceToSqr(Vec3.atCenterOf(workstation));
		if(distanceSq > 4.5 * 4.5)
		{
			ChatUtils.warning("The lectern is out of reach.");
			setEnabled(false);
			return;
		}

		BlockBreaker.breakOneBlock(workstation);
	}

	private void waitForWorkstation()
	{
		if(System.currentTimeMillis() >= waitUntil)
			phase = Phase.PLACING;
	}

	private void placeWorkstation()
	{
		if(workstation == null)
		{
			phase = Phase.IDLE;
			return;
		}

		if(!BlockUtils.getState(workstation).canBeReplaced())
		{
			phase = Phase.IDLE;
			cooldown = 20;
			placeAttempts = 0;
			return;
		}

		if(!MC.player.getMainHandItem().is(Items.LECTERN))
		{
			InventoryUtils.selectItem(Items.LECTERN, 36);
			return;
		}

		if(BlockPlacer.place(workstation, false, false))
		{
			MC.player.swing(InteractionHand.MAIN_HAND);
			phase = Phase.IDLE;
			cooldown = 20;
			placeAttempts = 0;
			return;
		}

		if(++placeAttempts > 40)
		{
			ChatUtils.warning("Couldn't place the lectern again.");
			setEnabled(false);
		}
	}

	private Villager findVillager()
	{
		Villager best = null;
		double bestDistance = Double.MAX_VALUE;
		double rangeSq = range.getValueSq();
		double wallRangeSq = wallRange.getValueSq();

		for(Entity entity : MC.level.entitiesForRendering())
		{
			if(!(entity instanceof Villager villager)
				|| villager.isRemoved() || villager.getHealth() <= 0
				|| villager.isBaby())
				continue;

			double distanceSq = MC.player.distanceToSqr(villager);
			if(distanceSq > rangeSq)
				continue;

			if(distanceSq > wallRangeSq
				&& !MC.player.hasLineOfSight(villager))
				continue;

			if(onlyLibrarians.isChecked() && !villager.getVillagerData()
				.profession().is(VillagerProfession.LIBRARIAN))
				continue;

			if(distanceSq < bestDistance)
			{
				bestDistance = distanceSq;
				best = villager;
			}
		}

		return best;
	}

	private BlockPos findWorkstation()
	{
		BlockPos center = target != null ? target.blockPosition()
			: MC.player.blockPosition();
		Vec3 eyes = RotationUtils.getEyesPos();
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;

		for(int x = -4; x <= 4; x++)
			for(int y = -2; y <= 2; y++)
				for(int z = -4; z <= 4; z++)
				{
					BlockPos pos = center.offset(x, y, z);
					if(BlockUtils.getBlock(pos) != Blocks.LECTERN)
						continue;

					if(eyes.distanceToSqr(Vec3.atCenterOf(pos)) > 4.5 * 4.5)
						continue;

					double distance = center.distSqr(pos);
					if(distance < bestDistance)
					{
						bestDistance = distance;
						best = pos;
					}
				}

		return best;
	}

	private int findWantedBookLevel(MerchantOffers offers)
	{
		int minimum = minLevel.getValueI();

		for(MerchantOffer offer : offers)
		{
			ItemStack result = offer.getResult();
			if(!result.is(Items.ENCHANTED_BOOK))
				continue;

			ItemEnchantments enchantments = result.getOrDefault(
				DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
			for(var entry : enchantments.entrySet())
				if(entry.getIntValue() >= minimum)
					return entry.getIntValue();
		}

		return -1;
	}

	private void reset()
	{
		phase = Phase.IDLE;
		target = null;
		workstation = null;
		waitUntil = 0;
		cooldown = 0;
		placeAttempts = 0;
	}

	private enum Phase
	{
		IDLE,
		BREAKING,
		WAITING,
		PLACING
	}
}
