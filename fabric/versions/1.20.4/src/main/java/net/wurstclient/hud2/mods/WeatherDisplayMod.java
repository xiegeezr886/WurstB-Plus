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

public final class WeatherDisplayMod extends SimpleHUDMod
{
	private final CheckboxSetting iconSetting =
		new CheckboxSetting("Icon", "Shows an icon next to the text.", true);

	public WeatherDisplayMod()
	{
		super("WeatherDisplay", "Weather Display",
			"Shows the current weather.");
		setTranslationKey("weather_display");
		setDefaultPosition(10, 186);
		addSetting(iconSetting);
	}

	@Override
	public String getText()
	{
		if(HudCompat.thundering())
			return "Thunder";
		if(HudCompat.raining())
			return "Rain";
		return "Clear";
	}

	@Override
	public String getIcon()
	{
		if(!iconSetting.isChecked())
			return null;
		if(HudCompat.thundering())
			return HudIcon.CLOUD_LIGHTING;
		if(HudCompat.raining())
			return HudIcon.CLOUD_RAIN;
		return HudIcon.SUN;
	}
}
