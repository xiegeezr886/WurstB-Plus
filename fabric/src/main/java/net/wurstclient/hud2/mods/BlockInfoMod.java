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

public final class BlockInfoMod extends SimpleHUDMod
{
	public BlockInfoMod()
	{
		super("BlockInfo", "Block Info",
			"Shows the block the player is looking at.");
		setTranslationKey("block_info");
		setDefaultPosition(180, 10);
	}

	@Override
	public String getText()
	{
		String block = HudCompat.lookingAtBlock();
		return block.isEmpty() ? "No block" : block;
	}

	@Override
	public String getIcon()
	{
		return HudIcon.DATABASE;
	}
}
