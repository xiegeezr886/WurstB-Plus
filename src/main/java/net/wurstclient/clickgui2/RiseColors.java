/*
 * Adapted from Rise 6.1.30's standard ClickGUI color model.
 */
package net.wurstclient.clickgui2;

import net.wurstclient.gui.visual.VisualTheme;

enum RiseColors
{
	BACKGROUND(VisualTheme.PANEL),
	SECONDARY(VisualTheme.PANEL_SECONDARY),
	TEXT(VisualTheme.TEXT),
	SECONDARY_TEXT(VisualTheme.TEXT_DIMMED),
	TRINARY_TEXT(VisualTheme.TEXT_MUTED),
	OVERLAY(VisualTheme.SURFACE_36);

	private final int argb;

	RiseColors(int argb)
	{
		this.argb = argb;
	}

	int argb()
	{
		return argb;
	}

	static int mix(int first, int second, float progress)
	{
		float amount = Math.max(0, Math.min(1, progress));
		int a = mixChannel(first >>> 24, second >>> 24, amount);
		int r = mixChannel(first >>> 16 & 0xFF, second >>> 16 & 0xFF,
			amount);
		int g = mixChannel(first >>> 8 & 0xFF, second >>> 8 & 0xFF,
			amount);
		int b = mixChannel(first & 0xFF, second & 0xFF, amount);
		return a << 24 | r << 16 | g << 8 | b;
	}

	private static int mixChannel(int first, int second, float progress)
	{
		return Math.round(first + (second - first) * progress);
	}
}
