/*
 * Adapted from Rise 6.1.30's standard ClickGUI color model.
 * 支持浅色（PVPUtils）与深色（Rise）两套主题，由 Navigator 每帧设定模式。
 */
package net.wurstclient.clickgui2;

enum RiseColors
{
	BACKGROUND,
	SECONDARY,
	TEXT,
	SECONDARY_TEXT,
	TRINARY_TEXT,
	OVERLAY;

	private static boolean riseMode;

	static void setRiseMode(boolean enabled)
	{
		riseMode = enabled;
	}

	static boolean isRiseMode()
	{
		return riseMode;
	}

	int argb()
	{
		return riseMode ? dark(ordinal()) : light(ordinal());
	}

	/** 浅色 PVPUtils 主题。 */
	private static int light(int index)
	{
		return switch(index)
		{
			case 0 -> PvPUtilsTheme.CARD;
			case 1 -> PvPUtilsTheme.SIDEBAR;
			case 2 -> PvPUtilsTheme.TEXT;
			case 3 -> PvPUtilsTheme.TEXT_ROW;
			case 4 -> PvPUtilsTheme.TEXT_MUTED;
			default -> PvPUtilsTheme.MODULE;
		};
	}

	/** 深色 Rise 主题。 */
	private static int dark(int index)
	{
		return switch(index)
		{
			case 0 -> RiseTheme.BACKGROUND;
			case 1 -> RiseTheme.SECONDARY;
			case 2 -> RiseTheme.TEXT;
			case 3 -> RiseTheme.SECONDARY_TEXT;
			case 4 -> RiseTheme.TRINARY_TEXT;
			default -> RiseTheme.OVERLAY;
		};
	}

	static int mix(int first, int second, float progress)
	{
		return RiseTheme.mix(first, second, progress);
	}
}
