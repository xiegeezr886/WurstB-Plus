/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.render.skia;

import net.wurstclient.util.esp.EspNameTagElement.Icon;
import net.wurstclient.util.esp.EspNameTagElement.IconPosition;

/**
 * ESP 铭牌状态指示所用的字形。
 *
 * <p>
 * OpenOpal 用 {@code materialicons-regular} 图标字体（如 {@code \uefe4} 力量、
 * {@code \uf19f} 潜行、{@code \ue8f5} 隐身、{@code \ue1d5} 举盾、
 * {@code \uE87D} 红心）。本工程没有该字体资源，而随包再引入一套图标字体
 * 要额外的字体文件与授权说明；{@code TwilightSkia} 在移植参考界面时对同类
 * 问题给出的结论也是「参考的图标字体无法移植，改用矢量图元」。
 *
 * <p>
 * 这里采用同样的取舍，但用<b>中文字形</b>而不是矢量图元：铭牌正文本来就由
 * {@link SkiaFontManager} 的苹方字体绘制，而苹方必然包含这些常用汉字，
 * 因此不需要新增任何资源，也不存在缺字风险。字形含义与原图标一一对应。
 *
 * <p>
 * 纯常量类，不触发任何 Skia 类加载（字体缺失时本类仍可安全加载）。
 */
public final class EspIndicatorGlyphs
{
	/** 力量（参考 {@code \uefe4}，红色）。 */
	public static final String STRENGTH = "力";

	/** 潜行（参考 {@code \uf19f}）。 */
	public static final String SNEAKING = "潜";

	/** 隐身（参考 {@code \ue8f5}）。 */
	public static final String INVISIBLE = "隐";

	/** 举盾格挡（参考 {@code \ue1d5}）。 */
	public static final String BLOCKING = "盾";

	/** 血量（参考 {@code \uE87D}）。 */
	public static final String HEALTH = "血";

	/** 伤害吸收（参考另一枚 {@code \uE87D}，琥珀色）。 */
	public static final String ABSORPTION = "吸";

	private EspIndicatorGlyphs()
	{
	}

	public static Icon strength()
	{
		return new Icon(STRENGTH);
	}

	public static Icon sneaking()
	{
		return new Icon(SNEAKING);
	}

	public static Icon invisible()
	{
		return new Icon(INVISIBLE);
	}

	public static Icon blocking()
	{
		return new Icon(BLOCKING);
	}

	public static Icon health()
	{
		return new Icon(HEALTH, IconPosition.LEFT);
	}

	public static Icon absorption()
	{
		return new Icon(ABSORPTION, IconPosition.LEFT);
	}
}
