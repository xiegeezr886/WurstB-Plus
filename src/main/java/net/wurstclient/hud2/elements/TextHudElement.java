package net.wurstclient.hud2.elements;

import java.awt.Color;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.wurstclient.WurstClient;
import net.wurstclient.hud2.HudElement;
import net.wurstclient.gui.visual.VisualTheme;
import net.wurstclient.settings.ColorSetting;

public abstract class TextHudElement extends HudElement
{
	// 逐元素设置。默认是不透明白，就是原来的 VisualTheme.TEXT（0xFFFFFFFF），
	// 所以默认观感不变。放在基类上，9 个子类一次全都有文字颜色。
	private final ColorSetting textColor =
		new ColorSetting("Text color", new Color(255, 255, 255));

	protected TextHudElement(String id, String name)
	{
		super(id, name);
		addSetting(textColor);
	}

	protected abstract String getText();

	@Override
	public boolean renderEditorPreview()
	{
		return true;
	}

	@Override
	public int getWidth()
	{
		return WurstClient.MC.font.width(getText()) + 4;
	}

	@Override
	public int getHeight()
	{
		return WurstClient.MC.font.lineHeight + 2;
	}

	@Override
	public void render(GuiGraphics graphics, int x, int y, float partialTicks)
	{
		Font font = WurstClient.MC.font;
		String text = getText();
		graphics.fill(x, y, x + font.width(text) + 4, y + font.lineHeight + 2,
			VisualTheme.SURFACE_50);
		// 用 getColor().getRGB() 而不是 getColorI()：后者会把 alpha 强制成不透明
		// （ColorSetting.java:72），用户调的透明度会被吃掉。
		graphics.drawString(font, text, x + 2, y + 1,
			textColor.getColor().getRGB(), false);
	}
}
