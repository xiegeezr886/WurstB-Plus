/*
 * Adapted from BleachHack's window system (GPL-3.0).
 * Copyright (c) 2025 Penguin
 */
package net.wurstclient.clickgui2;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.wurstclient.Category;
import net.wurstclient.Feature;
import net.wurstclient.WurstClient;
import net.wurstclient.clickgui2.GuiPreferences.TargetType;
import net.wurstclient.clickgui2.window.Window;
import net.wurstclient.hacks.TooManyHaxHack;
import net.wurstclient.keybinds.PossibleKeybind;
import net.wurstclient.options.EnterProfileNameScreen;
import net.wurstclient.util.ChatUtils;
import net.wurstclient.util.json.JsonException;
import org.lwjgl.glfw.GLFW;

public final class ClickGuiScreen extends Screen
{
	private static final Minecraft MC = WurstClient.MC;
	private static final WurstClient WURST = WurstClient.INSTANCE;
	private static final int WINDOW_WIDTH = 66;
	private static final int HEADER_HEIGHT = 14;
	private static final int ROW_HEIGHT = 13;
	private static final int WINDOW_GAP = 2;
	private static final int COMBAT_STACK_GAP = 26;
	private static final int START_Y = 8;
	private static final int SEARCH_WIDTH = 140;
	private static final int MAX_VISIBLE_ROWS = 18;
	private static final int COMBAT_VISIBLE_ROWS = 15;
	private static final float TEXT_SCALE = 0.75F;
	private static final int WINDOW_FILL = 0xFF050505;
	private static final int HEADER_FILL = 0xFF090909;
	private static final int ROW_FILL = 0xFF111111;
	private static final int ROW_HOVER = 0xFF1A1A1A;
	private static final int ROW_DISABLED = 0xFF0A0A0A;
	private static final int BORDER = 0xFF303030;
	private static final int BORDER_HOVER = 0xFF505050;
	private static final int TEXT = 0xFFF2F2F2;
	private static final int MUTED = 0xFF888888;
	private static final int KEYBIND_FILL = 0xFF3A3A3A;
	private static final int KEYBIND_TEXT = 0xFFB8B8B8;

	private final List<TemplateWindow> windows = new ArrayList<>();
	private final Map<String, TemplateWindow> namedWindows = new java.util.HashMap<>();
	private EditBox searchBox;
	private TemplateWindow draggedWindow;
	private int dragOffsetX;
	private int dragOffsetY;
	private double pressX;
	private double pressY;
	private boolean dragMoved;
	private boolean searchVisible;
	private int layoutStartX;
	private Feature bindingFeature;

	public ClickGuiScreen()
	{
		super(Component.literal(WurstClient.CLIENT_NAME));
	}

	@Override
	protected void init()
	{
		WURST.getGui().init();
		GuiIcon.configureFiltering(MC);
		String query = searchBox == null ? "" : searchBox.getValue();
		int searchX = Math.max(12, width - SEARCH_WIDTH - 12);
		searchBox = new EditBox(MC.font, searchX, 7, SEARCH_WIDTH, 14,
			Component.literal("搜索功能"));
		searchBox.setBordered(false);
		searchBox.setMaxLength(80);
		searchBox.setHint(Component.literal("搜索功能"));
		searchBox.setValue(query);
		searchBox.setResponder(ignored -> buildWindows());
		searchBox.visible = searchVisible;
		addRenderableWidget(searchBox);
		buildWindows();
	}

	private void buildWindows()
	{
		windows.clear();
		namedWindows.clear();
		String query = searchBox == null ? "" : searchBox.getValue().trim();
		List<Feature> features = FeatureMenuSupport.getAllFeatures();
		if(!query.isEmpty())
		{
			List<Feature> results =
				FeatureMenuSupport.searchFeatures(features, query);
			add(new CategoryWindow(8, START_Y, "搜索", GuiIcon.SEARCH, results,
				MAX_VISIBLE_ROWS), "search");
			return;
		}

		Map<Category, List<Feature>> categorized = new EnumMap<>(Category.class);
		for(Category category : Category.values())
			categorized.put(category, new ArrayList<>());
		for(Feature feature : features)
			if(feature.getCategory() != null)
				categorized.get(feature.getCategory()).add(feature);

		int requiredWidth = 8 * WINDOW_WIDTH + 7 * WINDOW_GAP;
		layoutStartX = Math.max(0, width - requiredWidth - 31);
		if(width >= requiredWidth)
			buildReferenceLayout(categorized);
		else
			buildResponsiveLayout(categorized);
	}

	private void buildReferenceLayout(Map<Category, List<Feature>> categorized)
	{
		add(new CategoryWindow(columnX(0), START_Y, "视觉类", GuiIcon.RENDER,
			categorized.get(Category.RENDER), MAX_VISIBLE_ROWS), "visual");
		add(new CategoryWindow(columnX(1), START_Y, "移动类", GuiIcon.MOVEMENT,
			categorized.get(Category.MOVEMENT), MAX_VISIBLE_ROWS), "movement");
		CategoryWindow client = add(new CategoryWindow(columnX(2), START_Y,
			"客户端", GuiIcon.CLIENT, getInterfaceFeatures(), MAX_VISIBLE_ROWS),
			"client");
		add(new CategoryWindow(columnX(2),
			client.y2 + COMBAT_STACK_GAP, "战斗类", GuiIcon.COMBAT,
			categorized.get(Category.COMBAT), COMBAT_VISIBLE_ROWS), "combat");
		add(new CategoryWindow(columnX(3), START_Y, "世界类", GuiIcon.WORLD,
			combine(categorized, Category.BLOCKS, Category.ITEMS),
			MAX_VISIBLE_ROWS), "world");
		add(new CategoryWindow(columnX(4), START_Y, "其他类", GuiIcon.MISC,
			getOtherFeatures(categorized),
			MAX_VISIBLE_ROWS), "other");
		add(new TargetWindow(columnX(5), START_Y), "target");
		add(new ConfigWindow(columnX(6), START_Y), "config");
		add(new FontWindow(columnX(7), START_Y), "font");

		int settingsY = Mth.clamp(Math.round(height * 0.58F), START_Y,
			Math.max(START_Y, height - 90));
		int mainY = Mth.clamp(Math.round(height * 0.49F), START_Y,
			Math.max(START_Y, height - 150));
		add(new GlobalSettingsWindow(columnX(5), settingsY), "settings");
		add(new MainMenuWindow(columnX(7), mainY), "main");
	}

	private void buildResponsiveLayout(Map<Category, List<Feature>> categorized)
	{
		ArrayList<TemplateWindow> all = new ArrayList<>();
		all.add(new CategoryWindow(0, 0, "视觉类", GuiIcon.RENDER,
			categorized.get(Category.RENDER), MAX_VISIBLE_ROWS));
		all.add(new CategoryWindow(0, 0, "移动类", GuiIcon.MOVEMENT,
			categorized.get(Category.MOVEMENT), MAX_VISIBLE_ROWS));
		all.add(new CategoryWindow(0, 0, "客户端", GuiIcon.CLIENT,
			getInterfaceFeatures(), MAX_VISIBLE_ROWS));
		all.add(new CategoryWindow(0, 0, "战斗类", GuiIcon.COMBAT,
			categorized.get(Category.COMBAT), COMBAT_VISIBLE_ROWS));
		all.add(new CategoryWindow(0, 0, "世界类", GuiIcon.WORLD,
			combine(categorized, Category.BLOCKS, Category.ITEMS),
			MAX_VISIBLE_ROWS));
		all.add(new CategoryWindow(0, 0, "其他类", GuiIcon.MISC,
			getOtherFeatures(categorized),
			MAX_VISIBLE_ROWS));
		all.add(new TargetWindow(0, 0));
		all.add(new ConfigWindow(0, 0));
		all.add(new FontWindow(0, 0));
		all.add(new GlobalSettingsWindow(0, 0));
		all.add(new MainMenuWindow(0, 0));

		String[] ids = {"visual", "movement", "client", "combat", "world",
			"other", "target", "config", "font", "settings", "main"};
		int x = 0;
		int y = START_Y;
		int rowHeight = 0;
		for(int i = 0; i < all.size(); i++)
		{
			TemplateWindow window = all.get(i);
			if(x > 0 && x + window.windowWidth > width)
			{
				x = 0;
				y += rowHeight + WINDOW_GAP;
				rowHeight = 0;
			}
			window.setHome(x, y);
			add(window, ids[i]);
			rowHeight = Math.max(rowHeight, window.y2 - window.y1);
			x += window.windowWidth + WINDOW_GAP;
		}
	}

	@SafeVarargs
	private static List<Feature> combine(Map<Category, List<Feature>> categorized,
		Category... categories)
	{
		ArrayList<Feature> result = new ArrayList<>();
		for(Category category : categories)
			result.addAll(categorized.get(category));
		return result;
	}

	private <T extends TemplateWindow> T add(T window, String id)
	{
		window.open = true;
		window.updateHeight();
		window.setHome(window.x1, window.y1);
		windows.add(window);
		namedWindows.put(id, window);
		return window;
	}

	private int columnX(int column)
	{
		return layoutStartX + column * (WINDOW_WIDTH + WINDOW_GAP);
	}

	private List<Feature> getInterfaceFeatures()
	{
		return List.of(WURST.getHax().clickGuiHack,
			WURST.getOtfs().wurstLogoOtf, WURST.getOtfs().hackListOtf,
			WURST.getOtfs().keybindManagerOtf);
	}

	private List<Feature> getOtherFeatures(
		Map<Category, List<Feature>> categorized)
	{
		ArrayList<Feature> features = new ArrayList<>(combine(categorized,
			Category.OTHER, Category.CHAT, Category.FUN));
		features.add(WURST.getHax().navigatorHack);
		features.add(WURST.getOtfs().tabGuiOtf);
		features.add(WURST.getOtfs().translationsOtf);
		features.add(WURST.getOtfs().vanillaSpoofOtf);
		return features;
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY,
		float partialTicks)
	{
		ClickGui gui = WURST.getGui();
		gui.renderBackdrop(graphics);
		if(searchVisible)
		{
			FlatRenderer.drawControl(graphics, searchBox.getX() - 3,
				searchBox.getY() - 2,
				searchBox.getX() + searchBox.getWidth() + 3,
				searchBox.getY() + searchBox.getHeight() + 2, 2, gui.getTheme(),
				searchBox.isFocused() ? 1 : 0, false);
			searchBox.setTextColor(gui.getTheme().text());
			searchBox.render(graphics, mouseX, mouseY, partialTicks);
		}

		for(TemplateWindow window : windows)
			window.render(graphics, mouseX, mouseY, partialTicks);
		gui.render(graphics, mouseX, mouseY, partialTicks);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button)
	{
		ClickGui gui = WURST.getGui();
		boolean overSettings = gui.isMouseOverWindow(mouseX, mouseY);
		gui.handleMouseClick((int)mouseX, (int)mouseY, button);
		if(overSettings)
			return true;

		for(int i = windows.size() - 1; i >= 0; i--)
		{
			TemplateWindow window = windows.get(i);
			if(!window.contains(mouseX, mouseY))
				continue;
			windows.remove(i);
			windows.add(window);
			if(window.isBodyOver(mouseX, mouseY))
				return window.clickBody(mouseX, mouseY, button);
			if(!window.isHeaderOver(mouseX, mouseY))
				return true;
			if(button == GLFW.GLFW_MOUSE_BUTTON_RIGHT)
				window.toggleOpen();
			else if(button == GLFW.GLFW_MOUSE_BUTTON_LEFT)
			{
				draggedWindow = window;
				dragOffsetX = (int)mouseX - window.x1;
				dragOffsetY = (int)mouseY - window.y1;
				pressX = mouseX;
				pressY = mouseY;
				dragMoved = false;
			}
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button,
		double dragX, double dragY)
	{
		if(button != GLFW.GLFW_MOUSE_BUTTON_LEFT || draggedWindow == null)
			return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
		dragMoved |= Math.abs(mouseX - pressX) > 2
			|| Math.abs(mouseY - pressY) > 2;
		draggedWindow.moveTo((int)mouseX - dragOffsetX,
			(int)mouseY - dragOffsetY);
		return true;
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button)
	{
		WURST.getGui().handleMouseRelease(mouseX, mouseY, button);
		if(button == GLFW.GLFW_MOUSE_BUTTON_LEFT && draggedWindow != null)
		{
			if(!dragMoved)
				draggedWindow.toggleOpen();
			draggedWindow = null;
			return true;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double delta)
	{
		ClickGui gui = WURST.getGui();
		if(gui.isMouseOverWindow(mouseX, mouseY))
		{
			gui.handleMouseScroll(mouseX, mouseY, delta);
			return true;
		}
		for(int i = windows.size() - 1; i >= 0; i--)
			if(windows.get(i).scroll(mouseX, mouseY, delta))
				return true;
		return super.mouseScrolled(mouseX, mouseY, delta);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers)
	{
		if(bindingFeature != null)
		{
			Feature feature = bindingFeature;
			bindingFeature = null;
			if(keyCode == GLFW.GLFW_KEY_ESCAPE)
				return true;

			String command = getBindableCommand(feature);
			if(command == null)
				return true;
			if(keyCode == GLFW.GLFW_KEY_DELETE
				|| keyCode == GLFW.GLFW_KEY_BACKSPACE)
				WURST.getKeybinds().unbindCommand(command);
			else
				WURST.getKeybinds().bindCommand(
					InputConstants.getKey(keyCode, scanCode).getName(), command);
			return true;
		}
		if(keyCode == GLFW.GLFW_KEY_F
			&& (modifiers & GLFW.GLFW_MOD_CONTROL) != 0)
		{
			searchVisible = !searchVisible;
			searchBox.visible = searchVisible;
			searchBox.setFocused(searchVisible);
			if(!searchVisible)
			{
				searchBox.setValue("");
				buildWindows();
			}
			return true;
		}
		if(keyCode == GLFW.GLFW_KEY_ESCAPE)
		{
			if(searchVisible)
			{
				searchVisible = false;
				searchBox.visible = false;
				searchBox.setValue("");
				buildWindows();
				return true;
			}
			onClose();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	private String getKeyLabel(Feature feature)
	{
		if(bindingFeature == feature)
			return "...";
		String command = getBindableCommand(feature);
		if(command == null)
			return "无";
		String key = WURST.getKeybinds().getKeyForCommand(command);
		if(key == null)
			return "无";
		try
		{
			return InputConstants.getKey(key).getDisplayName().getString();
		}catch(IllegalArgumentException e)
		{
			return "无";
		}
	}

	private String getBindableCommand(Feature feature)
	{
		return feature.getPossibleKeybinds().stream().findFirst()
			.map(PossibleKeybind::getCommand).orElse(null);
	}

	@Override
	public boolean isPauseScreen()
	{
		return false;
	}

	private void toggleWindowVisibility(String id)
	{
		TemplateWindow window = namedWindows.get(id);
		if(window == null)
			return;
		window.visible = !window.visible;
		if(window.visible)
		{
			windows.remove(window);
			windows.add(window);
		}
	}

	private boolean isWindowVisible(String id)
	{
		TemplateWindow window = namedWindows.get(id);
		return window != null && window.visible;
	}

	public boolean isWaitingForKeybind()
	{
		return bindingFeature != null;
	}

	private boolean isTopVisibleWindow(TemplateWindow target)
	{
		for(int i = windows.size() - 1; i >= 0; i--)
		{
			TemplateWindow window = windows.get(i);
			if(window.visible)
				return window == target;
		}
		return false;
	}

	private void sortWindows()
	{
		for(TemplateWindow window : windows)
			window.resetPosition();
	}

	private abstract class TemplateWindow extends Window
	{
		private final GuiIcon icon;
		protected final int windowWidth;
		private final int maxVisibleRows;
		protected boolean open;
		private boolean visible = true;
		private int scrollOffset;
		private int homeX;
		private int homeY;

		private TemplateWindow(int x, int y, int windowWidth, String title,
			GuiIcon icon, int maxVisibleRows)
		{
			super(x, y, x + windowWidth, y + HEADER_HEIGHT, title,
				ItemStack.EMPTY);
			this.windowWidth = windowWidth;
			this.icon = icon;
			this.maxVisibleRows = maxVisibleRows;
			homeX = x;
			homeY = y;
		}

		protected abstract int rowCount();

		protected abstract void renderRow(GuiGraphics graphics, int rowIndex,
			int rowY, boolean hovering);

		protected abstract boolean clickRow(int rowIndex, int visualRow,
			double mouseX, int button);

		private int visibleRows()
		{
			int availableRows = Math.max(1,
				(height - y1 - HEADER_HEIGHT - 2) / ROW_HEIGHT);
			return Math.min(rowCount(), Math.min(maxVisibleRows, availableRows));
		}

		private void toggleOpen()
		{
			open = !open;
			updateHeight();
			clampToScreen();
		}

		protected void updateHeight()
		{
			int maxOffset = Math.max(0, rowCount() - visibleRows());
			scrollOffset = Mth.clamp(scrollOffset, 0, maxOffset);
			y2 = y1 + HEADER_HEIGHT
				+ (open ? visibleRows() * ROW_HEIGHT + 1 : 0);
		}

		protected void setHome(int x, int y)
		{
			homeX = x;
			homeY = y;
			moveTo(x, y);
		}

		private void resetPosition()
		{
			moveTo(homeX, homeY);
		}

		private void moveTo(int x, int y)
		{
			int windowHeight = y2 - y1;
			x1 = Mth.clamp(x, 0, Math.max(0, width - windowWidth));
			y1 = Mth.clamp(y, 0, Math.max(0, height - windowHeight));
			x2 = x1 + windowWidth;
			y2 = y1 + windowHeight;
		}

		private void clampToScreen()
		{
			updateHeight();
			moveTo(x1, y1);
		}

		private boolean contains(double mouseX, double mouseY)
		{
			return visible && mouseX >= x1 && mouseX < x2 && mouseY >= y1
				&& mouseY < y2;
		}

		private boolean isHeaderOver(double mouseX, double mouseY)
		{
			return visible && mouseX >= x1 && mouseX < x2 && mouseY >= y1
				&& mouseY < y1 + HEADER_HEIGHT;
		}

		private boolean isBodyOver(double mouseX, double mouseY)
		{
			return visible && open && mouseX >= x1 && mouseX < x2
				&& mouseY >= y1 + HEADER_HEIGHT && mouseY < y2;
		}

		private boolean clickBody(double mouseX, double mouseY, int button)
		{
			int row = (int)(mouseY - y1 - HEADER_HEIGHT) / ROW_HEIGHT;
			int index = row + scrollOffset;
			return index >= 0 && index < rowCount()
				&& clickRow(index, row, mouseX, button);
		}

		private boolean scroll(double mouseX, double mouseY, double delta)
		{
			if(!isBodyOver(mouseX, mouseY) || rowCount() <= visibleRows())
				return false;
			int direction = delta > 0 ? -1 : 1;
			scrollOffset = Mth.clamp(scrollOffset + direction, 0,
				rowCount() - visibleRows());
			return true;
		}

		protected void fillRow(GuiGraphics graphics, int index, int rowY,
			int color)
		{
			int rowBottom = rowY + ROW_HEIGHT - 1;
			FlatRenderer.fillRoundedRect(graphics, x1 + 2, rowY + 1, x2 - 2,
				rowBottom, 3, color);
		}

		@Override
		public void render(GuiGraphics graphics, int mouseX, int mouseY,
			float partialTicks)
		{
			if(!visible)
				return;
			updateHeight();
			selected = isTopVisibleWindow(this);
			boolean headerHover = isHeaderOver(mouseX, mouseY);
			FlatRenderer.fillRoundedRect(graphics, x1, y1, x2, y2, 3,
				headerHover ? BORDER_HOVER : BORDER);
			FlatRenderer.fillRoundedRect(graphics, x1 + 1, y1 + 1, x2 - 1,
				y2 - 1, 2, WINDOW_FILL);
			FlatRenderer.fillRoundedRect(graphics, x1 + 1, y1 + 1, x2 - 1,
				y1 + HEADER_HEIGHT - 1, 2, HEADER_FILL);
			if(open)
				graphics.fill(x1 + 1, y1 + HEADER_HEIGHT / 2, x2 - 1,
					y1 + HEADER_HEIGHT - 1, HEADER_FILL);
			graphics.fill(x1 + 1, y1 + HEADER_HEIGHT - 1, x2 - 1,
				y1 + HEADER_HEIGHT, BORDER);
			Font font = MC.font;
			int titleY = y1 + 3;
			icon.draw(graphics, x1 + 4, y1 + 3, 8, accentColor());
			String visibleTitle = font.plainSubstrByWidth(title,
				Math.round((windowWidth - 27) / TEXT_SCALE));
			drawText(graphics, font, visibleTitle, x1 + 15, titleY, TEXT);
			GuiIcon.CHEVRON.drawRotated(graphics, x2 - 10, y1 + 3, 8, TEXT,
				open ? 0 : -90);
			if(!open)
				return;

			graphics.enableScissor(x1 + 1, y1 + HEADER_HEIGHT, x2 - 1, y2 - 1);
			for(int row = 0; row < visibleRows(); row++)
			{
				int index = row + scrollOffset;
				if(index >= rowCount())
					break;
				int rowY = y1 + HEADER_HEIGHT + row * ROW_HEIGHT;
				boolean hovering = mouseX >= x1 + 1 && mouseX < x2 - 1
					&& mouseY >= rowY && mouseY < rowY + ROW_HEIGHT - 1;
				renderRow(graphics, index, rowY, hovering);
			}
			graphics.disableScissor();
		}
	}

	private final class CategoryWindow extends TemplateWindow
	{
		private final List<Feature> features;

		private CategoryWindow(int x, int y, String title, GuiIcon icon,
			List<Feature> features, int maxVisibleRows)
		{
			super(x, y, WINDOW_WIDTH, title, icon, maxVisibleRows);
			this.features = List.copyOf(features);
		}

		@Override
		protected int rowCount()
		{
			return features.size();
		}

		@Override
		protected void renderRow(GuiGraphics graphics, int index, int rowY,
			boolean hovering)
		{
			Feature feature = features.get(index);
			int rowColor = feature.isEnabled() ? accentColor()
				: hovering ? ROW_HOVER : ROW_FILL;
			FlatRenderer.fillRoundedRect(graphics, x1 + 2, rowY + 1, x2 - 2,
				rowY + ROW_HEIGHT - 1, 3, rowColor);
			String keyLabel = getKeyLabel(feature);
			int badgeWidth = getKeyBadgeWidth(keyLabel);
			int badgeRight = x2 - (feature.getSettings().isEmpty() ? 3 : 10);
			int badgeLeft = badgeRight - badgeWidth;
			String name = MC.font.plainSubstrByWidth(feature.getDisplayName(),
				Math.round((badgeLeft - x1 - 6) / TEXT_SCALE));
			drawText(graphics, MC.font, name, x1 + 4, rowY + 3, TEXT);
			FlatRenderer.fillRoundedRect(graphics, badgeLeft, rowY + 3,
				badgeRight, rowY + 11, 3, KEYBIND_FILL);
			String visibleKey = MC.font.plainSubstrByWidth(keyLabel,
				Math.round((badgeWidth - 4) / TEXT_SCALE));
			int textWidth = Math.round(MC.font.width(visibleKey) * TEXT_SCALE);
			drawText(graphics, MC.font, visibleKey,
				badgeLeft + Math.max(2, (badgeWidth - textWidth) / 2), rowY + 3,
				bindingFeature == feature ? TEXT : KEYBIND_TEXT);
			if(!feature.getSettings().isEmpty())
				GuiIcon.CHEVRON.drawRotated(graphics, x2 - 9, rowY + 3, 7,
					0xFFD8D8D8, -90);
		}

		@Override
		protected boolean clickRow(int index, int row, double mouseX,
			int button)
		{
			Feature feature = features.get(index);
			String keyLabel = getKeyLabel(feature);
			int badgeRight = x2 - (feature.getSettings().isEmpty() ? 3 : 10);
			int badgeLeft = badgeRight - getKeyBadgeWidth(keyLabel);
			if(button == GLFW.GLFW_MOUSE_BUTTON_LEFT && mouseX >= badgeLeft
				&& mouseX < badgeRight && getBindableCommand(feature) != null)
			{
				bindingFeature = feature;
				return true;
			}
			if(button == GLFW.GLFW_MOUSE_BUTTON_RIGHT
				|| feature.getPrimaryAction().isEmpty())
			{
				if(!feature.getSettings().isEmpty())
					WURST.getGui().addWindow(new SettingsWindow(feature, x2 + 6,
						y1 + HEADER_HEIGHT + row * ROW_HEIGHT));
				return true;
			}
			if(button != GLFW.GLFW_MOUSE_BUTTON_LEFT)
				return false;
			TooManyHaxHack tooManyHax = WURST.getHax().tooManyHaxHack;
			if(tooManyHax.isEnabled() && tooManyHax.isBlocked(feature))
			{
				ChatUtils.error(feature.getDisplayName()
					+ " 已被‘太多功能’限制。");
				return true;
			}
			feature.doPrimaryAction();
			return true;
		}

		private int getKeyBadgeWidth(String label)
		{
			return Mth.clamp(Math.round(MC.font.width(label) * TEXT_SCALE) + 6,
				12, 25);
		}
	}

	private abstract class RowsWindow extends TemplateWindow
	{
		private RowsWindow(int x, int y, String title, GuiIcon icon,
			int maxVisibleRows)
		{
			super(x, y, WINDOW_WIDTH, title, icon, maxVisibleRows);
		}

		protected abstract List<PanelRow> rows();

		@Override
		protected int rowCount()
		{
			return rows().size();
		}

		@Override
		protected void renderRow(GuiGraphics graphics, int index, int rowY,
			boolean hovering)
		{
			PanelRow row = rows().get(index);
			int rowColor;
			if(row.kind == RowKind.SECTION)
				rowColor = HEADER_FILL;
			else if(row.kind != RowKind.SWITCH && row.active.getAsBoolean())
				rowColor = accentColor();
			else if(row.kind == RowKind.DISABLED)
				rowColor = ROW_DISABLED;
			else
				rowColor = hovering ? ROW_HOVER : ROW_FILL;
			fillRow(graphics, index, rowY, rowColor);
			String label = MC.font.plainSubstrByWidth(row.label.get(),
				Math.round((windowWidth - 8) / TEXT_SCALE));
			drawText(graphics, MC.font, label, x1 + 4, rowY + 3,
				row.kind == RowKind.DISABLED ? MUTED : TEXT);
			if(row.kind == RowKind.SWITCH)
			{
				boolean active = row.active.getAsBoolean();
				int switchX = x2 - 15;
				FlatRenderer.fillRoundedRect(graphics, switchX, rowY + 4,
					x2 - 4, rowY + 10, 3,
					active ? accentColor() : 0xFF3A3A3A);
				int knobX = active ? x2 - 8 : switchX + 1;
				FlatRenderer.fillRoundedRect(graphics, knobX, rowY + 5,
					knobX + 4, rowY + 9, 2, 0xFFF0F0F0);
			}
		}

		@Override
		protected boolean clickRow(int index, int visualRow,
			double mouseX, int button)
		{
			if(button != GLFW.GLFW_MOUSE_BUTTON_LEFT)
				return false;
			PanelRow panelRow = rows().get(index);
			if(panelRow.kind == RowKind.SECTION
				|| panelRow.kind == RowKind.DISABLED)
				return true;
			panelRow.action.run();
			return true;
		}
	}

	private final class TargetWindow extends RowsWindow
	{
		private TargetWindow(int x, int y)
		{
			super(x, y, "目标", GuiIcon.PLAYER, 8);
		}

		@Override
		protected List<PanelRow> rows()
		{
			GuiPreferences preferences = WURST.getGuiPreferences();
			return List.of(
				toggleRow("玩家", () -> preferences.isTargetEnabled(TargetType.PLAYERS),
					() -> preferences.toggleTarget(TargetType.PLAYERS)),
				toggleRow("怪物", () -> preferences.isTargetEnabled(TargetType.MONSTERS),
					() -> preferences.toggleTarget(TargetType.MONSTERS)),
				toggleRow("动物", () -> preferences.isTargetEnabled(TargetType.ANIMALS),
					() -> preferences.toggleTarget(TargetType.ANIMALS)),
				toggleRow("队伍", () -> preferences.isTargetEnabled(TargetType.TEAMS),
					() -> preferences.toggleTarget(TargetType.TEAMS)),
				toggleRow("村民", () -> preferences.isTargetEnabled(TargetType.VILLAGERS),
					() -> preferences.toggleTarget(TargetType.VILLAGERS)));
		}
	}

	private final class ConfigWindow extends RowsWindow
	{
		private List<Path> profiles = List.of();
		private String selectedProfile;

		private ConfigWindow(int x, int y)
		{
			super(x, y, "配置", GuiIcon.CONFIG, 18);
			refresh();
		}

		private void refresh()
		{
			profiles = WURST.listSettingsProfiles().stream()
				.sorted((a, b) -> a.getFileName().toString()
					.compareToIgnoreCase(b.getFileName().toString()))
				.toList();
			if(selectedProfile != null && profiles.stream().noneMatch(path ->
				path.getFileName().toString().equals(selectedProfile)))
				selectedProfile = null;
		}

		@Override
		protected List<PanelRow> rows()
		{
			ArrayList<PanelRow> rows = new ArrayList<>();
			rows.add(sectionRow("配置列表"));
			if(profiles.isEmpty())
				rows.add(disabledRow("暂无配置"));
			else
				for(Path profile : profiles)
				{
					String name = profile.getFileName().toString();
					rows.add(actionRow(stripJson(name),
						() -> name.equals(selectedProfile), () -> selectedProfile = name));
				}
			rows.add(actionRow("加载配置", () -> false, this::loadSelected));
			rows.add(actionRow("保存配置", () -> false, this::saveSelected));
			rows.add(actionRow("刷新", () -> false, this::refresh));
			rows.add(actionRow("打开配置文件夹", () -> false, () -> {
				try
				{
					java.nio.file.Files.createDirectories(
						WURST.getSettingsProfileFolder());
				}catch(IOException e)
				{
					throw new RuntimeException(e);
				}
				Util.getPlatform().openFile(
					WURST.getSettingsProfileFolder().toFile());
			}));
			return rows;
		}

		private void loadSelected()
		{
			if(selectedProfile == null)
			{
				ChatUtils.warning("请先选择一个配置。");
				return;
			}
			try
			{
				WURST.loadSettingsProfile(selectedProfile);
				ChatUtils.message("已加载配置: " + stripJson(selectedProfile));
			}catch(IOException | JsonException e)
			{
				ChatUtils.error("加载配置失败: " + e.getMessage());
			}
		}

		private void saveSelected()
		{
			if(selectedProfile == null)
			{
				MC.setScreen(new EnterProfileNameScreen(ClickGuiScreen.this,
					this::createProfile));
				return;
			}
			saveProfile(selectedProfile);
		}

		private void createProfile(String name)
		{
			String fileName = name.endsWith(".json") ? name : name + ".json";
			saveProfile(fileName);
			selectedProfile = fileName;
			refresh();
		}

		private void saveProfile(String fileName)
		{
			try
			{
				WURST.saveSettingsProfile(fileName);
				ChatUtils.message("已保存配置: " + stripJson(fileName));
			}catch(IOException | JsonException e)
			{
				ChatUtils.error("保存配置失败: " + e.getMessage());
			}
		}
	}

	private final class FontWindow extends RowsWindow
	{
		private List<String> fonts = List.of();
		private String selected;

		private FontWindow(int x, int y)
		{
			super(x, y, "字体", GuiIcon.FONT, 18);
			refresh();
		}

		private void refresh()
		{
			fonts = WURST.getGuiPreferences().listFonts();
			String active = WURST.getGuiPreferences().getSelectedFont();
			selected = fonts.contains(active) ? active : GuiPreferences.BUILTIN_FONT;
		}

		@Override
		protected List<PanelRow> rows()
		{
			ArrayList<PanelRow> rows = new ArrayList<>();
			rows.add(sectionRow("字体列表"));
			for(String font : fonts)
				rows.add(actionRow(font, () -> font.equals(selected),
					() -> selected = font));
			rows.add(actionRow("加载字体", () -> false, this::loadSelected));
			rows.add(actionRow("刷新", () -> false, this::refresh));
			rows.add(actionRow("打开字体文件夹", () -> false, () -> {
				WURST.getGuiPreferences().listFonts();
				Util.getPlatform().openFile(
					WURST.getGuiPreferences().getFontsFolder().toFile());
			}));
			return rows;
		}

		private void loadSelected()
		{
			try
			{
				WURST.getGuiPreferences().selectFont(selected);
				ChatUtils.message("已加载字体: " + selected);
			}catch(RuntimeException e)
			{
				ChatUtils.error("加载字体失败: " + e.getMessage());
			}
		}
	}

	private final class GlobalSettingsWindow extends RowsWindow
	{
		private GlobalSettingsWindow(int x, int y)
		{
			super(x, y, "设置", GuiIcon.SETTINGS, 8);
		}

		@Override
		protected List<PanelRow> rows()
		{
			GuiPreferences preferences = WURST.getGuiPreferences();
			return List.of(
				switchRow("中文模式",
					() -> !WURST.getOtfs().translationsOtf.getForceEnglish().isChecked(),
					() -> {
						var forceEnglish = WURST.getOtfs().translationsOtf.getForceEnglish();
						forceEnglish.setChecked(!forceEnglish.isChecked());
					}),
				switchRow("打开指令", preferences::isCommandsEnabled,
					() -> preferences.setCommandsEnabled(!preferences.isCommandsEnabled())),
				switchRow("字体", preferences::isFontEnabled,
					() -> preferences.setFontEnabled(!preferences.isFontEnabled())),
				switchRow("中键加白名单",
					() -> WURST.getCmds().friendsCmd.getMiddleClickFriends().isChecked(),
					() -> {
						var middleClick =
							WURST.getCmds().friendsCmd.getMiddleClickFriends();
						middleClick.setChecked(!middleClick.isChecked());
					}),
				actionRow("SortGui", () -> false, ClickGuiScreen.this::sortWindows),
				actionRow("HUD编辑器", () -> false,
					() -> MC.setScreen(new net.wurstclient.hud2.HudEditorScreen())));
		}
	}

	private final class MainMenuWindow extends RowsWindow
	{
		private MainMenuWindow(int x, int y)
		{
			super(x, y, "主界面", GuiIcon.MENU, 12);
		}

		@Override
		protected List<PanelRow> rows()
		{
			return List.of(
				actionRow("战斗类", () -> isWindowVisible("combat"),
					() -> toggleWindowVisibility("combat")),
				actionRow("视觉类", () -> isWindowVisible("visual"),
					() -> toggleWindowVisibility("visual")),
				actionRow("移动类", () -> isWindowVisible("movement"),
					() -> toggleWindowVisibility("movement")),
				actionRow("客户端", () -> isWindowVisible("client"),
					() -> toggleWindowVisibility("client")),
				actionRow("世界类", () -> isWindowVisible("world"),
					() -> toggleWindowVisibility("world")),
				actionRow("其他类", () -> isWindowVisible("other"),
					() -> toggleWindowVisibility("other")),
				actionRow("配置", () -> isWindowVisible("config"),
					() -> toggleWindowVisibility("config")),
				actionRow("目标", () -> isWindowVisible("target"),
					() -> toggleWindowVisibility("target")),
				actionRow("字体", () -> isWindowVisible("font"),
					() -> toggleWindowVisibility("font")),
				actionRow("设置", () -> isWindowVisible("settings"),
					() -> toggleWindowVisibility("settings")));
		}
	}

	private static PanelRow sectionRow(String label)
	{
		return new PanelRow(() -> label, RowKind.SECTION, () -> false, () -> {});
	}

	private static PanelRow disabledRow(String label)
	{
		return new PanelRow(() -> label, RowKind.DISABLED, () -> false, () -> {});
	}

	private static PanelRow actionRow(String label, BooleanSupplier active,
		Runnable action)
	{
		return new PanelRow(() -> label, RowKind.ACTION, active, action);
	}

	private static PanelRow toggleRow(String label, BooleanSupplier active,
		Runnable action)
	{
		return new PanelRow(() -> label, RowKind.TOGGLE, active, action);
	}

	private static PanelRow switchRow(String label, BooleanSupplier active,
		Runnable action)
	{
		return new PanelRow(() -> label, RowKind.SWITCH, active, action);
	}

	private static String stripJson(String name)
	{
		return name.toLowerCase(Locale.ROOT).endsWith(".json")
			? name.substring(0, name.length() - 5) : name;
	}

	private static void drawText(GuiGraphics graphics, Font font, String text,
		int x, int y, int color)
	{
		graphics.pose().pushPose();
		graphics.pose().translate(x, y, 0);
		graphics.pose().scale(TEXT_SCALE, TEXT_SCALE, 1);
		graphics.drawString(font,
			WURST.getGuiPreferences().styleText(text), 0, 0, color, false);
		graphics.pose().popPose();
	}

	private int accentColor()
	{
		return WURST.getGui().getTheme().accent(1);
	}

	private record PanelRow(Supplier<String> label, RowKind kind,
		BooleanSupplier active, Runnable action)
	{
	}

	private enum RowKind
	{
		SECTION,
		ACTION,
		TOGGLE,
		SWITCH,
		DISABLED
	}
}
