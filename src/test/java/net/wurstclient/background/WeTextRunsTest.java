/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 行内混排的切分（{@link WeTextRuns}）。
 *
 * <p>
 * 这一件是"作者字体缺字形就画方框"的解药：切分对了，缺的字才会用 MC 字体补上，
 * 而且不会把整行都推去回退。用例覆盖三种边界：连续同类合并、代理对（散文里的 emoji /
 * 罕用字是 4 字节 UTF-8、Java 里两个 char）、以及全支持 / 全不支持。
 * </p>
 */
final class WeTextRunsTest
{
	@Test
	void keepsASupportedLineInOneRun()
	{
		List<WeTextRuns.Run> runs = WeTextRuns.split("2020", codePoint -> true);

		assertEquals(List.of(new WeTextRuns.Run("2020", true)), runs);
	}

	@Test
	void fallsBackWholeLineWhenNothingIsSupported()
	{
		List<WeTextRuns.Run> runs = WeTextRuns.split("May 28th", codePoint -> false);

		assertEquals(List.of(new WeTextRuns.Run("May 28th", false)), runs);
	}

	/** 场景里就长这样：`May\n28th`、`周日\n上午` —— 一行里两种来源混着。 */
	@Test
	void splitsMixedLineIntoAlternatingRuns()
	{
		// 只有汉字有字形，数字与拉丁字母没有
		List<WeTextRuns.Run> runs =
			WeTextRuns.split("2020周日", codePoint -> codePoint > 0x2000);

		assertEquals(List.of(new WeTextRuns.Run("2020", false),
			new WeTextRuns.Run("周日", true)), runs);
	}

	@Test
	void mergesAdjacentCharactersOfTheSameKind()
	{
		// 偶数码点支持、奇数不支持：应当切成一个个单字符片段
		List<WeTextRuns.Run> runs = WeTextRuns.split("abcd", codePoint -> codePoint % 2 == 0);

		assertEquals(List.of(new WeTextRuns.Run("a", false),
			new WeTextRuns.Run("b", true), new WeTextRuns.Run("c", false),
			new WeTextRuns.Run("d", true)), runs);
	}

	/** 代理对必须整对地留在一个片段里，不能被劈成两个 char。 */
	@Test
	void keepsSurrogatePairsTogether()
	{
		String supplementary = new String(Character.toChars(0x1F600));

		List<WeTextRuns.Run> runs =
			WeTextRuns.split("a" + supplementary + "b", codePoint -> true);

		assertEquals(1, runs.size());
		assertEquals("a" + supplementary + "b", runs.get(0).text());
		assertTrue(runs.get(0).custom());
	}

	/** 空行、null：没有片段，也别抛。 */
	@Test
	void handlesEmptyLines()
	{
		assertTrue(WeTextRuns.split("", codePoint -> true).isEmpty());
		assertTrue(WeTextRuns.split(null, codePoint -> true).isEmpty());
	}

	/** 没有"支持判定"时全行回退（调用方拿不到字体就是这个情形）。 */
	@Test
	void fallsBackWhenNoPredicateIsGiven()
	{
		assertEquals(List.of(new WeTextRuns.Run("abc", false)),
			WeTextRuns.split("abc", null));
	}
}
