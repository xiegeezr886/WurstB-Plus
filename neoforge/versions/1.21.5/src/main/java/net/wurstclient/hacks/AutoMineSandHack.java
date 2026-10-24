/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from FO (CC0-1.0).
package net.wurstclient.hacks;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.ItemListSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.TextFieldSetting;
import net.wurstclient.util.BaritoneUtils;
import net.wurstclient.util.BlockBreaker;
import net.wurstclient.util.ChatUtils;
import net.wurstclient.util.InventoryUtils;
import net.wurstclient.util.RenderUtils;
import net.wurstclient.util.chunk.ChunkUtils;

/**
 * Automatically mines sand, restocks shovels, food and totems from a named
 * supply shulker box, and stores full sand stacks in another shulker box.
 *
 * <p>
 * Simplified from FO's AutoMineSand: the extensive init-scan/fingerprint
 * state machine, AutoTrash linkage, precise standing-spot pathing and the
 * Nuker packet-limit loop are not reproduced. Baritone mining is requested via
 * Wurst's reflective bridge; Nuker mode breaks reachable sand directly.
 */
@SearchTags({"auto mine sand", "sand miner", "sand farm", "shulker supply",
	"fo auto mine sand"})
public final class AutoMineSandHack extends Hack
	implements UpdateListener, RenderListener
{
	private static final List<net.minecraft.world.item.Item> SHOVELS =
		List.of(Items.NETHERITE_SHOVEL, Items.DIAMOND_SHOVEL, Items.IRON_SHOVEL,
			Items.STONE_SHOVEL, Items.WOODEN_SHOVEL);

	private final SliderSetting searchRadius = new SliderSetting(
		"Search radius", "Radius for sand and shulker box searches.", 32, 4, 64,
		1, ValueDisplay.INTEGER.withSuffix(" blocks"));

	private final CheckboxSetting autoKillAura = new CheckboxSetting(
		"Enable FoKillAura", "Turns FoKillAura on together with this module.",
		true);

	private final SliderSetting delay = new SliderSetting("Delay",
		"Ticks between state machine operations.", 5, 0, 40, 1,
		ValueDisplay.INTEGER.withSuffix(" ticks"));

	private final CheckboxSetting nukerMode = new CheckboxSetting("Nuker mode",
		"Breaks sand within reach instead of using Baritone.", false);

	private final SliderSetting nukerRange = new SliderSetting("Nuker range",
		"Maximum distance for Nuker mode.", 4, 1, 6, 1,
		ValueDisplay.INTEGER.withSuffix(" blocks"));

	private final SliderSetting nukerMinDy = new SliderSetting("Nuker min Y",
		"Lowest relative Y offset mined in Nuker mode.", -6, -6, 6, 1,
		ValueDisplay.INTEGER);

	private final SliderSetting nukerMaxDy = new SliderSetting("Nuker max Y",
		"Highest relative Y offset mined in Nuker mode.", 6, -6, 6, 1,
		ValueDisplay.INTEGER);

	private final TextFieldSetting supplyBoxName = new TextFieldSetting(
		"Supply box name",
		"Shulker boxes whose custom name contains this text are treated as the"
			+ " supply box. Other shulker boxes are used for storage.",
		"FO");

	private final SliderSetting shovelDurability = new SliderSetting(
		"Shovel durability",
		"Restock a shovel when its remaining durability is at or below this"
			+ " value.",
		10, 1, 100, 1, ValueDisplay.INTEGER);

	private final ItemListSetting foodItems = new ItemListSetting("Food items",
		"Food restocked from the supply box.",
		"minecraft:golden_carrot", "minecraft:cooked_beef", "minecraft:bread");

	private final SliderSetting totemTarget = new SliderSetting("Totem target",
		"How many totems to keep in the inventory.", 3, 1, 10, 1,
		ValueDisplay.INTEGER);

	private final CheckboxSetting autoStore = new CheckboxSetting("Auto store",
		"Stores full sand stacks when the inventory is nearly full.", true);

	private final SliderSetting storeEmptySlots = new SliderSetting(
		"Store empty slots",
		"Start storing when the number of free main inventory slots reaches"
			+ " this value.",
		2, 0, 9, 1, ValueDisplay.INTEGER);

	private final CheckboxSetting highlightBoxes = new CheckboxSetting(
		"Highlight boxes", "Draws boxes around shulker boxes.", true);

	private final SliderSetting renderDistance = new SliderSetting(
		"Render distance", "Maximum shulker highlight distance.", 64, 8, 128, 1,
		ValueDisplay.INTEGER.withSuffix(" blocks"));

	private final ColorSetting supplyColor = new ColorSetting("Supply color",
		"Highlight color of the supply box.", new Color(0, 255, 0, 90));

	private final ColorSetting storeColor = new ColorSetting("Store color",
		"Highlight color of storage boxes.", new Color(255, 255, 0, 90));

	private State state = State.MINING;
	private BlockPos supplyPos;
	private BlockPos storePos;
	private BlockPos openPos;
	private BlockPos nukerTarget;
	private int timer;
	private int openTimer;
	private boolean miningRequested;
	private boolean auraForced;
	private boolean warnedNoSupply;

	public AutoMineSandHack()
	{
		super("AutoMineSand");
		setCategory(Category.BLOCKS);
		addSetting(searchRadius);
		addSetting(autoKillAura);
		addSetting(delay);
		addSetting(nukerMode);
		addSetting(nukerRange);
		addSetting(nukerMinDy);
		addSetting(nukerMaxDy);
		addSetting(supplyBoxName);
		addSetting(shovelDurability);
		addSetting(foodItems);
		addSetting(totemTarget);
		addSetting(autoStore);
		addSetting(storeEmptySlots);
		addSetting(highlightBoxes);
		addSetting(renderDistance);
		addSetting(supplyColor);
		addSetting(storeColor);

		nukerRange.visibleWhen(nukerMode::isChecked);
		nukerMinDy.visibleWhen(nukerMode::isChecked);
		nukerMaxDy.visibleWhen(nukerMode::isChecked);
	}

	@Override
	protected void onEnable()
	{
		reset();

		if(!nukerMode.isChecked() && !BaritoneUtils.IS_AVAILABLE)
		{
			ChatUtils.error("AutoMineSand: Baritone is not available. Enable"
				+ " Nuker mode or install Baritone.");
			setEnabled(false);
			return;
		}

		if(autoKillAura.isChecked()
			&& !WURST.getHax().foKillAuraHack.isEnabled())
		{
			WURST.getHax().foKillAuraHack.setEnabled(true);
			auraForced = true;
		}

		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(RenderListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		BaritoneUtils.stop();

		if(MC.player != null && MC.player.containerMenu != MC.player.inventoryMenu)
			MC.player.closeContainer();

		if(auraForced && WURST.getHax().foKillAuraHack.isEnabled())
			WURST.getHax().foKillAuraHack.setEnabled(false);

		reset();
	}

	private void reset()
	{
		state = State.MINING;
		supplyPos = null;
		storePos = null;
		openPos = null;
		nukerTarget = null;
		timer = 0;
		openTimer = 0;
		miningRequested = false;
		auraForced = false;
		warnedNoSupply = false;
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null || MC.gameMode == null)
			return;

		if(MC.player.isUsingItem())
			return;

		if(MC.screen instanceof ShulkerBoxScreen)
		{
			handleShulkerScreen();
			return;
		}

		if(timer > 0)
		{
			timer--;
			return;
		}

		switch(state)
		{
			case MINING -> tickMining();
			case TO_SUPPLY -> tickGoingToSupply();
			case OPEN_SUPPLY -> tickWaiting(State.TO_SUPPLY);
			case TO_STORE -> tickGoingToStore();
			case OPEN_STORE -> tickWaiting(State.TO_STORE);
		}
	}

	private void tickMining()
	{
		miningRequested = false;

		if(needSupply() && findSupply() != null)
		{
			state = State.TO_SUPPLY;
			return;
		}

		if(needStore() && findStore() != null)
		{
			state = State.TO_STORE;
			return;
		}

		equipShovel();

		if(nukerMode.isChecked())
		{
			if(nukerTarget == null || !isSand(nukerTarget))
				nukerTarget = findNearestSand(nukerRange.getValueI());

			if(nukerTarget != null)
				BlockBreaker.breakOneBlock(nukerTarget);

			timer = delay.getValueI();
			return;
		}

		if(!BaritoneUtils.isPathing())
		{
			if(BaritoneUtils.startMining(Blocks.SAND, Blocks.RED_SAND))
				miningRequested = true;
			else
			{
				BlockPos nearest =
					findNearestSand(Math.min(searchRadius.getValueI(), 16));
				if(nearest != null)
					BaritoneUtils.walkTo(nearest);
			}
		}

		timer = delay.getValueI();
	}

	private void tickGoingToSupply()
	{
		BlockPos pos = findSupply();
		if(pos == null)
		{
			if(!warnedNoSupply)
			{
				ChatUtils.warning("AutoMineSand: no supply shulker box found.");
				warnedNoSupply = true;
			}
			state = State.MINING;
			return;
		}

		supplyPos = pos;

		if(!isNear(pos, 5))
		{
			if(BaritoneUtils.IS_AVAILABLE)
				BaritoneUtils.walkTo(pos);
			else
				state = State.MINING;
			return;
		}

		BaritoneUtils.stop();
		openPos = pos;
		openShulker(pos);
		state = State.OPEN_SUPPLY;
		openTimer = 20;
	}

	private void tickGoingToStore()
	{
		BlockPos pos = findStore();
		if(pos == null)
		{
			state = State.MINING;
			return;
		}

		storePos = pos;

		if(!isNear(pos, 5))
		{
			if(BaritoneUtils.IS_AVAILABLE)
				BaritoneUtils.walkTo(pos);
			else
				state = State.MINING;
			return;
		}

		BaritoneUtils.stop();
		openPos = pos;
		openShulker(pos);
		state = State.OPEN_STORE;
		openTimer = 20;
	}

	private void tickWaiting(State retry)
	{
		if(--openTimer <= 0)
			state = retry;
	}

	private void handleShulkerScreen()
	{
		AbstractContainerMenu menu = MC.player.containerMenu;
		int containerSlots = Math.max(0, menu.slots.size() - 36);
		boolean supply = openPos != null && openPos.equals(supplyPos);

		if(supply)
		{
			int slot = findSupplyItem(menu, containerSlots);
			if(slot != -1)
			{
				IMC.getInteractionManager().windowClick_QUICK_MOVE(slot);
				timer = delay.getValueI();
				return;
			}

			MC.player.closeContainer();
			state = State.MINING;
			timer = delay.getValueI();
			return;
		}

		int sandSlot = findStoredSand(menu, containerSlots);
		if(sandSlot != -1)
		{
			IMC.getInteractionManager().windowClick_QUICK_MOVE(sandSlot);
			timer = delay.getValueI();
			return;
		}

		MC.player.closeContainer();
		state = State.MINING;
		timer = delay.getValueI();
	}

	private int findSupplyItem(AbstractContainerMenu menu, int containerSlots)
	{
		if(needShovel())
		{
			for(int i = 0; i < containerSlots; i++)
			{
				ItemStack stack = menu.getSlot(i).getItem();
				if(SHOVELS.contains(stack.getItem())
					&& hasEnoughDurability(stack))
					return i;
			}
		}

		if(foodCount() < 1)
		{
			for(int i = 0; i < containerSlots; i++)
			{
				ItemStack stack = menu.getSlot(i).getItem();
				if(isWantedFood(stack))
					return i;
			}
		}

		if(InventoryUtils.count(Items.TOTEM_OF_UNDYING, 36) < totemTarget
			.getValueI())
			for(int i = 0; i < containerSlots; i++)
			{
				ItemStack stack = menu.getSlot(i).getItem();
				if(stack.is(Items.TOTEM_OF_UNDYING))
					return i;
			}

		return -1;
	}

	private int findStoredSand(AbstractContainerMenu menu, int containerSlots)
	{
		for(int i = containerSlots; i < menu.slots.size(); i++)
		{
			ItemStack stack = menu.getSlot(i).getItem();
			if(isSand(stack) && stack.getCount() >= 64)
				return i;
		}

		return -1;
	}

	private boolean needSupply()
	{
		if(findSupply() == null)
			return false;

		if(needShovel())
			return true;

		if(foodCount() < 1)
			return true;

		return InventoryUtils.count(Items.TOTEM_OF_UNDYING, 36) < totemTarget
			.getValueI();
	}

	private boolean needShovel()
	{
		for(int i = 0; i < 36; i++)
		{
			ItemStack stack = MC.player.getInventory().getItem(i);
			if(SHOVELS.contains(stack.getItem())
				&& hasEnoughDurability(stack))
				return false;
		}

		return true;
	}

	private boolean hasEnoughDurability(ItemStack stack)
	{
		return !stack.isDamageableItem()
			|| stack.getMaxDamage() - stack.getDamageValue() > shovelDurability
				.getValueI();
	}

	private void equipShovel()
	{
		if(SHOVELS.contains(MC.player.getMainHandItem().getItem()))
			return;

		int slot = InventoryUtils.indexOf(stack -> SHOVELS
			.contains(stack.getItem()) && hasEnoughDurability(stack), 36);

		if(slot != -1)
			InventoryUtils.selectItem(slot);
	}

	private boolean needStore()
	{
		if(!autoStore.isChecked() || findStore() == null)
			return false;

		int empty = 0;
		for(int i = 9; i < 36; i++)
			if(MC.player.getInventory().getItem(i).isEmpty())
				empty++;

		return empty <= storeEmptySlots.getValueI() && hasFullSandStack();
	}

	private boolean hasFullSandStack()
	{
		for(int i = 0; i < 36; i++)
		{
			ItemStack stack = MC.player.getInventory().getItem(i);
			if(isSand(stack) && stack.getCount() >= 64)
				return true;
		}

		return false;
	}

	private int foodCount()
	{
		int count = 0;
		for(int i = 0; i < 36; i++)
			if(isWantedFood(MC.player.getInventory().getItem(i)))
				count += MC.player.getInventory().getItem(i).getCount();

		return count;
	}

	private boolean isWantedFood(ItemStack stack)
	{
		if(stack.isEmpty())
			return false;

		return foodItems.getItemNames().contains(
			net.minecraft.core.registries.BuiltInRegistries.ITEM
				.getKey(stack.getItem()).toString());
	}

	private BlockPos findSupply()
	{
		return findShulker(true);
	}

	private BlockPos findStore()
	{
		return findShulker(false);
	}

	private BlockPos findShulker(boolean supply)
	{
		double rangeSq = searchRadius.getValueSq();
		String name = supplyBoxName.getValue().toLowerCase();
		BlockPos playerPos = MC.player.blockPosition();

		List<BlockPos> matches = new ArrayList<>();
		for(BlockEntity be : ChunkUtils.getLoadedBlockEntities().toList())
		{
			if(!(be instanceof ShulkerBoxBlockEntity shulker))
				continue;

			BlockPos pos = shulker.getBlockPos();
			if(pos.distSqr(playerPos) > rangeSq)
				continue;

			String custom = shulker.getCustomName() == null ? ""
				: shulker.getCustomName().getString().toLowerCase();
			boolean namedSupply = !name.isEmpty() && custom.contains(name);

			if(supply == namedSupply)
				matches.add(pos);
		}

		if(matches.isEmpty())
			return null;

		return matches.stream()
			.min(java.util.Comparator.comparingDouble(
				pos -> pos.distSqr(playerPos)))
			.orElse(null);
	}

	private BlockPos findNearestSand(int radius)
	{
		BlockPos center = MC.player.blockPosition();
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;

		for(int x = -radius; x <= radius; x++)
			for(int y = nukerMode.isChecked() ? nukerMinDy.getValueI() : -radius;
				y <= (nukerMode.isChecked() ? nukerMaxDy.getValueI() : radius);
				y++)
				for(int z = -radius; z <= radius; z++)
				{
					BlockPos pos = center.offset(x, y, z);
					if(!isSand(pos))
						continue;

					double dist = pos.distSqr(center);
					if(dist < bestDist)
					{
						bestDist = dist;
						best = pos;
					}
				}

		return best;
	}

	private boolean isSand(BlockPos pos)
	{
		return MC.level.getBlockState(pos).getBlock() == Blocks.SAND
			|| MC.level.getBlockState(pos).getBlock() == Blocks.RED_SAND;
	}

	private boolean isSand(ItemStack stack)
	{
		return stack.is(Items.SAND) || stack.is(Items.RED_SAND);
	}

	private boolean isNear(BlockPos pos, double distance)
	{
		return MC.player.position().distanceToSqr(Vec3.atCenterOf(pos)) <= distance
			* distance;
	}

	private void openShulker(BlockPos pos)
	{
		IMC.getInteractionManager().rightClickBlock(pos, Direction.UP,
			Vec3.atCenterOf(pos));
	}

	@Override
	public void onRender(PoseStack matrixStack, float partialTicks)
	{
		if(!highlightBoxes.isChecked() || MC.player == null || MC.level == null)
			return;

		double rangeSq = renderDistance.getValueSq();
		String name = supplyBoxName.getValue().toLowerCase();

		for(BlockEntity be : ChunkUtils.getLoadedBlockEntities().toList())
		{
			if(!(be instanceof ShulkerBoxBlockEntity shulker))
				continue;

			BlockPos pos = shulker.getBlockPos();
			if(MC.player.getEyePosition()
				.distanceToSqr(Vec3.atCenterOf(pos)) > rangeSq)
				continue;

			String custom = shulker.getCustomName() == null ? ""
				: shulker.getCustomName().getString().toLowerCase();
			boolean isSupply = !name.isEmpty() && custom.contains(name);
			int color = isSupply ? supplyColor.getColorI()
				: storeColor.getColorI();

			RenderUtils.drawOutlinedBox(matrixStack, new AABB(pos), color,
				false);
		}
	}

	private enum State
	{
		MINING,
		TO_SUPPLY,
		OPEN_SUPPLY,
		TO_STORE,
		OPEN_STORE
	}
}
