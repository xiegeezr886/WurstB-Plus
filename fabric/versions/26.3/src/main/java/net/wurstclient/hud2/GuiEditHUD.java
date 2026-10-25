package net.wurstclient.hud2;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.wurstclient.WurstClient;
import net.wurstclient.clickgui2.FlatRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * HUD editor ported from Soar Client v7.1.2's {@code GuiEditHUD}: drag with
 * the left mouse button, wheel to scale, middle click to reset the scale,
 * right click to toggle snapping, and centered guide lines while dragging.
 *
 * <p>
 * A pageable sidebar is layered on top so Wurst's built-in HUD modules can be
 * enabled without leaving the editor (Soar normally does this from its mod
 * menu).
 */
public final class GuiEditHUD extends Screen
{
	private static final WurstClient WURST = WurstClient.INSTANCE;
	private static final int SIDEBAR_WIDTH = 230;
	private static final int SIDEBAR_HEADER = 26;
	private static final int SIDEBAR_FOOTER = 26;
	private static final int SIDEBAR_ROW = 16;
	private static final int TOOLBAR_HEIGHT = 32;
	private static final int SNAP_RANGE = 5;

	private final HudManager hudManager;
	private final Map<String, HUDMod> mods;

	private String hoveredId;
	private int page;
	private boolean sidebarVisible = true;
	private boolean canSnap = true;
	private int snapLineX = -1;
	private int snapLineY = -1;

	public GuiEditHUD()
	{
		super(Component.literal("HUD Editor"));
		hudManager = WURST.getHudManager();
		mods = hudManager.getMods();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics,
		int mouseX, int mouseY, float partialTicks)
	{
		renderContents(graphics, mouseX, mouseY, partialTicks);
	}

	private void renderContents(GuiGraphicsExtractor context, int mouseX,
		int mouseY, float partialTicks)
	{
		context.fill(0, 0, width, height, 0x50000000);
		context.fill(width / 2, 0, width / 2 + 1, height, 0x30FFFFFF);
		context.fill(0, height / 2, width, height / 2 + 1, 0x30FFFFFF);

		hoveredId = findHovered(mouseX, mouseY);
		snapLineX = -1;
		snapLineY = -1;

		for(HUDMod mod : mods.values())
			if(mod.isEnabled())
				renderMod(context, mod, mouseX, mouseY, partialTicks);

		if(snapLineX >= 0)
			context.fill(snapLineX, 0, snapLineX + 1, height - TOOLBAR_HEIGHT,
				0xFFD93CFF);
		if(snapLineY >= 0)
			context.fill(0, snapLineY, width, snapLineY + 1, 0xFFD93CFF);

		if(sidebarVisible)
			renderSidebar(context, mouseX, mouseY);

		renderToolbar(context);
	}

	private void renderMod(GuiGraphicsExtractor graphics, HUDMod mod,
		int mouseX, int mouseY, float partialTicks)
	{
		mod.clampToScreen(width, height - TOOLBAR_HEIGHT);
		mod.render(graphics, partialTicks);

		boolean hovered = mod.getId().equals(hoveredId);
		int outline = hovered ? 0xC8FFFFFF : 0x55000000;
		int x = mod.getX();
		int y = mod.getY();
		int w = mod.getWidth();
		int h = mod.getHeight();
		FlatRenderer.drawRoundedOutline(graphics, x - 2, y - 2, x + w + 2,
			y + h + 2, Math.round(6.5F * mod.getScale()), outline);
	}

	private String findHovered(int mouseX, int mouseY)
	{
		List<HUDMod> all = new ArrayList<>(mods.values());
		for(int i = all.size() - 1; i >= 0; i--)
		{
			HUDMod mod = all.get(i);
			if(!mod.isEnabled())
				continue;
			if(mouseX >= mod.getX() && mouseY >= mod.getY()
				&& mouseX < mod.getX() + mod.getWidth()
				&& mouseY < mod.getY() + mod.getHeight())
				return mod.getId();
		}
		return null;
	}

	private void renderSidebar(GuiGraphicsExtractor graphics, int mouseX,
		int mouseY)
	{
		Font font = minecraft.font;
		int x1 = width - SIDEBAR_WIDTH;
		int y2 = height - TOOLBAR_HEIGHT;
		graphics.fill(x1, 0, width, y2, 0xFF000000);
		graphics.fill(x1, 0, x1 + 1, y2, 0x20FFFFFF);

		graphics.text(font, "HUD \u5143\u7d20", x1 + 10, 8, 0xFFFFFFFF,
			false);
		int totalPages = getPageCount();
		String pageText = (page + 1) + " / " + totalPages;
		graphics.text(font, pageText, width - 10 - font.width(pageText), 8,
			0xCCFFFFFF, false);

		List<Map.Entry<String, HUDMod>> entries = getEntries();
		int rows = getRowsPerPage();
		int start = page * rows;
		int end = Math.min(entries.size(), start + rows);
		for(int index = start; index < end; index++)
		{
			Map.Entry<String, HUDMod> entry = entries.get(index);
			HUDMod mod = entry.getValue();
			int rowY = SIDEBAR_HEADER + (index - start) * SIDEBAR_ROW;
			boolean hovering = mouseX >= x1 + 6 && mouseX < width - 6
				&& mouseY >= rowY && mouseY < rowY + SIDEBAR_ROW;
			if(hovering)
				graphics.fill(x1 + 5, rowY, width - 5,
					rowY + SIDEBAR_ROW - 1, 0x18FFFFFF);

			int dotX = x1 + 10;
			int dotY = rowY + 4;
			if(mod.isEnabled())
				FlatRenderer.fillRoundedRect(graphics, dotX, dotY, dotX + 8,
					dotY + 8, 4, HUDMod.accentColor(255));
			else
				FlatRenderer.drawRoundedOutline(graphics, dotX, dotY,
					dotX + 8, dotY + 8, 4, 0x60FFFFFF);

			String name = font.plainSubstrByWidth(mod.getTranslatedName(),
				SIDEBAR_WIDTH - 44);
			graphics.text(font, name, x1 + 24, rowY + 3,
				mod.isEnabled() ? 0xFFFFFFFF : 0x99FFFFFF, false);
		}

		int footerY = y2 - SIDEBAR_FOOTER;
		graphics.fill(x1, footerY, width, y2, 0x40000000);
		drawPageButton(graphics, font, x1 + 8, footerY + 4, 72,
			"\u4e0a\u4e00\u9875", page > 0, mouseX, mouseY);
		drawPageButton(graphics, font, width - 80, footerY + 4, 72,
			"\u4e0b\u4e00\u9875", page + 1 < totalPages, mouseX, mouseY);
	}

	private void drawPageButton(GuiGraphicsExtractor graphics, Font font,
		int x, int y, int width, String label, boolean active, int mouseX,
		int mouseY)
	{
		boolean hovering = active && mouseX >= x && mouseX < x + width
			&& mouseY >= y && mouseY < y + 18;
		FlatRenderer.fillRoundedRect(graphics, x, y, x + width, y + 18, 6,
			hovering ? 0x80006366 : 0x40FFFFFF);
		graphics.centeredText(font, label, x + width / 2, y + 5,
			active ? 0xFFFFFFFF : 0x50FFFFFF);
	}

	private void renderToolbar(GuiGraphicsExtractor graphics)
	{
		Font font = minecraft.font;
		graphics.fill(0, height - TOOLBAR_HEIGHT, width, height, 0x99000000);
		String hint = "\u5de6\u952e\u62d6\u52a8  |  \u6eda\u8f6e\u7f29\u653e  |  "
			+ "\u4e2d\u952e\u91cd\u7f6e  |  \u53f3\u952e\u5f00\u5173\u5438\u9644  |  "
			+ "T \u4e3b\u9898  |  Tab \u4fa7\u680f  |  ESC \u5173\u95ed";
		graphics.centeredText(font, hint, width / 2,
			height - TOOLBAR_HEIGHT + 10, 0xCCFFFFFF);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick)
	{
		double mouseX = event.x();
		double mouseY = event.y();
		int button = event.button();

		if(sidebarVisible && handleSidebarClick(mouseX, mouseY))
			return true;

		if(button == InputConstants.MOUSE_BUTTON_RIGHT)
		{
			canSnap = !canSnap;
			return true;
		}

		HUDMod hovered = hoveredId == null ? null
			: mods.get(hoveredId);
		if(hovered == null)
			return super.mouseClicked(event, doubleClick);

		if(button == InputConstants.MOUSE_BUTTON_MIDDLE)
		{
			hovered.setScale(1.0F);
			hudManager.saveState();
			return true;
		}

		if(button == InputConstants.MOUSE_BUTTON_LEFT)
		{
			canSnap = true;
			hovered.setDragging(true);
			hovered.setDraggingX(hovered.getX() - (int)mouseX);
			hovered.setDraggingY(hovered.getY() - (int)mouseY);
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event)
	{
		if(event.button() == InputConstants.MOUSE_BUTTON_LEFT)
		{
			for(HUDMod mod : mods.values())
				mod.setDragging(false);
			hudManager.saveState();
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double deltaX,
		double deltaY)
	{
		HUDMod dragged = null;
		for(HUDMod mod : mods.values())
			if(mod.isDragging())
			{
				dragged = mod;
				break;
			}
		if(dragged == null)
			return super.mouseDragged(event, deltaX, deltaY);

		dragged.setX((int)event.x() + dragged.getDraggingX());
		dragged.setY((int)event.y() + dragged.getDraggingY());
		dragged.clampToScreen(width, height - TOOLBAR_HEIGHT);
		if(canSnap)
			applySnapping(dragged);
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY,
		double scrollX, double scrollY)
	{
		if(sidebarVisible && mouseX >= width - SIDEBAR_WIDTH)
			return true;

		HUDMod hovered = hoveredId == null ? null
			: mods.get(hoveredId);
		if(hovered == null || scrollY == 0)
			return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);

		float newScale = Math.round(
			(hovered.getScale() + (float)scrollY * 0.1F) * 10F) / 10F;
		hovered.setScale(newScale);
		hudManager.saveState();
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
			if(other == mod || !other.isEnabled())
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

	private boolean handleSidebarClick(double mouseX, double mouseY)
	{
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

		List<Map.Entry<String, HUDMod>> entries = getEntries();
		int index = page * getRowsPerPage()
			+ (int)((mouseY - SIDEBAR_HEADER) / SIDEBAR_ROW);
		if(index < 0 || index >= entries.size())
			return true;
		HUDMod mod = entries.get(index).getValue();
		hudManager.setModEnabled(mod.getId(), !mod.isEnabled());
		return true;
	}

	private List<Map.Entry<String, HUDMod>> getEntries()
	{
		return new ArrayList<>(mods.entrySet());
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
		return Math.max(1, (mods.size() + rows - 1) / rows);
	}

	@Override
	public boolean keyPressed(KeyEvent event)
	{
		if(event.key() == InputConstants.KEY_ESCAPE)
		{
			onClose();
			return true;
		}
		if(event.key() == InputConstants.KEY_TAB)
		{
			sidebarVisible = !sidebarVisible;
			return true;
		}
		if(event.key() == InputConstants.KEY_T)
		{
			hudManager.setTheme(hudManager.getTheme().next());
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose()
	{
		hudManager.saveState();
		minecraft.setScreenAndShow(null);
	}

	@Override
	public boolean isPauseScreen()
	{
		return false;
	}
}
