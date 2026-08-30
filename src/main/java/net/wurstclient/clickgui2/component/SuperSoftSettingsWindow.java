package net.wurstclient.clickgui2.component;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.wurstclient.Feature;
import net.wurstclient.clickgui2.GuiIcon;
import net.wurstclient.clickgui2.SettingTreeLayout;
import net.wurstclient.clickgui2.supersoft.SuperSoftRenderer;
import net.wurstclient.clickgui2.supersoft.EpsilonMd3Theme;
import net.wurstclient.clickgui2.supersoft.SuperSoftTheme;
import net.wurstclient.clickgui2.supersoft.UiTween;
import net.wurstclient.settings.Setting;

final class SuperSoftSettingsWindow implements SuperSoftFloatingWindow
{
	private static final int WIDTH = 150;
	private static final int HEADER_HEIGHT = 20;
	private static final int INDENT = 10;

	private final String id;
	private final Feature feature;
	private final List<Setting> roots;
	private final Map<Setting, GuiComponent> componentCache =
		new IdentityHashMap<>();
	private final Map<Setting, UiTween> arrowMotions = new IdentityHashMap<>();
	private final UiTween bodyMotion = new UiTween(0, 200);
	private List<Setting> visibleSettings = List.of();
	private List<Entry> entries = List.of();
	private double x;
	private double y;
	private double scrollOffset;
	private int renderedBodyHeight;
	private GuiComponent capturedComponent;
	private boolean collapsed;
	private boolean closing;
	private boolean closed;
	private boolean visible = true;

	SuperSoftSettingsWindow(String id, Feature feature, double x, double y)
	{
		this.id = id;
		this.feature = feature;
		this.x = x;
		this.y = y;
		roots = List.copyOf(feature.getSettings().values());
		refreshEntries();
	}

	@Override
	public String getId()
	{
		return id;
	}

	Feature getFeature()
	{
		return feature;
	}

	void close()
	{
		if(closing || closed)
			return;
		closing = true;
		capturedComponent = null;
		releaseTextFocus(componentCache.values());
	}

	boolean isOpen()
	{
		return !closing && !closed;
	}

	void reopen()
	{
		if(!closed)
			closing = false;
	}

	@Override
	public double getX()
	{
		return x;
	}

	@Override
	public double getY()
	{
		return y;
	}

	@Override
	public boolean isVisible()
	{
		return visible && !closed;
	}

	@Override
	public void setVisible(boolean visible)
	{
		this.visible = visible;
	}

	@Override
	public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY,
		float partialTicks, int maxBodyHeight, VapeGuiContext context)
	{
		if(!isVisible())
			return;
		refreshIfNeeded();
		int contentHeight = contentHeight();
		int fullBodyHeight = Math.min(maxBodyHeight, contentHeight);
		int bodyHeight = Math.round(fullBodyHeight
			* bodyMotion.update(collapsed || closing ? 0 : 1));
		renderedBodyHeight = bodyHeight;
		clampScroll(contentHeight, bodyHeight);
		int bottom = (int)y + HEADER_HEIGHT + bodyHeight;
		SuperSoftRenderer.window(graphics, (int)x, (int)y, (int)x + WIDTH,
			bottom, 2, SuperSoftTheme.BORDER);
		SuperSoftRenderer.header(graphics, (int)x, (int)y, (int)x + WIDTH,
			(int)y + HEADER_HEIGHT, 2, SuperSoftTheme.HEADER);
		String title = feature.getDisplayName() + " Settings";
		graphics.drawString(font, font.plainSubstrByWidth(title, WIDTH - 40),
			(int)x + 8, (int)y + 6, SuperSoftTheme.TEXT, false);
		GuiIcon.CLOSE.draw(graphics, (int)x + WIDTH - 15, (int)y + 6, 8,
			SuperSoftTheme.TEXT_SECONDARY);
		if(bodyHeight == 0)
			return;

		context.enableScissor(graphics, x, y + HEADER_HEIGHT, x + WIDTH, bottom);
		double cursorY = y + HEADER_HEIGHT - scrollOffset;
		for(Entry entry : entries)
		{
			Setting setting = entry.setting;
			GuiComponent component = entry.component;
			int indent = setting.getDepth() * INDENT;
			component.setX(x + 8 + indent);
			component.setY(cursorY);
			component.setWidth(Math.max(40, WIDTH - 12 - indent));
			int rowHeight = (int)Math.ceil(component.getHeight());
			if(cursorY + rowHeight > y + HEADER_HEIGHT && cursorY < bottom)
			{
				graphics.fill((int)x, (int)cursorY, (int)x + WIDTH,
					(int)cursorY + rowHeight, SuperSoftTheme.SETTING);
				if(setting.hasChildren())
				{
					GuiIcon.CHEVRON.drawRotated(graphics,
						(int)x + 2 + indent, (int)cursorY + 4, 7,
						SuperSoftTheme.TEXT_SECONDARY,
						arrowMotions.computeIfAbsent(setting,
							ignored -> new UiTween(
								setting.isExpanded() ? 90 : 0, 200))
							.update(setting.isExpanded() ? 90 : 0));
				}
				component.render(graphics, mouseX, mouseY, partialTicks);
			}
			cursorY += rowHeight;
		}
		graphics.disableScissor();
		renderScrollbar(graphics, contentHeight, bodyHeight);
	}

	@Override
	public boolean headerContains(double mouseX, double mouseY)
	{
		return isVisible() && !closing && mouseX >= x && mouseX < x + WIDTH
			&& mouseY >= y && mouseY < y + HEADER_HEIGHT;
	}

	@Override
	public boolean mouseClickedHeader(double mouseX, double mouseY, int button)
	{
		if(closing)
			return false;
		if(button == 0 && mouseX >= x + WIDTH - 22)
		{
			close();
			return true;
		}
		if(button == 1)
		{
			collapsed = !collapsed;
			if(collapsed)
				releaseTextFocus(componentCache.values());
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseClickedBody(double mouseX, double mouseY, int button)
	{
		if(!isVisible() || closing || collapsed || mouseX < x
			|| mouseX >= x + WIDTH
			|| mouseY < y + HEADER_HEIGHT
			|| mouseY >= y + HEADER_HEIGHT + visibleBodyHeight())
			return false;
		Entry entry = entryAt(mouseY);
		if(entry == null)
			return true;
		int indent = entry.setting.getDepth() * INDENT;
		if(entry.setting.hasChildren() && button == 0
			&& mouseX < x + 12 + indent)
		{
			entry.setting.setExpanded(!entry.setting.isExpanded());
			refreshEntries();
			return true;
		}
		boolean handled = entry.component.mouseClicked(mouseX, mouseY, button);
		if(handled)
			capturedComponent = entry.component;
		return true;
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button)
	{
		if(closing)
			return false;
		GuiComponent component = capturedComponent;
		capturedComponent = null;
		if(component != null)
			return component.mouseReleased(mouseX, mouseY, button);
		Entry entry = entryAt(mouseY);
		return entry != null
			&& entry.component.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button)
	{
		if(closing)
			return false;
		if(capturedComponent != null)
			return capturedComponent.mouseDragged(mouseX, mouseY, button);
		Entry entry = entryAt(mouseY);
		return entry != null
			&& entry.component.mouseDragged(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double delta)
	{
		if(!isVisible() || closing || collapsed || mouseX < x
			|| mouseX >= x + WIDTH
			|| mouseY < y + HEADER_HEIGHT
			|| mouseY >= y + HEADER_HEIGHT + visibleBodyHeight())
			return false;
		int maxOffset = Math.max(0, contentHeight() - visibleBodyHeight());
		if(maxOffset == 0)
			return false;
		scrollOffset = Mth.clamp(scrollOffset + (delta > 0 ? -16 : 16), 0,
			maxOffset);
		return true;
	}

	@Override
	public void moveTo(double x, double y, int screenWidth, int screenHeight)
	{
		this.x = Mth.clamp(x, 0, Math.max(0, screenWidth - WIDTH));
		this.y = Mth.clamp(y, 0,
			Math.max(0, screenHeight - HEADER_HEIGHT));
	}

	@Override
	public int totalHeight(int maxBodyHeight)
	{
		return HEADER_HEIGHT + (collapsed ? 0
			: Math.min(maxBodyHeight, contentHeight()));
	}

	@Override
	public void tick()
	{
		bodyMotion.update(collapsed || closing ? 0 : 1);
		if(closing && bodyMotion.get() <= 0.001F)
		{
			closed = true;
			return;
		}
		refreshIfNeeded();
		for(Entry entry : entries)
			entry.component.tick();
	}

	@Override
	public boolean isClosed()
	{
		return closed;
	}

	@Override
	public void dispose()
	{
		capturedComponent = null;
		for(GuiComponent component : componentCache.values())
			if(component instanceof GuiTextInput input)
				input.loseFocus();
	}

	private void refreshIfNeeded()
	{
		List<Setting> flattened = SettingTreeLayout.flatten(roots);
		if(!visibleSettings.equals(flattened))
			refreshEntries(flattened);
	}

	private void refreshEntries()
	{
		refreshEntries(SettingTreeLayout.flatten(roots));
	}

	private void refreshEntries(List<Setting> flattened)
	{
		ArrayList<GuiComponent> removed = new ArrayList<>();
		for(Setting oldSetting : visibleSettings)
			if(!flattened.contains(oldSetting))
			{
				GuiComponent component = componentCache.get(oldSetting);
				if(component != null)
					removed.add(component);
			}
		releaseTextFocus(removed);
		ArrayList<Entry> updated = new ArrayList<>(flattened.size());
		for(Setting setting : flattened)
		{
			GuiComponent component = componentCache.computeIfAbsent(setting,
				ValueComponentFactory::create);
			component.setSuperSoftTheme(true);
			updated.add(new Entry(setting, component));
		}
		visibleSettings = flattened;
		entries = List.copyOf(updated);
		if(renderedBodyHeight > 0)
			clampScroll(contentHeight(), visibleBodyHeight());
	}

	private static void releaseTextFocus(
		Iterable<? extends GuiComponent> components)
	{
		for(GuiComponent component : components)
			if(component instanceof GuiTextInput input)
				input.loseFocus();
	}

	private Entry entryAt(double mouseY)
	{
		if(collapsed || mouseY < y + HEADER_HEIGHT
			|| mouseY >= y + HEADER_HEIGHT + visibleBodyHeight())
			return null;
		double cursorY = y + HEADER_HEIGHT - scrollOffset;
		for(Entry entry : entries)
		{
			double height = entry.component.getHeight();
			if(mouseY >= cursorY && mouseY < cursorY + height)
				return entry;
			cursorY += height;
		}
		return null;
	}

	private int contentHeight()
	{
		double total = 0;
		for(Entry entry : entries)
			total += entry.component.getHeight();
		return (int)Math.ceil(total);
	}

	private int visibleBodyHeight()
	{
		return collapsed ? 0 : renderedBodyHeight;
	}

	private void clampScroll(int contentHeight, int bodyHeight)
	{
		scrollOffset = Mth.clamp(scrollOffset, 0,
			Math.max(0, contentHeight - bodyHeight));
	}

	private void renderScrollbar(GuiGraphics graphics, int contentHeight,
		int bodyHeight)
	{
		if(contentHeight <= bodyHeight || bodyHeight <= 0)
			return;
		int thumbHeight = Math.max(10, bodyHeight * bodyHeight / contentHeight);
		int thumbY = (int)y + HEADER_HEIGHT + Math.round((float)scrollOffset
			/ (contentHeight - bodyHeight) * (bodyHeight - thumbHeight));
		graphics.fill((int)x + WIDTH - 2, thumbY, (int)x + WIDTH,
			thumbY + thumbHeight, EpsilonMd3Theme.TEXT_MUTED);
	}

	private record Entry(Setting setting, GuiComponent component)
	{}
}
