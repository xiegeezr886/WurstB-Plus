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

public final class InventoryDisplayMod extends HUDMod
{
	private static final int COLUMNS = 9;
	private static final int ROWS = 3;
	private static final int SLOT = 20;

	public InventoryDisplayMod()
	{
		super("InventoryDisplay", "Inventory Display",
			"Shows the main inventory as a 9x3 grid.");
		setTranslationKey("inventory_display");
		setDefaultPosition(350, 10);
	}

	@Override
	protected void draw()
	{
		List<ItemStack> items = HudCompat.inventoryItems();
		float width = COLUMNS * SLOT + 8;
		float height = ROWS * SLOT + 8;
		drawBackground(width, height);

		for(int index = 0; index < Math.min(items.size(), COLUMNS * ROWS);
			index++)
		{
			int column = index % COLUMNS;
			int row = index / COLUMNS;
			float x = 4 + column * SLOT;
			float y = 4 + row * SLOT;
			drawRoundedRect(x, y, SLOT - 2, SLOT - 2, 3,
				new Color(255, 255, 255, 28));

			ItemStack stack = items.get(index);
			if(stack.isEmpty())
				continue;

			drawImage(HudItemIcons.get(stack), x + 1, y + 1, 16, 16);
			if(stack.getCount() > 1)
				drawText(Integer.toString(stack.getCount()), x + 1, y + 1, 7,
					HudFonts.REGULAR, getFontColor(220));
		}

		setSize(Math.round(width), Math.round(height));
	}
}
