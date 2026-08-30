package net.wurstclient.clickgui2.supersoft;

/**
 * Material Design 3 (TonalSpot 暗色) 调色板，取自 Epsilon 客户端的 MD3Theme。
 *
 * <p>本类只被 SuperSoft 体系（SuperSoftTheme / SuperSoftRenderer / 其组件）使用，
 * 与 {@link net.wurstclient.gui.visual.VisualTheme} 无关——改这里不影响
 * Vape / Flat / Rise / 音乐 GUI 的颜色。</p>
 */
public final class EpsilonMd3Theme
{
	// ---- 白色浅色主题（水影 NextGen 风格：白底 + 深字 + #007CFF 强调）----
	public static final int SURFACE_DIM = 0xE8F2F3F5;
	public static final int SURFACE_CONTAINER_LOW = 0xF0F5F6F8;
	public static final int SURFACE_CONTAINER = 0xF4FFFFFF;
	public static final int SURFACE_CONTAINER_HIGH = 0xF8F7F8FA;
	public static final int SURFACE_CONTAINER_HIGHEST = 0xFCE9EBEE;
	public static final int OUTLINE = 0xB49CA3AF;
	public static final int OUTLINE_SOFT = 0x609CA3AF;
	/** 统一强调色 = 水影 NextGen 蓝 #007CFF。 */
	public static final int PRIMARY = 0xFF007CFF;
	public static final int ON_PRIMARY = 0xFFFFFFFF;
	public static final int PRIMARY_CONTAINER = 0xFFD6ECFF;
	public static final int ON_PRIMARY_CONTAINER = 0xFF0A4D9E;
	public static final int SECONDARY = 0xFF5A6572;
	public static final int SECONDARY_CONTAINER = 0xFFE4EEFB;
	public static final int ON_SECONDARY_CONTAINER = 0xFF1A3E6E;
	public static final int TERTIARY = 0xFFB48A93;
	public static final int TEXT_PRIMARY = 0xFF1A1D24;
	public static final int TEXT_SECONDARY = 0xFF4B5563;
	public static final int TEXT_MUTED = 0xFF9CA3AF;
	public static final int ERROR = 0xFFD64545;
	public static final int SHADOW = 0x60000000;

	private EpsilonMd3Theme()
	{}

	/**
	 * 逐通道 ARGB 线性插值，语义与 VisualTheme.mix 完全一致（clamp 0..1 +
	 * Math.round），保证 hover / 过渡动画插值行为不变。
	 */
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

	/** 状态层：MD3 的 stateLayer(color, progress, maxAlpha)。 */
	public static int stateLayer(int color, float progress, int maxAlpha)
	{
		return withAlpha(color, Math.round(clamp01(progress)
			* Math.max(0, Math.min(255, maxAlpha))));
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
