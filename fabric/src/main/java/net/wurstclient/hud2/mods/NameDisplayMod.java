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
import net.wurstclient.settings.CheckboxSetting;

public final class NameDisplayMod extends SimpleHUDMod
{
	private final CheckboxSetting iconSetting =
		new CheckboxSetting("Icon", "Shows an icon next to the text.", true);

	public NameDisplayMod()
	{
		super("NameDisplay", "Name Display", "Shows the player's name.");
		setTranslationKey("name_display");
		setDefaultPosition(10, 274);
		addSetting(iconSetting);
	}

	@Override
	public String getText()
	{
		return HudCompat.playerName();
	}

	@Override
	public String getIcon()
	{
		return iconSetting.isChecked() ? HudIcon.USER : null;
	}
}
