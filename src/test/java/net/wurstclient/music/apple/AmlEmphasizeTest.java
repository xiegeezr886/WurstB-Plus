package net.wurstclient.music.apple;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.wurstclient.music.LyricWord;
import org.junit.jupiter.api.Test;

/**
 * 校验 {@link AmlEmphasize} 复刻的 AMLL 强调与上浮动画。
 */
final class AmlEmphasizeTest
{
	@Test
	void cjkWordsNeedOneSecond()
	{
		assertTrue(AmlEmphasize.shouldEmphasize(new LyricWord("海", 0, 1200)));
		assertFalse(AmlEmphasize.shouldEmphasize(new LyricWord("海", 0, 400)));
	}

	@Test
	void latinWordsNeedShortLength()
	{
		assertTrue(AmlEmphasize.shouldEmphasize(
			new LyricWord("Hello", 0, 1600)));
		// 单字符不强调
		assertFalse(AmlEmphasize.shouldEmphasize(new LyricWord("A", 0, 2000)));
		// 过长不强调
		assertFalse(AmlEmphasize.shouldEmphasize(
			new LyricWord("abcdefgh", 0, 2000)));
	}

	@Test
	void amountAndGlowFollowAmlFormulas()
	{
		AmlEmphasize.Word word = AmlEmphasize.word(new LyricWord("海", 0, 2000),
			0, false, false, 1);
		// amount = (2000/2000)^3 * 0.6
		assertEquals(0.6, word.amount(), 1e-9);
		// blur = (2000/3000)^3 * 0.5
		assertEquals(0.5 * Math.pow(2D / 3D, 3), word.glowBlur(), 1e-9);
		assertTrue(word.emphasize());
	}

	@Test
	void lastWordOfLineAmplifiesAndDropsTheCap()
	{
		AmlEmphasize.Word last = AmlEmphasize.word(
			new LyricWord("海", 0, 2000), 0, true, false, 1);
		// 0.6 * 1.6 = 0.96 < 1.2，未被截断
		assertEquals(0.96, last.amount(), 1e-9);
		// blur = 0.148148 * 1.5 = 0.222222 < 0.8
		assertEquals(0.5 * Math.pow(2D / 3D, 3) * 1.5, last.glowBlur(), 1e-9);
		// du 放大 1.2 倍
		assertEquals(2400, last.durationMs(), 1e-9);
	}

	@Test
	void backgroundWordsFloatTwiceAsFar()
	{
		AmlEmphasize.Word normal = AmlEmphasize.word(
			new LyricWord("海", 0, 2000), 0, false, false, 1);
		AmlEmphasize.Word background = AmlEmphasize.word(
			new LyricWord("海", 0, 2000), 0, false, true, 1);
		assertEquals(0.05, normal.upEm(), 1e-9);
		assertEquals(0.1, background.upEm(), 1e-9);
	}

	@Test
	void frameMidAnimationScalesUpAndGlows()
	{
		AmlEmphasize.Word word = AmlEmphasize.word(new LyricWord("海", 0, 2000),
			0, false, false, 1);
		// x=0.5 时 empEasing 达到峰值 1
		AmlEmphasize.Frame mid = AmlEmphasize.evaluate(word, 0, 1000);
		assertEquals(1 + 0.1 * word.amount(), mid.scale(), 1e-6);
		assertTrue(mid.glowAlpha() > 0, "中段应出现辉光");
		assertTrue(mid.offsetYEm() < 0, "中段应处于上浮位置");
	}

	@Test
	void frameSettlesBackAtWordEnd()
	{
		AmlEmphasize.Word word = AmlEmphasize.word(new LyricWord("海", 0, 2000),
			0, false, false, 1);
		AmlEmphasize.Frame end = AmlEmphasize.evaluate(word, 0, 2000);
		assertEquals(1, end.scale(), 1e-6);
		assertEquals(0, end.glowAlpha(), 1e-6);
		assertEquals(0, end.offsetXEm(), 1e-6);
	}

	@Test
	void nonEmphasizedWordsOnlyFloat()
	{
		AmlEmphasize.Word word = AmlEmphasize.word(new LyricWord("A", 0, 2000),
			0, false, false, 1);
		assertFalse(word.emphasize());
		AmlEmphasize.Frame mid = AmlEmphasize.evaluate(word, 0, 1000);
		assertEquals(1, mid.scale(), 1e-9);
		assertEquals(0, mid.glowAlpha(), 1e-9);
		assertTrue(mid.offsetYEm() < 0, "未强调的词也应参与上浮");
	}

	@Test
	void charCountStaggerSplitsTheDuration()
	{
		AmlEmphasize.Word word = AmlEmphasize.word(new LyricWord("海阔", 0, 2000),
			0, false, false, 2);
		assertEquals(2, word.charCount());
		AmlEmphasize.Frame first = AmlEmphasize.evaluate(word, 0, 1000);
		AmlEmphasize.Frame second = AmlEmphasize.evaluate(word, 1, 1000);
		// 后一个字延迟更大，同一时刻上浮更少
		assertTrue(first.offsetYEm() < second.offsetYEm(),
			"逐字延迟应让后一个字上浮更晚");
		// 横向位移以词中心为轴展开
		assertTrue(first.offsetXEm() < second.offsetXEm(),
			"前一个字应向左让位更多");
	}
}
