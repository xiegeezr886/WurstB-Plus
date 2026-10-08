/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.clickgui2;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.wurstclient.WurstClient;
import net.wurstclient.settings.SettingComponent;

/**
 * 声明自己就是「设置能渲染成的东西」的一种。这样 {@code Setting} 只依赖
 * {@link SettingComponent} 那个空接口，不必反过来依赖本包 —— 删掉或替换
 * 这套 GUI 不再会打断设置层。见 {@link SettingComponent} 的说明。
 */
public abstract class Component implements SettingComponent
{
	protected static final Minecraft MC = WurstClient.MC;
	protected static final WurstClient WURST = WurstClient.INSTANCE;
	
	private int x;
	private int y;
	private int width;
	private int height;
	private int indent;

	private Window parent;
	
	public void handleMouseClick(double mouseX, double mouseY, int mouseButton)
	{
		
	}
	
	public abstract void render(GuiGraphics context, int mouseX, int mouseY,
		float partialTicks);
	
	public abstract int getDefaultWidth();
	
	public abstract int getDefaultHeight();
	
	public int getX()
	{
		return x;
	}
	
	public void setX(int x)
	{
		if(this.x != x)
			invalidateParent();
		
		this.x = x;
	}
	
	public int getY()
	{
		return y;
	}
	
	public void setY(int y)
	{
		if(this.y != y)
			invalidateParent();
		
		this.y = y;
	}
	
	public int getWidth()
	{
		return width;
	}
	
	public void setWidth(int width)
	{
		if(this.width != width)
			invalidateParent();
		
		this.width = width;
	}
	
	public int getHeight()
	{
		return height;
	}
	
	public void setHeight(int height)
	{
		if(this.height != height)
			invalidateParent();
		
		this.height = height;
	}
	
	public int getIndent()
	{
		return indent;
	}

	public void setIndent(int indent)
	{
		if(this.indent != indent)
			invalidateParent();

		this.indent = indent;
	}

	public Window getParent()
	{
		return parent;
	}
	
	public void setParent(Window parent)
	{
		this.parent = parent;
	}
	
	private void invalidateParent()
	{
		if(parent != null)
			parent.invalidate();
	}
	
	protected boolean isHovering(int mouseX, int mouseY)
	{
		int x1 = getX();
		int x2 = x1 + getWidth();
		int y1 = getY();
		int y2 = y1 + getHeight();
		
		Window parent = getParent();
		if(parent == null)
			return false;

		boolean scrollEnabled = parent.isScrollingEnabled();
		int scroll = scrollEnabled ? parent.getScrollOffset() : 0;
		
		return mouseX >= x1 && mouseY >= y1 && mouseX < x2 && mouseY < y2
			&& mouseY >= -scroll && mouseY < parent.getHeight() - 13 - scroll;
	}
}
