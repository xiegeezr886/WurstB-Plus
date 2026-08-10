package net.wurstclient.clickgui2.supersoft;

import net.wurstclient.gui.visual.VisualTheme;

public final class SuperSoftTheme
{
	public static final int BACKDROP = VisualTheme.OVERLAY;
	public static final int WINDOW = VisualTheme.PANEL;
	public static final int HEADER = VisualTheme.SURFACE_90;
	public static final int ROW = VisualTheme.SURFACE_85;
	public static final int SETTING = VisualTheme.CONTROL;
	public static final int SETTING_HOVER = VisualTheme.CONTROL_HOVER;
	public static final int MODULE_HOVER = VisualTheme.ACCENT_SUBTLE_STRONG;
	public static final int ACCENT = VisualTheme.ACCENT;
	public static final int TEXT = VisualTheme.TEXT;
	public static final int TEXT_SECONDARY = VisualTheme.TEXT_DIMMED;
	public static final int MUTED = VisualTheme.TEXT_MUTED;
	public static final int BORDER = VisualTheme.BORDER;
	public static final int SHADOW = VisualTheme.SHADOW;

	private SuperSoftTheme()
	{}

	public static int mix(int from, int to, float progress)
	{
		return VisualTheme.mix(from, to, progress);
	}
}
