package net.wurstclient.infinitegui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

/**
 * 三项的文案格式：逐字照无限GUI 的三个 {@code DrawContent}。
 *
 * <p>
 * 这些字符串是用户直接看到的东西，也是最容易在移植里"顺手改好看"的地方——所以钉住：
 * 时间带日期时是 {@code "%Y-%m-%d | %H:%M:%S"}，FPS 的前后缀默认 {@code [}…{@code FPS]}，
 * CPS 两边都显示时中间是 {@code " | "}。</p>
 */
class InfiniteTextTest
{
	private static final LocalDateTime FIXED =
		LocalDateTime.of(2026, 10, 15, 23, 59, 59);

	@Test
	void timeMatchesTheUpstreamFormat()
	{
		assertEquals("2026-10-15 | 23:59:59",
			InfiniteText.time(FIXED, true));
		assertEquals("23:59:59", InfiniteText.time(FIXED, false));
	}

	@Test
	void fpsMatchesTheUpstreamFormat()
	{
		assertEquals("[120FPS]", InfiniteText.fps("[", 120, "FPS]", false, 0));
		assertEquals("[120 | 60FPS]",
			InfiniteText.fps("[", 120, "FPS]", true, 60));
	}

	@Test
	void cpsMatchesTheUpstreamFormat()
	{
		assertEquals("[CPS: 3 | 1]",
			InfiniteText.cps("[CPS: ", 3, 1, "]", true, true));
		assertEquals("[CPS: 3]",
			InfiniteText.cps("[CPS: ", 3, 1, "]", true, false));
		assertEquals("[CPS: 1]",
			InfiniteText.cps("[CPS: ", 3, 1, "]", false, true));
	}
}
