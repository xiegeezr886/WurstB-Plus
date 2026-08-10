package net.wurstclient.clickgui2.component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.wurstclient.Category;
import net.wurstclient.Feature;
import net.wurstclient.WurstClient;
import net.wurstclient.clickgui2.ClickGuiScreens;
import net.wurstclient.clickgui2.FeatureMenuSupport;
import net.wurstclient.clickgui2.FlatRenderer;
import net.wurstclient.clickgui2.GuiIcon;
import net.wurstclient.clickgui2.GuiPreferences.VapeFrameState;
import net.wurstclient.keybinds.PossibleKeybind;
import org.lwjgl.glfw.GLFW;

public final class VapeClickGuiScreen extends Screen implements VapeGuiContext
{
	private static final int FRAME_WIDTH = 112;
	private static final int HEADER_HEIGHT = 20;
	private static final int ROW_HEIGHT = 20;
	private static final int FRAME_GAP = 7;
	private static final int SIDEBAR_WIDTH = 112;
	private static final int SIDEBAR_HEIGHT = 215;
	private static final int START_Y = 48;
	private static final int MAX_BODY_HEIGHT = 290;
	private static final int SETTINGS_ROW_COUNT = 6;
	private static final int SETTINGS_FOOTER_HEIGHT = 12;
	private static final int SEARCH_HEIGHT = 16;
	private static final int LOGO_TEXTURE_WIDTH = 716;
	private static final int LOGO_TEXTURE_HEIGHT = 80;
	private static final ResourceLocation CLIENT_LOGO = new ResourceLocation(
		"wurst", "textures/gui/wurstb_plus_logo.png");

	private final List<FloatingFrame> frames = new ArrayList<>();
	private FloatingFrame draggingFrame;
	private double dragOffsetX;
	private double dragOffsetY;
	private Feature bindingFeature;
	private boolean editingHiddenModules;
	private String searchQuery = "";
	private boolean searchFocused;
	private CategoryPanelComponent searchPanel;
	private GuiTextInput activeTextInput;

	public VapeClickGuiScreen()
	{
		super(Component.literal(WurstClient.CLIENT_NAME));
	}

	@Override
	protected void init()
	{
		VapeTextInputComponent.setContext(this);
		WurstClient.INSTANCE.getGuiPreferences().migrateVapeLayout();
		buildFrames();
		rebuildSearchPanel();
	}

	private void buildFrames()
	{
		Map<Category, List<Feature>> categories = new EnumMap<>(Category.class);
		for(Category category : Category.values())
			categories.put(category, new ArrayList<>());
		for(Feature feature : FeatureMenuSupport.getAllFeatures())
			if(feature.getCategory() != null)
				categories.get(feature.getCategory()).add(feature);
		for(List<Feature> features : categories.values())
			features.sort(Comparator.comparing(Feature::getDisplayName,
				String.CASE_INSENSITIVE_ORDER));

		List<FrameSpec> specs = List.of(
			new FrameSpec("Combat", GuiIcon.COMBAT,
				visibleFeatures(categories.get(Category.COMBAT)), true),
			new FrameSpec("Render", GuiIcon.RENDER,
				visibleFeatures(categories.get(Category.RENDER)), true),
			new FrameSpec("Utility", GuiIcon.MOVEMENT,
				visibleFeatures(categories.get(Category.MOVEMENT)), true),
			new FrameSpec("World", GuiIcon.WORLD,
				visibleFeatures(categories.get(Category.BLOCKS)), true),
			new FrameSpec("Inventory", GuiIcon.PLAYER,
				visibleFeatures(categories.get(Category.ITEMS)), true),
			new FrameSpec("Other", GuiIcon.MISC,
				visibleFeatures(combined(categories, Category.CHAT, Category.FUN,
					Category.OTHER)), false),
			new FrameSpec("Favorites", GuiIcon.PIN,
				favoriteFeatures(), false));

		frames.clear();
		double x = sidebarX() + SIDEBAR_WIDTH + FRAME_GAP;
		double y = START_Y;
		double rowHeight = 0;
		for(FrameSpec spec : specs)
		{
			CategoryPanelComponent panel = new CategoryPanelComponent(x,
				y + HEADER_HEIGHT, FRAME_WIDTH, spec.features,
				VapePalette.ACCENT, 11, this);
			double bodyHeight = Math.min(MAX_BODY_HEIGHT,
				Math.max(ROW_HEIGHT, panel.getContentHeight()));
			panel.setHeight(bodyHeight);
			double frameHeight = HEADER_HEIGHT + bodyHeight;
			if(x + FRAME_WIDTH > width - 12)
			{
				x = sidebarX() + SIDEBAR_WIDTH + FRAME_GAP;
				y += rowHeight + 8;
				rowHeight = 0;
				panel.setX(x);
				panel.setY(y + HEADER_HEIGHT);
			}
			FloatingFrame frame = new FloatingFrame(spec.title, spec.icon, panel);
			frame.visible = spec.defaultVisible;
			restoreFrameState(frame);
			frames.add(frame);
			rowHeight = Math.max(rowHeight, frameHeight);
			x += FRAME_WIDTH + FRAME_GAP;
		}
		if(x + FRAME_WIDTH > width - 12)
		{
			x = sidebarX() + SIDEBAR_WIDTH + FRAME_GAP;
			y += rowHeight + 8;
		}
		FloatingFrame settings = FloatingFrame.settings(sidebarX(),
			START_Y + SIDEBAR_HEIGHT + FRAME_GAP);
		settings.visible = false;
		restoreFrameState(settings);
		frames.add(settings);
	}

	private void restoreFrameState(FloatingFrame frame)
	{
		VapeFrameState state = WurstClient.INSTANCE.getGuiPreferences()
			.getVapeFrameState(frame.title);
		if(state == null)
			return;
		frame.moveTo(state.x(), state.y());
		frame.collapsed = state.collapsed();
		frame.visible = state.visible();
	}

	private void saveFrameState(FloatingFrame frame)
	{
		WurstClient.INSTANCE.getGuiPreferences().setVapeFrameState(frame.title,
			frame.x(), frame.y(), frame.collapsed, frame.visible);
	}

	private List<Feature> visibleFeatures(List<Feature> features)
	{
		if(editingHiddenModules)
			return new ArrayList<>(features);
		return features.stream().filter(feature -> !isHidden(feature)).toList();
	}

	private List<Feature> favoriteFeatures()
	{
		return FeatureMenuSupport.getAllFeatures().stream().filter(this::isFavorite)
			.filter(feature -> editingHiddenModules || !isHidden(feature))
			.sorted(Comparator.comparing(Feature::getDisplayName,
				String.CASE_INSENSITIVE_ORDER))
			.toList();
	}

	@SafeVarargs
	private static List<Feature> combined(Map<Category, List<Feature>> categories,
		Category... values)
	{
		List<Feature> result = new ArrayList<>();
		for(Category category : values)
			result.addAll(categories.get(category));
		return result;
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY,
		float partialTicks)
	{
		renderSidebar(graphics, mouseX, mouseY);
		if(searchFocused || !searchQuery.isBlank())
			renderSearch(graphics, mouseX, mouseY, partialTicks);
		for(FloatingFrame frame : frames)
			if(frame.visible)
				renderFrame(graphics, frame, mouseX, mouseY, partialTicks);
	}

	private int sidebarX()
	{
		return Math.max(12, Math.min(22, width / 34));
	}

	private void renderSidebar(GuiGraphics graphics, int mouseX, int mouseY)
	{
		int x = sidebarX();
		int y = START_Y;
		FlatRenderer.fillRoundedRect(graphics, x + 1, y + 2,
			x + SIDEBAR_WIDTH + 2, y + SIDEBAR_HEIGHT + 3, 2, 0x70000000);
		FlatRenderer.fillRoundedRect(graphics, x, y, x + SIDEBAR_WIDTH,
			y + SIDEBAR_HEIGHT, 2, 0xF2171517);
		graphics.blit(CLIENT_LOGO, x + 7, y + 6, 68, 8, 0, 0,
			LOGO_TEXTURE_WIDTH, LOGO_TEXTURE_HEIGHT, LOGO_TEXTURE_WIDTH,
			LOGO_TEXTURE_HEIGHT);
		GuiIcon.SETTINGS.draw(graphics, x + SIDEBAR_WIDTH - 27, y + 5, 7,
			VapePalette.TEXT);
		GuiIcon.CHEVRON.draw(graphics, x + SIDEBAR_WIDTH - 14, y + 6, 6,
			VapePalette.TEXT);

		String[] labels = {"Combat", "Render", "Utility", "World",
			"Inventory"};
		GuiIcon[] icons = {GuiIcon.COMBAT, GuiIcon.RENDER, GuiIcon.MOVEMENT,
			GuiIcon.WORLD, GuiIcon.PLAYER};
		int rowY = y + 24;
		for(int i = 0; i < labels.length; i++)
			renderSidebarRow(graphics, mouseX, mouseY, x, rowY + i * ROW_HEIGHT,
				labels[i], icons[i], isFrameVisible(labels[i]));

		int miscY = rowY + labels.length * ROW_HEIGHT + 2;
		graphics.fill(x, miscY, x + SIDEBAR_WIDTH, miscY + 10, 0x80100F10);
		graphics.drawString(font, "MISC", x + 7, miscY + 1,
			VapePalette.TEXT_HIDDEN, false);
		renderSidebarRow(graphics, mouseX, mouseY, x, miscY + 12, "Friends",
			GuiIcon.PLAYER, false);
		renderSidebarRow(graphics, mouseX, mouseY, x, miscY + 32, "Profiles",
			GuiIcon.CONFIG, false);
		graphics.fill(x + 52, miscY + 36, x + 94, miscY + 47,
			0xFF242124);
		graphics.drawString(font, "Classic PV...", x + 55, miscY + 38,
			VapePalette.TEXT, false);
		renderSidebarRow(graphics, mouseX, mouseY, x, miscY + 52, "Macros",
			GuiIcon.MENU, false);
		GuiIcon.PLAYER.draw(graphics, x + 8, y + SIDEBAR_HEIGHT - 15, 7,
			VapePalette.TEXT);
		GuiIcon.PIN.draw(graphics, x + SIDEBAR_WIDTH - 30,
			y + SIDEBAR_HEIGHT - 15, 7,
			isFavoritesVisible() ? VapePalette.ACCENT : VapePalette.TEXT);
		GuiIcon.MENU.draw(graphics, x + SIDEBAR_WIDTH - 15,
			y + SIDEBAR_HEIGHT - 15, 7, VapePalette.TEXT);
	}

	private void renderSidebarRow(GuiGraphics graphics, int mouseX, int mouseY,
		int x, int y, String label, GuiIcon icon, boolean active)
	{
		boolean hovered = mouseX >= x && mouseX < x + SIDEBAR_WIDTH
			&& mouseY >= y && mouseY < y + ROW_HEIGHT;
		if(hovered || active)
			graphics.fill(x, y, x + SIDEBAR_WIDTH, y + ROW_HEIGHT, 0x661F1E1F);
		icon.draw(graphics, x + 8, y + 7, 7,
			hovered ? VapePalette.TEXT_HOVER : VapePalette.TEXT);
		graphics.drawString(font, label, x + 22, y + 6,
			hovered ? VapePalette.TEXT_HOVER : VapePalette.TEXT, false);
		GuiIcon.CHEVRON.drawRotated(graphics, x + SIDEBAR_WIDTH - 11,
			y + 7, 6, VapePalette.TEXT_HIDDEN, -90);
	}

	private boolean isFrameVisible(String title)
	{
		return frames.stream().filter(frame -> frame.title.equals(title))
			.anyMatch(frame -> frame.visible);
	}

	private void renderSearch(GuiGraphics graphics, int mouseX, int mouseY,
		float partialTicks)
	{
		int x = searchX();
		int y = 7;
		FlatRenderer.fillRoundedRect(graphics, x - 1, y - 1,
			x + FRAME_WIDTH + 1, y + SEARCH_HEIGHT + 1, 2, VapePalette.BORDER);
		FlatRenderer.fillRoundedRect(graphics, x, y, x + FRAME_WIDTH,
			y + SEARCH_HEIGHT, 2, VapePalette.FRAME);
		String text = searchQuery.isEmpty() ? "Search" : searchQuery;
		int color = searchQuery.isEmpty() && !searchFocused
			? VapePalette.TEXT_HIDDEN : VapePalette.TEXT_HOVER;
		graphics.drawString(font, font.plainSubstrByWidth(text, 84), x + 6,
			y + 4, color, false);
		GuiIcon.SEARCH.draw(graphics, x + FRAME_WIDTH - 14, y + 4, 8,
			searchFocused ? VapePalette.ACCENT : VapePalette.TEXT);
		if(searchFocused && System.currentTimeMillis() / 500 % 2 == 0)
		{
			int cursorX = Math.min(x + FRAME_WIDTH - 18,
				x + 6 + font.width(font.plainSubstrByWidth(text, 84)));
			graphics.fill(cursorX, y + 3, cursorX + 1, y + 13,
				VapePalette.TEXT_HOVER);
		}
		if(searchPanel != null && !searchQuery.isBlank())
			searchPanel.render(graphics, mouseX, mouseY, partialTicks);
	}

	private int searchX()
	{
		return Math.max(4, (width - FRAME_WIDTH) / 2);
	}

	private void rebuildSearchPanel()
	{
		if(searchQuery.isBlank())
		{
			searchPanel = null;
			return;
		}
		String query = searchQuery.toLowerCase(Locale.ROOT);
		List<Feature> matches = FeatureMenuSupport.getAllFeatures().stream()
			.filter(feature -> editingHiddenModules || !isHidden(feature))
			.filter(feature -> feature.getDisplayName().toLowerCase(Locale.ROOT)
				.contains(query) || feature.getName().toLowerCase(Locale.ROOT)
				.contains(query) || feature.getSearchTags().toLowerCase(Locale.ROOT)
				.contains(query))
			.sorted(Comparator.comparing(Feature::getDisplayName,
				String.CASE_INSENSITIVE_ORDER))
			.toList();
		searchPanel = new CategoryPanelComponent(searchX(), 7 + SEARCH_HEIGHT,
			FRAME_WIDTH, matches, VapePalette.ACCENT, 11, this);
	}

	private void renderFrame(GuiGraphics graphics, FloatingFrame frame,
		int mouseX, int mouseY, float partialTicks)
	{
		int x = (int)frame.x();
		int y = (int)frame.y();
		int bodyHeight = frame.settings
			? SETTINGS_ROW_COUNT * ROW_HEIGHT + SETTINGS_FOOTER_HEIGHT
			: (int)frame.panel.getHeight();
		int bottom = y + HEADER_HEIGHT + (frame.collapsed ? 0 : bodyHeight);
		FlatRenderer.fillRoundedRect(graphics, x + 1, y + 2,
			x + FRAME_WIDTH + 2, bottom + 3, 2, 0x70000000);
		FlatRenderer.fillRoundedRect(graphics, x, y, x + FRAME_WIDTH, bottom,
			2, VapePalette.FRAME);
		graphics.fill(x, y + HEADER_HEIGHT - 1, x + FRAME_WIDTH, bottom,
			VapePalette.FRAME);
		frame.icon.draw(graphics, x + 7, y + 7, 7, VapePalette.TEXT_HOVER);
		graphics.drawString(font, frame.title, x + 19, y + 6,
			VapePalette.TEXT_HOVER,
			false);
		GuiIcon.CHEVRON.drawRotated(graphics, x + FRAME_WIDTH - 12, y + 7,
			6, VapePalette.TEXT, frame.collapsed ? -90 : 0);
		if(frame.collapsed)
			return;
		if(frame.settings)
			renderSettingsFrame(graphics, frame, mouseX, mouseY);
		else
			frame.panel.render(graphics, mouseX, mouseY, partialTicks);
	}

	private void renderSettingsFrame(GuiGraphics graphics, FloatingFrame frame,
		int mouseX, int mouseY)
	{
		int x = (int)frame.x();
		int bodyY = (int)frame.y() + HEADER_HEIGHT;
		String[] labels = {"VapeMode", "Search modules",
			editingHiddenModules ? "Done editing" : "Edit modules", "Favorites",
			"Reset GUI positions", "Sort GUI"};
		for(int row = 0; row < labels.length; row++)
		{
			int y = bodyY + row * ROW_HEIGHT;
			boolean hovered = mouseX >= x && mouseX < x + FRAME_WIDTH
				&& mouseY >= y && mouseY < y + ROW_HEIGHT;
			graphics.fill(x, y, x + FRAME_WIDTH, y + ROW_HEIGHT,
				hovered ? VapePalette.ROW_HOVER : VapePalette.FRAME);
			graphics.drawString(font, labels[row], x + 6, y + 6,
				hovered ? VapePalette.TEXT_HOVER : VapePalette.TEXT, false);
			if(row == 0)
				renderEnabledSwitch(graphics, x + FRAME_WIDTH - 23, y + 6);
			else if(row == 1)
				GuiIcon.SEARCH.draw(graphics, x + FRAME_WIDTH - 14, y + 6, 8,
					VapePalette.TEXT);
			else if(row == 2)
				GuiIcon.CONFIG.draw(graphics, x + FRAME_WIDTH - 14, y + 6, 8,
					editingHiddenModules ? VapePalette.ACCENT : VapePalette.TEXT);
			else if(row == 3)
				GuiIcon.PIN.draw(graphics, x + FRAME_WIDTH - 14, y + 6, 8,
					isFavoritesVisible() ? VapePalette.ACCENT : VapePalette.TEXT);
		}
		int footerY = bodyY + SETTINGS_ROW_COUNT * ROW_HEIGHT;
		graphics.drawString(font, "Vape 4.21", x + FRAME_WIDTH
			- font.width("Vape 4.21") - 4, footerY + 2,
			VapePalette.TEXT_HIDDEN, false);
	}

	private static void renderEnabledSwitch(GuiGraphics graphics, int x, int y)
	{
		graphics.fill(x, y, x + 16, y + 8, VapePalette.ACCENT);
		graphics.fill(x + 9, y + 1, x + 15, y + 7,
			VapePalette.TEXT_ACTIVE);
	}

	private FloatingFrame headerAt(double mouseX, double mouseY)
	{
		for(int index = frames.size() - 1; index >= 0; index--)
		{
			FloatingFrame frame = frames.get(index);
			if(!frame.visible)
				continue;
			if(mouseX >= frame.x() && mouseX < frame.x() + FRAME_WIDTH
				&& mouseY >= frame.y() && mouseY < frame.y() + HEADER_HEIGHT)
				return frame;
		}
		return null;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button)
	{
		if(handleSidebarClick(mouseX, mouseY, button))
			return true;
		if(mouseX >= searchX() && mouseX < searchX() + FRAME_WIDTH
			&& mouseY >= 7 && mouseY < 7 + SEARCH_HEIGHT)
		{
			if(button == 0)
				searchFocused = true;
			return true;
		}
		if(searchPanel != null && !searchQuery.isBlank()
			&& searchPanel.mouseClicked(mouseX, mouseY, button))
			return true;
		FloatingFrame header = headerAt(mouseX, mouseY);
		if(header != null)
		{
			boolean collapseButton = mouseX >= header.x() + FRAME_WIDTH - 20;
			if(button == 0 && collapseButton)
			{
				header.collapsed = !header.collapsed;
				saveFrameState(header);
				return true;
			}
			if(button == 1)
			{
				header.collapsed = !header.collapsed;
				saveFrameState(header);
				return true;
			}
			if(button == 0)
			{
				draggingFrame = header;
				dragOffsetX = mouseX - header.x();
				dragOffsetY = mouseY - header.y();
				frames.remove(header);
				frames.add(header);
				return true;
			}
		}
		for(int index = frames.size() - 1; index >= 0; index--)
		{
			FloatingFrame frame = frames.get(index);
			if(!frame.visible || frame.collapsed)
				continue;
			if(frame.settings && mouseX >= frame.x()
				&& mouseX < frame.x() + FRAME_WIDTH
				&& mouseY >= frame.y() + HEADER_HEIGHT
				&& mouseY < frame.y() + HEADER_HEIGHT
					+ SETTINGS_ROW_COUNT * ROW_HEIGHT)
			{
				if(button == 0)
					handleSettingsClick(frame, mouseY);
				return true;
			}
			if(!frame.settings
				&& frame.panel.mouseClicked(mouseX, mouseY, button))
				return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	private boolean handleSidebarClick(double mouseX, double mouseY,
		int button)
	{
		if(button != 0)
			return false;
		int x = sidebarX();
		int y = START_Y;
		if(mouseX < x || mouseX >= x + SIDEBAR_WIDTH || mouseY < y
			|| mouseY >= y + SIDEBAR_HEIGHT)
			return false;
		if(mouseY >= y && mouseY < y + 20
			&& mouseX >= x + SIDEBAR_WIDTH - 36
			&& mouseX < x + SIDEBAR_WIDTH - 18)
		{
			toggleFrame("Settings");
			return true;
		}
		int rowY = y + 24;
		String[] titles = {"Combat", "Render", "Utility", "World",
			"Inventory"};
		for(int index = 0; index < titles.length; index++)
			if(mouseY >= rowY + index * ROW_HEIGHT
				&& mouseY < rowY + (index + 1) * ROW_HEIGHT)
			{
				toggleFrame(titles[index]);
				return true;
			}
		if(mouseY >= y + SIDEBAR_HEIGHT - 20
			&& mouseX >= x + SIDEBAR_WIDTH - 38
			&& mouseX < x + SIDEBAR_WIDTH - 18)
		{
			toggleFavoritesFrame();
			return true;
		}
		return true;
	}

	private void toggleFrame(String title)
	{
		for(FloatingFrame frame : frames)
			if(frame.title.equals(title))
			{
				frame.visible = !frame.visible;
				saveFrameState(frame);
				return;
			}
	}

	private void handleSettingsClick(FloatingFrame frame, double mouseY)
	{
		int row = (int)((mouseY - frame.y() - HEADER_HEIGHT) / ROW_HEIGHT);
		switch(row)
		{
			case 0 -> ClickGuiScreens.setVapeMode(false);
			case 1 -> searchFocused = true;
			case 2 ->
			{
				editingHiddenModules = !editingHiddenModules;
				buildFrames();
				rebuildSearchPanel();
			}
			case 3 -> toggleFavoritesFrame();
			case 4, 5 ->
			{
				WurstClient.INSTANCE.getGuiPreferences().clearVapeFrameStates();
				buildFrames();
			}
			default -> {}
		}
	}

	private boolean isFavoritesVisible()
	{
		return frames.stream().filter(frame -> frame.title.equals("Favorites"))
			.anyMatch(frame -> frame.visible);
	}

	private void toggleFavoritesFrame()
	{
		for(FloatingFrame frame : frames)
			if(frame.title.equals("Favorites"))
			{
				frame.visible = !frame.visible;
				saveFrameState(frame);
				return;
			}
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button)
	{
		if(draggingFrame != null)
		{
			saveFrameState(draggingFrame);
			draggingFrame = null;
			return true;
		}
		for(int index = frames.size() - 1; index >= 0; index--)
		{
			FloatingFrame frame = frames.get(index);
			if(frame.visible && !frame.settings && !frame.collapsed
				&& frame.panel.mouseReleased(mouseX, mouseY, button))
				return true;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button,
		double dragX, double dragY)
	{
		if(draggingFrame != null)
		{
			draggingFrame.moveTo(mouseX - dragOffsetX,
				mouseY - dragOffsetY);
			return true;
		}
		for(int index = frames.size() - 1; index >= 0; index--)
		{
			FloatingFrame frame = frames.get(index);
			if(frame.visible && !frame.settings && !frame.collapsed
				&& frame.panel.mouseDragged(mouseX, mouseY, button))
				return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double delta)
	{
		if(searchPanel != null && !searchQuery.isBlank()
			&& searchPanel.mouseScrolled(mouseX, mouseY, delta))
			return true;
		for(int index = frames.size() - 1; index >= 0; index--)
		{
			FloatingFrame frame = frames.get(index);
			if(frame.visible && !frame.settings && !frame.collapsed
				&& frame.panel.mouseScrolled(mouseX, mouseY, delta))
				return true;
		}
		return super.mouseScrolled(mouseX, mouseY, delta);
	}

	@Override
	public void tick()
	{
		for(FloatingFrame frame : frames)
			if(frame.visible && !frame.settings)
				frame.panel.tick();
		if(searchPanel != null)
			searchPanel.tick();
		super.tick();
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers)
	{
		if(activeTextInput != null && activeTextInput.acceptKey(keyCode))
			return true;
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
				WurstClient.INSTANCE.getKeybinds().unbindCommand(command);
			else
				WurstClient.INSTANCE.getKeybinds().bindCommand(
					InputConstants.getKey(keyCode, scanCode).getName(), command);
			return true;
		}
		if(searchFocused)
		{
			if(keyCode == GLFW.GLFW_KEY_ESCAPE
				|| keyCode == GLFW.GLFW_KEY_ENTER)
			{
				searchFocused = false;
				return true;
			}
			if(keyCode == GLFW.GLFW_KEY_BACKSPACE && !searchQuery.isEmpty())
			{
				searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
				rebuildSearchPanel();
				return true;
			}
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean charTyped(char codePoint, int modifiers)
	{
		if(activeTextInput != null)
		{
			activeTextInput.acceptChar(codePoint);
			return true;
		}
		if(!searchFocused || Character.isISOControl(codePoint))
			return super.charTyped(codePoint, modifiers);
		if(font.width(searchQuery + codePoint) < 84)
		{
			searchQuery += codePoint;
			rebuildSearchPanel();
		}
		return true;
	}

	@Override
	public void beginBinding(Feature feature)
	{
		if(getBindableCommand(feature) != null)
			bindingFeature = feature;
	}

	@Override
	public void beginTextInput(GuiTextInput component)
	{
		if(activeTextInput != null && activeTextInput != component)
			activeTextInput.loseFocus();
		activeTextInput = component;
	}

	@Override
	public void endTextInput(GuiTextInput component)
	{
		if(activeTextInput == component)
			activeTextInput = null;
	}

	private String getBindableCommand(Feature feature)
	{
		return feature.getPossibleKeybinds().stream().findFirst()
			.map(PossibleKeybind::getCommand).orElse(null);
	}

	public boolean isWaitingForKeybind()
	{
		return bindingFeature != null;
	}

	public boolean isBindingFeature(Feature feature)
	{
		return bindingFeature == feature;
	}

	@Override
	public boolean isBinding(Feature feature)
	{
		return isBindingFeature(feature);
	}

	@Override
	public boolean isFavorite(Feature feature)
	{
		return WurstClient.INSTANCE.getGuiPreferences()
			.isVapeFavorite(feature.getName());
	}

	@Override
	public void toggleFavorite(Feature feature)
	{
		WurstClient.INSTANCE.getGuiPreferences()
			.toggleVapeFavorite(feature.getName());
		buildFrames();
		rebuildSearchPanel();
	}

	@Override
	public boolean isHidden(Feature feature)
	{
		return WurstClient.INSTANCE.getGuiPreferences()
			.isVapeModuleHidden(feature.getName());
	}

	@Override
	public void toggleHidden(Feature feature)
	{
		WurstClient.INSTANCE.getGuiPreferences()
			.toggleVapeModuleHidden(feature.getName());
	}

	@Override
	public boolean isEditingHiddenModules()
	{
		return editingHiddenModules;
	}

	@Override
	public boolean isPauseScreen()
	{
		return false;
	}

	private record FrameSpec(String title, GuiIcon icon,
		List<Feature> features, boolean defaultVisible)
	{
	}

	private static final class FloatingFrame
	{
		private final String title;
		private final GuiIcon icon;
		private final CategoryPanelComponent panel;
		private final boolean settings;
		private double settingsX;
		private double settingsY;
		private boolean collapsed;
		private boolean visible = true;

		private FloatingFrame(String title, GuiIcon icon,
			CategoryPanelComponent panel)
		{
			this.title = title;
			this.icon = icon;
			this.panel = panel;
			settings = false;
		}

		private FloatingFrame(double x, double y)
		{
			title = "Settings";
			icon = GuiIcon.SETTINGS;
			panel = null;
			settings = true;
			settingsX = x;
			settingsY = y;
		}

		private static FloatingFrame settings(double x, double y)
		{
			return new FloatingFrame(x, y);
		}

		private double x()
		{
			return settings ? settingsX : panel.getX();
		}

		private double y()
		{
			return settings ? settingsY : panel.getY() - HEADER_HEIGHT;
		}

		private void moveTo(double x, double y)
		{
			if(settings)
			{
				settingsX = x;
				settingsY = y;
				return;
			}
			panel.setX(x);
			panel.setY(y + HEADER_HEIGHT);
		}
	}
}
