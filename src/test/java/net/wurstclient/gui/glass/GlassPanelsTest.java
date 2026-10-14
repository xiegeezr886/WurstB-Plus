package net.wurstclient.gui.glass;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * 卡片级玻璃的纯算式。绘制本身要 GL 上下文，测不到；回退路径的颜色换算可以先钉住——
 * 玻璃装不上时元素必须逐像素画回改造前的样子，那个 alpha 乘法错了就会"回退成一片黑"。
 */
class GlassPanelsTest
{
	@Test
	void keepsTheColorWhenFullyOpaque()
	{
		assertEquals(0x80FFFFFF, GlassPanels.withOpacity(0x80FFFFFF, 1F));
		assertEquals(0xFF123456, GlassPanels.withOpacity(0xFF123456, 1F));
	}

	@Test
	void multipliesTheAlphaOnly()
	{
		// 记分板的 0x78000000：半透明黑，减半后是 0x3C000000
		assertEquals(0x3C000000, GlassPanels.withOpacity(0x78000000, 0.5F));
		assertEquals(0x6E000000, GlassPanels.withOpacity(0x6E000000, 1F));
		assertEquals(0xFF102030, GlassPanels.withOpacity(0xFF102030, 1F));
		// 通知卡的 0xD90A0A0A 淡出到三成
		assertEquals(0x410A0A0A,
			GlassPanels.withOpacity(0xD90A0A0A, 0.3F));
	}

	@Test
	void roundsInsteadOfTruncating()
	{
		// 0xFF * 0.5 = 127.5 → 128，不是 127
		assertEquals(0x80FFFFFF, GlassPanels.withOpacity(0xFFFFFFFF, 0.5F));
	}

	@Test
	void clampsTheOpacityToZeroOne()
	{
		assertEquals(0xFFFFFFFF, GlassPanels.withOpacity(0xFFFFFFFF, 4F));
		assertEquals(0x00FFFFFF, GlassPanels.withOpacity(0xFFFFFFFF, -1F));
		assertEquals(0x00FFFFFF, GlassPanels.withOpacity(0xFFFFFFFF, 0F));
	}

	@Test
	void aTransparentColorStaysTransparent()
	{
		assertEquals(0x00000000, GlassPanels.withOpacity(0x00000000, 1F));
		assertEquals(0x00FFFFFF, GlassPanels.withOpacity(0x00FFFFFF, 1F));
	}
}
