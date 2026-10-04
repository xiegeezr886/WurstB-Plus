package net.wurstclient.twilight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 校验内容头的外边距取值。
 *
 * <p>
 * 这里曾经用的是内容区的 24px 常量，而参考是 {@code clamp(36px, 6vw, 84px)}——
 * 设计宽下应当是 84px，标题几乎贴窗口边就是那么来的。
 */
final class TwilightHeaderLayoutTest
{
	@Test
	void takesTheClampUpperBoundAtTheDesignWidth()
	{
		// 6% of 1500 is 90, so the 84px cap wins
		assertEquals(84, TwilightShellLayout.headerMarginX(1500, 1F));
	}

	@Test
	void scalesWithTheViewportInBetween()
	{
		// 6% of 800 is 48, inside the 36..84 band
		assertEquals(48, TwilightShellLayout.headerMarginX(800, 1F));
	}

	@Test
	void neverDropsBelowTheMinimum()
	{
		// 6% of 300 is 18, raised to the 36px floor
		assertEquals(36, TwilightShellLayout.headerMarginX(300, 1F));
		assertTrue(TwilightShellLayout.headerMarginX(100, 1F) >= 36);
	}

	@Test
	void followsTheCanvasScale()
	{
		assertEquals(42, TwilightShellLayout.headerMarginX(1500, 0.5F));
	}
}
