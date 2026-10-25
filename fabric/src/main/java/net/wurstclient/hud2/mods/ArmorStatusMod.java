/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
package net.wurstclient.hud2.mods;

import java.util.List;

import net.minecraft.world.item.ItemStack;
import net.wurstclient.hud2.HUDMod;
import net.wurstclient.hud2.compat.HudItemIcons;
import net.wurstclient.hud2.compat.HudCompat;
import net.wurstclient.hud2.nanovg.HudFonts;
import net.wurstclient.settings.CheckboxSetting;

public final class ArmorStatusMod extends HUDMod
{
	private final CheckboxSetting durability = new CheckboxSetting(
		"Durability", "Shows each armor piece's remaining durability.", true);

	public ArmorStatusMod()
	{
		super("ArmorStatus", "Armor Status",
			"Shows the equipped armor and its durability.");
		setTranslationKey("armor_status");
		setDefaultPosition(10, 366);
		addSetting(durability);
	}

	@Override
	protected void draw()
	{
		List<ItemStack> items = HudCompat.armorItems();
		int rows = 0;
		float width = 90;
		for(ItemStack stack : items)
		{
			if(stack.isEmpty())
				continue;
			rows++;
			width = Math.max(width,
				getTextWidth(line(stack), 9, HudFonts.REGULAR) + 34);
		}

		if(rows == 0)
		{
			drawBackground(90, 20);
			drawText("No armor", 6, 5, 9, HudFonts.REGULAR,
				getFontColor(200));
			setSize(90, 20);
			return;
		}

		float height = 6 + rows * 20;
		drawBackground(width, height);
		int row = 0;
		for(ItemStack stack : items)
		{
			if(stack.isEmpty())
				continue;
			drawImage(HudItemIcons.get(stack), 6, 3 + row * 20, 16, 16);
			drawText(line(stack), 26, 6 + row * 20, 9, HudFonts.REGULAR);
			row++;
		}
		setSize(Math.round(width), Math.round(height));
	}

	private String line(ItemStack stack)
	{
		String text = stack.getHoverName().getString();
		if(!durability.isChecked() || stack.getMaxDamage() <= 0)
			return text;
		int remaining = stack.getMaxDamage() - stack.getDamageValue();
		int percent = Math.round(remaining * 100F / stack.getMaxDamage());
		return text + " " + percent + "%";
	}
}
