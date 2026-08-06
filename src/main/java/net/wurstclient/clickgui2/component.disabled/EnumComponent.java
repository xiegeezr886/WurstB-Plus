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
import net.wurstclient.settings.EnumSetting;

/**
 * 仿 VAPE CompactListValueComponent 的枚举下拉行。
 */
public class EnumComponent<T extends Enum<T>> extends ValueRowComponent
{
	private final EnumSetting<T> enumSetting;
	private boolean expanded;
	
	public EnumComponent(EnumSetting<T> enumSetting)
	{
		super(enumSetting);
		this.enumSetting = enumSetting;
	}
	
	@Override
	protected void renderSelf(GuiGraphics graphics, int mouseX, int mouseY,
		float partialTicks)
	{
		drawLabel(graphics, mouseX, mouseY);
		
		if(!expanded)
		{
			// 值按钮
			int btnX = (int)(x + getWidth() * 0.45);
			int btnW = (int)(getWidth() * 0.5) - 4;
			int btnY = (int)y + 1;
			int btnH = (int)getHeight() - 2;
			int bg = hovered ? 0xFF2F2E2F : 0xFF252426;
			FlatRenderer.fillRoundedRect(graphics, btnX, btnY,
				btnX + btnW, btnY + btnH, 3, bg);
			String value = enumSetting.getSelected().name();
			Font font = Minecraft.getInstance().font;
			graphics.drawString(font, value, btnX + 4, btnY + 3,
				0xFFD1D1D1, false);
			return;
		}
		
		// 展开下拉
		int panelX = (int)x;
		int panelY = (int)(y + getHeight());
		int panelW = (int)getWidth();
		FlatRenderer.fillRoundedRect(graphics, panelX, panelY,
			panelX + panelW, panelY + 16 * enumSetting.getValues().length,
			3, 0xFF1A191A);
		Font font = Minecraft.getInstance().font;
		T[] values = enumSetting.getValues();
		for(int i = 0; i < values.length; i++)
		{
			int rowY = panelY + i * 16;
			boolean rowHover = mouseX >= panelX && mouseX < panelX + panelW
				&& mouseY >= rowY && mouseY < rowY + 16;
			if(rowHover)
				graphics.fill(panelX + 1, rowY, panelX + panelW - 1,
					rowY + 16, 0xFF2F2E2F);
			boolean selected = values[i] == enumSetting.getSelected();
			graphics.drawString(font, values[i].name(), panelX + 4,
				rowY + 3, selected ? 0xFFF0F0F0 : 0xFFA3A3A3, false);
		}
	}
	
	@Override
	protected boolean onClick(double mouseX, double mouseY, int button)
	{
		if(button != 0)
			return false;
		
		if(expanded)
		{
			// 点击下拉项
			int panelY = (int)(y + getHeight());
			int index = (int)((mouseY - panelY) / 16);
			T[] values = enumSetting.getValues();
			if(mouseX >= x && mouseX < x + getWidth()
				&& index >= 0 && index < values.length)
				enumSetting.setSelected(values[index]);
			expanded = false;
			return true;
		}
		
		// 点击值按钮展开
		int btnX = (int)(x + getWidth() * 0.45);
		if(mouseX >= btnX)
		{
			expanded = true;
			return true;
		}
		return false;
	}
	
	@Override
	protected double minHeight()
	{
		return 16;
	}
}
