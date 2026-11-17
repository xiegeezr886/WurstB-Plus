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
import net.wurstclient.settings.CheckboxSetting;

public final class CoordsMod extends SimpleHUDMod
{
	private final CheckboxSetting iconSetting =
		new CheckboxSetting("Icon", "Shows an icon next to the text.", true);
	private final CheckboxSetting biome =
		new CheckboxSetting("Biome", "Shows the current biome.", false);

	public CoordsMod()
	{
		super("Coords", "Coordinates", "Shows the player's coordinates.");
		setTranslationKey("coordinates");
		setDefaultPosition(10, 54);
		setDefaultEnabled(true);
		addSetting(iconSetting);
		addSetting(biome);
	}

	@Override
	public String getText()
	{
		if(MC.player == null)
			return "X: 0 Y: 0 Z: 0";
		String text = String.format("X: %d Y: %d Z: %d",
			(int)MC.player.getX(), (int)MC.player.getY(),
			(int)MC.player.getZ());
		if(biome.isChecked())
			text += "  " + net.wurstclient.hud2.compat.HudCompat.biomeName();
		return text;
	}

	@Override
	public String getIcon()
	{
		return iconSetting.isChecked() ? HudIcon.MAP_PIN : null;
	}
}
