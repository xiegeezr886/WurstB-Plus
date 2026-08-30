/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.clickgui2;

import java.util.ArrayList;
import net.minecraft.client.gui.Font;
import net.minecraft.util.Mth;
import net.wurstclient.WurstClient;

public class Window
{
	private static final int MIN_WIDTH = 104;
	private static final int TITLE_BAR_HEIGHT = 13;

	private String title;
	private int x;
	private int y;
	private int width;
	private int height;
	
	private boolean valid;
	private final ArrayList<Component> children = new ArrayList<>();
	
	private boolean dragging;
	private int dragOffsetX;
	private int dragOffsetY;
	
	private boolean minimized;
	private boolean minimizable = true;
	
	private boolean pinned;
	private boolean pinnable = true;
	
	private boolean closable;
	private boolean closing;
	
	private boolean invisible;
	
	private boolean fixedWidth;
	private int innerHeight;
	private int maxInnerHeight;
	private int scrollOffset;
	private boolean scrollingEnabled;
	
	private boolean draggingScrollbar;
	private int scrollbarDragOffsetY;
	
	public Window(String title)
	{
		this.title = title;
	}
	
	public final String getTitle()
	{
		return title;
	}
	
	public final void setTitle(String title)
	{
		this.title = title;
	}
	
	/**
	 * Returns the X position of the window, adjusted to fit inside the screen.
	 */
	public final int getX()
	{
		int scaledWidth = WurstClient.MC.getWindow().getGuiScaledWidth();
		int result;
		if(width > scaledWidth)
			result = Math.max(0, x);
		else
			result = Mth.clamp(x, 0, Math.max(0, scaledWidth - width));
		System.out.println("[WURST-DEBUG] getX title=" + title
			+ " x=" + x + " width=" + width
			+ " scaledWidth=" + scaledWidth + " result=" + result);
		return result;
	}
	
	/**
	 * Returns the actual X position of the window, without any adjustments.
	 * This should only be used for saving the window's position to the config
	 * file.
	 */
	public final int getActualX()
	{
		return x;
	}
	
	public final void setX(int x)
	{
		this.x = x;
	}
	
	/**
	 * Returns the Y position of the window, adjusted to fit inside the screen.
	 */
	public final int getY()
	{
		int scaledHeight = WurstClient.MC.getWindow().getGuiScaledHeight();
		int result;
		if(height > scaledHeight)
			result = Math.max(0, y);
		else
			result = Mth.clamp(y, 0,
				Math.max(0, scaledHeight - TITLE_BAR_HEIGHT));
		System.out.println("[WURST-DEBUG] getY title=" + title
			+ " y=" + y + " scaledHeight=" + scaledHeight + " result=" + result);
		return result;
	}
	
	/**
	 * Returns the actual Y position of the window, without any adjustments.
	 * This should only be used for saving the window's position to the config
	 * file.
	 */
	public final int getActualY()
	{
		return y;
	}
	
	public final void setY(int y)
	{
		this.y = y;
	}
	
	public final int getWidth()
	{
		return width;
	}
	
	public final void setWidth(int width)
	{
		if(fixedWidth)
			return;
		
		if(this.width != width)
			invalidate();
		
		this.width = width;
	}
	
	public final int getHeight()
	{
		return height;
	}
	
	public final void setHeight(int height)
	{
		if(this.height != height)
			invalidate();
		
		this.height = height;
	}
	
	public final void pack()
	{
		int maxChildWidth = 0;
		for(Component c : children)
			if(c.getDefaultWidth() > maxChildWidth)
				maxChildWidth = c.getDefaultWidth();
		maxChildWidth += 4;
		
		Font tr = WurstClient.MC.font;
		int titleBarWidth = tr.width(title) + 4;
		if(minimizable)
			titleBarWidth += 11;
		if(pinnable)
			titleBarWidth += 11;
		if(closable)
			titleBarWidth += 11;
		
		int childrenHeight = TITLE_BAR_HEIGHT;
		for(Component c : children)
			childrenHeight += c.getHeight() + 2;
		childrenHeight += 2;
		
		if(maxInnerHeight > 0
			&& childrenHeight > maxInnerHeight + TITLE_BAR_HEIGHT)
		{
			setWidth(Math.max(MIN_WIDTH,
				Math.max(maxChildWidth + 3, titleBarWidth)));
			setHeight(maxInnerHeight + TITLE_BAR_HEIGHT);
			
		}else
		{
			setWidth(Math.max(MIN_WIDTH,
				Math.max(maxChildWidth, titleBarWidth)));
			setHeight(childrenHeight);
		}
		
		validate();
	}
	
	public final void validate()
	{
		if(valid)
			return;
		
		int offsetY = 2;
		int cWidth = width - 4;
		for(Component c : children)
		{
			c.setX(2 + c.getIndent());
			c.setY(offsetY);
			c.setWidth(cWidth - c.getIndent());
			offsetY += c.getHeight() + 2;
		}
		
		innerHeight = offsetY;
		
		if(maxInnerHeight == 0 || innerHeight < maxInnerHeight)
			setHeight(innerHeight + TITLE_BAR_HEIGHT);
		else
			setHeight(maxInnerHeight + TITLE_BAR_HEIGHT);
		
		scrollingEnabled = innerHeight + TITLE_BAR_HEIGHT > height;
		if(scrollingEnabled)
			cWidth -= 3;
		
		scrollOffset = Math.min(scrollOffset, 0);
		scrollOffset = Math.max(scrollOffset,
			-innerHeight + height - TITLE_BAR_HEIGHT);
		
		for(Component c : children)
			c.setWidth(cWidth);
		
		valid = true;
	}
	
	public final void invalidate()
	{
		valid = false;
	}

	public void prepareForRender()
	{
	}
	
	public final int countChildren()
	{
		return children.size();
	}
	
	public final Component getChild(int index)
	{
		return children.get(index);
	}
	
	public final void add(Component component)
	{
		children.add(component);
		component.setParent(this);
		invalidate();
	}
	
	public final void remove(int index)
	{
		children.get(index).setParent(null);
		children.remove(index);
		invalidate();
	}
	
	public final void remove(Component component)
	{
		children.remove(component);
		component.setParent(null);
		invalidate();
	}

	public final void clear()
	{
		for(Component component : children)
			component.setParent(null);

		children.clear();
		invalidate();
	}
	
	public final boolean isDragging()
	{
		return dragging;
	}
	
	public final void startDragging(int mouseX, int mouseY)
	{
		dragging = true;
		dragOffsetX = getX() - mouseX;
		dragOffsetY = getY() - mouseY;
	}
	
	public final void dragTo(int mouseX, int mouseY)
	{
		int scaledWidth = WurstClient.MC.getWindow().getGuiScaledWidth();
		int scaledHeight = WurstClient.MC.getWindow().getGuiScaledHeight();
		x = Mth.clamp(mouseX + dragOffsetX, 0,
			Math.max(0, scaledWidth - width));
		y = Mth.clamp(mouseY + dragOffsetY, 0,
			Math.max(0, scaledHeight - TITLE_BAR_HEIGHT));
	}
	
	public final void stopDragging()
	{
		dragging = false;
		dragOffsetX = 0;
		dragOffsetY = 0;
	}
	
	public final boolean isMinimized()
	{
		return minimized;
	}
	
	public final void setMinimized(boolean minimized)
	{
		this.minimized = minimized;
	}
	
	public final boolean isMinimizable()
	{
		return minimizable;
	}
	
	public final void setMinimizable(boolean minimizable)
	{
		this.minimizable = minimizable;
	}
	
	public final boolean isPinned()
	{
		return pinned;
	}
	
	public final void setPinned(boolean pinned)
	{
		this.pinned = pinned;
	}
	
	public final boolean isPinnable()
	{
		return pinnable;
	}
	
	public final void setPinnable(boolean pinnable)
	{
		this.pinnable = pinnable;
	}
	
	public final boolean isClosable()
	{
		return closable;
	}
	
	public final void setClosable(boolean closable)
	{
		this.closable = closable;
	}
	
	public final boolean isClosing()
	{
		return closing;
	}
	
	public final void close()
	{
		if(closing)
			return;

		closing = true;
		onClose();
	}

	protected void onClose()
	{

	}
	
	public final boolean isInvisible()
	{
		return invisible;
	}
	
	public final void setInvisible(boolean invisible)
	{
		this.invisible = invisible;
	}
	
	public final boolean isFixedWidth()
	{
		return fixedWidth;
	}
	
	public final void setFixedWidth(boolean fixedWidth)
	{
		this.fixedWidth = fixedWidth;
	}
	
	public final int getInnerHeight()
	{
		return innerHeight;
	}
	
	public final void setMaxInnerHeight(int maxInnerHeight)
	{
		if(maxInnerHeight < 0)
			maxInnerHeight = 0;
		
		if(this.maxInnerHeight != maxInnerHeight)
			invalidate();
		
		this.maxInnerHeight = maxInnerHeight;
	}
	
	public final void setMaxHeight(int maxHeight)
	{
		setMaxInnerHeight(maxHeight - TITLE_BAR_HEIGHT);
	}
	
	public final int getScrollOffset()
	{
		return scrollOffset;
	}
	
	public final void setScrollOffset(int scrollOffset)
	{
		this.scrollOffset = scrollOffset;
	}
	
	public final boolean isScrollingEnabled()
	{
		return scrollingEnabled;
	}
	
	public final boolean isDraggingScrollbar()
	{
		return draggingScrollbar;
	}
	
	public final void startDraggingScrollbar(int mouseY)
	{
		draggingScrollbar = true;
		double outerHeight = height - TITLE_BAR_HEIGHT;
		double scrollbarY =
			outerHeight * (-scrollOffset / (double)innerHeight) + 1;
		scrollbarDragOffsetY = (int)(scrollbarY - mouseY);
	}
	
	public final void dragScrollbarTo(int mouseY)
	{
		int scrollbarY = mouseY + scrollbarDragOffsetY;
		double outerHeight = height - TITLE_BAR_HEIGHT;
		scrollOffset = (int)((scrollbarY - 1) / outerHeight * innerHeight * -1);
		scrollOffset = Math.min(scrollOffset, 0);
		scrollOffset = Math.max(scrollOffset,
			-innerHeight + height - TITLE_BAR_HEIGHT);
	}
	
	public final void stopDraggingScrollbar()
	{
		draggingScrollbar = false;
		scrollbarDragOffsetY = 0;
	}
}
