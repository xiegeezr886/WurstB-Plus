/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from InvincibleMachineGun (GPL-3.0).
package net.wurstclient.hacks;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.ItemListSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.ChatUtils;
import net.wurstclient.util.InventoryUtils;
import net.wurstclient.util.text.WText;

/**
 * Sorts the hotbar into one of five saved presets.
 *
 * <p>
 * Each preset stores an ordered list of wanted items for slots 1-9. Slots with
 * an empty list are left untouched. The original chat preview button is
 * preserved as a checkbox that prints the preset and then unchecks itself.
 */
@SearchTags({"auto hotbar", "hotbar preset", "hotbar sorter", "loadout",
	"ada auto hotbar"})
public final class AdaAutoHotbarHack extends Hack implements UpdateListener
{
	private final SliderSetting tickDelay = new SliderSetting("Sort delay",
		"Ticks between item moves.", 2, 0, 10, 1,
		ValueDisplay.INTEGER.withSuffix(" ticks"));

	private final EnumSetting<Mode> mode = new EnumSetting<>("Mode",
		"Fixed uses the selected preset every time. Cycle switches to the next"
			+ " enabled preset when enabled.",
		Mode.values(), Mode.FIXED);

	private final CheckboxSetting fuzzyMatch = new CheckboxSetting("Fuzzy match",
		"Uses the same tiered tool or armor type when the exact item is missing.",
		true);

	private final CheckboxSetting autoDisable = new CheckboxSetting("Auto disable",
		"Turns the module off once the hotbar matches the preset.", true);

	private final EnumSetting<Preset> activePreset = new EnumSetting<>(
		"Active preset", "Preset that is applied when sorting starts.",
		Preset.values(), Preset.PRESET_1);

	private final CheckboxSetting saveNow = new CheckboxSetting(
		"Save current hotbar", "Saves the current hotbar into the active preset.",
		false);

	private final CheckboxSetting previewNow = new CheckboxSetting(
		"Print preset", "Prints the active preset to chat.", false);

	private final List<CheckboxSetting> cycleEnables = new ArrayList<>();
	private final List<List<ItemListSetting>> presetSlots =
		new ArrayList<>();

	private int timer;
	private int currentSlotIndex;
	private boolean sorting;

	public AdaAutoHotbarHack()
	{
		super("AdaAutoHotbar");
		setCategory(Category.ITEMS);
		addSetting(tickDelay);
		addSetting(mode);
		addSetting(fuzzyMatch);
		addSetting(autoDisable);
		addSetting(activePreset);
		addSetting(saveNow);
		addSetting(previewNow);

		for(int i = 0; i < 5; i++)
		{
			int preset = i + 1;
			CheckboxSetting cycle = new CheckboxSetting(
				"Cycle preset " + preset,
				"Include preset " + preset + " in cycle mode.", preset <= 2);
			addSetting(cycle);
			cycleEnables.add(cycle);
		}

		for(int p = 0; p < 5; p++)
		{
			List<ItemListSetting> slots = new ArrayList<>();
			for(int s = 0; s < 9; s++)
			{
				ItemListSetting setting = new ItemListSetting(
					"Preset " + (p + 1) + " slot " + (s + 1),
					WText.literal("Wanted item in hotbar slot " + (s + 1)
						+ " of preset " + (p + 1) + "."));
				addSetting(setting);
				slots.add(setting);
			}
			presetSlots.add(slots);
		}
	}

	@Override
	protected void onEnable()
	{
		if(MC.player == null)
		{
			setEnabled(false);
			return;
		}

		if(mode.getSelected() == Mode.CYCLE)
			cycleNextPreset();

		ChatUtils.message("Applying " + activePreset.getSelected() + ".");
		sorting = true;
		currentSlotIndex = 0;
		timer = 0;
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		sorting = false;
		saveNow.setChecked(false);
		previewNow.setChecked(false);
	}

	@Override
	public void onUpdate()
	{
		if(saveNow.isChecked())
		{
			saveNow.setChecked(false);
			saveHotbarToPreset();
		}

		if(previewNow.isChecked())
		{
			previewNow.setChecked(false);
			previewPreset();
		}

		if(MC.player == null || !sorting)
			return;

		if(timer > 0)
		{
			timer--;
			return;
		}

		List<ItemListSetting> targetSettings =
			presetSlots.get(activePreset.getSelected().ordinal());

		while(currentSlotIndex < 9)
		{
			List<String> preferred = targetSettings.get(currentSlotIndex)
				.getItemNames();

			if(preferred.isEmpty())
			{
				currentSlotIndex++;
				continue;
			}

			Item targetItem = BuiltInRegistries.ITEM
				.get(ResourceLocation.parse(preferred.get(0)));
			if(targetItem == Items.AIR)
			{
				currentSlotIndex++;
				continue;
			}

			if(isSlotCorrectExact(currentSlotIndex, targetItem)
				|| fuzzyMatch.isChecked()
					&& isSlotCorrectFuzzy(currentSlotIndex, targetItem))
			{
				currentSlotIndex++;
				continue;
			}

			if(findAndMoveItem(currentSlotIndex, targetItem))
			{
				timer = tickDelay.getValueI();
				currentSlotIndex++;
				return;
			}

			currentSlotIndex++;
		}

		if(autoDisable.isChecked())
			setEnabled(false);
		else
			sorting = false;
	}

	private void saveHotbarToPreset()
	{
		if(MC.player == null)
			return;

		List<ItemListSetting> slots =
			presetSlots.get(activePreset.getSelected().ordinal());
		StringBuilder message = new StringBuilder("Saved ")
			.append(activePreset.getSelected()).append(": ");

		for(int i = 0; i < 9; i++)
		{
			ItemStack stack = MC.player.getInventory().getItem(i);
			ItemListSetting setting = slots.get(i);
			while(!setting.getItemNames().isEmpty())
				setting.remove(0);

			if(stack.isEmpty())
			{
				message.append("empty, ");
				continue;
			}

			setting.add(stack.getItem());
			message.append(stack.getHoverName().getString()).append(", ");
		}

		ChatUtils.message(message.toString());
	}

	private void previewPreset()
	{
		List<ItemListSetting> slots =
			presetSlots.get(activePreset.getSelected().ordinal());

		ChatUtils.message("--- " + activePreset.getSelected() + " ---");
		for(int i = 0; i < 9; i++)
		{
			List<String> items = slots.get(i).getItemNames();
			String name = items.isEmpty() ? "empty" : items.get(0);
			ChatUtils.message("Slot " + (i + 1) + ": " + name);
		}
	}

	private void cycleNextPreset()
	{
		int current = activePreset.getSelected().ordinal();
		for(int i = 1; i <= 5; i++)
		{
			int next = (current + i) % 5;
			if(cycleEnables.get(next).isChecked())
			{
				activePreset.setSelected(Preset.values()[next]);
				return;
			}
		}
	}

	private boolean isSlotCorrectExact(int slot, Item targetItem)
	{
		return MC.player.getInventory().getItem(slot).is(targetItem);
	}

	private boolean isSlotCorrectFuzzy(int slot, Item targetItem)
	{
		ItemStack stack = MC.player.getInventory().getItem(slot);
		if(stack.isEmpty())
			return false;

		String currentName = BuiltInRegistries.ITEM.getKey(stack.getItem())
			.toString();
		String targetName = BuiltInRegistries.ITEM.getKey(targetItem)
			.toString();

		if(targetName.contains("enchanted_golden_apple")
			&& currentName.contains("golden_apple"))
			return true;

		String type = getItemType(targetName);
		return type != null && currentName.contains(type);
	}

	private boolean findAndMoveItem(int targetSlot, Item targetItem)
	{
		int sourceSlot = InventoryUtils.indexOf(
			stack -> stack.getItem() == targetItem, 36);

		if(sourceSlot == -1 && fuzzyMatch.isChecked())
			sourceSlot = findFuzzyReplacement(targetItem);

		if(sourceSlot == -1 || sourceSlot == targetSlot)
			return false;

		IMC.getInteractionManager().windowClick_SWAP(
			InventoryUtils.toNetworkSlot(sourceSlot), targetSlot);
		return true;
	}

	private int findFuzzyReplacement(Item target)
	{
		String name = BuiltInRegistries.ITEM.getKey(target).toString()
			.toLowerCase();

		if(name.contains("enchanted_golden_apple"))
			return InventoryUtils.indexOf(s -> s.is(Items.GOLDEN_APPLE), 36);

		String type = getItemType(name);
		if(type == null)
			return -1;

		for(String tier : new String[]{"netherite", "diamond", "iron", "golden",
			"stone", "wooden", "chainmail"})
		{
			int slot = InventoryUtils.indexOf(s -> !s.isEmpty()
				&& BuiltInRegistries.ITEM.getKey(s.getItem()).toString()
					.contains(tier + type), 36);
			if(slot != -1)
				return slot;
		}

		return -1;
	}

	private String getItemType(String name)
	{
		for(String type : new String[]{"_sword", "_pickaxe", "_axe", "_shovel",
			"_hoe", "_helmet", "_chestplate", "_leggings", "_boots"})
			if(name.contains(type))
				return type;

		return null;
	}

	private enum Preset
	{
		PRESET_1,
		PRESET_2,
		PRESET_3,
		PRESET_4,
		PRESET_5;

		@Override
		public String toString()
		{
			return "Preset " + (ordinal() + 1);
		}
	}

	private enum Mode
	{
		FIXED("Fixed"),
		CYCLE("Cycle");

		private final String name;

		private Mode(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}
}
