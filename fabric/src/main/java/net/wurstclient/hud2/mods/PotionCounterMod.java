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

public final class PotionCounterMod extends SimpleHUDMod
{
	public PotionCounterMod()
	{
		super("PotionCounter", "Potion Counter",
			"Shows how many potion effects are active.");
		setTranslationKey("potion_counter");
		setDefaultPosition(180, 440);
	}

	@Override
	public String getText()
	{
		return HudCompat.effects().size() + " Effects";
	}

	@Override
	public String getIcon()
	{
		return HudIcon.SHIELD;
	}
}
