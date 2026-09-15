/*
 * Adapted from Rise 6.1.30's standard ClickGUI color model.
 */
package net.wurstclient.clickgui2;

/**
 * Rise 6.1.30 导航器的配色项。
 *
 * <p>
 * 这里原本还有一套浅色（PVPUtils）主题，由 {@code NavigatorScreen} 每帧按
 * riseMode 切换。PvPUtils 的界面已经删除，而且 riseMode 现在唯一的作用是决定
 * 导航器**打开哪个界面**（开 = Rise 界面），Rise 界面里那套浅色分支永远不会执行，
 * 所以连同 {@code light()} 一起删掉。
 */
enum RiseColors
{
	BACKGROUND,
	SECONDARY,
	TEXT,
	SECONDARY_TEXT,
	TRINARY_TEXT,
	OVERLAY;
	
	int argb()
	{
		return switch(this)
		{
			case BACKGROUND -> RiseTheme.BACKGROUND;
			case SECONDARY -> RiseTheme.SECONDARY;
			case TEXT -> RiseTheme.TEXT;
			case SECONDARY_TEXT -> RiseTheme.SECONDARY_TEXT;
			case TRINARY_TEXT -> RiseTheme.TRINARY_TEXT;
			case OVERLAY -> RiseTheme.OVERLAY;
		};
	}
	
	static int mix(int first, int second, float progress)
	{
		return RiseTheme.mix(first, second, progress);
	}
}
