/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.clickgui2.component;

import java.awt.Color;

import net.minecraft.client.gui.GuiGraphics;
import net.wurstclient.clickgui2.FlatRenderer;
import net.wurstclient.settings.ColorSetting;

/**
 * 仿 VAPE ColorValueEditorComponent 的颜色行。
 * 点击弹出 RGB 滑块。
 */
public class ColorComponent extends ValueRowComponent
{
	private final ColorSetting colorSetting;
	private boolean expanded;
	
	public ColorComponent(ColorSetting colorSetting)
	{
		super(colorSetting);
		this.colorSetting = colorSetting;
	}
	
	@Override
	protected void renderSelf(GuiGraphics graphics, int mouseX, int mouseY,
		float partialTicks)
	{
		drawLabel(graphics, mouseX, mouseY);
		
		int swatchX = (int)(x + getWidth() - 28);
		int swatchY = (int)y + 3;
		Color color = colorSetting.getColor();
		FlatRenderer.fillRoundedRect(graphics, swatchX, swatchY,
			swatchX + 24, swatchY + 10, 3, color.getRGB());
		// 边框
		graphics.fill(swatchX, swatchY, swatchX + 1, swatchY + 10,
			0xFF000000);
		graphics.fill(swatchX + 23, swatchY, swatchX + 24, swatchY + 10,
			0xFF000000);
		graphics.fill(swatchX, swatchY, swatchX + 24, swatchY + 1,
			0xFF000000);
		graphics.fill(swatchX, swatchY + 9, swatchX + 24, swatchY + 10,
			0xFF000000);
		
		if(expanded)
		{
			int panelX = (int)x;
			int panelY = (int)(y + getHeight());
			int panelW = (int)getWidth();
			int panelH = 60;
			FlatRenderer.fillRoundedRect(graphics, panelX, panelY,
				panelX + panelW, panelY + panelH, 3, 0xFF1A191A);
			
			float[] hsb = Color.RGBtoHSB(color.getRed(),
				color.getGreen(), color.getBlue(), null);
			float hue = hsb[0];
			float sat = hsb[1];
			float bri = hsb[2];
			
			// 简易三通道滑块
			drawChannel(graphics, panelX + 4, panelY + 6, panelW - 8,
				"R", color.getRed(), 0xFFFF5555, mouseX, mouseY);
			drawChannel(graphics, panelX + 4, panelY + 22, panelW - 8,
				"G", color.getGreen(), 0xFF55FF55, mouseX, mouseY);
			drawChannel(graphics, panelX + 4, panelY + 38, panelW - 8,
				"B", color.getBlue(), 0xFF5555FF, mouseX, mouseY);
			
			// 简化的拖拽处理（每帧根据鼠标位置更新）
			// 完整实现需要记录每通道的 dragging 状态
		}
	}
	
	private void drawChannel(GuiGraphics graphics, int cx, int cy, int cw,
		String label, int value, int channelColor, int mouseX, int mouseY)
	{
		graphics.drawString(net.minecraft.client.Minecraft.getInstance()
			.font, label, cx, cy + 1, channelColor, false);
		int trackX = cx + 12;
		int trackW = cw - 12;
		graphics.fill(trackX, cy + 4, trackX + trackW, cy + 5,
			0xFF2A292A);
		int progressX = trackX + (trackW * value / 255);
		graphics.fill(trackX, cy + 4, progressX, cy + 5, channelColor);
	}
	
	@Override
	protected boolean onClick(double mouseX, double mouseY, int button)
	{
		if(button != 0)
			return false;
		int swatchX = (int)(x + getWidth() - 28);
		if(mouseX >= swatchX)
		{
			expanded = !expanded;
			return true;
		}
		if(expanded)
		{
			expanded = false;
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
