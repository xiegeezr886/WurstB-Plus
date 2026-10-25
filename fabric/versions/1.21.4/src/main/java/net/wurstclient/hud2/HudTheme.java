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
