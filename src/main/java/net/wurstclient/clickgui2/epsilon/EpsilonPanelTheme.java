/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.clickgui2.epsilon;

/**
 * Epsilon PanelScreen 的 MD3 暗色调色板与派生色（TonalSpot + Dark）。
 *
 * <p>
 * 数值取自参考项目 Nekoyahouse/Epsilon（GPLv3）的 {@code MD3Theme} 在
 * {@code syncFromSettings()} 之后按 TonalSpot + Dark 计算出的结果——不是类静态
 * 初值，两者在 {@code SURFACE_DIM} / {@code SURFACE_CONTAINER_LOW} /
 * {@code SURFACE_CONTAINER_HIGHEST} 上并不相同。
 *
 * <p>
 * 与 {@link net.wurstclient.clickgui2.supersoft.EpsilonMd3Theme} 是两套东西：
 * 那个是浅色（白底 + #007CFF）且服务于 SuperSoft 体系，这个只服务面板导航器，
 * 改这里不会影响其它 GUI。
 *
 * <p>
 * 故意不含 Minecraft 依赖，好让派生公式可以被单测覆盖。
 */
public final class EpsilonPanelTheme
{
	// ---- 面板与栏 ----
	public static final int SURFACE = 0xEE141218;
	public static final int SURFACE_DIM = 0xE81B1820;
	public static final int SURFACE_CONTAINER = 0xF4211F26;
	public static final int SURFACE_CONTAINER_LOW = 0xF01B1820;
	public static final int SURFACE_CONTAINER_HIGH = 0xF82B2930;
	public static final int SURFACE_CONTAINER_HIGHEST = 0xFC35333B;
	
	// ---- 描边 ----
	public static final int OUTLINE = 0xB4938F99;
	public static final int OUTLINE_SOFT = 0x60938F99;
	
	// ---- 主色 ----
	public static final int PRIMARY = 0xFFD0BCFF;
	public static final int ON_PRIMARY = 0xFF381E72;
	public static final int PRIMARY_CONTAINER = 0xEC4F378B;
	public static final int ON_PRIMARY_CONTAINER = 0xFFEADDFF;
	
	// ---- 次色 ----
	public static final int SECONDARY = 0xFFCCC2DC;
	public static final int SECONDARY_CONTAINER = 0xEC4A4458;
	public static final int ON_SECONDARY_CONTAINER = 0xFFE8DEF8;
	
	// ---- 文字与反色 ----
	public static final int TEXT_PRIMARY = 0xFFECE6F0;
	public static final int TEXT_SECONDARY = 0xFFCAC4D0;
	public static final int TEXT_MUTED = 0xFF938F99;
	public static final int INVERSE_SURFACE = 0xFFE6E0E9;
	public static final int INVERSE_ON_SURFACE = 0xFF313033;
	public static final int ERROR = 0xFFF2B8B5;
	public static final int SHADOW = 0x60000000;
	
	// ---- 尺寸常量（CSS 逻辑像素，与参考项目同名同值）----
	public static final int PANEL_RADIUS = 17;
	public static final int SECTION_RADIUS = 13;
	public static final int CARD_RADIUS = 9;
	public static final int CONTROL_RADIUS = 7;
	public static final int PANEL_SHADOW_ALPHA = 96;
	public static final float OUTER_PADDING = 5.0F;
	public static final float SECTION_GAP = 3.0F;
	public static final float INNER_PADDING = 5.0F;
	public static final float ROW_GAP = 3.0F;
	public static final float PANEL_TITLE_INSET = 6.0F;
	public static final float ROW_CONTENT_INSET = 5.0F;
	public static final float ROW_TRAILING_INSET = 5.0F;
	public static final float CONTROL_HEIGHT = 18.0F;
	public static final float SWITCH_WIDTH = 26.0F;
	public static final float SWITCH_HEIGHT = 16.0F;
	
	private EpsilonPanelTheme()
	{}
	
	/** 行底：hover 时从 CONTAINER 混向 HIGH。 */
	public static int rowSurface(float hover)
	{
		return mix(SURFACE_CONTAINER, SURFACE_CONTAINER_HIGH, hover);
	}
	
	/** 开关轨道：关 = HIGHEST，开 = PRIMARY。 */
	public static int switchTrack(float progress)
	{
		return mix(SURFACE_CONTAINER_HIGHEST, PRIMARY, progress);
	}
	
	/** 开关手柄：关 = OUTLINE，开 = ON_PRIMARY。 */
	public static int switchKnob(float progress)
	{
		return mix(OUTLINE, ON_PRIMARY, progress);
	}
	
	/**
	 * 开关描边：只关着的时候可见，越关越明显（alpha (1-t)*168），
	 * 并且随 hover 向文字色靠拢。
	 */
	public static int switchOutline(float hover, float progress)
	{
		int rgb = mix(OUTLINE, TEXT_PRIMARY,
			Math.max(0F, Math.min(1F, hover)) * 0.35F);
		return withAlpha(rgb,
			Math.round((1F - Math.max(0F, Math.min(1F, progress))) * 168F));
	}
	
	/** 分段控件内底。 */
	public static int segmentedSurface()
	{
		return SURFACE_CONTAINER_HIGH;
	}
	
	/** 滚动条滑块：未 hover = OUTLINE@64，hover = PRIMARY@190。 */
	public static int scrollThumb(float hover)
	{
		return mix(withAlpha(OUTLINE, 64), withAlpha(PRIMARY, 190), hover);
	}
	
	public static int mix(int from, int to, float progress)
	{
		float amount = Math.max(0F, Math.min(1F, progress));
		int a = channel(from >>> 24, to >>> 24, amount);
		int r = channel(from >> 16 & 0xFF, to >> 16 & 0xFF, amount);
		int g = channel(from >> 8 & 0xFF, to >> 8 & 0xFF, amount);
		int b = channel(from & 0xFF, to & 0xFF, amount);
		return a << 24 | r << 16 | g << 8 | b;
	}
	
	public static int withAlpha(int color, int alpha)
	{
		return Math.max(0, Math.min(255, alpha)) << 24 | color & 0xFFFFFF;
	}
	
	public static int withAlpha(int color, float alpha)
	{
		return withAlpha(color, Math.round(Math.max(0F,
			Math.min(1F, alpha)) * 255F));
	}
	
	/** MD3 状态层：同 RGB，alpha = progress * maxAlpha。 */
	public static int stateLayer(int color, float progress, int maxAlpha)
	{
		return withAlpha(color, Math.round(Math.max(0F,
			Math.min(1F, progress)) * Math.max(0, Math.min(255, maxAlpha))));
	}
	
	private static int channel(int from, int to, float progress)
	{
		return Math.round(from + (to - from) * progress);
	}
}
