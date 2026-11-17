/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
package net.wurstclient.hud2;

import java.util.Locale;

/**
 * HUD background themes ported from Soar Client v7.1.2
 * ({@code GlobalSettingsMod#modThemeSetting}).
 */
public enum HudTheme
{
	NORMAL,
	GLOW,
	OUTLINE,
	VANILLA,
	OUTLINE_GLOW,
	VANILLA_GLOW,
	SHADOW,
	DARK,
	LIGHT;

	public String getId()
	{
		return name().toLowerCase(Locale.ROOT);
	}

	public String getDisplayName()
	{
		switch(this)
		{
			case NORMAL:
			return "Black";
			case GLOW:
			return "Glow";
			case OUTLINE:
			return "Outline";
			case VANILLA:
			return "Vanilla";
			case OUTLINE_GLOW:
			return "Outline Glow";
			case VANILLA_GLOW:
			return "Vanilla Glow";
			case SHADOW:
			return "Shadow";
			case DARK:
			return "Dark";
			case LIGHT:
			return "Light";
			default:
			return name();
		}
	}

	public HudTheme next()
	{
		HudTheme[] values = values();
		return values[(ordinal() + 1) % values.length];
	}

	public static HudTheme byId(String id)
	{
		for(HudTheme theme : values())
			if(theme.getId().equalsIgnoreCase(id))
				return theme;
		return NORMAL;
	}
}
