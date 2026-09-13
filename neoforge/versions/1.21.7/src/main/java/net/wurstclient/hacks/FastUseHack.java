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
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

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

	public FastUseHack()
	{
		super("FastUse");
		setCategory(Category.ITEMS);
		addSetting(mode);
		addSetting(multiCount);
		addSetting(throwablesOnly);
		addSetting(xpOnly);
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
		if(throwablesOnly.isChecked()
			&& !(THROWABLE.contains(MC.player.getMainHandItem().getItem())
				&& (!xpOnly.isChecked()
					|| MC.player.getMainHandItem()
						.getItem() == Items.EXPERIENCE_BOTTLE)))
			return;

		// TODO: 26.1.2 - rightClickDelay and startUseItem() are private
		// // MC.rightClickDelay = 0; // TODO: 26.1.2 - rightClickDelay is private
		//
		// if(mode.getSelected() == Mode.MULTI
		// 	&& MC.options.keyUse.isDown())
		// {
		// 	for(int i = 0; i < multiCount.getValueI(); i++)
		// 		MC.startUseItem();
		// }
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
