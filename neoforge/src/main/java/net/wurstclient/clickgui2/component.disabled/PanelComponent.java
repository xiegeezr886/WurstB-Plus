/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.clickgui2.component;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 仿 VAPE PanelComponent 的垂直堆叠面板。
 * 子组件从上到下排列，支持间距与内边距。
 */
public class PanelComponent extends GuiComponent
{
	protected double spacing = 4.0;
	protected double paddingTop;
	protected double paddingBottom;
	protected boolean autoHeight = true;
	
	public PanelComponent(double x, double y, double width)
	{
		this.x = x;
		this.y = y;
		this.width = width;
	}
	
	public void setSpacing(double spacing)
	{
		this.spacing = spacing;
	}
	
	public void setPadding(double top, double bottom)
	{
		paddingTop = top;
		paddingBottom = bottom;
	}
	
	@Override
	protected void renderSelf(GuiGraphics graphics, int mouseX, int mouseY,
		float partialTicks)
	{
		// 面板自身不绘制背景，由子类或 Frame 决定
	}
	
	@Override
	public void layoutChildren()
	{
		double cursorY = paddingTop;
		for(GuiComponent child : getChildren())
		{
			if(!child.isVisible())
				continue;
			child.setX(x);
			child.setY(y + cursorY);
			child.setWidth(getWidth());
			if(child instanceof PanelComponent panel)
				panel.layoutChildren();
			cursorY += child.getHeight() + spacing;
		}
		if(autoHeight)
			height = cursorY - spacing + paddingBottom;
	}
	
	@Override
	public double getHeight()
	{
		if(autoHeight)
		{
			layoutChildren();
			return height;
		}
		return super.getHeight();
	}
	
	@Override
	protected double minHeight()
	{
		double total = paddingTop + paddingBottom;
		List<GuiComponent> children = getChildren();
		for(int i = 0; i < children.size(); i++)
		{
			total += children.get(i).getHeight();
			if(i < children.size() - 1)
				total += spacing;
		}
		return total;
	}
}
