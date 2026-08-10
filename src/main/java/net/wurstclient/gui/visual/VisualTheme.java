package net.wurstclient.gui.visual;

/** Semantic visual tokens adapted from LiquidBounce Nextgen's theme. */
public final class VisualTheme
{
	public static final int BACKGROUND = 0xFF080A0F;
	public static final int SURFACE = 0xFF000000;
	public static final int SURFACE_36 = 0x5C000000;
	public static final int SURFACE_50 = 0x80000000;
	public static final int SURFACE_68 = 0xAD000000;
	public static final int SURFACE_80 = 0xCC000000;
	public static final int SURFACE_85 = 0xD9000000;
	public static final int SURFACE_90 = 0xE6000000;
	public static final int PANEL = 0xE60E1118;
	public static final int PANEL_SECONDARY = 0xD9141820;
	public static final int CONTROL = 0xB31B202A;
	public static final int CONTROL_HOVER = 0xE6232935;
	public static final int OVERLAY = 0x99000000;

	public static final int TEXT = 0xFFFFFFFF;
	public static final int TEXT_DIMMED = 0xFFD3D3D3;
	public static final int TEXT_MUTED = 0xA6FFFFFF;
	public static final int TEXT_DISABLED = 0x667F8794;

	public static final int ACCENT = 0xFF4677FF;
	public static final int ACCENT_HOVER = 0xFF365FCC;
	public static final int ACCENT_SUBTLE = 0x1F4677FF;
	public static final int ACCENT_SUBTLE_STRONG = 0x554677FF;
	public static final int SUCCESS = 0xFF4DAC68;
	public static final int ERROR = 0xFFFC4130;
	public static final int WARNING = 0xFFefbf04;

	public static final int BORDER = 0x1FFFFFFF;
	public static final int BORDER_STRONG = 0x3DFFFFFF;
	public static final int GRID = 0x40808080;
	public static final int SHADOW = 0x80000000;

	public static final int RADIUS_SMALL = 3;
	public static final int RADIUS_MEDIUM = 5;
	public static final int RADIUS_LARGE = 8;
	public static final int MOTION_FAST_MS = 150;
	public static final int MOTION_STANDARD_MS = 200;

	private VisualTheme()
	{}

	public static int mix(int from, int to, float progress)
	{
		float amount = Math.max(0, Math.min(1, progress));
		int a = mixChannel(from >>> 24, to >>> 24, amount);
		int r = mixChannel(from >> 16 & 0xFF, to >> 16 & 0xFF, amount);
		int g = mixChannel(from >> 8 & 0xFF, to >> 8 & 0xFF, amount);
		int b = mixChannel(from & 0xFF, to & 0xFF, amount);
		return a << 24 | r << 16 | g << 8 | b;
	}

	public static int withAlpha(int color, float alpha)
	{
		return withAlpha(color, Math.round(clamp01(alpha) * 255));
	}

	public static int withAlpha(int color, int alpha)
	{
		return Math.max(0, Math.min(255, alpha)) << 24 | color & 0xFFFFFF;
	}

	private static int mixChannel(int from, int to, float progress)
	{
		return Math.round(from + (to - from) * progress);
	}

	private static float clamp01(float value)
	{
		return Math.max(0, Math.min(1, value));
	}
}
