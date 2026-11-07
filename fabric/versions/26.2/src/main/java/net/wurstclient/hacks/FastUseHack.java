/*
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.Set;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BlockItem;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.TextFieldSetting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.wurstclient.util.RegistryIdFilter;

@SearchTags({"fast use", "fastuse", "instant use"})
public final class FastUseHack extends Hack implements UpdateListener
{
	private static final Set<Item> THROWABLE = Set.of(
		Items.SNOWBALL, Items.EGG, Items.EXPERIENCE_BOTTLE,
		Items.ENDER_EYE, Items.ENDER_PEARL, Items.SPLASH_POTION,
		Items.LINGERING_POTION);

	private final EnumSetting<Mode> mode = new EnumSetting<>("Mode",
		"\u00a7lSingle\u00a7r - Removes use cooldown.\n"
			+ "\u00a7lMulti\u00a7r - Uses items N times per tick.",
		Mode.values(), Mode.SINGLE);

	private final SliderSetting multiCount = new SliderSetting("Multi",
		"How many items to use per tick in Multi mode.", 20, 1, 100, 1,
		ValueDisplay.INTEGER);

	private final CheckboxSetting throwablesOnly = new CheckboxSetting(
		"Throwables only", "Only fast-uses throwable items.", true);

	private final CheckboxSetting xpOnly = new CheckboxSetting("XP only",
		"Only fast-uses XP bottles.", false);
	private final EnumSetting<Scope> scope = new EnumSetting<>("Items",
		"Native uses the throwable filters; All and Selected provide per-hand item filtering.",
		Scope.values(), Scope.NATIVE);
	private final TextFieldSetting selectedItems = new TextFieldSetting("Selected items",
		"Item registry IDs used by Selected mode.", "");
	private final CheckboxSetting blocks = new CheckboxSetting("Blocks",
		"Include all block items in Selected mode.", false);
	private final SliderSetting cooldown = new SliderSetting("Cooldown",
		"Ticks between uses of a matching item.", 0, 0, 4, 1, ValueDisplay.INTEGER);
	private final RegistryIdFilter itemFilter = new RegistryIdFilter();

	public FastUseHack()
	{
		super("FastUse");
		setCategory(Category.ITEMS);
		addSetting(mode);
		addSetting(multiCount);
		addSetting(throwablesOnly);
		addSetting(xpOnly);
		addSetting(scope);
		addSetting(selectedItems);
		addSetting(blocks);
		addSetting(cooldown);
	}

	@Override
	protected void onEnable()
	{
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
		if(MC.player == null || MC.level == null || MC.gui.screen() != null)
			return;

		if(mode.getSelected() == Mode.MULTI
			&& MC.options.keyUse.isDown() && MC.rightClickDelay == 0)
		{
			for(int i = 0; i < multiCount.getValueI(); i++)
			{
				if(!matches(MC.player.getMainHandItem()) && !matches(MC.player.getOffhandItem()))
					break;
				MC.startUseItem();
				if(MC.rightClickDelay > 0)
					break;
			}
		}
	}

	public int getItemUseCooldown(ItemStack stack)
	{
		return isEnabled() && matches(stack) ? cooldown.getValueI() : 4;
	}

	private boolean matches(ItemStack stack)
	{
		Item item = stack.getItem();
		return switch(scope.getSelected())
		{
			case ALL -> true;
			case SELECTED -> blocks.isChecked() && item instanceof BlockItem
				|| itemFilter.contains(selectedItems.getValue(), BuiltInRegistries.ITEM.getKey(item).toString());
			case NATIVE -> !throwablesOnly.isChecked() || THROWABLE.contains(item)
				&& (!xpOnly.isChecked() || item == Items.EXPERIENCE_BOTTLE);
		};
	}

	private enum Scope
	{
		NATIVE, ALL, SELECTED
	}

	private enum Mode
	{
		SINGLE("Single"),
		MULTI("Multi");

		private final String name;

		Mode(String name)
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
