package net.wurstclient.hud2.elements;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.wurstclient.WurstClient;
import net.wurstclient.hud2.HudElement;

public abstract class TextHudElement extends HudElement
{
	protected TextHudElement(String id, String name)
	{
		super(id, name);
	}

	protected abstract String getText();

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
			0x80000000);
		graphics.drawString(font, text, x + 2, y + 1, 0xFFFFFFFF, false);
	}
}
