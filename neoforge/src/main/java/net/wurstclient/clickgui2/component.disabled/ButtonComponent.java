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
import net.wurstclient.clickgui2.FlatRenderer;

/**
 * 仿 VAPE ActionButtonComponent/TextButton 的按钮组件。
 */
public class ButtonComponent extends GuiComponent
{
	protected String text;
	protected int fillColor = 0xFF252426;
	protected int hoverColor = 0xFF2F2E2F;
	protected int textColor = 0xFFD1D1D1;
	protected Runnable action;
	
	public ButtonComponent(String text, Runnable action)
	{
		this.text = text;
		this.action = action;
	}
	
	public ButtonComponent(String text, Runnable action, int fillColor)
	{
		this.text = text;
		this.action = action;
		this.fillColor = fillColor;
	}
	
	public void setText(String text)
	{
		this.text = text;
	}
	
	public void setColors(int fillColor, int hoverColor, int textColor)
	{
		this.fillColor = fillColor;
		this.hoverColor = hoverColor;
		this.textColor = textColor;
	}
	
	@Override
	protected void renderSelf(GuiGraphics graphics, int mouseX, int mouseY,
		float partialTicks)
	{
		int bg = hovered ? hoverColor : fillColor;
		FlatRenderer.fillRoundedRect(graphics, (int)x, (int)y,
			(int)(x + getWidth()), (int)(y + getHeight()), 3, bg);
		
		Font font = Minecraft.getInstance().font;
		int textX = (int)(x + getWidth() / 2 - font.width(text) / 2);
		int textY = (int)(y + getHeight() / 2 - font.lineHeight / 2);
		graphics.drawString(font, text, textX, textY, textColor, false);
	}
	
	@Override
	protected boolean onClick(double mouseX, double mouseY, int button)
	{
		if(button != 0)
			return false;
		if(action != null)
			action.run();
		return true;
	}
	
	@Override
	protected double minHeight()
	{
		return 16;
	}
}
