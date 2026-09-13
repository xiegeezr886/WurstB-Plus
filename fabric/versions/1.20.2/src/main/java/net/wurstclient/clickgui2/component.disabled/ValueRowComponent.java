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
import net.minecraft.network.chat.Component;
import net.wurstclient.settings.Setting;

/**
 * 仿 VAPE 值组件行的基础类。
 * 显示设置名称 + 值，右侧可放置控件。
 */
public abstract class ValueRowComponent extends GuiComponent
{
	protected final Setting setting;
	protected boolean hovered;
	
	protected ValueRowComponent(Setting setting)
	{
		this.setting = setting;
		height = 16;
	}
	
	public Setting getSetting()
	{
		return setting;
	}
	
	protected void drawLabel(GuiGraphics graphics, int mouseX, int mouseY)
	{
		Font font = Minecraft.getInstance().font;
		String label = setting.getName();
		int labelColor = 0xFFD1D1D1;
		if(isHovered(mouseX, mouseY))
		{
			labelColor = 0xFFF0F0F0;
			// 工具提示：显示描述
		}
		graphics.drawString(font, label, (int)x + 4, (int)y + 3,
			labelColor, false);
	}
	
	protected void drawValue(GuiGraphics graphics, String value)
	{
		Font font = Minecraft.getInstance().font;
		int valueWidth = font.width(value);
		graphics.drawString(font, value,
			(int)(x + getWidth() - valueWidth - 4), (int)y + 3,
			0xFFA3A3A3, false);
	}
	
	@Override
	protected double minHeight()
	{
		return 16;
	}
	
	protected Component asComponent(String s)
	{
		return Component.literal(s);
	}
}
