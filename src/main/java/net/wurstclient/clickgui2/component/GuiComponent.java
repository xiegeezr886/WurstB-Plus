/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.clickgui2.component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 仿 VAPE GuiComponent 的基础组件。
 * 维护子组件树、悬停状态、激活监听器，
 * 负责把渲染/鼠标事件向下分发。
 */
public abstract class GuiComponent implements GuiComponentContract
{
	protected double x;
	protected double y;
	protected double width = DEFAULT_WIDTH;
	protected double height = DEFAULT_HEIGHT;
	protected boolean visible = true;
	protected boolean hovered;
	protected boolean active;
	protected boolean acceptsMouseInput = true;
	private boolean superSoftTheme;
	
	private final List<GuiComponent> children = new ArrayList<>();
	private final List<Runnable> activationListeners = new ArrayList<>();
	
	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY,
		float partialTicks)
	{
		if(!isVisible())
			return;
		
		updateHover(mouseX, mouseY);
		renderSelf(graphics, mouseX, mouseY, partialTicks);
		
		if(isChildRenderingSuppressed())
			return;
		
		for(GuiComponent child : children)
			if(child.isVisible())
				child.render(graphics, mouseX, mouseY, partialTicks);
	}
	
	/**
	 * 渲染组件自身（不含子组件）。
	 */
	protected abstract void renderSelf(GuiGraphics graphics, int mouseX,
		int mouseY, float partialTicks);
	
	@Override
	public void tick()
	{
		for(GuiComponent child : children)
			child.tick();
	}
	
	@Override
	public double getX()
	{
		return x;
	}
	
	@Override
	public void setX(double x)
	{
		this.x = x;
	}
	
	@Override
	public double getY()
	{
		return y;
	}
	
	@Override
	public void setY(double y)
	{
		this.y = y;
	}
	
	@Override
	public double getWidth()
	{
		return Math.max(width, minWidth());
	}
	
	@Override
	public void setWidth(double width)
	{
		this.width = width;
	}
	
	@Override
	public double getHeight()
	{
		return Math.max(height, minHeight());
	}
	
	@Override
	public void setHeight(double height)
	{
		this.height = height;
	}
	
	/**
	 * 组件最小宽度（内容需求）。
	 */
	protected double minWidth()
	{
		return 0;
	}
	
	/**
	 * 组件最小高度（内容需求）。
	 */
	protected double minHeight()
	{
		return 0;
	}
	
	@Override
	public boolean isVisible()
	{
		return visible;
	}
	
	public void setVisible(boolean visible)
	{
		this.visible = visible;
	}
	
	@Override
	public boolean isHovered(int mouseX, int mouseY)
	{
		return mouseX >= x && mouseX < x + getWidth()
			&& mouseY >= y && mouseY < y + getHeight();
	}
	
	protected void updateHover(int mouseX, int mouseY)
	{
		hovered = isHovered(mouseX, mouseY);
	}
	
	public boolean isHovered()
	{
		return hovered;
	}
	
	public boolean isActive()
	{
		return active;
	}
	
	public void setActive(boolean active)
	{
		if(this.active == active)
			return;
		this.active = active;
		for(Runnable listener : activationListeners)
			listener.run();
	}
	
	public void addActivationListener(Runnable listener)
	{
		activationListeners.add(listener);
	}
	
	public void addChild(GuiComponent child)
	{
		children.add(child);
	}
	
	public void addChildren(GuiComponent... newChildren)
	{
		Collections.addAll(children, newChildren);
	}
	
	public void removeChild(GuiComponent child)
	{
		children.remove(child);
	}
	
	public List<GuiComponent> getChildren()
	{
		return children;
	}
	
	public void clearChildren()
	{
		children.clear();
	}
	
	protected boolean isChildRenderingSuppressed()
	{
		return false;
	}
	
	public boolean acceptsMouseInput()
	{
		return acceptsMouseInput;
	}
	
	public void setAcceptsMouseInput(boolean acceptsMouseInput)
	{
		this.acceptsMouseInput = acceptsMouseInput;
	}

	public boolean usesSuperSoftTheme()
	{
		return superSoftTheme;
	}

	public void setSuperSoftTheme(boolean superSoftTheme)
	{
		this.superSoftTheme = superSoftTheme;
	}
	
	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button)
	{
		if(!isVisible() || !acceptsMouseInput)
			return false;
		
		// 子组件逆序优先（后渲染的在顶层）
		List<GuiComponent> reversed =
			new ArrayList<>(children);
		Collections.reverse(reversed);
		for(GuiComponent child : reversed)
			if(child.isVisible() && child.acceptsMouseInput()
				&& child.mouseClicked(mouseX, mouseY, button))
				return true;
		
		return onClick(mouseX, mouseY, button);
	}
	
	/**
	 * 处理点击（子组件已优先处理）。
	 */
	protected boolean onClick(double mouseX, double mouseY, int button)
	{
		return false;
	}
	
	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button)
	{
		if(!isVisible() || !acceptsMouseInput)
			return false;
		
		List<GuiComponent> reversed =
			new ArrayList<>(children);
		Collections.reverse(reversed);
		for(GuiComponent child : reversed)
			if(child.isVisible() && child.acceptsMouseInput()
				&& child.mouseReleased(mouseX, mouseY, button))
				return true;
		
		return onRelease(mouseX, mouseY, button);
	}
	
	protected boolean onRelease(double mouseX, double mouseY, int button)
	{
		return false;
	}
	
	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button)
	{
		if(!isVisible() || !acceptsMouseInput)
			return false;
		
		List<GuiComponent> reversed =
			new ArrayList<>(children);
		Collections.reverse(reversed);
		for(GuiComponent child : reversed)
			if(child.isVisible() && child.acceptsMouseInput()
				&& child.mouseDragged(mouseX, mouseY, button))
				return true;
		
		return onDrag(mouseX, mouseY, button);
	}
	
	protected boolean onDrag(double mouseX, double mouseY, int button)
	{
		return false;
	}
	
	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double delta)
	{
		if(!isVisible() || !acceptsMouseInput)
			return false;
		
		List<GuiComponent> reversed =
			new ArrayList<>(children);
		Collections.reverse(reversed);
		for(GuiComponent child : reversed)
			if(child.isVisible() && child.acceptsMouseInput()
				&& child.mouseScrolled(mouseX, mouseY, delta))
				return true;
		
		return onScroll(mouseX, mouseY, delta);
	}
	
	protected boolean onScroll(double mouseX, double mouseY, double delta)
	{
		return false;
	}
	
	/**
	 * 布局子组件（按需重写）。
	 */
	public void layoutChildren()
	{
	}
}
