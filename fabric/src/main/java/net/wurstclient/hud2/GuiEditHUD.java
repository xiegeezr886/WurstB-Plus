/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
package net.wurstclient.hud2;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.wurstclient.WurstClient;
import net.wurstclient.hud2.compat.HudItemIcons;
import net.wurstclient.hud2.nanovg.NanoVGUI;

/**
 * Soar v7 style HUD editor with a pageable module sidebar.
 *
 * <p>Module previews, selection outlines, the sidebar and the guides are all
 * drawn inside one NanoVG pass, so controls and outlines always share the same
 * coordinate system.
 */
public final class GuiEditHUD extends Screen
{
	private static final WurstClient WURST = WurstClient.INSTANCE;
	private static final int SNAP_RANGE = 5;
	private static final int GRID_SIZE = 40;
	private static final int SIDEBAR_WIDTH = 220;
	private static final int SIDEBAR_HEADER = 26;
	private static final int SIDEBAR_FOOTER = 26;
	private static final int SIDEBAR_ROW = 16;
	private static final int TOOLBAR_HEIGHT = 32;

	private final HudManager manager;
	private final Map<String, HUDMod> mods;
	private final List<HUDMod> sortedMods;

	private String hoveredId;
	private int snapLineX = -1;
	private int snapLineY = -1;
	private boolean canSnap = true;
	private boolean sidebarVisible = true;
	private int page;

	public GuiEditHUD()
	{
		super(Component.literal("HUD Editor"));
		manager = WURST.getHudManager();
		mods = manager.getMods();
		sortedMods = new ArrayList<>(mods.values());
		sortedMods.sort(Comparator.comparing(HUDMod::getTranslatedName,
			String.CASE_INSENSITIVE_ORDER));
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY,
		float partialTicks)
	{
		hoveredId = findHovered(mouseX, mouseY);
		HudItemIcons.capturePending(NanoVGUI.context(), 4);

		NanoVGUI.pass(graphics, () -> {
			NanoVGUI.fill(0, 0, width, height, 0x50000000);
			for(int x = 0; x < width; x += GRID_SIZE)
				NanoVGUI.fill(x, 0, x + 1, height, 0x08FFFFFF);
			for(int y = 0; y < height; y += GRID_SIZE)
				NanoVGUI.fill(0, y, width, y + 1, 0x08FFFFFF);

			manager.renderEditor(NanoVGUI.context(), this, partialTicks);

			if(sidebarVisible)
				renderSidebar();
			renderToolbar();
		});
	}

	private void renderSidebar()
	{
		int x1 = width - SIDEBAR_WIDTH;
		int y2 = height - TOOLBAR_HEIGHT;
		NanoVGUI.fill(x1, 0, width, y2, 0xE6000000);
		NanoVGUI.fill(x1, 0, x1 + 1, y2, 0x30FFFFFF);

		NanoVGUI.text("HUD \u6a21\u5757", x1 + 10, 8, 0xFFFFFFFF, 10F);
		int totalPages = getPageCount();
		String pageText = (page + 1) + " / " + totalPages;
		NanoVGUI.rightText(pageText, width - 10, 9, 0xCCFFFFFF, 9F);

		int rows = getRowsPerPage();
		int start = page * rows;
		int end = Math.min(sortedMods.size(), start + rows);
		for(int index = start; index < end; index++)
		{
			HUDMod mod = sortedMods.get(index);
			int rowY = SIDEBAR_HEADER + (index - start) * SIDEBAR_ROW;
			boolean enabled = mod.isEnabled();

			if(enabled)
				NanoVGUI.fillRounded(x1 + 10, rowY + 3, x1 + 20,
					rowY + 13, 3, HUDMod.accentColor(255));
			else
				NanoVGUI.outlineRounded(x1 + 10, rowY + 3, x1 + 20,
					rowY + 13, 3, 0x60FFFFFF);

			String name = NanoVGUI.plainSubstrByWidth(
				mod.getTranslatedName(), SIDEBAR_WIDTH - 42, 9F);
			NanoVGUI.text(name, x1 + 26, rowY + 3,
				enabled ? 0xFFFFFFFF : 0x99FFFFFF, 9F);
		}

		int footerY = y2 - SIDEBAR_FOOTER;
		NanoVGUI.fill(x1, footerY, width, y2, 0x40000000);
		drawPageButton(x1 + 8, footerY + 4, 72,
			"\u4e0a\u4e00\u9875", page > 0);
		drawPageButton(width - 80, footerY + 4, 72,
			"\u4e0b\u4e00\u9875", page + 1 < totalPages);
	}

	private void drawPageButton(int x, int y, int buttonWidth, String label,
		boolean active)
	{
		NanoVGUI.fillRounded(x, y, x + buttonWidth, y + 18, 5,
			active ? 0x80006366 : 0x30FFFFFF);
		NanoVGUI.centeredText(label, x + buttonWidth / 2F, y + 5,
			active ? 0xFFFFFFFF : 0x50FFFFFF, 9F);
	}

	private void renderToolbar()
	{
		NanoVGUI.fill(0, height - TOOLBAR_HEIGHT, width, height, 0x99000000);
		NanoVGUI.centeredText(
			"\u5de6\u952e\u62d6\u52a8  |  \u6eda\u8f6e\u7f29\u653e  |  "
				+ "\u4e2d\u952e\u91cd\u7f6e  |  \u53f3\u952e\u5f00\u5173\u5438\u9644  |  "
				+ "T \u4e3b\u9898  |  Tab \u4fa7\u680f  |  ESC \u5173\u95ed",
			width / 2F, height - TOOLBAR_HEIGHT + 10, 0xCCFFFFFF, 9F);
	}

	private String findHovered(int mouseX, int mouseY)
	{
		if(sidebarVisible && mouseX >= width - SIDEBAR_WIDTH
			&& mouseY < height - TOOLBAR_HEIGHT)
			return null;

		List<HUDMod> all = new ArrayList<>(sortedMods);
		for(int i = all.size() - 1; i >= 0; i--)
		{
			HUDMod mod = all.get(i);
			if(!mod.isDraggable())
				continue;
			if(mouseX >= mod.getX() && mouseY >= mod.getY()
				&& mouseX < mod.getX() + mod.getWidth()
				&& mouseY < mod.getY() + mod.getHeight())
				return mod.getId();
		}
		return null;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button)
	{
		if(handleSidebarClick(mouseX, mouseY))
			return true;

		if(button == GLFW.GLFW_MOUSE_BUTTON_RIGHT)
		{
			canSnap = !canSnap;
			return true;
		}

		HUDMod hovered = hoveredId == null ? null : mods.get(hoveredId);
		if(hovered == null)
			return super.mouseClicked(mouseX, mouseY, button);

		if(button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE)
		{
			hovered.setScale(1F);
			manager.saveState();
			return true;
		}

		if(button == GLFW.GLFW_MOUSE_BUTTON_LEFT)
		{
			canSnap = true;
			hovered.setDragging(true);
			hovered.setDraggingX(hovered.getX() - (int)mouseX);
			hovered.setDraggingY(hovered.getY() - (int)mouseY);
			return true;
		}

		return super.mouseClicked(mouseX, mouseY, button);
	}

	private boolean handleSidebarClick(double mouseX, double mouseY)
	{
		if(!sidebarVisible)
			return false;

		int x1 = width - SIDEBAR_WIDTH;
		int y2 = height - TOOLBAR_HEIGHT;
		if(mouseX < x1 || mouseY >= y2)
			return false;

		int footerY = y2 - SIDEBAR_FOOTER;
		if(mouseY >= footerY)
		{
			if(mouseX >= x1 + 8 && mouseX < x1 + 80)
			{
				if(page > 0)
					page--;
				return true;
			}
			if(mouseX >= width - 80 && mouseX < width - 8)
			{
				if(page + 1 < getPageCount())
					page++;
				return true;
			}
			return true;
		}

		if(mouseY < SIDEBAR_HEADER)
			return true;

		int index = page * getRowsPerPage()
			+ (int)((mouseY - SIDEBAR_HEADER) / SIDEBAR_ROW);
		if(index < 0 || index >= sortedMods.size())
			return true;

		HUDMod mod = sortedMods.get(index);
		manager.setModuleEnabled(mod.getId(), !mod.isEnabled());
		return true;
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button)
	{
		if(button == GLFW.GLFW_MOUSE_BUTTON_LEFT)
		{
			for(HUDMod mod : mods.values())
				mod.setDragging(false);
			manager.saveState();
			return true;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button,
		double deltaX, double deltaY)
	{
		HUDMod dragged = null;
		for(HUDMod mod : mods.values())
			if(mod.isDragging())
			{
				dragged = mod;
				break;
			}

		if(dragged == null)
			return super.mouseDragged(mouseX, mouseY, button, deltaX,
				deltaY);

		snapLineX = -1;
		snapLineY = -1;
		dragged.setX((int)mouseX + dragged.getDraggingX());
		dragged.setY((int)mouseY + dragged.getDraggingY());
		dragged.clampToScreen(width, height - TOOLBAR_HEIGHT);
		if(canSnap)
			applySnapping(dragged);
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY)
	{
		if(sidebarVisible && mouseX >= width - SIDEBAR_WIDTH
			&& mouseY < height - TOOLBAR_HEIGHT)
			return true;

		HUDMod hovered = hoveredId == null ? null : mods.get(hoveredId);
		if(hovered == null || scrollY == 0)
			return super.mouseScrolled(mouseX, mouseY, scrollY);

		float newScale = Math.round(
			(hovered.getScale() + (float)scrollY * 0.1F) * 10F) / 10F;
		hovered.setScale(newScale);
		manager.saveState();
		return true;
	}

	private void applySnapping(HUDMod mod)
	{
		int x = mod.getX();
		int y = mod.getY();
		int w = mod.getWidth();
		int h = mod.getHeight();
		int centerX = width / 2;
		int centerY = (height - TOOLBAR_HEIGHT) / 2;

		if(Math.abs(x) <= SNAP_RANGE)
		{
			mod.setX(0);
			snapLineX = 0;
		}else if(Math.abs(x + w - width) <= SNAP_RANGE)
		{
			mod.setX(width - w);
			snapLineX = width;
		}else if(Math.abs(x + w / 2 - centerX) <= SNAP_RANGE)
		{
			mod.setX(centerX - w / 2);
			snapLineX = centerX;
		}

		if(Math.abs(y) <= SNAP_RANGE)
		{
			mod.setY(0);
			snapLineY = 0;
		}else if(Math.abs(y + h - (height - TOOLBAR_HEIGHT)) <= SNAP_RANGE)
		{
			mod.setY(height - TOOLBAR_HEIGHT - h);
			snapLineY = height - TOOLBAR_HEIGHT;
		}else if(Math.abs(y + h / 2 - centerY) <= SNAP_RANGE)
		{
			mod.setY(centerY - h / 2);
			snapLineY = centerY;
		}

		x = mod.getX();
		y = mod.getY();
		for(HUDMod other : mods.values())
		{
			if(other == mod)
				continue;
			int ox = other.getX();
			int oy = other.getY();
			int ow = other.getWidth();
			int oh = other.getHeight();

			if(Math.abs(x - ox) <= SNAP_RANGE)
			{
				mod.setX(ox);
				snapLineX = ox;
			}else if(Math.abs(x + w - ox) <= SNAP_RANGE)
			{
				mod.setX(ox - w);
				snapLineX = ox;
			}else if(Math.abs(x - (ox + ow)) <= SNAP_RANGE)
			{
				mod.setX(ox + ow);
				snapLineX = ox + ow;
			}else if(Math.abs(x + w - (ox + ow)) <= SNAP_RANGE)
			{
				mod.setX(ox + ow - w);
				snapLineX = ox + ow;
			}

			if(Math.abs(y - oy) <= SNAP_RANGE)
			{
				mod.setY(oy);
				snapLineY = oy;
			}else if(Math.abs(y + h - oy) <= SNAP_RANGE)
			{
				mod.setY(oy - h);
				snapLineY = oy;
			}else if(Math.abs(y - (oy + oh)) <= SNAP_RANGE)
			{
				mod.setY(oy + oh);
				snapLineY = oy + oh;
			}else if(Math.abs(y + h - (oy + oh)) <= SNAP_RANGE)
			{
				mod.setY(oy + oh - h);
				snapLineY = oy + oh;
			}
			x = mod.getX();
			y = mod.getY();
		}
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers)
	{
		if(keyCode == GLFW.GLFW_KEY_ESCAPE)
		{
			onClose();
			return true;
		}
		if(keyCode == GLFW.GLFW_KEY_T)
		{
			manager.setTheme(manager.getTheme().next());
			return true;
		}
		if(keyCode == GLFW.GLFW_KEY_TAB)
		{
			sidebarVisible = !sidebarVisible;
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public void onClose()
	{
		manager.saveState();
		minecraft.setScreen(null);
	}

	@Override
	public boolean isPauseScreen()
	{
		return false;
	}

	private int getRowsPerPage()
	{
		int available = height - TOOLBAR_HEIGHT - SIDEBAR_HEADER
			- SIDEBAR_FOOTER;
		return Math.max(1, available / SIDEBAR_ROW);
	}

	private int getPageCount()
	{
		int rows = getRowsPerPage();
		return Math.max(1, (sortedMods.size() + rows - 1) / rows);
	}

	public String getHoveredId()
	{
		return hoveredId;
	}

	public int getSnapLineX()
	{
		return snapLineX;
	}

	public int getSnapLineY()
	{
		return snapLineY;
	}

	public boolean canSnap()
	{
		return canSnap;
	}
}
