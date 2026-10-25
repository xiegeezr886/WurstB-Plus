/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
package net.wurstclient.hud2.mods;

import java.awt.Color;
import java.util.List;

import net.minecraft.world.item.ItemStack;
import net.wurstclient.hud2.HUDMod;
import net.wurstclient.hud2.compat.HudItemIcons;
import net.wurstclient.hud2.compat.HudCompat;
import net.wurstclient.hud2.nanovg.HudFonts;

public final class ModernHotbarMod extends HUDMod
{
	private static final int SLOT = 20;

	public ModernHotbarMod()
	{
		super("ModernHotbar", "Modern Hotbar",
			"Shows a modern hotbar overlay.");
		setTranslationKey("modern_hotbar");
		setDefaultPosition(350, 120);
	}

	@Override
	protected void draw()
	{
		List<ItemStack> items = HudCompat.hotbarItems();
		int selected = MC.player == null ? 0
			: MC.player.getInventory().selected;
		float width = 9 * SLOT + 8;
		float height = 30;
		drawBackground(width, height);

		for(int i = 0; i < Math.min(9, items.size()); i++)
		{
			float x = 4 + i * SLOT;
			boolean active = i == selected;
			drawRoundedRect(x, 4, SLOT - 2, SLOT - 2, 3,
				active ? new Color(accentColor(220), true)
					: new Color(255, 255, 255, 28));

			ItemStack stack = items.get(i);
			if(stack.isEmpty())
				continue;
			drawImage(HudItemIcons.get(stack), x + 1, 5, 16, 16);
			if(stack.getCount() > 1)
				drawText(Integer.toString(stack.getCount()), x + 1, 5, 7,
					HudFonts.REGULAR, getFontColor(220));
		}

		setSize(Math.round(width), Math.round(height));
	}
}
