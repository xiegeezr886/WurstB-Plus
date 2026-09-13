/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.clickgui2.component;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 仿 VAPE GuiComponentContract 的组件契约。
 * 每个组件有位置(x,y)、尺寸(width,height)、可见性、子组件树，
 * 以及渲染/更新/鼠标事件生命周期。
 */
public interface GuiComponentContract
{
	/** 默认组件宽度 */
	double DEFAULT_WIDTH = 110.0;
	
	/** 默认组件高度 */
	double DEFAULT_HEIGHT = 20.0;
	
	/**
	 * 渲染组件自身。
	 */
	void render(GuiGraphics graphics, int mouseX, int mouseY,
		float partialTicks);
	
	/**
	 * 每帧更新（动画、状态）。
	 */
	default void tick()
	{
	}
	
	/**
	 * 获取组件 X 坐标。
	 */
	double getX();
	
	/**
	 * 设置组件 X 坐标。
	 */
	void setX(double x);
	
	/**
	 * 获取组件 Y 坐标。
	 */
	double getY();
	
	/**
	 * 设置组件 Y 坐标。
	 */
	void setY(double y);
	
	/**
	 * 获取组件宽度。
	 */
	double getWidth();
	
	/**
	 * 设置组件宽度。
	 */
	void setWidth(double width);
	
	/**
	 * 获取组件高度。
	 */
	double getHeight();
	
	/**
	 * 设置组件高度。
	 */
	void setHeight(double height);
	
	/**
	 * 组件是否可见（含绑定值隐藏条件）。
	 */
	boolean isVisible();
	
	/**
	 * 鼠标是否悬停在本组件上。
	 */
	boolean isHovered(int mouseX, int mouseY);
	
	/**
	 * 鼠标左键点击。
	 * @return true 表示事件已消费
	 */
	default boolean mouseClicked(double mouseX, double mouseY, int button)
	{
		return false;
	}
	
	/**
	 * 鼠标释放。
	 */
	default boolean mouseReleased(double mouseX, double mouseY, int button)
	{
		return false;
	}
	
	/**
	 * 鼠标拖拽。
	 */
	default boolean mouseDragged(double mouseX, double mouseY, int button)
	{
		return false;
	}
	
	/**
	 * 鼠标滚轮。
	 */
	default boolean mouseScrolled(double mouseX, double mouseY, double delta)
	{
		return false;
	}
}
