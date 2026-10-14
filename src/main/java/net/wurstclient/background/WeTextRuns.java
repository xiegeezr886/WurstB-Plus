/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * 把一行文本切成「作者字体画得出的」与「必须回退到 MC 字体的」两种片段。
 *
 * <p>
 * 场景里的文本对象用的是作者包内的字体（实测 9 个对象用
 * {@code fonts/workshop/3759258840/yuanshen.ttf}）。作者字体未必收录每个字符：
 * 缺字形时渲染器画的是一个**小方框**（用户截图里 {@code May◻28th} 那种方块，
 * 已经因为换行符出现过一次）。所以画的时候按**码点**问一次"这个字作者字体有没有"，
 * 有就用作者字体，没有就用 MC 字体接着画 —— 一行里两种字体混排，位置按各自字宽累加。
 * </p>
 *
 * <p>
 * 这里只做切分，不碰任何渲染状态，所以可以单测：给一个"支持哪些码点"的判定，
 * 就能把任意一行的切分结果钉死（含代理对、连续同类片段的合并、空行）。
 * </p>
 */
public final class WeTextRuns
{
	private WeTextRuns()
	{}

	/**
	 * 一段同源的文本。
	 *
	 * @param text
	 *            这一段的文字
	 * @param custom
	 *            true = 用作者字体画；false = 回退到 MC 自带字体
	 */
	public record Run(String text, boolean custom)
	{}

	/**
	 * 按码点切分：相邻的同类字符合并成一段，顺序与原串一致。
	 *
	 * @param supported
	 *            作者字体是否有这个码点的字形；{@code null} 表示全都没有
	 */
	public static List<Run> split(String line, IntPredicate supported)
	{
		List<Run> runs = new ArrayList<>();

		if(line == null || line.isEmpty())
			return List.copyOf(runs);

		StringBuilder current = new StringBuilder();
		boolean currentCustom = false;

		for(int i = 0; i < line.length();)
		{
			int codePoint = line.codePointAt(i);
			boolean custom = supported != null && supported.test(codePoint);

			if(current.length() > 0 && custom != currentCustom)
			{
				runs.add(new Run(current.toString(), currentCustom));
				current.setLength(0);
			}

			currentCustom = custom;
			current.appendCodePoint(codePoint);
			i += Character.charCount(codePoint);
		}

		if(current.length() > 0)
			runs.add(new Run(current.toString(), currentCustom));

		return List.copyOf(runs);
	}
}
