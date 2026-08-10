package net.wurstclient.hud2;

import net.minecraft.client.gui.GuiGraphics;

import java.util.Map;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.Font;
import net.wurstclient.util.render.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
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

	private final HudManager hudManager;
	private final HudLayout layout;
	private final Map<String, HudElement> elements;

	private String draggedId;
	private int dragStartX;
	private int dragStartY;
	private int dragStartOffsetX;
	private int dragStartOffsetY;

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
		renderContents(new GuiGraphicsExtractor(graphics), mouseX, mouseY, partialTicks);
	}

	private void renderContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
		float partialTicks)
	{

		renderGrid(graphics);
		renderElements(graphics, mouseX, mouseY);
		renderToolbar(graphics, mouseX, mouseY);
	}

	private void renderGrid(GuiGraphicsExtractor graphics)
	{
		for(int x = 0; x < width; x += GRID_SIZE)
			graphics.fill(x, 0, x + 1, height, 0x08FFFFFF);
		for(int y = 0; y < height; y += GRID_SIZE)
			graphics.fill(0, y, width, y + 1, 0x08FFFFFF);
	}

	private void renderElements(GuiGraphicsExtractor graphics, int mouseX, int mouseY)
	{
		Font font = minecraft.font;
		for(Map.Entry<String, HudLayout.HudElementConfig> entry : layout
			.getElements().entrySet())
		{
			String id = entry.getKey();
			HudLayout.HudElementConfig config = entry.getValue();
			HudElement element = elements.get(id);
			if(element == null)
				continue;

			int editorWidth = getEditorWidth(element, config);
			int editorHeight = getEditorHeight(element, config);
			int elX = getElementX(config, editorWidth);
			int elY = getElementY(config, editorHeight);
			boolean hovering = mouseX >= elX && mouseY >= elY
				&& mouseX < elX + editorWidth
				&& mouseY < elY + editorHeight;
			boolean dragging = id.equals(draggedId);

			int border = dragging ? 0xFF006366
				: hovering ? 0x80006366 : 0x40202020;
			if(config.isEnabled() && element.renderEditorPreview())
			{
				element.render(graphics, elX, elY,
					minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false));
				FlatRenderer.drawRoundedOutline(graphics, elX, elY,
					elX + editorWidth, elY + editorHeight, 8, border);
				continue;
			}

			int bg = config.isEnabled() ? 0x900D0D0D : 0x60050505;
			FlatRenderer.fillRoundedRect(graphics, elX, elY,
				elX + editorWidth, elY + editorHeight, 5, bg);
			FlatRenderer.drawRoundedOutline(graphics, elX, elY,
				elX + editorWidth, elY + editorHeight, 5, border);

			String name = font.plainSubstrByWidth(element.getName(),
				CARD_WIDTH - 12);
			graphics.text(font, name, elX + 6, elY + 5,
				config.isEnabled() ? 0xFFF2F4F7 : 0xFF727B88, false);

			String status = config.isEnabled() ? "ON" : "OFF";
			int statusColor = config.isEnabled() ? 0xFF006366 : 0xFF4C5562;
			graphics.text(font, status, elX + 6, elY + 18,
				statusColor, false);

			String align = config.getHorizontalAlignment().charAt(0) + "/"
				+ config.getVerticalAlignment().charAt(0);
			graphics.text(font, align, elX + 6, elY + 31, 0xFF4C5562,
				false);
		}
	}

	private void renderToolbar(GuiGraphicsExtractor graphics, int mouseX, int mouseY)
	{
		Font font = minecraft.font;
		int barH = 32;
		graphics.fill(0, height - barH, width, height, 0xC8080808);

		String hint = "\u5355\u51fb\u5f00\u5173  |  \u62d6\u52a8\u79fb\u52a8  |  "
			+ "\u53f3\u952e\u5207\u6362\u951a\u70b9  |  ESC \u5173\u95ed";
		graphics.centeredText(font, hint, width / 2, height - barH + 10,
			0xFF727B88);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick)
	{
		double mouseX = event.x();
		double mouseY = event.y();
		int button = event.button();
		for(Map.Entry<String, HudLayout.HudElementConfig> entry : layout
			.getElements().entrySet())
		{
			String id = entry.getKey();
			HudLayout.HudElementConfig config = entry.getValue();
			HudElement element = elements.get(id);
			if(element == null)
				continue;
			int editorWidth = getEditorWidth(element, config);
			int editorHeight = getEditorHeight(element, config);
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
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event)
	{
		if(draggedId != null && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT)
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
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event,
		double deltaX, double deltaY)
	{
		double mouseX = event.x();
		double mouseY = event.y();
		if(draggedId == null)
			return super.mouseDragged(event, deltaX, deltaY);

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

		int editorWidth = getEditorWidth(element, config);
		int editorHeight = getEditorHeight(element, config);
		config.setHorizontalOffset(Mth.clamp(newX, 0,
			Math.max(0, width - editorWidth)));
		config.setVerticalOffset(Mth.clamp(newY, 0,
			Math.max(0, height - editorHeight - 32)));
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event)
	{
		if(event.key() == GLFW.GLFW_KEY_ESCAPE)
		{
			onClose();
			return true;
		}
		return super.keyPressed(event);
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
		return hudManager.getElementX(config, width, elementWidth);
	}

	private int getElementY(HudLayout.HudElementConfig config,
		int elementHeight)
	{
		return hudManager.getElementY(config, height, elementHeight);
	}

	private int getEditorWidth(HudElement element,
		HudLayout.HudElementConfig config)
	{
		return config.isEnabled() && element.renderEditorPreview()
			? element.getWidth() : CARD_WIDTH;
	}

	private int getEditorHeight(HudElement element,
		HudLayout.HudElementConfig config)
	{
		return config.isEnabled() && element.renderEditorPreview()
			? element.getHeight() : CARD_HEIGHT;
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
