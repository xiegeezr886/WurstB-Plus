/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
package net.wurstclient.hud2.mods;

import net.wurstclient.hud2.nanovg.HudIcon;
import net.wurstclient.hud2.SimpleHUDMod;
import net.wurstclient.hud2.compat.HudCompat;

public final class ItemInfoMod extends SimpleHUDMod
{
	public ItemInfoMod()
	{
		super("ItemInfo", "Item Info",
			"Shows the name of the held item.");
		setTranslationKey("item_info");
		setDefaultPosition(180, 32);
	}

	@Override
	public String getText()
	{
		String item = HudCompat.heldItem();
		return item.isEmpty() ? "Empty hand" : item;
	}

	@Override
	public String getIcon()
	{
		return HudIcon.SHOPPING;
	}
}
