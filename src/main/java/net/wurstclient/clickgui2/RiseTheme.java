package net.wurstclient.clickgui2;

/**
 * Rise 模式深色主题（原导航器视觉，硬冻结）：导航器开启 Rise mode 时使用，
 * 不再随 {@code VisualTheme} 漂移。
 */
final class RiseTheme
{
	/** 面板底（原 VisualTheme.PANEL）。 */
	static final int BACKGROUND = 0xE60E1118;
	/** 侧栏底（原 VisualTheme.PANEL_SECONDARY）。 */
	static final int SECONDARY = 0xD9141820;
	/** 主文字。 */
	static final int TEXT = 0xFFFFFFFF;
	/** 次文字（原 VisualTheme.TEXT_DIMMED）。 */
	static final int SECONDARY_TEXT = 0xFFD3D3D3;
	/** 三级文字（原 VisualTheme.TEXT_MUTED）。 */
	static final int TRINARY_TEXT = 0xA6FFFFFF;
	/** 模块卡底（原 VisualTheme.SURFACE_36）。 */
	static final int OVERLAY = 0x5C000000;
	/** 强调色 = 统一客户端色调 #4677FF（原为 Flat 深青 #006366）。 */
	static final int ACCENT = 0xFF007CFF;

	private RiseTheme()
	{}

	static int mix(int first, int second, float progress)
	{
		float amount = Math.max(0, Math.min(1, progress));
		int a = mixChannel(first >>> 24, second >>> 24, amount);
		int r = mixChannel(first >>> 16 & 0xFF, second >>> 16 & 0xFF, amount);
		int g = mixChannel(first >>> 8 & 0xFF, second >>> 8 & 0xFF, amount);
		int b = mixChannel(first & 0xFF, second & 0xFF, amount);
		return a << 24 | r << 16 | g << 8 | b;
	}

	static int withAlpha(int color, float alpha)
	{
		return Math.max(0, Math.min(255,
			Math.round(Math.max(0, Math.min(1, alpha)) * 255))) << 24
			| color & 0xFFFFFF;
	}

	private static int mixChannel(int first, int second, float progress)
	{
		return Math.round(first + (second - first) * progress);
	}
}
