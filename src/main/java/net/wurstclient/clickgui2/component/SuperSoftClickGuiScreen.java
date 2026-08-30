package net.wurstclient.clickgui2.component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.wurstclient.Category;
import net.wurstclient.Feature;
import net.wurstclient.WurstClient;
import net.wurstclient.clickgui2.FeatureMenuSupport;
import net.wurstclient.clickgui2.FlatRenderer;
import net.wurstclient.clickgui2.GuiIcon;
import net.wurstclient.clickgui2.ClickGuiScreens;
import net.wurstclient.clickgui2.GuiPreferences;
import net.wurstclient.clickgui2.GuiPreferences.TargetType;
import net.wurstclient.clickgui2.screens.NeteaseMusicScreen;
import net.wurstclient.clickgui2.supersoft.SuperSoftRenderer;
import net.wurstclient.clickgui2.supersoft.EpsilonMd3Theme;
import net.wurstclient.clickgui2.supersoft.SuperSoftTheme;
import net.wurstclient.clickgui2.supersoft.UiMotion;
import net.wurstclient.clickgui2.supersoft.UiTween;
import net.wurstclient.keybinds.PossibleKeybind;
import net.wurstclient.hud2.HudEditorScreen;
import net.wurstclient.options.EnterProfileNameScreen;
import net.wurstclient.util.ChatUtils;
import net.wurstclient.util.json.JsonException;
import org.lwjgl.glfw.GLFW;

/** Java port of SuperSoft's Compose ClickGuiScreen. */
public final class SuperSoftClickGuiScreen extends Screen
	implements VapeGuiContext
{
	private static final WurstClient WURST = WurstClient.INSTANCE;
	private static final int WINDOW_WIDTH = 100;
	private static final int GAP = 6;
	private static final int START_Y = 18;
	private static final int HUD_BUTTON_WIDTH = 72;
	private static final int HUD_BUTTON_HEIGHT = 24;
	private static final int HUD_BUTTON_MARGIN = 16;
	private static final long HUD_BUTTON_PRESS_NANOS = 90_000_000L;
	private static final List<String> DEFAULT_WINDOW_IDS = List.of("visual",
		"movement", "client", "combat", "world", "other", "target",
		"config", "font", "settings", "main");

	private final Screen parentScreen;
	private final List<SuperSoftFloatingWindow> windows = new ArrayList<>();
	private final UiMotion screenMotion = new UiMotion(0);
	private final UiTween contentMotion = new UiTween(0, 200);
	private final UiTween hudButtonHoverMotion = new UiTween(0, 150);
	private final UiTween hudButtonPressMotion = new UiTween(0, 120);
	private final ClickGuiKeyLatch closeKeyLatch = new ClickGuiKeyLatch();
	private final Map<String, SuperSoftClickGuiWindow> categoryWindows =
		new java.util.HashMap<>();
	private SuperSoftFloatingWindow draggingWindow;
	private double dragOffsetX;
	private double dragOffsetY;
	private Feature bindingFeature;
	private GuiTextInput activeTextInput;
	private List<Path> profiles = List.of();
	private String selectedProfile;
	private List<String> fonts = List.of();
	private String selectedFont;
	private String searchQuery = "";
	private boolean searchFocused;
	private boolean initialized;
	private boolean defaultWindowsBuilt;
	private boolean closing;
	private boolean openingHudEditor;
	private long hudEditorOpenAtNanos;

	public SuperSoftClickGuiScreen()
	{
		this(null);
	}

	public SuperSoftClickGuiScreen(Screen parentScreen)
	{
		super(Component.literal(WurstClient.CLIENT_NAME));
		this.parentScreen = parentScreen;
	}

	@Override
	protected void init()
	{
		VapeTextInputComponent.setContext(this);
		GuiIcon.configureFiltering(minecraft);
		if(!initialized)
		{
			screenMotion.snap(0);
			initialized = true;
		}
		refreshWindows();
	}

	private void refreshWindows()
	{
		Map<Category, List<Feature>> categorized = categorizeFeatures();
		List<CategorySpec> categories = List.of(
			new CategorySpec("visual", "Visual", GuiIcon.RENDER,
				categorized.get(Category.RENDER)),
			new CategorySpec("movement", "Movement", GuiIcon.MOVEMENT,
				categorized.get(Category.MOVEMENT)),
			new CategorySpec("client", "Client", GuiIcon.CLIENT,
				getInterfaceFeatures()),
			new CategorySpec("combat", "Combat", GuiIcon.COMBAT,
				categorized.get(Category.COMBAT)),
			new CategorySpec("world", "World", GuiIcon.WORLD,
				merge(categorized.get(Category.BLOCKS),
					categorized.get(Category.ITEMS))),
			new CategorySpec("other", "Other", GuiIcon.MISC,
				getOtherFeatures(categorized)));

		if(!defaultWindowsBuilt)
		{
			buildDefaultWindows(categories);
			defaultWindowsBuilt = true;
			refreshProfiles();
			refreshFonts();
			sortWindows();
			return;
		}
		for(CategorySpec spec : categories)
		{
			SuperSoftClickGuiWindow window = categoryWindows.get(spec.id);
			if(window != null)
				window.setFeatures(spec.features, accentColor());
		}
		for(SuperSoftFloatingWindow window : windows)
			window.moveTo(window.getX(), window.getY(), uiWidth(), uiHeight());
	}

	private Map<Category, List<Feature>> categorizeFeatures()
	{
		List<Feature> features = FeatureMenuSupport.getAllFeatures();
		if(!searchQuery.isBlank())
			features = FeatureMenuSupport.searchFeatures(features, searchQuery);
		Map<Category, List<Feature>> categorized = new EnumMap<>(Category.class);
		for(Category category : Category.values())
			categorized.put(category, new ArrayList<>());
		for(Feature feature : features)
			if(feature.getCategory() != null)
				categorized.get(feature.getCategory()).add(feature);
		Comparator<Feature> order = Comparator.comparing(Feature::getDisplayName,
			String.CASE_INSENSITIVE_ORDER);
		categorized.values().forEach(list -> list.sort(order));
		return categorized;
	}

	private void buildDefaultWindows(List<CategorySpec> categories)
	{
		for(CategorySpec spec : categories)
		{
			SuperSoftClickGuiWindow window = new SuperSoftClickGuiWindow(spec.id,
				spec.title, spec.icon, spec.features, 0, 0, accentColor(), this);
			windows.add(window);
			categoryWindows.put(spec.id, window);
		}
		windows.add(new SuperSoftRowsWindow("target", "Target", GuiIcon.PLAYER,
			0, 0, this::targetRows));
		windows.add(new SuperSoftRowsWindow("config", "Config", GuiIcon.CONFIG,
			0, 0, this::configRows));
		windows.add(new SuperSoftRowsWindow("font", "Font", GuiIcon.FONT,
			0, 0, this::fontRows));
		windows.add(new SuperSoftRowsWindow("settings", "Settings",
			GuiIcon.SETTINGS, 0, 0, this::globalSettingsRows));
		windows.add(new SuperSoftRowsWindow("main", "Main", GuiIcon.MENU,
			0, 0, this::mainRows));
	}

	private List<Feature> getInterfaceFeatures()
	{
		return filterSearch(List.of(WURST.getHax().clickGuiHack,
			WURST.getOtfs().wurstLogoOtf, WURST.getOtfs().hackListOtf,
			WURST.getOtfs().keybindManagerOtf));
	}

	private List<Feature> getOtherFeatures(
		Map<Category, List<Feature>> categorized)
	{
		ArrayList<Feature> features = new ArrayList<>(merge(
			categorized.get(Category.OTHER), categorized.get(Category.CHAT),
			categorized.get(Category.FUN)));
		features.addAll(filterSearch(List.of(WURST.getHax().navigatorHack,
			WURST.getOtfs().tabGuiOtf, WURST.getOtfs().translationsOtf,
			WURST.getOtfs().vanillaSpoofOtf)));
		return features;
	}

	private List<Feature> filterSearch(List<Feature> features)
	{
		if(searchQuery.isBlank())
			return features;
		return FeatureMenuSupport.searchFeatures(features, searchQuery);
	}

	@SafeVarargs
	private static List<Feature> merge(List<Feature>... lists)
	{
		ArrayList<Feature> result = new ArrayList<>();
		for(List<Feature> list : lists)
			result.addAll(list);
		return result;
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY,
		float partialTicks)
	{
		float progress = screenMotion.update(closing ? 0 : 1);
		if(closing && progress <= 0.001F)
		{
			minecraft.setScreen(parentScreen);
			return;
		}
		int scaledMouseX = Math.round(mouseX / interfaceScale());
		int scaledMouseY = Math.round(mouseY / interfaceScale());
		graphics.pose().pushPose();
		graphics.pose().scale(interfaceScale(), interfaceScale(), 1);
		SuperSoftRenderer.backdrop(graphics, uiWidth(), uiHeight(), progress);
		graphics.flush();
		float contentAlpha = contentMotion.update(closing ? 0 : 1);
		RenderSystem.setShaderColor(1, 1, 1, contentAlpha);
		try
		{
			for(SuperSoftFloatingWindow window : windows)
				window.render(graphics, font, scaledMouseX, scaledMouseY,
					partialTicks, maxBodyHeight(), this);
			renderHudEditorButton(graphics, scaledMouseX, scaledMouseY);
			renderSearch(graphics);
			graphics.flush();
		}finally
		{
			RenderSystem.setShaderColor(1, 1, 1, 1);
		}
		graphics.pose().popPose();
	}

	private void renderHudEditorButton(GuiGraphics graphics, int mouseX,
		int mouseY)
	{
		int left = uiWidth() - HUD_BUTTON_MARGIN - HUD_BUTTON_WIDTH;
		int top = uiHeight() - HUD_BUTTON_MARGIN - HUD_BUTTON_HEIGHT;
		boolean hovered = hudButtonContains(mouseX, mouseY);
		float hover = hudButtonHoverMotion.update(hovered ? 1 : 0);
		float pressed = hudButtonPressMotion.update(openingHudEditor ? 1 : 0);
		int offsetY = pressed > 0.5F ? 1 : 0;
		int color = SuperSoftTheme.mix(EpsilonMd3Theme.SURFACE_CONTAINER,
			SuperSoftTheme.SETTING_HOVER, hover);
		color = SuperSoftTheme.mix(color, SuperSoftTheme.ACCENT,
			pressed * 0.8F);

		FlatRenderer.fillRoundedRect(graphics, left - 1, top + 2,
			left + HUD_BUTTON_WIDTH + 1, top + HUD_BUTTON_HEIGHT + 3, 5,
			EpsilonMd3Theme.SHADOW);
		FlatRenderer.fillRoundedRect(graphics, left, top + offsetY,
			left + HUD_BUTTON_WIDTH, top + HUD_BUTTON_HEIGHT + offsetY, 5,
			color);
		String label = "\u7f16\u8f91 HUD";
		graphics.drawCenteredString(font, label, left + HUD_BUTTON_WIDTH / 2,
			top + (HUD_BUTTON_HEIGHT - font.lineHeight) / 2 + offsetY,
			SuperSoftTheme.TEXT);
	}

	private boolean hudButtonContains(double mouseX, double mouseY)
	{
		int left = uiWidth() - HUD_BUTTON_MARGIN - HUD_BUTTON_WIDTH;
		int top = uiHeight() - HUD_BUTTON_MARGIN - HUD_BUTTON_HEIGHT;
		return mouseX >= left && mouseX < left + HUD_BUTTON_WIDTH
			&& mouseY >= top && mouseY < top + HUD_BUTTON_HEIGHT;
	}

	private void renderSearch(GuiGraphics graphics)
	{
		if(!searchFocused && searchQuery.isBlank())
			return;
		int left = Math.max(12, (uiWidth() - 120) / 2);
		SuperSoftRenderer.window(graphics, left, 7, left + 120, 27, 4,
			SuperSoftTheme.ACCENT);
		String search = searchQuery.isEmpty() ? "Search" : searchQuery;
		graphics.drawString(font, font.plainSubstrByWidth(search, 96), left + 8,
			13, SuperSoftTheme.TEXT, false);
		GuiIcon.SEARCH.draw(graphics, left + 105, 13, 8, SuperSoftTheme.TEXT);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button)
	{
		if(closing)
			return true;
		mouseX /= interfaceScale();
		mouseY /= interfaceScale();
		if(button == GLFW.GLFW_MOUSE_BUTTON_LEFT
			&& hudButtonContains(mouseX, mouseY))
		{
			openingHudEditor = true;
			hudEditorOpenAtNanos = System.nanoTime() + HUD_BUTTON_PRESS_NANOS;
			return true;
		}
		for(int index = windows.size() - 1; index >= 0; index--)
		{
			SuperSoftFloatingWindow window = windows.get(index);
			if(!window.isVisible())
				continue;
			if(window.headerContains(mouseX, mouseY))
			{
				bringToFront(index, window);
				if(window.mouseClickedHeader(mouseX, mouseY, button))
					return true;
				if(button == 0)
				{
					draggingWindow = window;
					dragOffsetX = mouseX - window.getX();
					dragOffsetY = mouseY - window.getY();
				}
				return true;
			}
			if(window.mouseClickedBody(mouseX, mouseY, button))
			{
				bringToFront(index, window);
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	private void bringToFront(int index, SuperSoftFloatingWindow window)
	{
		windows.remove(index);
		windows.add(window);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button)
	{
		mouseX /= interfaceScale();
		mouseY /= interfaceScale();
		if(draggingWindow != null)
		{
			draggingWindow = null;
			return true;
		}
		for(int index = windows.size() - 1; index >= 0; index--)
			if(windows.get(index).mouseReleased(mouseX, mouseY, button))
				return true;
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button,
		double dragX, double dragY)
	{
		mouseX /= interfaceScale();
		mouseY /= interfaceScale();
		if(draggingWindow != null && button == 0)
		{
			draggingWindow.moveTo(mouseX - dragOffsetX, mouseY - dragOffsetY,
				uiWidth(), uiHeight());
			return true;
		}
		for(int index = windows.size() - 1; index >= 0; index--)
			if(windows.get(index).mouseDragged(mouseX, mouseY, button))
				return true;
		return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double delta)
	{
		mouseX /= interfaceScale();
		mouseY /= interfaceScale();
		for(int index = windows.size() - 1; index >= 0; index--)
			if(windows.get(index).mouseScrolled(mouseX, mouseY, delta))
				return true;
		return super.mouseScrolled(mouseX, mouseY, delta);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers)
	{
		if(activeTextInput != null && activeTextInput.acceptKey(keyCode))
			return true;
		if(bindingFeature != null)
			return finishBinding(keyCode, scanCode);
		if(searchFocused)
		{
			if(keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER)
			{
				searchFocused = false;
				return true;
			}
			if(keyCode == GLFW.GLFW_KEY_BACKSPACE && !searchQuery.isEmpty())
			{
				searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
				refreshWindows();
				return true;
			}
		}
		if(keyCode == GLFW.GLFW_KEY_F
			&& (modifiers & GLFW.GLFW_MOD_CONTROL) != 0)
		{
			searchFocused = true;
			return true;
		}
		boolean clickGuiBinding = isClickGuiBinding(keyCode, scanCode);
		if(keyCode == GLFW.GLFW_KEY_ESCAPE
			|| closeKeyLatch.shouldCloseOnPress(clickGuiBinding))
		{
			onClose();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean keyReleased(int keyCode, int scanCode, int modifiers)
	{
		if(closeKeyLatch.armOnRelease(isClickGuiBinding(keyCode, scanCode)))
			return true;
		return super.keyReleased(keyCode, scanCode, modifiers);
	}

	private boolean finishBinding(int keyCode, int scanCode)
	{
		Feature feature = bindingFeature;
		bindingFeature = null;
		if(keyCode == GLFW.GLFW_KEY_ESCAPE)
			return true;
		String command = getBindableCommand(feature);
		if(command == null)
			return true;
		if(keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE)
			WURST.getKeybinds().unbindCommand(command);
		else
			WURST.getKeybinds().bindCommand(
				InputConstants.getKey(keyCode, scanCode).getName(), command);
		return true;
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
		if(font.width(searchQuery + codePoint) < 90)
		{
			searchQuery += codePoint;
			refreshWindows();
		}
		return true;
	}

	@Override
	public void tick()
	{
		if(openingHudEditor
			&& System.nanoTime() >= hudEditorOpenAtNanos)
		{
			openingHudEditor = false;
			minecraft.setScreen(new HudEditorScreen(this));
			return;
		}
		for(SuperSoftFloatingWindow window : windows)
			window.tick();
		windows.removeIf(window -> {
			if(!window.isClosed())
				return false;
			window.dispose();
			return true;
		});
		super.tick();
	}

	@Override
	public void onClose()
	{
		if(closing)
			return;
		closing = true;
		openingHudEditor = false;
		draggingWindow = null;
		bindingFeature = null;
		searchFocused = false;
		if(activeTextInput != null)
		{
			activeTextInput.loseFocus();
			activeTextInput = null;
		}
		for(SuperSoftFloatingWindow window : windows)
			window.dispose();
	}

	@Override
	public void removed()
	{
		VapeTextInputComponent.setContext(null);
		super.removed();
	}

	private List<SuperSoftRowsWindow.Row> targetRows()
	{
		GuiPreferences preferences = WURST.getGuiPreferences();
		return List.of(
			SuperSoftRowsWindow.toggle("Players",
				() -> preferences.isTargetEnabled(TargetType.PLAYERS),
				() -> preferences.toggleTarget(TargetType.PLAYERS)),
			SuperSoftRowsWindow.toggle("Monsters",
				() -> preferences.isTargetEnabled(TargetType.MONSTERS),
				() -> preferences.toggleTarget(TargetType.MONSTERS)),
			SuperSoftRowsWindow.toggle("Animals",
				() -> preferences.isTargetEnabled(TargetType.ANIMALS),
				() -> preferences.toggleTarget(TargetType.ANIMALS)),
			SuperSoftRowsWindow.toggle("Teams",
				() -> preferences.isTargetEnabled(TargetType.TEAMS),
				() -> preferences.toggleTarget(TargetType.TEAMS)),
			SuperSoftRowsWindow.toggle("Villagers",
				() -> preferences.isTargetEnabled(TargetType.VILLAGERS),
				() -> preferences.toggleTarget(TargetType.VILLAGERS)));
	}

	private List<SuperSoftRowsWindow.Row> configRows()
	{
		ArrayList<SuperSoftRowsWindow.Row> rows = new ArrayList<>();
		rows.add(SuperSoftRowsWindow.section("Profiles"));
		if(profiles.isEmpty())
			rows.add(SuperSoftRowsWindow.disabled("No profiles"));
		else
			for(Path profile : profiles)
			{
				String name = profile.getFileName().toString();
				rows.add(SuperSoftRowsWindow.action(stripJson(name),
					() -> name.equals(selectedProfile),
					() -> selectedProfile = name));
			}
		rows.add(SuperSoftRowsWindow.action("Load", () -> false,
			this::loadSelectedProfile));
		rows.add(SuperSoftRowsWindow.action("Save", () -> false,
			this::saveSelectedProfile));
		rows.add(SuperSoftRowsWindow.action("Refresh", () -> false,
			this::refreshProfiles));
		rows.add(SuperSoftRowsWindow.action("Open folder", () -> false,
			this::openProfilesFolder));
		return rows;
	}

	private void refreshProfiles()
	{
		profiles = WURST.listSettingsProfiles().stream()
			.sorted(Comparator.comparing(path -> path.getFileName().toString(),
				String.CASE_INSENSITIVE_ORDER)).toList();
		if(selectedProfile != null && profiles.stream().noneMatch(path -> path
			.getFileName().toString().equals(selectedProfile)))
			selectedProfile = null;
	}

	private void loadSelectedProfile()
	{
		if(selectedProfile == null)
		{
			ChatUtils.warning("Select a profile first.");
			return;
		}
		try
		{
			WURST.loadSettingsProfile(selectedProfile);
			ChatUtils.message("Loaded profile: " + stripJson(selectedProfile));
		}catch(IOException | JsonException e)
		{
			ChatUtils.error("Failed to load profile: " + e.getMessage());
		}
	}

	private void saveSelectedProfile()
	{
		if(selectedProfile == null)
		{
			minecraft.setScreen(new EnterProfileNameScreen(this,
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
		refreshProfiles();
	}

	private void saveProfile(String fileName)
	{
		try
		{
			WURST.saveSettingsProfile(fileName);
			ChatUtils.message("Saved profile: " + stripJson(fileName));
		}catch(IOException | JsonException e)
		{
			ChatUtils.error("Failed to save profile: " + e.getMessage());
		}
	}

	private void openProfilesFolder()
	{
		try
		{
			Files.createDirectories(WURST.getSettingsProfileFolder());
			Util.getPlatform().openFile(WURST.getSettingsProfileFolder().toFile());
		}catch(IOException e)
		{
			ChatUtils.error("Failed to open profile folder: " + e.getMessage());
		}
	}

	private List<SuperSoftRowsWindow.Row> fontRows()
	{
		ArrayList<SuperSoftRowsWindow.Row> rows = new ArrayList<>();
		rows.add(SuperSoftRowsWindow.section("Fonts"));
		for(String availableFont : fonts)
			rows.add(SuperSoftRowsWindow.action(availableFont,
				() -> availableFont.equals(selectedFont),
				() -> selectedFont = availableFont));
		rows.add(SuperSoftRowsWindow.action("Load", () -> false,
			this::loadSelectedFont));
		rows.add(SuperSoftRowsWindow.action("Refresh", () -> false,
			this::refreshFonts));
		rows.add(SuperSoftRowsWindow.action("Open folder", () -> false,
			this::openFontsFolder));
		return rows;
	}

	private void refreshFonts()
	{
		fonts = WURST.getGuiPreferences().listFonts();
		String active = WURST.getGuiPreferences().getSelectedFont();
		selectedFont = fonts.contains(active) ? active
			: GuiPreferences.BUILTIN_FONT;
	}

	private void loadSelectedFont()
	{
		try
		{
			WURST.getGuiPreferences().selectFont(selectedFont);
			ChatUtils.message("Loaded font: " + selectedFont);
		}catch(RuntimeException e)
		{
			ChatUtils.error("Failed to load font: " + e.getMessage());
		}
	}

	private void openFontsFolder()
	{
		WURST.getGuiPreferences().listFonts();
		Util.getPlatform().openFile(
			WURST.getGuiPreferences().getFontsFolder().toFile());
	}

	private List<SuperSoftRowsWindow.Row> globalSettingsRows()
	{
		GuiPreferences preferences = WURST.getGuiPreferences();
		return List.of(
			SuperSoftRowsWindow.switchRow("Chinese",
				() -> !WURST.getOtfs().translationsOtf.getForceEnglish().isChecked(),
				() -> {
					var setting = WURST.getOtfs().translationsOtf.getForceEnglish();
					setting.setChecked(!setting.isChecked());
				}),
			SuperSoftRowsWindow.switchRow("Vape mode",
				preferences::isVapeMode,
				() -> ClickGuiScreens.setVapeMode(
					!preferences.isVapeMode())),
			SuperSoftRowsWindow.switchRow("Commands",
				preferences::isCommandsEnabled,
				() -> preferences.setCommandsEnabled(
					!preferences.isCommandsEnabled())),
			SuperSoftRowsWindow.switchRow("Font", preferences::isFontEnabled,
				() -> preferences.setFontEnabled(!preferences.isFontEnabled())),
			SuperSoftRowsWindow.switchRow("Middle-click friend",
				() -> WURST.getCmds().friendsCmd.getMiddleClickFriends().isChecked(),
				() -> {
					var setting = WURST.getCmds().friendsCmd.getMiddleClickFriends();
					setting.setChecked(!setting.isChecked());
				}),
			SuperSoftRowsWindow.action("Sort GUI", () -> false, this::sortWindows),
			SuperSoftRowsWindow.action("HUD editor", () -> false,
				() -> minecraft.setScreen(new HudEditorScreen(this))));
	}

	private List<SuperSoftRowsWindow.Row> mainRows()
	{
		ArrayList<SuperSoftRowsWindow.Row> rows = new ArrayList<>();
		for(String id : DEFAULT_WINDOW_IDS)
		{
			if(id.equals("main"))
				continue;
			String label = Character.toUpperCase(id.charAt(0)) + id.substring(1);
			rows.add(SuperSoftRowsWindow.toggle(label,
				() -> isWindowVisible(id), () -> toggleWindowVisibility(id)));
		}
		rows.add(SuperSoftRowsWindow.action("Netease Music", () -> false,
			() -> minecraft.setScreen(new NeteaseMusicScreen(this))));
		return rows;
	}

	private boolean isWindowVisible(String id)
	{
		SuperSoftFloatingWindow window = findDefaultWindow(id);
		return window != null && window.isVisible();
	}

	private void toggleWindowVisibility(String id)
	{
		SuperSoftFloatingWindow window = findDefaultWindow(id);
		if(window != null)
			window.setVisible(!window.isVisible());
	}

	private SuperSoftFloatingWindow findDefaultWindow(String id)
	{
		for(SuperSoftFloatingWindow window : windows)
			if(window.getId().equals(id))
				return window;
		return null;
	}

	private void sortWindows()
	{
		int referenceWidth = GAP * 9 + WINDOW_WIDTH * 8;
		if(uiWidth() >= referenceWidth)
		{
			place("visual", 0, START_Y);
			place("movement", 1, START_Y);
			place("client", 2, START_Y);
			SuperSoftFloatingWindow client = findDefaultWindow("client");
			place("combat", 2, START_Y + (client == null ? 80
				: client.totalHeight(maxBodyHeight())) + GAP);
			place("world", 3, START_Y);
			place("other", 4, START_Y);
			place("target", 5, START_Y);
			place("config", 6, START_Y);
			place("font", 7, START_Y);
			place("settings", 5, Math.max(START_Y,
				Math.round(uiHeight() * 0.58F)));
			place("main", 7, Math.max(START_Y,
				Math.round(uiHeight() * 0.49F)));
			return;
		}

		int columns = Math.max(1,
			(uiWidth() - GAP) / (WINDOW_WIDTH + GAP));
		int[] columnBottoms = new int[columns];
		java.util.Arrays.fill(columnBottoms, START_Y);
		for(String id : DEFAULT_WINDOW_IDS)
		{
			SuperSoftFloatingWindow window = findDefaultWindow(id);
			if(window == null)
				continue;
			int column = 0;
			for(int index = 1; index < columns; index++)
				if(columnBottoms[index] < columnBottoms[column])
					column = index;
			int x = GAP + column * (WINDOW_WIDTH + GAP);
			window.moveTo(x, columnBottoms[column], uiWidth(), uiHeight());
			columnBottoms[column] += window.totalHeight(maxBodyHeight()) + GAP;
		}
	}

	private void place(String id, int column, int y)
	{
		SuperSoftFloatingWindow window = findDefaultWindow(id);
		if(window != null)
			window.moveTo(GAP + column * (WINDOW_WIDTH + GAP), y, uiWidth(),
				uiHeight());
	}

	private static String stripJson(String name)
	{
		return name.toLowerCase(Locale.ROOT).endsWith(".json")
			? name.substring(0, name.length() - 5) : name;
	}

	private String getBindableCommand(Feature feature)
	{
		return feature.getPossibleKeybinds().stream().findFirst()
			.map(PossibleKeybind::getCommand).orElse(null);
	}

	private boolean isClickGuiBinding(int keyCode, int scanCode)
	{
		String command = getBindableCommand(WURST.getHax().clickGuiHack);
		if(command == null)
			return false;
		String boundKey = WURST.getKeybinds().getKeyForCommand(command);
		return boundKey != null && boundKey.equalsIgnoreCase(
			InputConstants.getKey(keyCode, scanCode).getName());
	}

	@Override
	public void beginBinding(Feature feature)
	{
		if(getBindableCommand(feature) != null)
			bindingFeature = feature;
	}

	@Override
	public boolean isBinding(Feature feature)
	{
		return bindingFeature == feature;
	}

	@Override
	public boolean isFavorite(Feature feature)
	{
		return WURST.getGuiPreferences().isVapeFavorite(feature.getName());
	}

	@Override
	public void toggleFavorite(Feature feature)
	{
		WURST.getGuiPreferences().toggleVapeFavorite(feature.getName());
	}

	@Override
	public boolean isHidden(Feature feature)
	{
		return WURST.getGuiPreferences().isVapeModuleHidden(feature.getName());
	}

	@Override
	public void toggleHidden(Feature feature)
	{
		WURST.getGuiPreferences().toggleVapeModuleHidden(feature.getName());
	}

	@Override
	public boolean isEditingHiddenModules()
	{
		return false;
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

	@Override
	public boolean usesSuperSoftTheme()
	{
		return true;
	}

	@Override
	public float renderScale()
	{
		return interfaceScale();
	}

	@Override
	public boolean isPauseScreen()
	{
		return false;
	}

	public boolean isWaitingForKeybind()
	{
		return bindingFeature != null || activeTextInput != null;
	}

	int defaultWindowCount()
	{
		return DEFAULT_WINDOW_IDS.size();
	}

	static List<String> defaultWindowIds()
	{
		return DEFAULT_WINDOW_IDS;
	}

	private int accentColor()
	{
		return SuperSoftTheme.ACCENT;
	}

	private float interfaceScale()
	{
		return 1;
	}

	private int uiWidth()
	{
		return Math.round(width / interfaceScale());
	}

	private int uiHeight()
	{
		return Math.round(height / interfaceScale());
	}

	private int maxBodyHeight()
	{
		return Math.max(80, Math.round(uiHeight() * 0.7F));
	}

	private record CategorySpec(String id, String title, GuiIcon icon,
		List<Feature> features)
	{}
}
