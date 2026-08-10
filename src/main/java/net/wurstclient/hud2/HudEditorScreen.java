package net.wurstclient.hud2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
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
import net.wurstclient.gui.visual.VisualTheme;
import net.wurstclient.hud2.render.RiseFrostedGlass;

public final class HudEditorScreen extends Screen
{
	private static final WurstClient WURST = WurstClient.INSTANCE;
	private static final int CARD_WIDTH = 96;
	private static final int CARD_HEIGHT = 44;
	private static final int TOOLBAR_HEIGHT = 32;
	private static final int SNAP_RANGE = 5;
	private static final long RIPPLE_DURATION_NANOS = 650_000_000L;

	private final HudManager hudManager;
	private final HudLayout layout;
	private final Map<String, HudElement> elements;
	private final Screen parentScreen;
	private final Map<String, Float> hoverOpacity = new HashMap<>();
	private final List<ClickRipple> clickRipples = new ArrayList<>();

	private String draggedId;
	private int dragButton = -1;
	private int dragGrabX;
	private int dragGrabY;
	private int dragStartMouseX;
	private int dragStartMouseY;
	private boolean dragMoved;
	private Integer snapLineX;
	private Integer snapLineY;
	private long lastRenderNanos;

	public HudEditorScreen()
	{
		this(null);
	}

	public HudEditorScreen(Screen parentScreen)
	{
		super(Component.literal("HUD Editor"));
		this.parentScreen = parentScreen;
		hudManager = WURST.getHudManager();
		layout = hudManager.getLayout();
		elements = hudManager.getElements();
	}

	@Override
	protected void init()
	{
		clearDrag();
		lastRenderNanos = 0;
		boolean changed = false;
		for(Map.Entry<String, HudLayout.HudElementConfig> entry : layout
			.getElements().entrySet())
		{
			HudElement element = elements.get(entry.getKey());
			if(element == null)
				continue;
			HudLayout.HudElementConfig config = entry.getValue();
			int editorWidth = getEditorWidth(element, config);
			int editorHeight = getEditorHeight(element, config);
			int oldX = getElementX(config, editorWidth);
			int oldY = getElementY(config, editorHeight);
			int x = Mth.clamp(oldX, 0, Math.max(0, width - editorWidth));
			int y = Mth.clamp(oldY, 0,
				Math.max(0, height - TOOLBAR_HEIGHT - editorHeight));
			if(x == oldX && y == oldY)
				continue;
			setAbsolutePosition(config, x, y, editorWidth, editorHeight);
			changed = true;
		}
		if(changed)
			hudManager.saveLayout();
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY,
		float partialTicks)
	{
		renderBackground(graphics);
		renderReferenceGuides(graphics);
		graphics.flush();
		RiseFrostedGlass.captureFrame();

		long now = System.nanoTime();
		float elapsedSeconds = lastRenderNanos == 0 ? 0
			: Math.min(0.1F, (now - lastRenderNanos) / 1_000_000_000F);
		lastRenderNanos = now;
		String hoveredId = findTopmostElement(mouseX, mouseY);
		renderElements(graphics, hoveredId, elapsedSeconds);
		renderSnapGuides(graphics);
		renderToolbar(graphics);
		renderClickRipples(graphics, now);
	}

	private void renderReferenceGuides(GuiGraphics graphics)
	{
		int guideColor = VisualTheme.GRID;
		int centerY = (height - TOOLBAR_HEIGHT) / 2;
		graphics.fill(0, centerY, width, centerY + 1, guideColor);
		graphics.fill(width / 2, 0, width / 2 + 1,
			height - TOOLBAR_HEIGHT, guideColor);
	}

	private void renderElements(GuiGraphics graphics, String hoveredId,
		float elapsedSeconds)
	{
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
			boolean dragging = id.equals(draggedId);
			float opacity = updateHoverOpacity(id,
				id.equals(hoveredId) || dragging, elapsedSeconds);

			if(config.isEnabled() && element.renderEditorPreview())
				hudManager.renderElement(graphics, element, config, elX, elY,
					minecraft.getFrameTime());
			else
				renderElementCard(graphics, element, config, elX, elY);

			int alpha = Math.round(opacity * 255);
			int outline = dragging ? withAlpha(getAccentColor(), alpha)
				: withAlpha(VisualTheme.BORDER_STRONG, alpha);
			int radius = Math.max(3, Math.round(6.5F * config.getScale()));
			FlatRenderer.drawRoundedOutline(graphics, elX - 2, elY - 2,
				elX + editorWidth + 2, elY + editorHeight + 2, radius,
				outline);
		}
	}

	private void renderElementCard(GuiGraphics graphics, HudElement element,
		HudLayout.HudElementConfig config, int x, int y)
	{
		Font font = minecraft.font;
		float scale = config.getScale();
		graphics.pose().pushPose();
		graphics.pose().translate(x, y, 0);
		graphics.pose().scale(scale, scale, 1);

		int bg = config.isEnabled() ? VisualTheme.PANEL
			: VisualTheme.SURFACE_68;
		FlatRenderer.fillRoundedRect(graphics, 0, 0, CARD_WIDTH, CARD_HEIGHT,
			5, bg);
		FlatRenderer.drawRoundedOutline(graphics, 0, 0, CARD_WIDTH,
			CARD_HEIGHT, 5, VisualTheme.BORDER);

		String name = font.plainSubstrByWidth(element.getName(),
			CARD_WIDTH - 12);
		graphics.drawString(font, name, 6, 5,
			config.isEnabled() ? VisualTheme.TEXT : VisualTheme.TEXT_DISABLED,
			false);
		graphics.drawString(font, config.isEnabled() ? "ON" : "OFF", 6, 18,
			config.isEnabled() ? getAccentColor() : VisualTheme.TEXT_DISABLED,
			false);

		String align = config.getHorizontalAlignment().charAt(0) + "/"
			+ config.getVerticalAlignment().charAt(0);
		graphics.drawString(font, align, 6, 31, VisualTheme.TEXT_MUTED, false);
		String scaleText = Math.round(scale * 100) + "%";
		graphics.drawString(font, scaleText,
			CARD_WIDTH - 6 - font.width(scaleText), 31,
			VisualTheme.TEXT_MUTED, false);
		graphics.pose().popPose();
	}

	private float updateHoverOpacity(String id, boolean active,
		float elapsedSeconds)
	{
		float current = hoverOpacity.getOrDefault(id, 0F);
		float target = active ? 1 : 0;
		float amount = 1 - (float)Math.exp(-14 * elapsedSeconds);
		current = Mth.lerp(amount, current, target);
		if(Math.abs(current - target) < 0.005F)
			current = target;
		hoverOpacity.put(id, current);
		return current;
	}

	private void renderSnapGuides(GuiGraphics graphics)
	{
		int color = withAlpha(getAccentColor(), 225);
		if(snapLineX != null)
			graphics.fill(snapLineX, 0, snapLineX + 1,
				height - TOOLBAR_HEIGHT, color);
		if(snapLineY != null)
			graphics.fill(0, snapLineY, width, snapLineY + 1, color);
	}

	private void renderToolbar(GuiGraphics graphics)
	{
		Font font = minecraft.font;
		graphics.fill(0, height - TOOLBAR_HEIGHT, width, height,
			VisualTheme.SURFACE_90);
		graphics.fill(0, height - TOOLBAR_HEIGHT, width,
			height - TOOLBAR_HEIGHT + 1, VisualTheme.BORDER);
		String hint = "\u5355\u51fb\u5f00\u5173  |  \u5de6\u952e\u5438\u9644\u62d6\u52a8  |  "
			+ "\u53f3\u952e\u81ea\u7531\u62d6\u52a8  |  \u6eda\u8f6e\u7f29\u653e  |  \u4e2d\u952e\u91cd\u7f6e\u7f29\u653e";
		hint = font.plainSubstrByWidth(hint, Math.max(0, width - 12));
		graphics.drawCenteredString(font, hint, width / 2,
			height - TOOLBAR_HEIGHT + 10, VisualTheme.TEXT_MUTED);
	}

	private void renderClickRipples(GuiGraphics graphics, long now)
	{
		Iterator<ClickRipple> iterator = clickRipples.iterator();
		while(iterator.hasNext())
		{
			ClickRipple ripple = iterator.next();
			float progress = (now - ripple.startedAt())
				/ (float)RIPPLE_DURATION_NANOS;
			if(progress >= 1)
			{
				iterator.remove();
				continue;
			}
			float eased = 1 - (1 - progress) * (1 - progress);
			int radius = 2 + Math.round(eased * 8);
			int color = withAlpha(getAccentColor(),
				Math.round((1 - progress) * 210));
			FlatRenderer.drawRoundedOutline(graphics, ripple.x() - radius,
				ripple.y() - radius, ripple.x() + radius,
				ripple.y() + radius, radius, color);
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button)
	{
		clickRipples.add(new ClickRipple((int)mouseX, (int)mouseY,
			System.nanoTime()));
		String id = findTopmostElement(mouseX, mouseY);
		if(id == null)
			return super.mouseClicked(mouseX, mouseY, button);

		HudLayout.HudElementConfig config = layout.get(id);
		HudElement element = elements.get(id);
		if(config == null || element == null)
			return false;

		int editorWidth = getEditorWidth(element, config);
		int editorHeight = getEditorHeight(element, config);
		int elX = getElementX(config, editorWidth);
		int elY = getElementY(config, editorHeight);

		if(button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE)
		{
			config.setScale(1);
			int newWidth = getEditorWidth(element, config);
			int newHeight = getEditorHeight(element, config);
			setAbsolutePosition(config, elX, elY, newWidth, newHeight);
			hudManager.updateElementLayout(id,
				config.getHorizontalAlignment(), config.getVerticalAlignment(),
				config.getHorizontalOffset(), config.getVerticalOffset());
			return true;
		}

		if(button != GLFW.GLFW_MOUSE_BUTTON_LEFT
			&& button != GLFW.GLFW_MOUSE_BUTTON_RIGHT)
			return super.mouseClicked(mouseX, mouseY, button);

		draggedId = id;
		dragButton = button;
		dragGrabX = (int)mouseX - elX;
		dragGrabY = (int)mouseY - elY;
		dragStartMouseX = (int)mouseX;
		dragStartMouseY = (int)mouseY;
		dragMoved = false;
		snapLineX = null;
		snapLineY = null;
		return true;
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button)
	{
		if(draggedId == null || button != dragButton)
			return super.mouseReleased(mouseX, mouseY, button);

		String id = draggedId;
		HudLayout.HudElementConfig config = layout.get(id);
		HudElement element = elements.get(id);
		if(config != null && element != null)
		{
			int editorWidth = getEditorWidth(element, config);
			int editorHeight = getEditorHeight(element, config);
			int absoluteX = getElementX(config, editorWidth);
			int absoluteY = getElementY(config, editorHeight);

			if(!dragMoved && button == GLFW.GLFW_MOUSE_BUTTON_LEFT)
			{
				hudManager.toggleElementEnabled(id);
				editorWidth = getEditorWidth(element, config);
				editorHeight = getEditorHeight(element, config);
				setAbsolutePosition(config, absoluteX, absoluteY, editorWidth,
					editorHeight);
			}else if(!dragMoved && button == GLFW.GLFW_MOUSE_BUTTON_RIGHT)
			{
				cycleAlignment(config);
				setAbsolutePosition(config, absoluteX, absoluteY, editorWidth,
					editorHeight);
			}

			hudManager.updateElementLayout(id,
				config.getHorizontalAlignment(), config.getVerticalAlignment(),
				config.getHorizontalOffset(), config.getVerticalOffset());
		}

		clearDrag();
		return true;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button,
		double deltaX, double deltaY)
	{
		if(draggedId == null || button != dragButton)
			return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);

		HudLayout.HudElementConfig config = layout.get(draggedId);
		HudElement element = elements.get(draggedId);
		if(config == null || element == null)
			return false;

		int dx = (int)mouseX - dragStartMouseX;
		int dy = (int)mouseY - dragStartMouseY;
		if(Math.abs(dx) >= 3 || Math.abs(dy) >= 3)
			dragMoved = true;

		int editorWidth = getEditorWidth(element, config);
		int editorHeight = getEditorHeight(element, config);
		int newX = (int)mouseX - dragGrabX;
		int newY = (int)mouseY - dragGrabY;
		newX = Mth.clamp(newX, 0, Math.max(0, width - editorWidth));
		newY = Mth.clamp(newY, 0,
			Math.max(0, height - TOOLBAR_HEIGHT - editorHeight));

		if(dragButton == GLFW.GLFW_MOUSE_BUTTON_LEFT)
		{
			int[] snapped = snapPosition(draggedId, newX, newY, editorWidth,
				editorHeight);
			newX = snapped[0];
			newY = snapped[1];
		}else
		{
			snapLineX = null;
			snapLineY = null;
		}

		setAbsolutePosition(config, newX, newY, editorWidth, editorHeight);
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double delta)
	{
		String id = findTopmostElement(mouseX, mouseY);
		HudLayout.HudElementConfig config = id == null ? null : layout.get(id);
		HudElement element = id == null ? null : elements.get(id);
		if(config == null || element == null || delta == 0)
			return super.mouseScrolled(mouseX, mouseY, delta);

		int oldWidth = getEditorWidth(element, config);
		int oldHeight = getEditorHeight(element, config);
		int absoluteX = getElementX(config, oldWidth);
		int absoluteY = getElementY(config, oldHeight);
		float newScale = config.getScale() + (delta > 0 ? 0.1F : -0.1F);
		config.setScale(Math.round(newScale * 10) / 10F);
		int newWidth = getEditorWidth(element, config);
		int newHeight = getEditorHeight(element, config);
		absoluteX = Mth.clamp(absoluteX, 0, Math.max(0, width - newWidth));
		absoluteY = Mth.clamp(absoluteY, 0,
			Math.max(0, height - TOOLBAR_HEIGHT - newHeight));
		setAbsolutePosition(config, absoluteX, absoluteY, newWidth, newHeight);
		hudManager.updateElementLayout(id, config.getHorizontalAlignment(),
			config.getVerticalAlignment(), config.getHorizontalOffset(),
			config.getVerticalOffset());
		return true;
	}

	private int[] snapPosition(String movingId, int x, int y, int elementWidth,
		int elementHeight)
	{
		int snappedX = x;
		int snappedY = y;
		int bestXDistance = SNAP_RANGE + 1;
		int bestYDistance = SNAP_RANGE + 1;
		snapLineX = null;
		snapLineY = null;

		int centerX = (width - elementWidth) / 2;
		int centerY = (height - TOOLBAR_HEIGHT - elementHeight) / 2;
		if(Math.abs(x - centerX) <= SNAP_RANGE)
		{
			snappedX = centerX;
			bestXDistance = Math.abs(x - centerX);
			snapLineX = width / 2;
		}
		if(Math.abs(y - centerY) <= SNAP_RANGE)
		{
			snappedY = centerY;
			bestYDistance = Math.abs(y - centerY);
			snapLineY = (height - TOOLBAR_HEIGHT) / 2;
		}

		for(Map.Entry<String, HudLayout.HudElementConfig> entry : layout
			.getElements().entrySet())
		{
			if(entry.getKey().equals(movingId) || !entry.getValue().isEnabled())
				continue;
			HudElement other = elements.get(entry.getKey());
			if(other == null)
				continue;

			int otherWidth = getEditorWidth(other, entry.getValue());
			int otherHeight = getEditorHeight(other, entry.getValue());
			int otherX = getElementX(entry.getValue(), otherWidth);
			int otherY = getElementY(entry.getValue(), otherHeight);
			int[] targetXs = {otherX, otherX + otherWidth};
			int[] targetYs = {otherY, otherY + otherHeight};

			for(int targetX : targetXs)
				for(int movingEdge : new int[]{0, elementWidth})
				{
					int candidate = targetX - movingEdge;
					int distance = Math.abs(candidate - x);
					if(distance <= SNAP_RANGE && distance < bestXDistance
						&& candidate >= 0 && candidate + elementWidth <= width)
					{
						snappedX = candidate;
						bestXDistance = distance;
						snapLineX = targetX;
					}
				}

			for(int targetY : targetYs)
				for(int movingEdge : new int[]{0, elementHeight})
				{
					int candidate = targetY - movingEdge;
					int distance = Math.abs(candidate - y);
					if(distance <= SNAP_RANGE && distance < bestYDistance
						&& candidate >= 0 && candidate + elementHeight
							<= height - TOOLBAR_HEIGHT)
					{
						snappedY = candidate;
						bestYDistance = distance;
						snapLineY = targetY;
					}
				}
		}
		return new int[]{snappedX, snappedY};
	}

	private String findTopmostElement(double mouseX, double mouseY)
	{
		List<Map.Entry<String, HudLayout.HudElementConfig>> entries =
			new ArrayList<>(layout.getElements().entrySet());
		for(int i = entries.size() - 1; i >= 0; i--)
		{
			Map.Entry<String, HudLayout.HudElementConfig> entry = entries.get(i);
			HudElement element = elements.get(entry.getKey());
			if(element == null)
				continue;
			int editorWidth = getEditorWidth(element, entry.getValue());
			int editorHeight = getEditorHeight(element, entry.getValue());
			int x = getElementX(entry.getValue(), editorWidth);
			int y = getElementY(entry.getValue(), editorHeight);
			if(mouseX >= x && mouseY >= y && mouseX < x + editorWidth
				&& mouseY < y + editorHeight)
				return entry.getKey();
		}
		return null;
	}

	private void setAbsolutePosition(HudLayout.HudElementConfig config, int x,
		int y, int elementWidth, int elementHeight)
	{
		if(config.getHorizontalAlignment()
			.equals(HudLayout.HudElementConfig.HORIZONTAL_RIGHT))
			config.setHorizontalOffset(width - elementWidth - x);
		else if(config.getHorizontalAlignment()
			.equals(HudLayout.HudElementConfig.HORIZONTAL_CENTER))
			config.setHorizontalOffset(x - (width - elementWidth) / 2);
		else
			config.setHorizontalOffset(x);

		if(config.getVerticalAlignment()
			.equals(HudLayout.HudElementConfig.VERTICAL_BOTTOM))
			config.setVerticalOffset(height - elementHeight - y);
		else if(config.getVerticalAlignment()
			.equals(HudLayout.HudElementConfig.VERTICAL_CENTER))
			config.setVerticalOffset(y - (height - elementHeight) / 2);
		else
			config.setVerticalOffset(y);
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
		if(draggedId != null)
		{
			HudLayout.HudElementConfig config = layout.get(draggedId);
			if(config != null)
				hudManager.updateElementLayout(draggedId,
					config.getHorizontalAlignment(),
					config.getVerticalAlignment(),
					config.getHorizontalOffset(), config.getVerticalOffset());
		}
		clearDrag();
		minecraft.setScreen(parentScreen);
	}

	@Override
	public boolean isPauseScreen()
	{
		return false;
	}

	private void clearDrag()
	{
		draggedId = null;
		dragButton = -1;
		dragMoved = false;
		snapLineX = null;
		snapLineY = null;
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
		int baseWidth = config.isEnabled() && element.renderEditorPreview()
			? element.getWidth() : CARD_WIDTH;
		return Math.max(1, Math.round(baseWidth * config.getScale()));
	}

	private int getEditorHeight(HudElement element,
		HudLayout.HudElementConfig config)
	{
		int baseHeight = config.isEnabled() && element.renderEditorPreview()
			? element.getHeight() : CARD_HEIGHT;
		return Math.max(1, Math.round(baseHeight * config.getScale()));
	}

	private void cycleAlignment(HudLayout.HudElementConfig config)
	{
		String[] horizontal = {HudLayout.HudElementConfig.HORIZONTAL_LEFT,
			HudLayout.HudElementConfig.HORIZONTAL_CENTER,
			HudLayout.HudElementConfig.HORIZONTAL_RIGHT};
		String[] vertical = {HudLayout.HudElementConfig.VERTICAL_TOP,
			HudLayout.HudElementConfig.VERTICAL_CENTER,
			HudLayout.HudElementConfig.VERTICAL_BOTTOM};
		int horizontalIndex = indexOf(horizontal,
			config.getHorizontalAlignment()) + 1;
		int verticalIndex = indexOf(vertical, config.getVerticalAlignment());
		if(horizontalIndex >= horizontal.length)
		{
			horizontalIndex = 0;
			verticalIndex = (verticalIndex + 1) % vertical.length;
		}
		config.setHorizontalAlignment(horizontal[horizontalIndex]);
		config.setVerticalAlignment(vertical[verticalIndex]);
	}

	private int indexOf(String[] values, String value)
	{
		for(int i = 0; i < values.length; i++)
			if(values[i].equals(value))
				return i;
		return 0;
	}

	private int getAccentColor()
	{
		return VisualTheme.ACCENT;
	}

	private int withAlpha(int color, int alpha)
	{
		return color & 0x00FFFFFF | Mth.clamp(alpha, 0, 255) << 24;
	}

	private record ClickRipple(int x, int y, long startedAt)
	{
	}
}
