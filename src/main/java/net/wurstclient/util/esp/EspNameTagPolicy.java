/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util.esp;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.wurstclient.render.skia.EspIndicatorGlyphs;
import net.wurstclient.util.esp.EspNameTagElement.Icon;

/**
 * 决定一块铭牌上「有哪些元素、按什么顺序」的纯策略，移植自 OpenOpal 的
 * {@code visual/esp/ESPModule.java} 的 {@code renderNameTag()}（GPL-3.0）。
 *
 * <p>
 * 参考把「读设置」和「读实体状态」揉在同一个方法里，这里按本工程
 * {@code *Policy} 的惯例拆成 {@link Options}（设置）与 {@link State}
 * （实体状态）两份输入，输出排列好的元素列表，于是元素顺序、颜色与
 * 「什么时候加哪个元素」全部可单测，绘制方只负责把列表画出来。
 *
 * <p>
 * <b>顺序与参考一致</b>：指示（潜行 → 隐身 → 举盾）→ 距离 → 名字 → 血量
 * → 伤害吸收。
 *
 * <p>
 * <b>故意不移植的</b>：参考的 {@code Strength} 指示读的是 OpenPal 自己的
 * {@code LocalDataWatch.getStrengthedPlayerList()}（按玩家名记录的力量药水
 * 状态），本工程没有任何等价数据源，为一个恒为 {@code false} 的分支保留
 * 字段属于凭空造抽象，故整项略去。
 */
public final class EspNameTagPolicy
{
	/** 潜行指示，参考 {@code 0xFFFF5555}。 */
	public static final int SNEAKING_COLOR = 0xFFFF5555;

	/** 隐身指示，参考 {@code 0xFFAAAAAA}。 */
	public static final int INVISIBLE_COLOR = 0xFFAAAAAA;

	/** 举盾指示，参考 {@code 0xFF41AF7D}。 */
	public static final int BLOCKING_COLOR = 0xFF41AF7D;

	/** 距离文本，参考 {@code 0xFFAAAAAA}。 */
	public static final int DISTANCE_COLOR = 0xFFAAAAAA;

	/** 伤害吸收，参考 {@code 0xFFFFC247}。 */
	public static final int ABSORPTION_COLOR = 0xFFFFC247;

	/** 名字与血量的颜色，参考是 {@code -1}。 */
	public static final int DEFAULT_COLOR = 0xFFFFFFFF;

	private EspNameTagPolicy()
	{
	}

	/**
	 * 铭牌由哪些元素组成（来自设置）。
	 *
	 * <p>
	 * 没有单独的「伤害吸收」开关：参考把吸收挂在血量元素里面，
	 * 只要目标当前有吸收值就自动多出一枚，这里照做。
	 */
	public record Options(boolean sneaking, boolean invisible, boolean blocking,
		boolean distance, boolean name, boolean health)
	{
		/** 与参考默认开启项一致：距离 / 名字 / 血量 + 三个指示。 */
		public static Options defaults()
		{
			return new Options(true, true, true, true, true, true);
		}
	}

	/**
	 * 这一帧这只实体的事实（来自实体与设置）。
	 */
	public record State(boolean isSneaking, boolean isInvisible,
		boolean isBlocking, int distanceBlocks, String name, float health,
		float absorption)
	{
	}

	public static List<EspNameTagElement> build(Options options, State state)
	{
		ArrayList<EspNameTagElement> elements = new ArrayList<>(7);

		if(options.sneaking() && state.isSneaking())
			elements.add(new EspNameTagElement(EspIndicatorGlyphs.sneaking(),
				SNEAKING_COLOR));

		if(options.invisible() && state.isInvisible())
			elements.add(new EspNameTagElement(EspIndicatorGlyphs.invisible(),
				INVISIBLE_COLOR));

		if(options.blocking() && state.isBlocking())
			elements.add(new EspNameTagElement(EspIndicatorGlyphs.blocking(),
				BLOCKING_COLOR));

		if(options.distance() && state.distanceBlocks() >= 0)
			elements.add(new EspNameTagElement(
				state.distanceBlocks() + "m", DISTANCE_COLOR));

		if(options.name() && state.name() != null && !state.name().isEmpty())
			elements.add(new EspNameTagElement(state.name(), DEFAULT_COLOR));

		if(options.health())
		{
			Icon heart = EspIndicatorGlyphs.health();
			elements.add(new EspNameTagElement(heart,
				formatHealth(state.health()), DEFAULT_COLOR));

			// 参考：吸收值挂在血量元素下，> 0 时自动追加一枚
			if(state.absorption() > 0)
				elements.add(new EspNameTagElement(
					EspIndicatorGlyphs.absorption(),
					formatHealth(state.absorption()), ABSORPTION_COLOR));
		}

		return List.copyOf(elements);
	}

	/**
	 * 参考用 {@code new DecimalFormat("0.#")}：最多一位小数、整数不带小数点。
	 *
	 * <p>
	 * 这里不用 {@code DecimalFormat} 而是显式按 {@link Locale#ROOT} 格式化，
	 * 因为 {@code DecimalFormat} 跟随默认区域设置，在逗号做小数点的区域会
	 * 输出 {@code "12,3"}；铭牌是要贴在世界里的固定视觉，不该随系统语言变。
	 */
	public static String formatHealth(float value)
	{
		if(Float.isNaN(value) || Float.isInfinite(value))
			return "0";

		String formatted = String.format(Locale.ROOT, "%.1f", value);
		if(formatted.endsWith(".0"))
			return formatted.substring(0, formatted.length() - 2);
		return formatted;
	}
}
