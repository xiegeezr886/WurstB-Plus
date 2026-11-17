/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
package net.wurstclient.hud2.mods;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import net.wurstclient.hud2.nanovg.HudIcon;
import net.wurstclient.hud2.SimpleHUDMod;
import net.wurstclient.settings.CheckboxSetting;

public final class ClockMod extends SimpleHUDMod
{
	private static final DateTimeFormatter FORMAT_24 =
		DateTimeFormatter.ofPattern("HH:mm:ss");
	private static final DateTimeFormatter FORMAT_12 =
		DateTimeFormatter.ofPattern("hh:mm:ss a");

	private final CheckboxSetting iconSetting =
		new CheckboxSetting("Icon", "Shows an icon next to the text.", true);
	private final CheckboxSetting twentyFourHour = new CheckboxSetting(
		"24-hour", "Uses the 24-hour clock format.", true);

	public ClockMod()
	{
		super("Clock", "Clock", "Shows the local time.");
		setTranslationKey("clock");
		setDefaultPosition(10, 296);
		setDefaultEnabled(true);
		addSetting(iconSetting);
		addSetting(twentyFourHour);
	}

	@Override
	public String getText()
	{
		return LocalTime.now().format(
			twentyFourHour.isChecked() ? FORMAT_24 : FORMAT_12);
	}

	@Override
	public String getIcon()
	{
		return iconSetting.isChecked() ? HudIcon.CLOCK : null;
	}
}
