/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from InvincibleMachineGun (GPL-3.0).
package net.wurstclient.hacks;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;

import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.ItemListSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.BaritoneUtils;
import net.wurstclient.util.ChatUtils;
import net.wurstclient.util.InventoryUtils;
import net.wurstclient.util.chunk.ChunkUtils;
import net.wurstclient.util.text.WText;

/**
 * Automatically walks between a supply barrel, a dump chest and nearby
 * villagers, restocking emeralds and executing matching trades.
 *
 * <p>
 * Ported from InvincibleMachineGun's VillagerTrader. The second trade profile,
 * shulker-box goods restocking, the hard-coded work schedule and the Baritone
 * path-event listener are not reproduced. This port uses Wurst's reflective
 * Baritone bridge, trades at any time of day, and keeps one buy/sell profile.
 */
@SearchTags({"auto villager trade", "villager trader", "trade bot",
	"villager trading", "emerald farm"})
public final class VillagerTraderHack extends Hack implements UpdateListener
{
	private final CheckboxSetting debug = new CheckboxSetting("Debug",
		"Prints state transitions and distances to chat.", false);

	private final SliderSetting supplyQty = new SliderSetting("Supply stacks",
		"How many stacks of emeralds to stock before trading.", 5, 1, 18, 1,
		ValueDisplay.INTEGER);

	private final SliderSetting supplyRange = new SliderSetting(
		"Container range", "How far away supply and dump containers are searched.",
		8, 6, 20, 1, ValueDisplay.INTEGER.withSuffix(" blocks"));

	private final SliderSetting minDistance = new SliderSetting("Reach",
		"How close to stand before opening a villager or container.", 2, 2, 3,
		1, ValueDisplay.INTEGER.withSuffix(" blocks"));

	private final EnumSetting<PriceMode> priceMode = new EnumSetting<>(
		"Price mode",
		"One emerald only accepts trades whose displayed price is 1. Any price"
			+ " accepts inflated prices too.",
		PriceMode.values(), PriceMode.ONE_EMERALD);

	private final SliderSetting cooldown = new SliderSetting("Restock cooldown",
		"Seconds before an out-of-stock villager is checked again.", 30, 10, 120,
		1, ValueDisplay.INTEGER.withSuffix(" s"));

	private final SliderSetting maxTrades = new SliderSetting("Max trades",
		"How many times one villager may be bought out per day.", 3, 1, 4, 1,
		ValueDisplay.INTEGER);

	private final EnumSetting<VillagerType> villagerType = new EnumSetting<>(
		"Villager type", "Only villagers with this profession are traded with.",
		VillagerType.values(), VillagerType.FARMER);

	private final ItemListSetting sellItems = new ItemListSetting(
		"Sell items", WText.literal(
			"Items to sell to villagers for emeralds."),
		"minecraft:rotten_flesh");

	private final ItemListSetting buyItems = new ItemListSetting("Buy items",
		WText.literal("Items to buy from villagers with emeralds."));

	private final SliderSetting delay = new SliderSetting("Delay",
		"Ticks between state machine actions.", 10, 1, 40, 1,
		ValueDisplay.INTEGER.withSuffix(" ticks"));

	private final SliderSetting clickDelay = new SliderSetting("Click delay",
		"Ticks between container or trade clicks.", 1, 0, 10, 1,
		ValueDisplay.INTEGER.withSuffix(" ticks"));

	private final SliderSetting windowDelay = new SliderSetting("Window delay",
		"Ticks to wait for a container screen to open.", 5, 1, 20, 1,
		ValueDisplay.INTEGER.withSuffix(" ticks"));

	private final Map<UUID, TradeRecord> tradeRecords = new HashMap<>();

	private Step step = Step.WAIT;
	private BlockPos supplyPos;
	private BlockPos dumpPos;
	private BlockPos openPos;
	private BlockPos targetVillagerPos;
	private Villager targetVillager;
	private int timer;
	private int windowTimer;
	private long lastDay = -1;
	private int selectedOffer = -1;
	private boolean anyTradeThisSession;
	private boolean idleReported;

	public VillagerTraderHack()
	{
		super("VillagerTrader");
		setCategory(Category.ITEMS);
		addSetting(debug);
		addSetting(supplyQty);
		addSetting(supplyRange);
		addSetting(minDistance);
		addSetting(priceMode);
		addSetting(cooldown);
		addSetting(maxTrades);
		addSetting(villagerType);
		addSetting(sellItems);
		addSetting(buyItems);
		addSetting(delay);
		addSetting(clickDelay);
		addSetting(windowDelay);
	}

	@Override
	protected void onEnable()
	{
		resetState();
		tradeRecords.clear();
		lastDay = -1;
		EVENTS.add(UpdateListener.class, this);

		if(MC.player != null && MC.level != null)
		{
			scanContainers();
			if(!BaritoneUtils.IS_AVAILABLE)
				ChatUtils.warning("Baritone is not available. VillagerTrader"
					+ " will only work on targets that are already in reach.");
		}
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		BaritoneUtils.stop();

		if(MC.player != null && MC.player.containerMenu != MC.player.inventoryMenu)
			MC.player.closeContainer();

		resetState();
	}

	private void resetState()
	{
		step = Step.WAIT;
		supplyPos = null;
		dumpPos = null;
		openPos = null;
		targetVillager = null;
		targetVillagerPos = null;
		timer = 0;
		windowTimer = 0;
		selectedOffer = -1;
		anyTradeThisSession = false;
		idleReported = false;
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null || MC.gameMode == null)
			return;

		resetRecordsAtDawn();

		if(MC.player.containerMenu != MC.player.inventoryMenu)
		{
			if(timer > 0)
			{
				timer--;
				return;
			}

			handleOpenMenu(MC.player.containerMenu);
			return;
		}

		if(timer > 0)
		{
			timer--;
			return;
		}

		switch(step)
		{
			case WAIT -> chooseAction();
			case TO_SUPPLY -> moveToContainer(true);
			case TO_DUMP -> moveToContainer(false);
			case TO_VILLAGER -> moveToVillager();
			case OPENING -> waitForWindow();
		}
	}

	private void resetRecordsAtDawn()
	{
		long day = MC.level.getOverworldClockTime() / 24000L;
		long timeOfDay = MC.level.getOverworldClockTime() % 24000L;

		if(lastDay == -1)
			lastDay = day;

		if(day != lastDay || timeOfDay < 100L)
		{
			if(day != lastDay)
				tradeRecords.clear();
			lastDay = day;
		}
	}

	private void chooseAction()
	{
		scanContainers();

		boolean needEmeralds = !buyItems.getItemNames().isEmpty()
			&& InventoryUtils.count(Items.EMERALD, 36) < Math.max(12,
				supplyQty.getValueI() * 64);

		if(needEmeralds && supplyPos != null)
		{
			debug("Going to the supply container.");
			step = Step.TO_SUPPLY;
			return;
		}

		if(freeInventorySlots() <= 2 && dumpPos != null
			&& !buyItems.getItemNames().isEmpty())
		{
			debug("Inventory is full, going to the dump container.");
			step = Step.TO_DUMP;
			return;
		}

		Villager villager = findReadyVillager();
		if(villager == null)
		{
			if(!idleReported)
			{
				ChatUtils.message(
					"VillagerTrader: no ready villager nearby, waiting.");
				idleReported = true;
			}
			return;
		}

		idleReported = false;
		targetVillager = villager;
		targetVillagerPos = villager.blockPosition();
		step = Step.TO_VILLAGER;
	}

	private void moveToContainer(boolean supply)
	{
		BlockPos pos = supply ? supplyPos : dumpPos;
		if(pos == null)
		{
			step = Step.WAIT;
			return;
		}

		if(!isNear(pos))
		{
			walkTo(pos);
			return;
		}

		BaritoneUtils.stop();
		debug("Opening " + (supply ? "supply" : "dump") + " container.");
		openPos = pos;
		openBlock(pos);
		step = Step.OPENING;
		windowTimer = windowDelay.getValueI();
	}

	private void moveToVillager()
	{
		if(targetVillager == null || targetVillager.isRemoved()
			|| targetVillager.getHealth() <= 0)
		{
			step = Step.WAIT;
			return;
		}

		if(MC.player.distanceToSqr(targetVillager) > Math.pow(
			minDistance.getValue() + 1.5, 2))
		{
			walkTo(targetVillager.blockPosition());
			return;
		}

		BaritoneUtils.stop();
		debug("Opening trade screen.");
		MC.gameMode.interact(MC.player, targetVillager, new net.minecraft.world.phys.EntityHitResult(targetVillager), InteractionHand.MAIN_HAND);
		step = Step.OPENING;
		windowTimer = windowDelay.getValueI();
	}

	private void waitForWindow()
	{
		if(MC.player.containerMenu != MC.player.inventoryMenu)
			return;

		if(--windowTimer <= 0)
		{
			debug("Timed out waiting for a window.");
			step = Step.WAIT;
			timer = 20;
		}
	}

	private void handleOpenMenu(AbstractContainerMenu menu)
	{
		if(menu instanceof MerchantMenu merchant)
		{
			handleMerchant(merchant);
			return;
		}

		if(menu instanceof ChestMenu || menu instanceof ShulkerBoxMenu)
		{
			handleContainer(menu);
			return;
		}

		MC.player.closeContainer();
		step = Step.WAIT;
	}

	private void handleContainer(AbstractContainerMenu menu)
	{
		int containerSlots = Math.max(0, menu.slots.size() - 36);
		boolean supply = openPos != null && openPos.equals(supplyPos);

		if(supply)
		{
			int targetEmeralds = Math.max(12, supplyQty.getValueI() * 64);

			if(InventoryUtils.count(Items.EMERALD, 36) >= targetEmeralds)
			{
				closeAndWait();
				return;
			}

			for(int i = 0; i < containerSlots; i++)
			{
				ItemStack stack = menu.getSlot(i).getItem();
				if(!stack.isEmpty() && stack.is(Items.EMERALD))
				{
					IMC.getInteractionManager()
						.windowClick_QUICK_MOVE(i);
					timer = clickDelay.getValueI();
					return;
				}
			}

			ChatUtils.warning("VillagerTrader: the supply container is out of"
				+ " emeralds.");
			closeAndWait();
			return;
		}

		for(int i = containerSlots; i < menu.slots.size(); i++)
		{
			ItemStack stack = menu.getSlot(i).getItem();
			if(!stack.isEmpty() && isBuyItem(stack.getItem()))
			{
				IMC.getInteractionManager().windowClick_QUICK_MOVE(i);
				timer = clickDelay.getValueI();
				return;
			}
		}

		closeAndWait();
	}

	private void handleMerchant(MerchantMenu menu)
	{
		if(!(MC.screen instanceof MerchantScreen))
			return;

		if(selectedOffer != -1)
		{
			ItemStack result = menu.getSlot(2).getItem();
			if(!result.isEmpty())
			{
				MC.gameMode.handleContainerInput(menu.containerId, 2, 0,
					ContainerInput.QUICK_MOVE, MC.player);
				anyTradeThisSession = true;
				incrementTrades();
				timer = clickDelay.getValueI();
				return;
			}

			selectedOffer = -1;
			timer = 1;
			return;
		}

		int index = findMatchingOffer(menu.getOffers());
		if(index == -1)
		{
			finishSession(anyTradeThisSession);
			return;
		}

		selectedOffer = index;
		menu.setSelectionHint(index);
		menu.tryMoveItems(index);
		MC.getConnection().send(new ServerboundSelectTradePacket(index));
		timer = 1;
	}

	private int findMatchingOffer(MerchantOffers offers)
	{
		for(int i = 0; i < offers.size(); i++)
		{
			MerchantOffer offer = offers.get(i);

			if(offer.isOutOfStock() || offer.getUses() >= offer.getMaxUses())
				continue;

			ItemStack cost = offer.getCostA();
			ItemStack result = offer.getResult();

			boolean selling = isSellItem(cost.getItem())
				&& result.is(Items.EMERALD);
			boolean buying = cost.is(Items.EMERALD)
				&& isBuyItem(result.getItem());

			if(!selling && !buying)
				continue;

			if(buying && priceMode.getSelected() == PriceMode.ONE_EMERALD
				&& cost.getCount() > 1)
				continue;

			return i;
		}

		return -1;
	}

	private void finishSession(boolean traded)
	{
		if(targetVillager != null)
		{
			TradeRecord record = tradeRecords.computeIfAbsent(
				targetVillager.getUUID(), uuid -> new TradeRecord());

			record.lastTrade = System.currentTimeMillis();
			if(!traded)
				debug("No matching trade, cooling down.");
		}

		selectedOffer = -1;
		anyTradeThisSession = false;
		MC.player.closeContainer();
		step = Step.WAIT;
		timer = delay.getValueI();
	}

	private void incrementTrades()
	{
		if(targetVillager == null)
			return;

		TradeRecord record =
			tradeRecords.computeIfAbsent(targetVillager.getUUID(),
				uuid -> new TradeRecord());
		record.trades++;
		record.lastTrade = System.currentTimeMillis();
	}

	private Villager findReadyVillager()
	{
		long now = System.currentTimeMillis();
		long cooldownMs = cooldown.getValueI() * 1000L;
		VillagerType type = villagerType.getSelected();

		return StreamSupport
			.stream(MC.level.entitiesForRendering().spliterator(), false)
			.filter(Villager.class::isInstance).map(Villager.class::cast)
			.filter(v -> !v.isRemoved() && v.getHealth() > 0)
			.filter(v -> Math.abs(v.getY() - MC.player.getY()) <= 2.0)
			.filter(type::matches)
			.filter(v ->
			{
				TradeRecord record = tradeRecords.get(v.getUUID());
				if(record == null)
					return true;

				return record.trades < maxTrades.getValueI()
					&& now - record.lastTrade > cooldownMs;
			})
			.min(Comparator.comparingDouble(MC.player::distanceToSqr))
			.orElse(null);
	}

	private void scanContainers()
	{
		supplyPos = findNearest(BarrelBlockEntity.class);
		dumpPos = findNearest(ChestBlockEntity.class);
	}

	private BlockPos findNearest(Class<? extends BlockEntity> type)
	{
		double rangeSq = supplyRange.getValueSq();

		return ChunkUtils.getLoadedBlockEntities()
			.filter(type::isInstance)
			.filter(be -> be.getBlockPos().distSqr(MC.player.blockPosition())
				<= rangeSq)
			.min(Comparator.comparingDouble(
				be -> be.getBlockPos().distSqr(MC.player.blockPosition())))
			.map(BlockEntity::getBlockPos).orElse(null);
	}

	private void openBlock(BlockPos pos)
	{
		IMC.getInteractionManager().rightClickBlock(pos, Direction.UP,
			Vec3.atCenterOf(pos));
	}

	private void walkTo(BlockPos pos)
	{
		if(BaritoneUtils.IS_AVAILABLE)
			BaritoneUtils.walkTo(pos);
	}

	private boolean isNear(BlockPos pos)
	{
		double reach = minDistance.getValue() + 1.5;
		return Vec3.atCenterOf(pos)
			.distanceToSqr(MC.player.position()) <= reach * reach;
	}

	private int freeInventorySlots()
	{
		int free = 0;
		for(int i = 0; i < 36; i++)
			if(MC.player.getInventory().getItem(i).isEmpty())
				free++;

		return free;
	}

	private boolean isSellItem(Item item)
	{
		return sellItems.getItemNames().contains(
			BuiltInRegistries.ITEM.getKey(item).toString());
	}

	private boolean isBuyItem(Item item)
	{
		return buyItems.getItemNames().contains(
			BuiltInRegistries.ITEM.getKey(item).toString());
	}

	private void closeAndWait()
	{
		MC.player.closeContainer();
		step = Step.WAIT;
		timer = delay.getValueI();
		openPos = null;
	}

	private void debug(String message)
	{
		if(debug.isChecked())
			ChatUtils.message("VillagerTrader: " + message);
	}

	private static final class TradeRecord
	{
		private int trades;
		private long lastTrade;
	}

	private enum Step
	{
		WAIT,
		TO_SUPPLY,
		TO_DUMP,
		TO_VILLAGER,
		OPENING
	}

	private enum PriceMode
	{
		ONE_EMERALD("One emerald"),
		ANY_PRICE("Any price");

		private final String name;

		private PriceMode(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	private enum VillagerType
	{
		FARMER("Farmer", VillagerProfession.FARMER),
		LIBRARIAN("Librarian", VillagerProfession.LIBRARIAN),
		TOOLSMITH("Toolsmith", VillagerProfession.TOOLSMITH),
		WEAPONSMITH("Weaponsmith", VillagerProfession.WEAPONSMITH),
		ARMORER("Armorer", VillagerProfession.ARMORER),
		CARTOGRAPHER("Cartographer", VillagerProfession.CARTOGRAPHER),
		CLERIC("Cleric", VillagerProfession.CLERIC),
		FLETCHER("Fletcher", VillagerProfession.FLETCHER),
		LEATHERWORKER("Leatherworker", VillagerProfession.LEATHERWORKER),
		MASON("Mason", VillagerProfession.MASON),
		SHEPHERD("Shepherd", VillagerProfession.SHEPHERD),
		BUTCHER("Butcher", VillagerProfession.BUTCHER),
		FISHERMAN("Fisherman", VillagerProfession.FISHERMAN),
		NITWIT("Nitwit", VillagerProfession.NITWIT),
		NONE("Any", null);

		private final String name;
		private final ResourceKey<VillagerProfession> profession;

		private VillagerType(String name, ResourceKey<VillagerProfession> profession)
		{
			this.name = name;
			this.profession = profession;
		}

		private boolean matches(Villager villager)
		{
			return profession == null
				|| villager.getVillagerData().profession().is(profession);
		}

		@Override
		public String toString()
		{
			return name;
		}
	}
}
