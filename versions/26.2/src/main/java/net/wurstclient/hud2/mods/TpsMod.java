/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
package net.wurstclient.hud2.mods;

import net.wurstclient.hud2.nanovg.HudIcon;
import net.wurstclient.hud2.SimpleHUDMod;
import net.wurstclient.settings.CheckboxSetting;

public final class TpsMod extends SimpleHUDMod
{
	private final CheckboxSetting iconSetting =
		new CheckboxSetting("Icon", "Shows an icon next to the text.", true);

	public TpsMod()
	{
		super("Tps", "TPS", "Shows the server ticks per second.");
		setTranslationKey("tps");
		setDefaultPosition(10, 318);
		addSetting(iconSetting);
	}

	@Override
	public String getText()
	{
		double tps = WURST.getClientMetricsManager() == null ? 20
			: WURST.getClientMetricsManager().getTicksPerSecond();
		return String.format("%.1f TPS", tps);
	}

	@Override
	public String getIcon()
	{
		return iconSetting.isChecked() ? HudIcon.CPU : null;
	}
}
