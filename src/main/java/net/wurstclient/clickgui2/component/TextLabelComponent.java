/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.clickgui2.component;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 仿 VAPE SimpleTextLabelComponent 的文本标签。
 */
public class TextLabelComponent extends GuiComponent
{
	protected String text;
	protected int color = 0xFFD1D1D1;
	
	public TextLabelComponent(String text)
	{
		this.text = text;
	}
	
	public TextLabelComponent(String text, int color)
	{
		this.text = text;
		this.color = color;
	}
	
	public void setText(String text)
	{
		this.text = text;
	}
	
	public String getText()
	{
		return text;
	}
	
	public void setColor(int color)
	{
		this.color = color;
	}
	
	@Override
	protected void renderSelf(GuiGraphics graphics, int mouseX, int mouseY,
		float partialTicks)
	{
		Font font = Minecraft.getInstance().font;
		graphics.drawString(font, text, (int)x, (int)y + 3, color, false);
	}
	
	@Override
	protected double minWidth()
	{
		Font font = Minecraft.getInstance().font;
		return font.width(text);
	}
	
	@Override
	protected double minHeight()
	{
		return 11;
	}
}
