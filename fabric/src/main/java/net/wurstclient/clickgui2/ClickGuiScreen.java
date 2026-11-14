/*
 * OpaiBounce appearance adapted from snmwdd/OpaiBounce (GPL-3.0-or-later).
 * Existing Wurst category and utility actions are preserved.
 * See assets/wurstpenguin/opai/NOTICE.txt.
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
import net.wurstclient.clickgui2.animation.SmoothScroll;
import net.wurstclient.clickgui2.window.Window;
import net.wurstclient.hud2.nanovg.NanoVGUI;
import net.wurstclient.keybinds.PossibleKeybind;
import net.wurstclient.options.EnterProfileNameScreen;
import net.wurstclient.util.ChatUtils;
import net.wurstclient.util.json.JsonException;
import org.lwjgl.glfw.GLFW;

public final class ClickGuiScreen extends Screen
{
	private static final Minecraft MC = WurstClient.MC;
	private static final WurstClient WURST = WurstClient.INSTANCE;
	private static final int WINDOW_WIDTH = 152;
	private static final int HEADER_HEIGHT = 21;
	private static final float ROW_HEIGHT = 19.6F;
	private static final int WINDOW_GAP = 8;
	private static final int COMBAT_STACK_GAP = 26;
	private int startY = 35;
	private static final int SEARCH_WIDTH = 140;
	private static final int MAX_VISIBLE_ROWS = 18;
	private static final int COMBAT_VISIBLE_ROWS = 15;
	private static final int TEXT = OpaiStyle.TEXT;
	private static final int MUTED = OpaiStyle.MUTED;

	private final List<TemplateWindow> windows = new ArrayList<>();
	private final Map<String, TemplateWindow> namedWindows = new java.util.HashMap<>();
	private EditBox searchBox;
	private TemplateWindow draggedWindow;
	private int dragOffsetX;
	private int dragOffsetY;
	private boolean searchVisible;
	private int layoutStartX;
	private Feature bindingFeature;
    private final Map<Feature, OpaiSettings> inlineSettings = new java.util.IdentityHashMap<>();
    private final java.util.Set<Feature> expanded = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    private final Map<Feature, Float> expansion = new java.util.IdentityHashMap<>();
    private final Map<String, com.google.gson.JsonObject> panelState = new java.util.HashMap<>();
    private float canvasOffset;
    private int canvasWidth;
    private long openedAt, previousFrame;
    private float frameSeconds;
    private TemplateWindow hoverWindow;
    private boolean showingSearch, stateLoaded, panning;
    private float normalCanvasOffset;
    private final java.util.Set<String> savedExpanded = new java.util.HashSet<>();
    private Feature hoveredFeature;
    private int mouseCanvasX, mouseCanvasY;
    private Feature rippleFeature;
    private float rippleX, rippleY;
    private long rippleStarted, rippleReleased;
    private final Path layoutPath = WURST.getWurstFolder().resolve("clickgui-opai.json");

	public ClickGuiScreen()
	{
		super(Component.literal(WurstClient.CLIENT_NAME));
	}

	@Override
	protected void init()
	{
		WURST.getGui().init();
        snapshotPanels();
        openedAt = System.currentTimeMillis();
        previousFrame = 0;
        loadPanelState();
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
		snapshotPanels();
        blurValues();
        draggedWindow = null;
        windows.clear();
		namedWindows.clear();
		String query = searchBox == null ? "" : searchBox.getValue().trim();
        startY = Math.max(8, Math.round(height * 0.112F));
		List<Feature> features = FeatureMenuSupport.getAllFeatures();
        for(Feature feature : features)
            if(savedExpanded.contains(feature.getName())) {
                expanded.add(feature); expansion.putIfAbsent(feature, 1F);
            }
        savedExpanded.clear();
        if(!showingSearch && !query.isEmpty()) normalCanvasOffset = canvasOffset;
        if(showingSearch && query.isEmpty()) canvasOffset = normalCanvasOffset;
        showingSearch = !query.isEmpty();
		if(!query.isEmpty())
		{
			List<Feature> results =
				features.stream().filter(feature -> NavigatorSearch.matches(
                    List.of(feature.getName(), featureLabel(feature), feature.getSearchTags()), query))
                    .sorted(java.util.Comparator.comparing(ClickGuiScreen::featureLabel)).toList();
            canvasOffset = 0;
			add(new CategoryWindow(8, startY, "搜索", GuiIcon.SEARCH, results,
				MAX_VISIBLE_ROWS), "search");
			return;
		}

		Map<Category, List<Feature>> categorized = new EnumMap<>(Category.class);
		for(Category category : Category.values())
			categorized.put(category, new ArrayList<>());
		for(Feature feature : features)
			if(feature.getCategory() != null)
				categorized.get(feature.getCategory()).add(feature);

        layoutStartX = Math.min(30, Math.round(width * 0.0382F));
        canvasWidth = layoutStartX + 9 * (WINDOW_WIDTH + WINDOW_GAP) + 20;
        buildReferenceLayout(categorized);
        windows.sort(java.util.Comparator.comparingInt(window -> {
            String id = namedWindows.entrySet().stream().filter(entry -> entry.getValue() == window)
                .map(Map.Entry::getKey).findFirst().orElse("");
            var saved = panelState.get(id);
            return saved != null && saved.has("z") ? saved.get("z").getAsInt() : 0;
        }));
        canvasOffset = Mth.clamp(canvasOffset, 0, Math.max(0, canvasWidth - width));
	}

	private void buildReferenceLayout(Map<Category, List<Feature>> categorized)
	{
		add(new CategoryWindow(columnX(0), startY, "HUD", GuiIcon.CLIENT,
			categorized.get(Category.HUD), MAX_VISIBLE_ROWS), "hud");
		add(new CategoryWindow(columnX(1), startY, "视觉类", GuiIcon.RENDER,
			categorized.get(Category.RENDER), MAX_VISIBLE_ROWS), "visual");
		add(new CategoryWindow(columnX(2), startY, "移动类", GuiIcon.MOVEMENT,
			categorized.get(Category.MOVEMENT), MAX_VISIBLE_ROWS), "movement");
		CategoryWindow client = add(new CategoryWindow(columnX(3), startY,
			"客户端", GuiIcon.CLIENT, getInterfaceFeatures(), MAX_VISIBLE_ROWS),
			"client");
		add(new CategoryWindow(columnX(3),
			client.y2 + COMBAT_STACK_GAP, "战斗类", GuiIcon.COMBAT,
			categorized.get(Category.COMBAT), COMBAT_VISIBLE_ROWS), "combat");
		add(new CategoryWindow(columnX(4), startY, "世界类", GuiIcon.WORLD,
			combine(categorized, Category.BLOCKS, Category.ITEMS),
			MAX_VISIBLE_ROWS), "world");
		add(new CategoryWindow(columnX(5), startY, "其他类", GuiIcon.MISC,
			getOtherFeatures(categorized),
			MAX_VISIBLE_ROWS), "other");
		add(new TargetWindow(columnX(6), startY), "target");
		add(new ConfigWindow(columnX(7), startY), "config");
		add(new FontWindow(columnX(8), startY), "font");

		int settingsY = Mth.clamp(Math.round(height * 0.58F), startY,
			Math.max(startY, height - 90));
		int mainY = Mth.clamp(Math.round(height * 0.49F), startY,
			Math.max(startY, height - 150));
		add(new GlobalSettingsWindow(columnX(6), settingsY), "settings");
		add(new MainMenuWindow(columnX(8), mainY), "main");
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

	private <T extends TemplateWindow> T add(T value, String id)
	{
        TemplateWindow window = value;
		window.open = true;
        var saved = panelState.get(id);
        if(saved != null) {
            try { window.x1 = saved.get("x").getAsInt(); window.y1 = saved.get("y").getAsInt();
                window.open = saved.get("open").getAsBoolean(); window.scrollOffset = saved.get("scroll").getAsFloat();
                window.visible = !saved.has("visible") || saved.get("visible").getAsBoolean();
                window.smoothScroll.snapTo(window.scrollOffset);
            } catch(RuntimeException ignored) {}
        }
        window.reveal = window.open ? 1 : 0;
        window.clampToScreen();
		windows.add(window);
		namedWindows.put(id, window);
		return value;
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
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks)
    {
        long now = System.currentTimeMillis();
        frameSeconds = previousFrame == 0 ? 0.016F : Math.min(0.1F, (now - previousFrame) / 1000F);
        previousFrame = now;
        hoveredFeature = null;
        for(Feature feature : inlineSettings.keySet()) {
            float target = expanded.contains(feature) ? 1 : 0;
            expansion.put(feature, approach(expansion.getOrDefault(feature, target), target));
        }
        for(TemplateWindow window : windows) {
            window.reveal = approach(window.reveal, window.open ? 1 : 0);
            window.updateHeight();
        }
        int localX = Math.round(mouseX + canvasOffset);
        mouseCanvasX = localX; mouseCanvasY = mouseY;
        hoverWindow = null;
        for(int i = windows.size() - 1; i >= 0; i--)
            if(windows.get(i).contains(localX, mouseY)) { hoverWindow = windows.get(i); break; }
        boolean ready = now - openedAt >= 320;
        NanoVGUI.pass(graphics, () -> {
            if(MC.level == null) net.wurstclient.gui.MainMenuBackground.render(width, height, 255);
            NanoVGUI.push();
            NanoVGUI.context().scale(0, 0, width, height, OpaiStyle.openingScale(now - openedAt));
            NanoVGUI.context().setAlpha(now - openedAt >= 320 ? 1 : (float)(1 - Math.exp(-30 * (now - openedAt) / 1000.0)));
            NanoVGUI.translate(-canvasOffset, 0);
            for(TemplateWindow window : windows) window.render(graphics, ready ? localX : -10000, ready ? mouseY : -10000, partialTicks);
            NanoVGUI.pop();
            renderToolbar(graphics, mouseX, mouseY);
            if(hoveredFeature != null && !searchVisible && draggedWindow == null) renderDescription(mouseX, mouseY);
        });
        if(searchVisible) searchBox.render(graphics, mouseX, mouseY, partialTicks);
    }

    private void renderToolbar(GuiGraphics graphics, int mouseX, int mouseY)
    {
        if(!showingSearch && canvasWidth > width) {
            float track = width - 46;
            float thumb = Math.max(24, track * width / canvasWidth);
            float left = 6 + canvasOffset / (canvasWidth - width) * (track - thumb);
            NanoVGUI.fillRounded(6, height - 5, width - 40, height - 3, 1, 0x3356413E);
            NanoVGUI.fillRounded(left, height - 5, left + thumb, height - 3, 1, 0x999E8D8B);
        }
        NanoVGUI.fillRounded(width - 31, height - 31, width - 7, height - 7, 5, OpaiStyle.ACCENT);
        float px = width - 24, py = height - 15;
        NanoVGUI.line(px, py, px + 8, py - 8, 2.5F, OpaiStyle.INK);
        NanoVGUI.line(px + 1.5F, py + 1.5F, px + 9.5F, py - 6.5F, 2.5F, OpaiStyle.INK);
        NanoVGUI.line(px - 1, py + 3, px, py, 1, OpaiStyle.INK);
        NanoVGUI.line(px - 1, py + 3, px + 1.5F, py + 1.5F, 1, OpaiStyle.INK);
        if(searchVisible)
        {
            OpaiStyle.field(width - 158, 8, 148, 21, searchBox.isFocused());
            searchBox.setX(width - 152); searchBox.setY(14); searchBox.setWidth(136);
            searchBox.setTextColor(TEXT);

        }
    }

    private float approach(float current, float target)
    {
        float value = target + (current - target) * (float)Math.exp(-40 * frameSeconds);
        return Math.abs(value - target) < 0.0005F ? target : value;
    }

    private void renderDescription(int mouseX, int mouseY)
    {
        String description = NanoVGUI.stripCodes(FeatureMenuSupport.getOneLineDescription(hoveredFeature));
        List<String> lines = new ArrayList<>();
        while(!description.isEmpty() && lines.size() < 8)
        {
            String line = NanoVGUI.plainSubstrByWidth(description, 152, 7);
            if(line.isEmpty()) break;
            lines.add(line); description = description.substring(line.length());
        }
        float h = lines.size() * 10 + 8;
        float x = Math.max(4, Math.min(width - 164, mouseX + 6));
        float y = Math.max(4, Math.min(height - h - 4, mouseY - h / 2));
        NanoVGUI.fillRounded(x, y, x + 160, y + h, 4.5F, 0xEB272425);
        NanoVGUI.outlineRounded(x, y, x + 160, y + h, 4.5F, 0.5F, 0x66716665);
        for(int i = 0; i < lines.size(); i++) NanoVGUI.text(lines.get(i), x + 4, y + 4 + i * 10, TEXT, 7);
    }

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button)
	{
        if(System.currentTimeMillis() - openedAt < 320) return true;
        if(button == 0 && !showingSearch && mouseY >= height - 9 && mouseX >= 6 && mouseX < width - 40) {
            panning = true; moveCanvas(mouseX); return true;
        }
        if(button == 0 && mouseX >= width - 31 && mouseX < width - 7 && mouseY >= height - 31 && mouseY < height - 7) {
            MC.setScreen(new net.wurstclient.hud2.GuiEditHUD()); return true;
        }
        if(searchVisible && searchBox.mouseClicked(mouseX, mouseY, button)) { blurValues(); return true; }
        searchBox.setFocused(false);
        blurValues();
        mouseX += canvasOffset;
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
			}
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button,
		double dragX, double dragY)
	{
        if(button == 0 && panning) { moveCanvas(mouseX); return true; }
		if(button != GLFW.GLFW_MOUSE_BUTTON_LEFT || draggedWindow == null)
			return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        int x = (int)(mouseX + canvasOffset) - dragOffsetX;
        int y = (int)mouseY - dragOffsetY;
        if(!hasShiftDown()) { x = Math.round(x / 5F) * 5; y = Math.round(y / 5F) * 5; }
        draggedWindow.moveTo(x, y);
		return true;
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button)
	{
        WURST.getGui().handleMouseRelease(mouseX, mouseY, button);
        inlineSettings.values().forEach(OpaiSettings::release);
        rippleReleased = System.currentTimeMillis();
        panning = false;
		if(button == GLFW.GLFW_MOUSE_BUTTON_LEFT && draggedWindow != null)
		{

			draggedWindow = null;
			return true;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double delta)
	{
        inlineSettings.values().forEach(OpaiSettings::release);
        if(hasShiftDown()) { pan(delta > 0 ? -1 : 1); return true; }
        mouseX += canvasOffset;
        for(int i = windows.size() - 1; i >= 0; i--) {
            TemplateWindow panel = windows.get(i);
            if(panel.contains(mouseX, mouseY)) { panel.scroll(mouseX, mouseY, delta); return true; }
        }
		pan(delta > 0 ? -1 : 1); return true;
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
        for(OpaiSettings settings : inlineSettings.values())
            if(settings.editing()) return settings.key(keyCode, modifiers);
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
		if(searchVisible) return searchBox.keyPressed(keyCode, scanCode, modifiers);
        if(keyCode == GLFW.GLFW_KEY_LEFT) { pan(-1); return true; }
        if(keyCode == GLFW.GLFW_KEY_RIGHT) { pan(1); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
	}

    @Override
    public boolean charTyped(char character, int modifiers)
    {
        for(OpaiSettings settings : inlineSettings.values()) if(settings.editing()) return settings.character(character);
        return searchVisible && searchBox.charTyped(character, modifiers);
    }

    private void moveCanvas(double mouseX)
    {
        float track = width - 46;
        float thumb = Math.max(24, track * width / Math.max(width, canvasWidth));
        canvasOffset = (float)Mth.clamp((mouseX - 6 - thumb / 2) / Math.max(1, track - thumb), 0, 1) * Math.max(0, canvasWidth - width);
        blurValues();
    }

    private void pan(int direction)
    {
        canvasOffset = Mth.clamp(canvasOffset + direction * (WINDOW_WIDTH + WINDOW_GAP), 0, Math.max(0, canvasWidth - width));
        blurValues();
    }

    private void blurValues() { inlineSettings.values().forEach(OpaiSettings::blur); }

    private void loadPanelState()
    {
        if(stateLoaded) return;
        stateLoaded = true;
        try {
            if(!java.nio.file.Files.isRegularFile(layoutPath)) return;
            var root = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(layoutPath)).getAsJsonObject();
            root.entrySet().forEach(entry -> { if(entry.getValue().isJsonObject()) panelState.put(entry.getKey(), entry.getValue().getAsJsonObject()); });
            if(root.has("_offset")) canvasOffset = Math.max(0, root.get("_offset").getAsFloat());
            if(root.has("_expanded")) root.getAsJsonArray("_expanded").forEach(value -> savedExpanded.add(value.getAsString()));
        } catch(IOException | RuntimeException e) { System.err.println("[Opai ClickGUI] Could not load layout: " + e.getMessage()); }
    }

    private void snapshotPanels()
    {
        if(showingSearch) return;
        namedWindows.forEach((id, panel) -> {
            var value = new com.google.gson.JsonObject();
            value.addProperty("x", panel.x1); value.addProperty("y", panel.y1);
            value.addProperty("open", panel.open); value.addProperty("scroll", panel.scrollOffset);
            value.addProperty("visible", panel.visible); value.addProperty("z", windows.indexOf(panel));
            panelState.put(id, value);
        });
    }

    @Override
    public void removed()
    {
        blurValues();
        draggedWindow = null;
        snapshotPanels();
        var root = new com.google.gson.JsonObject();
        panelState.forEach(root::add);
        root.addProperty("_offset", showingSearch ? normalCanvasOffset : canvasOffset);
        var modules = new com.google.gson.JsonArray();
        expanded.forEach(feature -> modules.add(feature.getName()));
        root.add("_expanded", modules);
        try {
            java.nio.file.Files.createDirectories(layoutPath.getParent());
            java.nio.file.Files.writeString(layoutPath, root.toString());
        } catch(IOException e) { System.err.println("[Opai ClickGUI] Could not save layout: " + e.getMessage()); }
        super.removed();
    }

    private static String featureLabel(Feature feature)
    {
        if(feature instanceof net.wurstclient.hack.Hack) {
            String key = "hack.name." + feature.getName().toLowerCase(Locale.ROOT);
            String translated = WURST.translate(key);
            return translated.equals(key) ? feature.getName() : translated;
        }
        return feature.getDisplayName();
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
		return bindingFeature != null || searchVisible && searchBox.isFocused()
            || inlineSettings.values().stream().anyMatch(OpaiSettings::editing);
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
        for(TemplateWindow window : windows) window.resetPosition();
        canvasOffset = 0;
	}

	private abstract class TemplateWindow extends Window
	{
		protected final int windowWidth;
		protected boolean open;
		private boolean visible = true;
		private float scrollOffset;
        private float reveal = 1;
        private OpaiPanelLayout layout;
		private final SmoothScroll smoothScroll = new SmoothScroll();
		private int homeX;
		private int homeY;

		private TemplateWindow(int x, int y, int windowWidth, String title,
			GuiIcon icon, int maxVisibleRows)
		{
			super(x, y, x + windowWidth, y + HEADER_HEIGHT, title,
				ItemStack.EMPTY);
			this.windowWidth = windowWidth;
			homeX = x;
			homeY = y;
		}

		protected abstract int rowCount();

		protected abstract void renderRow(GuiGraphics graphics, int rowIndex,
			float rowY, boolean hovering);

		protected abstract boolean clickRow(int rowIndex, int visualRow,
			double mouseX, int button);

        protected float rowHeight(int index) { return ROW_HEIGHT; }
        protected float totalHeight() { return layout.total(); }
        private float bodyHeight() { return Math.min(totalHeight(), Math.max(20, Math.min(320.5F, height - y1 - HEADER_HEIGHT - 28))); }
        private void toggleOpen() { open = !open; blurValues(); }
        protected void updateHeight() {
            layout = new OpaiPanelLayout(rowCount(), this::rowHeight);
            scrollOffset = layout.clampScroll(scrollOffset, bodyHeight());
            if(smoothScroll.getCurrent() > layout.clampScroll(smoothScroll.getCurrent(), bodyHeight())) smoothScroll.snapTo(scrollOffset);
            smoothScroll.setTarget(scrollOffset);
            y2 = y1 + HEADER_HEIGHT + Math.round(bodyHeight() * reveal);
            x2 = x1 + windowWidth;
        }
        private void resetPosition() { moveTo(homeX, homeY); }
        private void moveTo(int x, int y) {
            x1 = Mth.clamp(x, 0, Math.max(0, canvasWidth - windowWidth));
            y1 = Mth.clamp(y, 0, Math.max(0, height - HEADER_HEIGHT - 24)); updateHeight();
        }
        private void clampToScreen() { moveTo(x1, y1); }
        private boolean contains(double mouseX, double mouseY) {
            return visible && mouseX >= x1 && mouseX < x2 && mouseY >= y1 && mouseY < y2;
        }
        private boolean isHeaderOver(double mouseX, double mouseY) { return contains(mouseX, mouseY) && mouseY < y1 + HEADER_HEIGHT; }
        private boolean isBodyOver(double mouseX, double mouseY) { return open && contains(mouseX, mouseY) && mouseY >= y1 + HEADER_HEIGHT; }
        private boolean clickBody(double mouseX, double mouseY, int button) {
            int index = layout.hit(mouseY - y1 - HEADER_HEIGHT, smoothScroll.getCurrent(), y2 - y1 - HEADER_HEIGHT);
            if(index < 0) return true;
            float rowY = y1 + HEADER_HEIGHT - smoothScroll.getCurrent() + layout.start(index);
            if(this instanceof CategoryWindow category) return category.clickFeature(index, mouseX, mouseY, rowY, button);
            return clickRow(index, index, mouseX, button);
        }
        private boolean scroll(double mouseX, double mouseY, double delta) {
            if(!isBodyOver(mouseX, mouseY)) return false;
            scrollOffset = layout.clampScroll(scrollOffset - (float)(delta * 30), bodyHeight());
            smoothScroll.setTarget(scrollOffset); return true;
        }
        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            if(!visible) return;
            updateHeight(); selected = isTopVisibleWindow(this);
            NanoVGUI.fillRounded(x1, y1, x2, y2, 7, OpaiStyle.BODY);
            NanoVGUI.fillRounded(x1, y1, x2, y1 + HEADER_HEIGHT, 7, OpaiStyle.HEADER);
            if(reveal > 0) NanoVGUI.fill(x1, y1 + HEADER_HEIGHT / 2F, x2, y1 + HEADER_HEIGHT, OpaiStyle.HEADER);
            NanoVGUI.text(title, x1 + 10, y1 + 6, TEXT, 9.2F);
            if(reveal <= 0) return;
            NanoVGUI.pushScissor(x1, y1 + HEADER_HEIGHT, windowWidth, y2 - y1 - HEADER_HEIGHT - 3);
            float origin = y1 + HEADER_HEIGHT - smoothScroll.update();
            for(int index = 0; index < rowCount(); index++) {
                float rowY = origin + layout.start(index);
                float h = layout.height(index);
                if(rowY + h >= y1 + HEADER_HEIGHT && rowY < y2) {
                    boolean hover = hoverWindow == this && mouseX >= x1 && mouseX < x2 && mouseY >= Math.max(rowY, y1 + HEADER_HEIGHT) && mouseY < Math.min(rowY + ROW_HEIGHT, y2);
                    renderRow(graphics, index, rowY, hover);
                }
            }
            NanoVGUI.popScissor();
            if(totalHeight() > bodyHeight()) {
                float thumb = bodyHeight() * bodyHeight() / totalHeight();
                float offset = smoothScroll.getCurrent() / (totalHeight() - bodyHeight());
                float top = y1 + HEADER_HEIGHT + (bodyHeight() - thumb) * offset;
                NanoVGUI.fillRounded(x2 - 1.5F, top, x2 - 0.5F, top + thumb, 0.5F, 0x669E8D8B);
            }
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
        protected float rowHeight(int index) {
            Feature feature = features.get(index);
            float amount = expansion.getOrDefault(feature, expanded.contains(feature) ? 1F : 0F);
            if(amount <= 0) return ROW_HEIGHT;
            return ROW_HEIGHT + inlineSettings.computeIfAbsent(feature, OpaiSettings::new).height() * amount;
        }

        @Override
        protected void renderRow(GuiGraphics graphics, int index, float rowY, boolean hovering) {
            Feature feature = features.get(index);
            float amount = expansion.getOrDefault(feature, 0F);
            if(feature.isEnabled()) NanoVGUI.fill(x1, rowY, x2, rowY + ROW_HEIGHT, OpaiStyle.ENABLED);
            else if(hovering) NanoVGUI.fill(x1, rowY, x2, rowY + ROW_HEIGHT, 0x0CEDE0DE);
            if(rippleFeature == feature) {
                long now = System.currentTimeMillis();
                float progress = Math.min(1, (now - rippleStarted) / 800F);
                float fade = rippleReleased == 0 ? 1 : Math.max(0, 1 - (now - rippleReleased) / 240F);
                NanoVGUI.pushScissor(x1, rowY, windowWidth, ROW_HEIGHT);
                NanoVGUI.circle(rippleX, rowY + rippleY, windowWidth * (1 - (float)Math.pow(1 - progress, 3)), ((int)(fade * 76) << 24) | 0xFFFFFF);
                NanoVGUI.popScissor();
            }
            OpaiStyle.text(bindingFeature == feature ? "按下按键…" : featureLabel(feature), x1 + 10, rowY + 6, windowWidth - 20, feature.isEnabled() ? OpaiStyle.INK : TEXT);
            if(hovering) hoveredFeature = feature;
            if(amount > 0) {
                float settingsBottom = rowY + rowHeight(index);
                NanoVGUI.fill(x1, rowY + ROW_HEIGHT, x2, settingsBottom, OpaiStyle.SETTINGS);
                NanoVGUI.pushScissor(x1, rowY + ROW_HEIGHT, windowWidth, settingsBottom - rowY - ROW_HEIGHT);
                inlineSettings.get(feature).render(graphics, x1 + 10, rowY + ROW_HEIGHT, windowWidth - 20,
                    Math.max(y1 + HEADER_HEIGHT, rowY + ROW_HEIGHT), Math.min(y2 - 3, settingsBottom), mouseCanvasX, mouseCanvasY);
                NanoVGUI.popScissor();
            }
        }

        private boolean clickFeature(int index, double mouseX, double mouseY, float rowY, int button) {
            Feature feature = features.get(index);
            if(mouseY >= rowY + ROW_HEIGHT) {
                if(expanded.contains(feature)) inlineSettings.computeIfAbsent(feature, OpaiSettings::new).click(mouseX, mouseY, button);
                return true;
            }
            if(button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) { if(getBindableCommand(feature) != null) bindingFeature = feature; }
            else if(button == GLFW.GLFW_MOUSE_BUTTON_RIGHT || feature.getPrimaryAction().isEmpty()) {
                if(!feature.getSettings().isEmpty()) {
                    expansion.putIfAbsent(feature, 0F);
                    if(!expanded.remove(feature)) expanded.add(feature);
                    else inlineSettings.computeIfAbsent(feature, OpaiSettings::new).blur();
                    inlineSettings.computeIfAbsent(feature, OpaiSettings::new);
                }
            }else if(button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                rippleFeature = feature; rippleX = (float)mouseX; rippleY = (float)(mouseY - rowY);
                rippleStarted = System.currentTimeMillis(); rippleReleased = 0;
                FeatureMenuSupport.runPrimaryAction(feature);
            }
            return true;
        }
        @Override
        protected boolean clickRow(int index, int row, double mouseX, int button) { return false; }
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
		protected void renderRow(GuiGraphics graphics, int index, float rowY,
			boolean hovering)
		{
            PanelRow row = rows().get(index);
            boolean active = row.active.getAsBoolean();
            boolean selectedRow = row.kind != RowKind.SWITCH && active;
            if(row.kind == RowKind.SECTION) NanoVGUI.fill(x1, rowY, x2, rowY + ROW_HEIGHT, OpaiStyle.SETTINGS);
            else if(selectedRow) NanoVGUI.fill(x1, rowY, x2, rowY + ROW_HEIGHT, OpaiStyle.ENABLED);
            else if(hovering && row.kind != RowKind.DISABLED) NanoVGUI.fill(x1, rowY, x2, rowY + ROW_HEIGHT, 0x0CEDE0DE);
            OpaiStyle.text(row.label.get(), x1 + 10, rowY + 6,
                windowWidth - (row.kind == RowKind.SWITCH ? 49 : 20),
                selectedRow ? OpaiStyle.INK : hovering ? TEXT : MUTED);
            if(row.kind == RowKind.SWITCH) OpaiStyle.toggle(x2 - 36, rowY + 1.8F, active);
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
					() -> MC.setScreen(new net.wurstclient.hud2.GuiEditHUD())));
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
                actionRow("HUD", () -> isWindowVisible("hud"), () -> toggleWindowVisibility("hud")),
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
