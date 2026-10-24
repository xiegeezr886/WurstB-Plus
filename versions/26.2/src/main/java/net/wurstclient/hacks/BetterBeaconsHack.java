/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
// Ported from Meteor Client (GPL-3.0).
package net.wurstclient.hacks;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.screens.inventory.BeaconScreen;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.inventory.BeaconMenu;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.GUIRenderListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;

@SearchTags({"better beacons", "BeaconRange", "beacon range",
	"beacon effects", "beacon level", "beacon radius"})
public final class BetterBeaconsHack extends Hack implements GUIRenderListener
{
	private final CheckboxSetting showEffectNames = new CheckboxSetting(
		"Show effect names",
		"Shows the primary and secondary effects selected in the beacon.",
		true);

	private final CheckboxSetting showLevelTable = new CheckboxSetting(
		"Show level table", "Shows the effect range of every beacon level.",
		true);

	public BetterBeaconsHack()
	{
		super("BetterBeacons");
		setCategory(Category.ITEMS);
		addSetting(showEffectNames);
		addSetting(showLevelTable);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(GUIRenderListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(GUIRenderListener.class, this);
	}

	@Override
	public void onRenderGUI(GuiGraphicsExtractor graphics, float partialTicks)
	{
		if(!(MC.gui.screen() instanceof BeaconScreen screen))
			return;

		BeaconMenu menu = screen.getMenu();
		int level = menu.getLevels();

		List<String> lines = new ArrayList<>();
		lines.add("Beacon level: " + (level > 0 ? level : "none"));
		lines.add("Effect range: "
			+ (level > 0 ? level * 10 + 10 + " blocks" : "-"));

		if(showEffectNames.isChecked())
		{
			lines.add("Primary: " + getEffectName(menu.getPrimaryEffect()));
			lines.add(
				"Secondary: " + getEffectName(menu.getSecondaryEffect()));
		}

		if(showLevelTable.isChecked())
		{
			lines.add("");
			lines.add("Ranges: Lv1 20  Lv2 30");
			lines.add("        Lv3 40  Lv4 50");
		}

		int lineHeight = 10;
		int padding = 6;
		int width = 130;
		for(String line : lines)
			width = Math.max(width, MC.font.width(line) + padding * 2);

		int height = padding * 2 + lines.size() * lineHeight;
		int x = Math.min(screen.width / 2 + 118,
			screen.width - width - 4);
		int y = Math.min(screen.height / 2 - 96,
			screen.height - height - 4);
		x = Math.max(4, x);
		y = Math.max(4, y);

		graphics.fill(x, y, x + width, y + height, 0xB0101010);
		graphics.fill(x, y, x + width, y + 1, 0xFF606060);
		graphics.fill(x, y + height - 1, x + width, y + height,
			0xFF606060);
		graphics.fill(x, y, x + 1, y + height, 0xFF606060);
		graphics.fill(x + width - 1, y, x + width, y + height,
			0xFF606060);

		int textY = y + padding;
		for(int i = 0; i < lines.size(); i++)
		{
			int color = i == 0 ? 0xFFFFD700 : 0xFFE0E0E0;
			graphics.text(MC.font, lines.get(i), x + padding, textY,
				color, false);
			textY += lineHeight;
		}
	}

	private String getEffectName(Holder<MobEffect> effect)
	{
		return effect == null ? "none"
			: effect.value().getDisplayName().getString();
	}
}
