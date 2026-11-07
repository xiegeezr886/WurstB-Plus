package net.wurstclient.hud2;

import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.wurstclient.WurstClient;
import net.wurstclient.clickgui2.FlatRenderer;

public final class HudEditorScreen extends Screen
{
	private static final WurstClient WURST = WurstClient.INSTANCE;
	private static final int GRID_SIZE = 40;
	private static final int CARD_WIDTH = 96;
	private static final int CARD_HEIGHT = 44;
	private static final int SIDEBAR_WIDTH = 230;
	private static final int SIDEBAR_HEADER = 26;
	private static final int SIDEBAR_FOOTER = 26;
	private static final int SIDEBAR_ROW = 16;
	private static final int TOOLBAR_HEIGHT = 32;

	private final HudManager hudManager;
	private final HudLayout layout;
	private final Map<String, HudElement> elements;

	private String draggedId;
	private int dragStartX;
	private int dragStartY;
	private int dragStartOffsetX;
	private int dragStartOffsetY;
	private int page;

	public HudEditorScreen()
	{
		super(Component.literal("HUD Editor"));
		hudManager = WURST.getHudManager();
		layout = hudManager.getLayout();
		elements = hudManager.getElements();
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks)
	{
		renderBackground(graphics, mouseX, mouseY, partialTicks);
		renderContents(graphics, mouseX, mouseY, partialTicks);
	}

	private void renderContents(GuiGraphics graphics, int mouseX,
		int mouseY, float partialTicks)
	{
		renderGrid(graphics);
		renderElements(graphics, mouseX, mouseY, partialTicks);
		renderSidebar(graphics, mouseX, mouseY);
		renderToolbar(graphics, mouseX, mouseY);
	}

	private void renderGrid(GuiGraphics graphics)
	{
		for(int x = 0; x < width; x += GRID_SIZE)
			graphics.fill(x, 0, x + 1, height, 0x0CFFFFFF);
		for(int y = 0; y < height; y += GRID_SIZE)
			graphics.fill(0, y, width, y + 1, 0x0CFFFFFF);
	}

	private void renderElements(GuiGraphics graphics, int mouseX,
		int mouseY, float partialTicks)
	{
		Font font = minecraft.font;
		for(Map.Entry<String, HudLayout.HudElementConfig> entry : layout
			.getElements().entrySet())
		{
			String id = entry.getKey();
			HudLayout.HudElementConfig config = entry.getValue();
			HudElement element = elements.get(id);
			if(element == null || !config.isEnabled())
				continue;

			int editorWidth = getEditorWidth(element);
			int editorHeight = getEditorHeight(element);
			int elX = getElementX(config, editorWidth);
			int elY = getElementY(config, editorHeight);
			boolean hovering = mouseX >= elX && mouseY >= elY
				&& mouseX < elX + editorWidth
				&& mouseY < elY + editorHeight;
			boolean dragging = id.equals(draggedId);

			int border = dragging ? SoarHudStyle.ACCENT_COLOR
				: hovering ? 0x99006366 : 0x20FFFFFF;
			if(element.renderEditorPreview())
			{
				element.render(graphics, elX, elY, partialTicks);
				FlatRenderer.drawRoundedOutline(graphics, elX, elY,
					elX + editorWidth, elY + editorHeight,
					SoarHudStyle.RADIUS, border);
				continue;
			}

			FlatRenderer.fillRoundedRect(graphics, elX, elY,
				elX + editorWidth, elY + editorHeight,
				SoarHudStyle.RADIUS, SoarHudStyle.PANEL_COLOR);
			FlatRenderer.drawRoundedOutline(graphics, elX, elY,
				elX + editorWidth, elY + editorHeight,
				SoarHudStyle.RADIUS, border);

			String name = font.plainSubstrByWidth(element.getTranslatedName(),
				CARD_WIDTH - 12);
			graphics.drawString(font, name, elX + 6, elY + 5,
				SoarHudStyle.TEXT_COLOR, false);

			int statusColor = SoarHudStyle.ACCENT_COLOR;
			graphics.drawString(font, "ON", elX + 6, elY + 18, statusColor, false);

			String align = config.getHorizontalAlignment().charAt(0) + "/"
				+ config.getVerticalAlignment().charAt(0);
			graphics.drawString(font, align, elX + 6, elY + 31,
				SoarHudStyle.MUTED_TEXT_COLOR, false);
		}
	}

	private void renderSidebar(GuiGraphics graphics, int mouseX,
		int mouseY)
	{
		Font font = minecraft.font;
		int x1 = width - SIDEBAR_WIDTH;
		int y1 = 0;
		int y2 = height - TOOLBAR_HEIGHT;
		graphics.fill(x1, y1, width, y2, 0xCC000000);
		graphics.fill(x1, y1, x1 + 1, y2, 0x20FFFFFF);

		graphics.drawString(font, "HUD 元素", x1 + 10, y1 + 8,
			SoarHudStyle.TEXT_COLOR, false);
		int totalPages = getPageCount();
		String pageText = (page + 1) + " / " + totalPages;
		graphics.drawString(font, pageText, width - 10 - font.width(pageText),
			y1 + 8, SoarHudStyle.MUTED_TEXT_COLOR, false);

		List<Map.Entry<String, HudLayout.HudElementConfig>> entries =
			getEntries();
		int rows = getRowsPerPage();
		int start = page * rows;
		int end = Math.min(entries.size(), start + rows);
		for(int index = start; index < end; index++)
		{
			Map.Entry<String, HudLayout.HudElementConfig> entry =
				entries.get(index);
			HudElement element = elements.get(entry.getKey());
			if(element == null)
				continue;
			HudLayout.HudElementConfig config = entry.getValue();
			int rowY = y1 + SIDEBAR_HEADER
				+ (index - start) * SIDEBAR_ROW;
			boolean hovering = mouseX >= x1 + 6 && mouseX < width - 6
				&& mouseY >= rowY && mouseY < rowY + SIDEBAR_ROW;
			if(hovering)
				graphics.fill(x1 + 5, rowY, width - 5,
					rowY + SIDEBAR_ROW - 1, 0x18FFFFFF);

			int dotX = x1 + 10;
			int dotY = rowY + 4;
			if(config.isEnabled())
				FlatRenderer.fillRoundedRect(graphics, dotX, dotY,
					dotX + 8, dotY + 8, 4, SoarHudStyle.ACCENT_COLOR);
			else
				FlatRenderer.drawRoundedOutline(graphics, dotX, dotY,
					dotX + 8, dotY + 8, 4, 0x60FFFFFF);

			String name = font.plainSubstrByWidth(element.getTranslatedName(),
				SIDEBAR_WIDTH - 44);
			graphics.drawString(font, name, x1 + 24, rowY + 3,
				config.isEnabled() ? SoarHudStyle.TEXT_COLOR
					: SoarHudStyle.MUTED_TEXT_COLOR,
				false);
		}

		int footerY = y2 - SIDEBAR_FOOTER;
		graphics.fill(x1, footerY, width, y2, 0x40000000);
		drawPageButton(graphics, font, x1 + 8, footerY + 4, 72,
			"\u4e0a\u4e00\u9875", page > 0, mouseX, mouseY);
		drawPageButton(graphics, font, width - 80, footerY + 4, 72,
			"\u4e0b\u4e00\u9875", page + 1 < totalPages, mouseX, mouseY);
	}

	private void drawPageButton(GuiGraphics graphics, Font font,
		int x, int y, int width, String label, boolean active, int mouseX,
		int mouseY)
	{
		boolean hovering = active && mouseX >= x && mouseX < x + width
			&& mouseY >= y && mouseY < y + 18;
		FlatRenderer.fillRoundedRect(graphics, x, y, x + width, y + 18,
			6, hovering ? 0x80006366 : 0x40FFFFFF);
		graphics.drawCenteredString(font, label, x + width / 2, y + 5,
			active ? SoarHudStyle.TEXT_COLOR : 0x50FFFFFF);
	}

	private void renderToolbar(GuiGraphics graphics, int mouseX,
		int mouseY)
	{
		Font font = minecraft.font;
		graphics.fill(0, height - TOOLBAR_HEIGHT, width, height, 0x99000000);

		String hint = "\u5de6\u952e\u62d6\u52a8  |  \u53f3\u952e\u5207\u6362\u951a\u70b9  |  "
			+ "\u4fa7\u680f\u5f00\u5173  |  ESC \u5173\u95ed";
		graphics.drawCenteredString(font, hint, width / 2,
			height - TOOLBAR_HEIGHT + 10, SoarHudStyle.MUTED_TEXT_COLOR);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button)
	{

		if(handleSidebarClick(mouseX, mouseY))
			return true;

		for(Map.Entry<String, HudLayout.HudElementConfig> entry : layout
			.getElements().entrySet())
		{
			String id = entry.getKey();
			HudLayout.HudElementConfig config = entry.getValue();
			HudElement element = elements.get(id);
			if(element == null || !config.isEnabled())
				continue;
			int editorWidth = getEditorWidth(element);
			int editorHeight = getEditorHeight(element);
			int elX = getElementX(config, editorWidth);
			int elY = getElementY(config, editorHeight);

			if(mouseX >= elX && mouseY >= elY
				&& mouseX < elX + editorWidth
				&& mouseY < elY + editorHeight)
			{
				if(button == GLFW.GLFW_MOUSE_BUTTON_LEFT)
				{
					draggedId = id;
					dragStartX = (int)mouseX;
					dragStartY = (int)mouseY;
					dragStartOffsetX = config.getHorizontalOffset();
					dragStartOffsetY = config.getVerticalOffset();
					return true;
				}
				if(button == GLFW.GLFW_MOUSE_BUTTON_RIGHT)
				{
					cycleAlignment(config);
					hudManager.updateElementLayout(id,
						config.getHorizontalAlignment(),
						config.getVerticalAlignment(),
						config.getHorizontalOffset(),
						config.getVerticalOffset());
					return true;
				}
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
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
			if(mouseY < footerY + 4 || mouseY >= footerY + 22)
				return true;
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

		List<Map.Entry<String, HudLayout.HudElementConfig>> entries =
			getEntries();
		int index = page * getRowsPerPage()
			+ (int)((mouseY - SIDEBAR_HEADER) / SIDEBAR_ROW);
		if(index < 0 || index >= entries.size())
			return true;
		Map.Entry<String, HudLayout.HudElementConfig> entry =
			entries.get(index);
		if(elements.get(entry.getKey()) == null)
			return true;
		hudManager.setElementEnabled(entry.getKey(),
			!entry.getValue().isEnabled());
		return true;
	}

	private List<Map.Entry<String, HudLayout.HudElementConfig>> getEntries()
	{
		List<Map.Entry<String, HudLayout.HudElementConfig>> entries =
			new ArrayList<>();
		for(Map.Entry<String, HudLayout.HudElementConfig> entry : layout
			.getElements().entrySet())
			if(elements.get(entry.getKey()) != null)
				entries.add(entry);
		return entries;
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
		return Math.max(1, (getEntries().size() + rows - 1) / rows);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button)
	{
		if(draggedId != null
			&& button == GLFW.GLFW_MOUSE_BUTTON_LEFT)
		{
			HudLayout.HudElementConfig config = layout.get(draggedId);
			if(config != null)
			{
				int dx = config.getHorizontalOffset() - dragStartOffsetX;
				int dy = config.getVerticalOffset() - dragStartOffsetY;
				if(Math.abs(dx) < 3 && Math.abs(dy) < 3)
					hudManager.toggleElementEnabled(draggedId);
				else
					hudManager.updateElementLayout(draggedId,
						config.getHorizontalAlignment(),
						config.getVerticalAlignment(),
						config.getHorizontalOffset(),
						config.getVerticalOffset());
			}
			draggedId = null;
			return true;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button,
		double deltaX, double deltaY)
	{
		if(draggedId == null)
			return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);

		HudLayout.HudElementConfig config = layout.get(draggedId);
		if(config == null)
			return false;
		HudElement element = elements.get(draggedId);
		if(element == null)
			return false;

		int dx = (int)mouseX - dragStartX;
		int dy = (int)mouseY - dragStartY;

		int newX, newY;
		if(config.getHorizontalAlignment()
			.equals(HudLayout.HudElementConfig.HORIZONTAL_RIGHT))
			newX = dragStartOffsetX - dx;
		else
			newX = dragStartOffsetX + dx;

		if(config.getVerticalAlignment()
			.equals(HudLayout.HudElementConfig.VERTICAL_BOTTOM))
			newY = dragStartOffsetY - dy;
		else
			newY = dragStartOffsetY + dy;

		int editorWidth = getEditorWidth(element);
		int editorHeight = getEditorHeight(element);
		config.setHorizontalOffset(Mth.clamp(newX, 0,
			Math.max(0, width - SIDEBAR_WIDTH - editorWidth)));
		config.setVerticalOffset(Mth.clamp(newY, 0,
			Math.max(0, height - editorHeight - TOOLBAR_HEIGHT)));
		return true;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers)
	{
		if(keyCode == GLFW.GLFW_KEY_ESCAPE)
		{
			onClose();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public void onClose()
	{
		minecraft.setScreen(null);
	}

	@Override
	public boolean isPauseScreen()
	{
		return false;
	}

	private int getElementX(HudLayout.HudElementConfig config,
		int elementWidth)
	{
		return hudManager.getElementX(config, canvasWidth(), elementWidth);
	}

	private int canvasWidth()
	{
		return width - SIDEBAR_WIDTH;
	}

	private int getElementY(HudLayout.HudElementConfig config,
		int elementHeight)
	{
		return hudManager.getElementY(config, height, elementHeight);
	}

	private int getEditorWidth(HudElement element)
	{
		return element.renderEditorPreview() ? element.getWidth()
			: CARD_WIDTH;
	}

	private int getEditorHeight(HudElement element)
	{
		return element.renderEditorPreview() ? element.getHeight()
			: CARD_HEIGHT;
	}

	private void cycleAlignment(HudLayout.HudElementConfig config)
	{
		String h = config.getHorizontalAlignment();
		String v = config.getVerticalAlignment();

		if(h.equals(HudLayout.HudElementConfig.HORIZONTAL_LEFT)
			&& v.equals(HudLayout.HudElementConfig.VERTICAL_TOP))
		{
			config.setHorizontalAlignment(
				HudLayout.HudElementConfig.HORIZONTAL_RIGHT);
		}else if(h.equals(HudLayout.HudElementConfig.HORIZONTAL_RIGHT)
			&& v.equals(HudLayout.HudElementConfig.VERTICAL_TOP))
		{
			config.setVerticalAlignment(
				HudLayout.HudElementConfig.VERTICAL_BOTTOM);
		}else if(h.equals(HudLayout.HudElementConfig.HORIZONTAL_RIGHT)
			&& v.equals(HudLayout.HudElementConfig.VERTICAL_BOTTOM))
		{
			config.setHorizontalAlignment(
				HudLayout.HudElementConfig.HORIZONTAL_LEFT);
		}else
		{
			config.setVerticalAlignment(
				HudLayout.HudElementConfig.VERTICAL_TOP);
		}
	}
}
