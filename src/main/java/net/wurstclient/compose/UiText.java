package net.wurstclient.compose;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.wurstclient.clickgui2.FlatRenderer;

/**
 * Compose {@code Text} 的等价物：带阴影、可着色的文字组件。
 *
 * <p>字号为像素（1.20.1 位图字体为 9px，整数渲染保持清晰）。可选背景
 * （半透明圆角矩形）与文字阴影。</p>
 */
public final class UiText extends UiNode
{
	private String text;
	private int color = 0xFFFFFFFF;
	private float backgroundAlpha;
	private int backgroundColor = 0xFF000000;
	private int backgroundRadius = 3;
	private boolean shadow = true;
	private float paddingH = 4;
	private float paddingV = 2;

	public UiText(String text)
	{
		this.text = text;
	}

	public UiText text(String text)
	{
		this.text = text;
		return this;
	}

	public UiText color(int color)
	{
		this.color = color;
		return this;
	}

	public UiText background(float alpha, int color, int radius)
	{
		backgroundAlpha = alpha;
		backgroundColor = color;
		backgroundRadius = radius;
		return this;
	}

	public UiText shadow(boolean shadow)
	{
		this.shadow = shadow;
		return this;
	}

	public UiText padding(float horizontal, float vertical)
	{
		paddingH = horizontal;
		paddingV = vertical;
		return this;
	}

	@Override
	public float measureWidth(float maxWidth)
	{
		return font().width(text) + paddingH * 2;
	}

	@Override
	public float measureHeight(float width)
	{
		return font().lineHeight + paddingV * 2;
	}

	@Override
	public void layout(float x, float y)
	{
		this.x = x;
		this.y = y;
		width = measureWidth(Float.MAX_VALUE);
		height = measureHeight(width);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY,
		float partialTicks)
	{
		if(text.isEmpty())
			return;
		int left = Math.round(x);
		int top = Math.round(y);
		int right = Math.round(x + width);
		int bottom = Math.round(y + height);
		if(backgroundAlpha > 0.01F)
			FlatRenderer.fillRoundedRect(graphics, left, top, right, bottom,
				backgroundRadius,
				withAlpha(backgroundColor, Math.round(255 * backgroundAlpha)));
		Font font = font();
		int textX = Math.round(x + paddingH);
		int textY = Math.round(y + paddingV);
		if(shadow)
			graphics.drawString(font, net.wurstclient.clickgui2.PingFangFont.text(text),
				textX + 1, textY + 1, withAlpha(0, 145), false);
		graphics.drawString(font,
			net.wurstclient.clickgui2.PingFangFont.text(text), textX, textY,
			color, false);
	}

	private static Font font()
	{
		return Minecraft.getInstance().font;
	}

	private static int withAlpha(int color, int alpha)
	{
		return Math.max(0, Math.min(255, alpha)) << 24 | color & 0xFFFFFF;
	}
}
