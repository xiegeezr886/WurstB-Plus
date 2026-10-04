/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.twilight;

import java.time.LocalDate;

/**
 * 主页上的固定文案与当天日期。
 *
 * <p>
 * 日期本来写死在常量里（{@code "31"} / {@code "7月31日 · 周五"}），页面上永远
 * 停在那一天。参考图的「每日推荐」是要每天变的，所以这里按当天算。
 *
 * <p>
 * 故意不含 Minecraft 类型，可以按参考图里那一天直接单测。
 */
public final class TwilightHomeCopy
{
	/** {@code .hero-kicker-day} 下面那行：参考图是「每日 06:00 焕新」。 */
	public static final String REFRESH_LINE = "每日 06:00 焕新";

	public static final String HERO_TITLE = "每日推荐";
	public static final String HERO_TITLE_EN = "DAILY MIX";
	public static final String HERO_DESCRIPTION =
		"来自 网易云音乐的个性化内容，随你的收听偏好持续更新。";
	public static final String HERO_PLAY = "播放全部";
	public static final String HERO_OPEN = "查看全部";

	public static final String DUO_LEFT_NAME = "私人漫游";
	public static final String DUO_LEFT_SUB = "网易云音乐 · 为你持续推荐";
	public static final String DUO_RIGHT_NAME = "私人雷达";
	public static final String DUO_RIGHT_SUB = "网易云音乐 · 发现更多好音乐";

	public static final String SECTION_TITLE = "网易云音乐 为你精选";
	public static final String SECTION_SUB = "点一首就开始·队列自动接上整份推荐";
	public static final String SECTION_MORE = "完整歌单";

	private static final String[] WEEKDAYS =
		{"一", "二", "三", "四", "五", "六", "日"};

	private TwilightHomeCopy()
	{
	}

	/** 徽章里的数字，如 {@code "27"}。 */
	public static String dayBadge(LocalDate date)
	{
		return Integer.toString(date.getDayOfMonth());
	}

	/** 徽章旁边那行，如 {@code "9月27日 · 周日"}。 */
	public static String dateLine(LocalDate date)
	{
		return date.getMonthValue() + "月" + date.getDayOfMonth() + "日 · 周"
			+ WEEKDAYS[date.getDayOfWeek().getValue() - 1];
	}
}
