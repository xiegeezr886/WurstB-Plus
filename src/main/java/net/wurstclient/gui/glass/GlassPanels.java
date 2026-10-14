package net.wurstclient.gui.glass;

import net.minecraft.client.gui.GuiGraphics;
import net.wurstclient.clickgui2.FlatRenderer;

/**
 * 卡片级玻璃：HUD 元素、通知卡这类"一块底板 + 可选描边"的地方走这里。
 *
 * <p>
 * 与 {@link GlassButtons} 的分工：那边是原版控件（按钮/滑块/复选框/滚动条）的策略，
 * 这边只画一块矩形，圆角与透明度由调用方给。玻璃不可用时**按调用方原来的扁平样式
 * 画回去**（填色 + 可选描边），所以每个调用点的观感在回退路径上与改造前逐像素一致。</p>
 */
public final class GlassPanels
{
	private GlassPanels()
	{}

	/** 只有底色，没有描边。 */
	public static void draw(GuiGraphics graphics, int x1, int y1, int x2, int y2,
		int radius, int fill)
	{
		draw(graphics, x1, y1, x2, y2, radius, fill, 0, 1F);
	}

	/** 底色 + 描边（描边透明时等于不画）。 */
	public static void draw(GuiGraphics graphics, int x1, int y1, int x2, int y2,
		int radius, int fill, int outline)
	{
		draw(graphics, x1, y1, x2, y2, radius, fill, outline, 1F);
	}

	public static void draw(GuiGraphics graphics, int x1, int y1, int x2, int y2,
		int radius, int fill, int outline, float opacity)
	{
		float clamped = Math.max(0, Math.min(1, opacity));
		if(clamped <= 0 || x2 <= x1 || y2 <= y1)
			return;

		// HUD 卡片没有悬停态，focus 传 0：材质与控件那条路一致（模糊 4.8、折射 24、
		// 磨砂 1、底色 0），只是不带悬停的高光增量。
		if(GlassButtons.renderPanel(graphics, x1, y1, x2 - x1, y2 - y1, radius,
			0F, clamped))
			return;

		FlatRenderer.fillRoundedRect(graphics, x1, y1, x2, y2, radius,
			withOpacity(fill, clamped));
		if((outline >>> 24) != 0)
			FlatRenderer.drawRoundedOutline(graphics, x1, y1, x2, y2, radius,
				withOpacity(outline, clamped));
	}

	/** 把颜色的 alpha 乘上不透明度；保留原色的 RGB。 */
	static int withOpacity(int color, float opacity)
	{
		float clamped = Math.max(0, Math.min(1, opacity));
		int alpha = Math.round((color >>> 24) * clamped);
		return Math.max(0, Math.min(255, alpha)) << 24 | color & 0xFFFFFF;
	}
}
