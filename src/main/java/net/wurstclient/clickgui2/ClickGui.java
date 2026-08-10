/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.clickgui2;

import java.util.ArrayList;
import java.util.Objects;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.wurstclient.WurstClient;
import net.wurstclient.clickgui2.theme.FlatTheme;
import net.wurstclient.hacks.ClickGuiHack;
import net.wurstclient.util.RenderUtils;

public final class ClickGui
{
	private static final WurstClient WURST = WurstClient.INSTANCE;
	private static final Minecraft MC = WurstClient.MC;
	private static final float[] BLEACH_BG_COLOR = {0.018F, 0.018F, 0.018F};
	private static final float[] BLEACH_AC_COLOR = {0.0F, 0.3882353F, 0.4F};
	private static final int BLEACH_TEXT_COLOR = 0xFFE8E8E8;
	
	private final ArrayList<Window> windows = new ArrayList<>();
	private final ArrayList<Popup> popups = new ArrayList<>();
	private final FlatTheme theme = new FlatTheme();
	private int maxHeight;
	private int maxSettingsHeight;
	
	private String tooltip = "";
	
	private boolean leftMouseButtonPressed;
	
	public ClickGui()
	{
	}
	
	public void init()
	{
		windows.clear();
		popups.clear();
		updateColors();

		Window radarWindow = WURST.getHax().radarHack.getWindow();
		if(radarWindow.getWidth() == 0)
		{
			radarWindow.pack();
			radarWindow.setX(5);
			radarWindow.setY(5);
		}
		addWindow(radarWindow);
	}

	public void initEmbedded()
	{
		windows.clear();
		popups.clear();
		updateColors();
	}
	
	public void handleMouseClick(int mouseX, int mouseY, int mouseButton)
	{
		if(mouseButton == GLFW.GLFW_MOUSE_BUTTON_LEFT)
			leftMouseButtonPressed = true;

		ArrayList<Popup> previouslyOpenPopups = new ArrayList<>(popups);
		
		boolean popupClicked =
			handlePopupMouseClick(mouseX, mouseY, mouseButton);
		
		if(!popupClicked)
		{
			handleWindowMouseClick(mouseX, mouseY, mouseButton);
			previouslyOpenPopups.forEach(Popup::close);
		}
		
		for(Popup popup : popups)
			if(popup.getOwner().getParent().isClosing())
				popup.close();
			
		windows.removeIf(Window::isClosing);
		popups.removeIf(Popup::isClosing);
	}
	
	public void handleMouseRelease(double mouseX, double mouseY,
		int mouseButton)
	{
		if(mouseButton == GLFW.GLFW_MOUSE_BUTTON_LEFT)
			leftMouseButtonPressed = false;
	}
	
	public void handleMouseScroll(double mouseX, double mouseY, double delta)
	{
		int dWheel = (int)delta * 4;
		if(dWheel == 0)
			return;
		
		for(int i = windows.size() - 1; i >= 0; i--)
		{
			Window window = windows.get(i);
			
			if(!window.isScrollingEnabled() || window.isMinimized()
				|| window.isInvisible())
				continue;
			
			if(mouseX < window.getX() || mouseY < window.getY() + 13)
				continue;
			if(mouseX >= window.getX() + window.getWidth()
				|| mouseY >= window.getY() + window.getHeight())
				continue;
			
			int scroll = window.getScrollOffset() + dWheel;
			scroll = Math.min(scroll, 0);
			scroll = Math.max(scroll,
				-window.getInnerHeight() + window.getHeight() - 13);
			window.setScrollOffset(scroll);
			break;
		}
	}
	
	private boolean handlePopupMouseClick(double mouseX, double mouseY,
		int mouseButton)
	{
		for(int i = popups.size() - 1; i >= 0; i--)
		{
			Popup popup = popups.get(i);
			Component owner = popup.getOwner();
			Window parent = owner.getParent();
			
			int x0 = parent.getX() + owner.getX();
			int y0 =
				parent.getY() + 13 + parent.getScrollOffset() + owner.getY();
			
			int x1 = x0 + popup.getX();
			int y1 = y0 + popup.getY();
			int x2 = x1 + popup.getWidth();
			int y2 = y1 + popup.getHeight();
			
			if(mouseX < x1 || mouseY < y1)
				continue;
			if(mouseX >= x2 || mouseY >= y2)
				continue;
			
			int cMouseX = (int)(mouseX - x0);
			int cMouseY = (int)(mouseY - y0);
			popup.handleMouseClick(cMouseX, cMouseY, mouseButton);
			
			popups.remove(i);
			popups.add(popup);
			return true;
		}
		
		return false;
	}
	
	private void handleWindowMouseClick(int mouseX, int mouseY, int mouseButton)
	{
		for(int i = windows.size() - 1; i >= 0; i--)
		{
			Window window = windows.get(i);
			if(window.isInvisible())
				continue;
			
			int x1 = window.getX();
			int y1 = window.getY();
			int x2 = x1 + window.getWidth();
			int y2 = y1 + window.getHeight();
			int y3 = y1 + 13;
			
			if(mouseX < x1 || mouseY < y1)
				continue;
			if(mouseX >= x2 || mouseY >= y2)
				continue;
			
			if(mouseY < y3)
				handleTitleBarMouseClick(window, mouseX, mouseY, mouseButton);
			else if(!window.isMinimized())
			{
				window.validate();
				
				int cMouseX = mouseX - x1;
				int cMouseY = mouseY - y3;
				
				if(window.isScrollingEnabled() && mouseX >= x2 - 3)
					handleScrollbarMouseClick(window, cMouseX, cMouseY,
						mouseButton);
				else
				{
					if(window.isScrollingEnabled())
						cMouseY -= window.getScrollOffset();
					
					handleComponentMouseClick(window, cMouseX, cMouseY,
						mouseButton);
				}
				
			}else
				continue;
			
			windows.remove(i);
			windows.add(window);
			break;
		}
	}
	
	private void handleTitleBarMouseClick(Window window, int mouseX, int mouseY,
		int mouseButton)
	{
		if(mouseButton != 0)
			return;
		
		if(mouseY < window.getY() + 2 || mouseY >= window.getY() + 11)
		{
			window.startDragging(mouseX, mouseY);
			return;
		}
		
		int x3 = window.getX() + window.getWidth();
		
		if(window.isClosable())
		{
			x3 -= 11;
			if(mouseX >= x3 && mouseX < x3 + 9)
			{
				window.close();
				return;
			}
		}
		
		if(window.isPinnable())
		{
			x3 -= 11;
			if(mouseX >= x3 && mouseX < x3 + 9)
			{
				window.setPinned(!window.isPinned());
				return;
			}
		}
		
		if(window.isMinimizable())
		{
			x3 -= 11;
			if(mouseX >= x3 && mouseX < x3 + 9)
			{
				window.setMinimized(!window.isMinimized());
				return;
			}
		}
		
		window.startDragging(mouseX, mouseY);
	}
	
	private void handleScrollbarMouseClick(Window window, int mouseX,
		int mouseY, int mouseButton)
	{
		if(mouseButton != GLFW.GLFW_MOUSE_BUTTON_LEFT)
			return;
		
		if(mouseX >= window.getWidth() - 1)
			return;
		
		double outerHeight = window.getHeight() - 13;
		double innerHeight = window.getInnerHeight();
		double maxScrollbarHeight = outerHeight - 2;
		int scrollbarY =
			(int)(outerHeight * (-window.getScrollOffset() / innerHeight) + 1);
		int scrollbarHeight =
			(int)(maxScrollbarHeight * outerHeight / innerHeight);
		
		if(mouseY < scrollbarY || mouseY >= scrollbarY + scrollbarHeight)
			return;
		
		window.startDraggingScrollbar(window.getY() + 13 + mouseY);
	}
	
	private void handleComponentMouseClick(Window window, double mouseX,
		double mouseY, int mouseButton)
	{
		for(int i2 = window.countChildren() - 1; i2 >= 0; i2--)
		{
			Component c = window.getChild(i2);
			
			if(mouseX < c.getX() || mouseY < c.getY())
				continue;
			if(mouseX >= c.getX() + c.getWidth()
				|| mouseY >= c.getY() + c.getHeight())
				continue;
			
			c.handleMouseClick(mouseX, mouseY, mouseButton);
			break;
		}
	}
	
	public void render(GuiGraphics context, int mouseX, int mouseY,
		float partialTicks)
	{
		PoseStack matrixStack = context.pose();
		matrixStack.pushPose();
		
		tooltip = "";
		for(Window window : windows)
		{
			if(window.isInvisible())
				continue;
			
			// dragging
			if(window.isDragging())
				if(leftMouseButtonPressed)
					window.dragTo(mouseX, mouseY);
				else
				{
					window.stopDragging();
				}
			
			// scrollbar dragging
			if(window.isDraggingScrollbar())
				if(leftMouseButtonPressed)
					window.dragScrollbarTo(mouseY);
				else
					window.stopDraggingScrollbar();
				
			matrixStack.translate(0, 0, 300);
			renderWindow(context, window, mouseX, mouseY, partialTicks);
		}
		
		renderPopups(context, mouseX, mouseY);
		renderTooltip(context, mouseX, mouseY);
		
		matrixStack.popPose();
	}
	
	public void renderBackdrop(GuiGraphics context)
	{
		updateColors();
		FlatRenderer.drawBackdrop(context, MC.getWindow().getGuiScaledWidth(),
			MC.getWindow().getGuiScaledHeight(), theme);
	}
	
	public void renderPopups(GuiGraphics context, int mouseX, int mouseY)
	{
		PoseStack matrixStack = context.pose();
		for(Popup popup : popups)
		{
			Component owner = popup.getOwner();
			Window parent = owner.getParent();
			
			int x1 = parent.getX() + owner.getX();
			int y1 =
				parent.getY() + 13 + parent.getScrollOffset() + owner.getY();
			
			matrixStack.pushPose();
			matrixStack.translate(x1, y1, 300);
			
			int cMouseX = mouseX - x1;
			int cMouseY = mouseY - y1;
			FlatRenderer.drawPopup(context, popup.getX(), popup.getY(),
				popup.getX() + popup.getWidth(), popup.getY() + popup.getHeight(),
				4, theme);
			popup.render(context, cMouseX, cMouseY);
			
			matrixStack.popPose();
		}
	}
	
	public void renderTooltip(GuiGraphics context, int mouseX, int mouseY)
	{
		PoseStack matrixStack = context.pose();
		
		if(tooltip.isEmpty())
			return;
		
		String[] lines = tooltip.split("\n");
		Font tr = MC.font;
		
		int tw = 0;
		int th = lines.length * tr.lineHeight;
		for(String line : lines)
		{
			int lw = tr.width(line);
			if(lw > tw)
				tw = lw;
		}
		int sw = MC.screen.width;
		int sh = MC.screen.height;
		
		int xt1 = mouseX + tw + 11 <= sw ? mouseX + 8 : mouseX - tw - 8;
		int xt2 = xt1 + tw + 3;
		int yt1 = mouseY + th - 2 <= sh ? mouseY - 4 : mouseY - th - 4;
		int yt2 = yt1 + th + 2;
		
		matrixStack.pushPose();
		matrixStack.translate(0, 0, 300);
		
		int tooltipColor = theme.tooltipFill();
		int outlineColor = theme.border(true);
		FlatRenderer.drawPanel(context, xt1, yt1, xt2, yt2, 4,
			tooltipColor, outlineColor);
		
		// text
		for(int i = 0; i < lines.length; i++)
			context.drawString(tr, lines[i], xt1 + 2, yt1 + 2 + i * tr.lineHeight,
				theme.text(), false);
		
		matrixStack.popPose();
	}

	public void renderPinnedWindows(GuiGraphics context, float partialTicks)
	{
		PoseStack matrixStack = context.pose();
		matrixStack.pushPose();

		for(Window window : windows)
		{
			if(!window.isPinned() || window.isInvisible())
				continue;

			matrixStack.pushPose();
			matrixStack.translate(0, 0, 300);
			renderWindow(context, window, Integer.MIN_VALUE,
				Integer.MIN_VALUE, partialTicks);
			matrixStack.popPose();
		}

		matrixStack.popPose();
	}
	
	public void updateColors()
	{
		ClickGuiHack clickGui = WURST.getHax().clickGuiHack;
		float[] accent = WURST.getHax().rainbowUiHack.isEnabled()
			? RenderUtils.getRainbowColor() : BLEACH_AC_COLOR;
		
		maxHeight = clickGui.getMaxHeight();
		maxSettingsHeight = clickGui.getMaxSettingsHeight();
		theme.update(BLEACH_BG_COLOR, accent, BLEACH_TEXT_COLOR, 1,
			clickGui.getTooltipOpacity());
	}
	
	private void renderWindow(GuiGraphics context, Window window, int mouseX,
		int mouseY, float partialTicks)
	{
		window.prepareForRender();
		int x1 = window.getX();
		int y1 = window.getY();
		int x2 = x1 + window.getWidth();
		int y2 = y1 + window.getHeight();
		int y3 = y1 + 13;
		
		boolean focused = !windows.isEmpty()
			&& windows.get(windows.size() - 1) == window;
		int windowBgColor = theme.windowBody();
		int outlineColor = theme.border(focused);
		
		PoseStack matrixStack = context.pose();
		
		if(window.isMinimized())
			y2 = y3;
		
		FlatRenderer.drawWindowPanel(context, x1, y1, x2, y2, 2, theme,
			focused);
		
		if(mouseX >= x1 && mouseY >= y1 && mouseX < x2 && mouseY < y2)
			tooltip = "";
		
		if(!window.isMinimized())
		{
			window.setMaxHeight(window instanceof SettingsWindow
				? maxSettingsHeight : maxHeight);
			window.validate();
			
			// scrollbar
			if(window.isScrollingEnabled())
			{
				int xs1 = x2 - 3;
				int xs2 = xs1 + 2;
				int xs3 = x2;
				
				double outerHeight = y2 - y3;
				double innerHeight = window.getInnerHeight();
				double maxScrollbarHeight = outerHeight - 2;
				double scrollbarY =
					outerHeight * (-window.getScrollOffset() / innerHeight) + 1;
				double scrollbarHeight =
					maxScrollbarHeight * outerHeight / innerHeight;
				
				int ys1 = y3;
				int ys2 = y2;
				int ys3 = ys1 + (int)scrollbarY;
				int ys4 = ys3 + (int)scrollbarHeight;
				
				// window background
				context.fill(xs2, ys1, xs3, ys2, windowBgColor);
				context.fill(xs1, ys1, xs2, ys3, windowBgColor);
				context.fill(xs1, ys4, xs2, ys2, windowBgColor);
				
				boolean hovering = mouseX >= xs1 && mouseY >= ys3
					&& mouseX < xs2 && mouseY < ys4;
				
				// scrollbar
				int scrollbarColor = theme.accent(hovering ? 0.9F : 0.58F);
				context.fill(xs1, ys3, xs2, ys4, scrollbarColor);
				
				// outline
				RenderUtils.drawBorder2D(context, xs1, ys3, xs2, ys4,
					outlineColor);
			}
			
			int x3 = x1 + 2;
			int x4 = window.isScrollingEnabled() ? x2 - 3 : x2;
			int x5 = x4 - 2;
			int y4 = y3 + window.getScrollOffset();
			
			// window background
			// left & right
			context.fill(x1, y3, x3, y2, windowBgColor);
			context.fill(x5, y3, x4, y2, windowBgColor);
			
			context.enableScissor(x1, y3, x2, y2);
			
			matrixStack.pushPose();
			matrixStack.translate(x1, y4, 0);
			
			// window background
			// between children
			int xc1 = 2;
			int xc2 = x5 - x1;
			for(int i = 0; i < window.countChildren(); i++)
			{
				int yc1 = window.getChild(i).getY();
				int yc2 = yc1 - 2;
				context.fill(xc1, yc2, xc2, yc1, windowBgColor);
			}
			
			// window background
			// bottom
			int yc1;
			if(window.countChildren() == 0)
				yc1 = 0;
			else
			{
				Component lastChild =
					window.getChild(window.countChildren() - 1);
				yc1 = lastChild.getY() + lastChild.getHeight();
			}
			int yc2 = yc1 + 2;
			context.fill(xc1, yc1, xc2, yc2, windowBgColor);
			
			// render children
			int cMouseX = mouseX - x1;
			int cMouseY = mouseY - y4;
			for(int i = 0; i < window.countChildren(); i++)
				window.getChild(i).render(context, cMouseX, cMouseY,
					partialTicks);
			
			matrixStack.popPose();
			context.disableScissor();
		}
		
		// window outline
		FlatRenderer.drawRoundedOutline(context, x1, y1, x2, y2, 5,
			outlineColor);
		
		// title bar separator line
		if(!window.isMinimized())
			RenderUtils.drawLine2D(context, x1 + 4, y3, x2 - 4, y3,
				theme.accent(focused ? 0.52F : 0.3F));
		
		// title bar buttons
		int x3 = x2;
		int y4 = y1 + 2;
		int y5 = y3 - 2;
		boolean hoveringY = mouseY >= y4 && mouseY < y5;
		if(window.isClosable())
		{
			x3 -= 11;
			int x4 = x3 + 9;
			boolean hovering = hoveringY && mouseX >= x3 && mouseX < x4;
			renderTitleBarButton(context, x3, y4, x4, y5, hovering);
			if(window instanceof SettingsWindow)
				ClickGuiIcons.drawSettingsClose(context, x3, y4, x4, y5,
					hovering);
			else
				ClickGuiIcons.drawCross(context, x3, y4, x4, y5, hovering);
		}
		
		if(window.isPinnable())
		{
			x3 -= 11;
			int x4 = x3 + 9;
			boolean hovering = hoveringY && mouseX >= x3 && mouseX < x4;
			renderTitleBarButton(context, x3, y4, x4, y5, hovering);
			ClickGuiIcons.drawPin(context, x3, y4, x4, y5, hovering,
				window.isPinned());
		}
		
		if(window.isMinimizable())
		{
			x3 -= 11;
			int x4 = x3 + 9;
			boolean hovering = hoveringY && mouseX >= x3 && mouseX < x4;
			renderTitleBarButton(context, x3, y4, x4, y5, hovering);
			ClickGuiIcons.drawWindowToggle(context, x3, y4, x4, y5, hovering,
				window.isMinimized());
		}
		
		// title bar background
		// above & below buttons
		int titleBgColor = theme.titleFill(focused);
		context.fill(x3, y1, x2, y4, titleBgColor);
		context.fill(x3, y5, x2, y3, titleBgColor);
		
		// title bar background
		// behind title
		context.fill(x1, y1, x3, y3, titleBgColor);
		
		// window title
		Font tr = MC.font;
		String title = tr.substrByWidth(
			net.minecraft.network.chat.Component.literal(window.getTitle()),
			x3 - x1 - 6)
			.getString();
		context.drawString(tr, title, x1 + 4, y1 + 3, theme.text(), false);
	}
	
	private void renderTitleBarButton(GuiGraphics context, int x1, int y1,
		int x2, int y2, boolean hovering)
	{
		int x3 = x2 + 2;
		
		// button background
		FlatRenderer.drawControl(context, x1, y1, x2, y2, 3, theme,
			hovering ? 1 : 0, false);
		
		// background between buttons
		int windowBgColor = theme.titleFill(true);
		context.fill(x2, y1, x3, y2, windowBgColor);
		
		// button outline
		int outlineColor = theme.controlBorder(hovering ? 1 : 0, false);
		RenderUtils.drawBorder2D(context, x1, y1, x2, y2, outlineColor);
	}
	
	public float[] getBgColor()
	{
		return theme.background();
	}
	
	public float[] getAcColor()
	{
		return theme.accent();
	}
	
	public int getTxtColor()
	{
		return theme.text();
	}
	
	public float getOpacity()
	{
		return theme.opacity();
	}
	
	public float getTooltipOpacity()
	{
		return theme.tooltipOpacity();
	}

	public FlatTheme getTheme()
	{
		return theme;
	}
	
	public void setTooltip(String tooltip)
	{
		this.tooltip = Objects.requireNonNull(tooltip);
	}
	
	public void addWindow(Window window)
	{
		if(!windows.contains(window))
			windows.add(window);
	}

	public boolean isMouseOverWindow(double mouseX, double mouseY)
	{
		if(isMouseOverPopup(mouseX, mouseY))
			return true;

		for(int i = windows.size() - 1; i >= 0; i--)
		{
			Window window = windows.get(i);
			if(window.isInvisible() || window.isClosing())
				continue;
			if(mouseX >= window.getX()
				&& mouseX < window.getX() + window.getWidth()
				&& mouseY >= window.getY()
				&& mouseY < window.getY() + window.getHeight())
				return true;
		}

		return false;
	}

	private boolean isMouseOverPopup(double mouseX, double mouseY)
	{
		for(int i = popups.size() - 1; i >= 0; i--)
		{
			Popup popup = popups.get(i);
			Window parent = popup.getOwner().getParent();
			if(parent == null || popup.isClosing())
				continue;

			int ownerX = parent.getX() + popup.getOwner().getX();
			int ownerY = parent.getY() + 13 + parent.getScrollOffset()
				+ popup.getOwner().getY();
			int x1 = ownerX + popup.getX();
			int y1 = ownerY + popup.getY();
			if(mouseX >= x1 && mouseX < x1 + popup.getWidth()
				&& mouseY >= y1 && mouseY < y1 + popup.getHeight())
				return true;
		}
		return false;
	}

	public void closePopupsOwnedBy(Window parent)
	{
		for(Popup popup : popups)
			if(popup.getOwner().getParent() == parent)
				popup.close();
		popups.removeIf(Popup::isClosing);
	}
	
	public void addPopup(Popup popup)
	{
		popups.add(popup);
	}
	
	public boolean isLeftMouseButtonPressed()
	{
		return leftMouseButtonPressed;
	}
}
