/*
 * Rise 6.1.30 standard ClickGUI port for WurstB+ Plus Navigator.
 * Copyright (c) 2025 Penguin
 */
package net.wurstclient.clickgui2;

import com.mojang.blaze3d.systems.RenderSystem;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.wurstclient.Category;
import net.wurstclient.Feature;
import net.wurstclient.WurstClient;
import net.wurstclient.clickgui2.theme.FlatTheme;
import org.lwjgl.glfw.GLFW;

public final class NavigatorScreen extends Screen
{
	private static final Minecraft MC = WurstClient.MC;
	private static final WurstClient WURST = WurstClient.INSTANCE;
	private static final int MAX_PANEL_WIDTH = 540;
	private static final int MAX_PANEL_HEIGHT = 400;
	private static final int MODULE_GAP = 7;
	private static final int SEARCH_CONTENT_OFFSET = 35;
	private static final int CATEGORY_TRANSITION_MS = 200;
	private static final float SEARCH_TEXT_SCALE = 0.78F;

	private static final String AUTO_SEARCH_CHARS =
		"abcdefghijklmnopqrstuvwxyz1234567890 ";

	private static final String[] CATEGORY_NAMES = {
		"\u641c\u7d22", "\u65b9\u5757", "\u79fb\u52a8", "\u6218\u6597",
		"\u6e32\u67d3", "\u804a\u5929", "\u5a31\u4e50", "\u7269\u54c1",
		"\u5176\u4ed6", "\u754c\u9762"};
	private static final GuiIcon[] CATEGORY_ICONS = {GuiIcon.SEARCH,
		GuiIcon.WORLD, GuiIcon.MOVEMENT, GuiIcon.COMBAT, GuiIcon.RENDER,
		GuiIcon.PLAYER, GuiIcon.FUN, GuiIcon.BOOK, GuiIcon.MISC,
		GuiIcon.CLIENT};

	private final List<Feature> visibleFeatures = new ArrayList<>();
	private List<Feature> previousFeatures = List.of();
	private final Map<Feature, RiseModuleComponent> moduleComponents =
		new IdentityHashMap<>();
	private final List<RiseSidebarCategory> categories = new ArrayList<>();
	private final RiseScrollState moduleScroll = new RiseScrollState();
	private final RiseAnimation scaleAnimation =
		new RiseAnimation(RiseAnimation.Easing.EASE_OUT_EXPO, 300);
	private final RiseAnimation opacityAnimation =
		new RiseAnimation(RiseAnimation.Easing.EASE_OUT_EXPO, 300);
	private final RiseAnimation searchOpacity =
		new RiseAnimation(RiseAnimation.Easing.LINEAR, 64);
	private final RiseAnimation sidebarOpacity =
		new RiseAnimation(RiseAnimation.Easing.LINEAR, 128);

	private ScaledEditBox searchBox;
	private int selectedCategory;
	private int previousCategory;
	private int categoryScroll;
	private long categoryChangedAt = Long.MIN_VALUE;
	private int panelX = -1;
	private int panelY = -1;
	private int panelWidth;
	private int panelHeight;
	private int sidebarWidth;
	private int contentHeight;
	private boolean initialized;
	private boolean closing;
	private boolean suppressSearchRefresh;
	private boolean typedWhileSearchOpen;
	private boolean dragging;
	private int dragOffsetX;
	private int dragOffsetY;
	private float animationProgress;

	public NavigatorScreen()
	{
		super(Component.literal(WurstClient.CLIENT_NAME));
		for(int index = 0; index < CATEGORY_NAMES.length; index++)
			categories.add(new RiseSidebarCategory(CATEGORY_NAMES[index],
				CATEGORY_ICONS[index]));
	}

	@Override
	protected void init()
	{
		updateDimensions();
		GuiIcon.configureFiltering(MC);
		if(!initialized || panelX < 0 || panelY < 0
			|| panelX + panelWidth > width || panelY + panelHeight > height)
		{
			panelX = (width - panelWidth) / 2;
			panelY = (height - panelHeight) / 2;
		}

		String query = searchBox == null ? "" : searchBox.getValue();
		searchBox = new ScaledEditBox(MC.font, 0, 0, 150, 16,
			RiseFont.text("\u5f00\u59cb\u8f93\u5165\u4ee5\u641c\u7d22..."),
			SEARCH_TEXT_SCALE);
		searchBox.setBordered(false);
		searchBox.setMaxLength(80);
		searchBox.setHint(RiseFont.text("\u5f00\u59cb\u8f93\u5165\u4ee5\u641c\u7d22..."));
		searchBox.setFormatter((text, cursor) -> RiseFont.sequence(text));
		searchBox.setTextColor(RiseColors.TEXT.argb());
		searchBox.setTextColorUneditable(RiseColors.TRINARY_TEXT.argb());
		searchBox.setValue(query);
		searchBox.setResponder(ignored -> {
			if(!suppressSearchRefresh)
			{
				moduleScroll.reset();
				refreshFeatures(false);
			}
		});
		addRenderableWidget(searchBox);
		WURST.getGui().initEmbedded();
		refreshFeatures(false);
		if(!initialized)
		{
			scaleAnimation.setValue(0);
			opacityAnimation.setValue(0);
			searchOpacity.setValue(1);
			previousCategory = selectedCategory;
			previousFeatures = List.copyOf(visibleFeatures);
			categoryChangedAt = System.currentTimeMillis() - 150;
			initialized = true;
		}
	}

	private void updateDimensions()
	{
		int targetWidth = Math.round(width * 0.415F);
		int targetHeight = Math.round(height * 0.555F);
		panelWidth = Math.min(Math.max(1, width - 24),
			Mth.clamp(targetWidth, 180, MAX_PANEL_WIDTH));
		panelHeight = Math.min(Math.max(1, height - 24),
			Mth.clamp(targetHeight, 140, MAX_PANEL_HEIGHT));
		sidebarWidth = Mth.clamp(Math.round(panelWidth * 0.25F), 48, 122);
		panelX = Mth.clamp(panelX, 0, Math.max(0, width - panelWidth));
		panelY = Mth.clamp(panelY, 0, Math.max(0, height - panelHeight));
	}

	private void refreshFeatures(boolean categoryChange)
	{
		if(categoryChange)
		{
			previousFeatures = List.copyOf(visibleFeatures);
			categoryChangedAt = System.currentTimeMillis();
			moduleScroll.reset();
		}

		String query = searchBox == null ? "" : searchBox.getValue().trim();
		visibleFeatures.clear();
		if(selectedCategory == 0)
		{
			if(query.isEmpty())
				visibleFeatures.addAll(FeatureMenuSupport.getAllFeatures());
			else
				visibleFeatures.addAll(searchRiseFeatures(query));
		}else if(selectedCategory <= Category.values().length)
		{
			Category category = Category.values()[selectedCategory - 1];
			for(Feature feature : FeatureMenuSupport.getAllFeatures())
				if(feature.getCategory() == category)
					visibleFeatures.add(feature);
		}else
			visibleFeatures.addAll(getInterfaceFeatures());

		if(selectedCategory != 0 || query.isEmpty())
			visibleFeatures.sort(Comparator.comparing(Feature::getDisplayName,
				String.CASE_INSENSITIVE_ORDER));
	}

	private List<Feature> searchRiseFeatures(String query)
	{
		List<Feature> sorted = new ArrayList<>(FeatureMenuSupport.getAllFeatures());
		Collator collator = Collator.getInstance();
		sorted.sort((first, second) -> collator.compare(first.getDisplayName(),
			second.getDisplayName()));
		String normalized = query.toLowerCase(Locale.ROOT);
		List<String> words = new ArrayList<>(List.of(normalized.split(" ")));
		words.add(normalized.replace(" ", ""));
		LinkedHashSet<Feature> matches = new LinkedHashSet<>();
		for(String word : words)
			for(Feature feature : sorted)
			{
				String needle = word.replace(" ", "");
				boolean found = List.of(feature.getName(),
					feature.getDisplayName(), feature.getSearchTags()).stream()
					.map(alias -> alias.toLowerCase(Locale.ROOT)
						.replace(" ", "").replace("\u00a7", ""))
					.anyMatch(alias -> alias.contains(needle));
				if(found)
					matches.add(feature);
			}
		return List.copyOf(matches);
	}

	private void switchCategory(int category)
	{
		if(category == selectedCategory)
			return;
		previousCategory = selectedCategory;
		selectedCategory = category;
		searchBox.setFocused(false);
		if(category == 0)
			typedWhileSearchOpen = false;
		refreshFeatures(true);
	}

	private List<Feature> getInterfaceFeatures()
	{
		return List.of(WURST.getHax().clickGuiHack,
			WURST.getHax().navigatorHack, WURST.getOtfs().wurstLogoOtf,
			WURST.getOtfs().hackListOtf, WURST.getOtfs().keybindManagerOtf,
			WURST.getOtfs().translationsOtf);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY,
		float partialTicks)
	{
		updateDimensions();
		WURST.getGui().updateColors();
		animationProgress = scaleAnimation.run(closing ? 0 : 1);
		float opacity = opacityAnimation.run(closing ? 0 : 1);
		if(closing && animationProgress <= 0.001F)
		{
			MC.setScreen(null);
			return;
		}

		float scale = Math.max(0.01F, animationProgress);
		int localMouseX = (int)Math.round(unscaleX(mouseX, scale));
		int localMouseY = (int)Math.round(unscaleY(mouseY, scale));
		boolean clip = true;

		graphics.pose().pushPose();
		applyScale(graphics, scale);
		graphics.flush();
		RenderSystem.setShaderColor(1, 1, 1, opacity);
		if(animationProgress > 0.993F)
			RiseShadow.draw(graphics, panelX, panelY,
				panelX + panelWidth, panelY + panelHeight, 12, 18,
				0x1E000000);
		FlatUiRenderer.fill(graphics, panelX, panelY, panelX + panelWidth,
			panelY + panelHeight, 12, RiseColors.BACKGROUND.argb());
		graphics.enableScissor(panelX + 1, panelY + 1,
			panelX + panelWidth - 1, panelY + panelHeight - 1);
		renderSidebarArea(graphics, localMouseX, localMouseY);
		moduleScroll.update();
		int renderingCategory = transitionCategory();
		List<Feature> renderingFeatures = transitionFeatures();
		if(renderingCategory == 0)
			renderSearchHeader(graphics, localMouseX, localMouseY, partialTicks,
				clip);
		else
			searchBox.visible = false;
		renderModules(graphics, renderingFeatures, renderingCategory == 0,
			localMouseX, localMouseY, partialTicks, clip);
		renderCategoryTransition(graphics);
		WURST.getGui().render(graphics, localMouseX, localMouseY, partialTicks);
		graphics.disableScissor();
		graphics.flush();
		RenderSystem.setShaderColor(1, 1, 1, 1);
		graphics.pose().popPose();
	}

	private void renderSidebarArea(GuiGraphics graphics, int mouseX, int mouseY)
	{
		graphics.flush();
		float[] previousColor = RenderSystem.getShaderColor().clone();
		float opacity = sidebarOpacity.run(1);
		RenderSystem.setShaderColor(previousColor[0], previousColor[1],
			previousColor[2], previousColor[3] * opacity);
		FlatUiRenderer.fill(graphics, panelX + 1, panelY + 1,
			panelX + sidebarWidth, panelY + panelHeight - 1, 11,
			RiseColors.SECONDARY.argb());
		renderSidebar(graphics, mouseX, mouseY);
		graphics.flush();
		RenderSystem.setShaderColor(previousColor[0], previousColor[1],
			previousColor[2], previousColor[3]);
	}

	private void renderSidebar(GuiGraphics graphics, int mouseX, int mouseY)
	{
		Font font = MC.font;
		String brand = "WurstB+";
		float versionScale = 0.55F;
		int brandWidth = RiseFont.width(font, brand);
		float versionWidth = RiseFont.width(font, WurstClient.VERSION)
			* versionScale;
		float brandScale = Math.min(1.05F, (sidebarWidth - 22F - versionWidth)
			/ Math.max(1, brandWidth));
		graphics.pose().pushPose();
		graphics.pose().translate(panelX + 14, panelY + 13, 0);
		graphics.pose().scale(brandScale, brandScale, 1);
		RiseFont.draw(graphics, font, brand, 0, 0, RiseColors.TEXT.argb());
		graphics.pose().popPose();
		graphics.pose().pushPose();
		graphics.pose().translate(panelX + 16 + brandWidth * brandScale,
			panelY + 11, 0);
		graphics.pose().scale(versionScale, versionScale, 1);
		RiseFont.draw(graphics, font, WurstClient.VERSION, 0, 0,
			accentColor());
		graphics.pose().popPose();

		int top = panelY + 38;
		int bottom = panelY + panelHeight - 24;
		int visibleRows = Math.max(1,
			(bottom - top) / RiseSidebarCategory.HEIGHT);
		categoryScroll = Mth.clamp(categoryScroll, 0,
			Math.max(0, CATEGORY_NAMES.length - visibleRows));
		if(animationProgress >= 0.995F)
			graphics.enableScissor(panelX + 5, top, panelX + sidebarWidth - 4,
				bottom);
		for(int row = 0; row < visibleRows; row++)
		{
			int index = row + categoryScroll;
			if(index >= CATEGORY_NAMES.length)
				break;
			int y = top + row * RiseSidebarCategory.HEIGHT;
			boolean hovering = inside(mouseX, mouseY, panelX + 9, y,
				panelX + sidebarWidth - 7, y + RiseSidebarCategory.HEIGHT);
			categories.get(index).render(graphics, font, panelX + 9, y,
				sidebarWidth - 16, selectedCategory == index, hovering,
				accentColor());
		}
		if(animationProgress >= 0.995F)
			graphics.disableScissor();

	}

	private void renderSearchHeader(GuiGraphics graphics, int mouseX, int mouseY,
		float partialTicks, boolean clip)
	{
		float opacity = searchOpacity.run(moduleScroll.target() < 0 ? 0 : 1);
		searchBox.setTextColor(withAlpha(RiseColors.TEXT.argb(), opacity));
		searchBox.setTextColorUneditable(
			withAlpha(RiseColors.TRINARY_TEXT.argb(), opacity));
		int top = panelY + 9 + (int)Math.round(moduleScroll.scroll());
		int center = contentLeft() + (contentRight() - contentLeft()) / 2;
		searchBox.setX(center - 75);
		searchBox.setY(top);
		searchBox.setWidth(150);
		searchBox.visible = top + 16 >= viewportTop() && top < listBottom();
		if(searchBox.visible)
		{
			if(clip)
				graphics.enableScissor(contentLeft() + 1, viewportTop(),
					contentRight() - 1, listBottom());
			searchBox.render(graphics, mouseX, mouseY, partialTicks);
			if(clip)
				graphics.disableScissor();
		}
	}

	private void renderModules(GuiGraphics graphics, List<Feature> features,
		boolean searchResult, int mouseX, int mouseY, float partialTicks,
		boolean clip)
	{
		int left = contentLeft() + 8;
		int right = contentRight() - 9;
		int width = right - left;
		int y = contentStart(searchResult)
			+ (int)Math.round(moduleScroll.scroll());
		int total = 0;
		FlatTheme theme = WURST.getGui().getTheme();
		if(clip)
			graphics.enableScissor(contentLeft() + 1, viewportTop(),
				contentRight() - 1, listBottom());
		for(Feature feature : features)
		{
			RiseModuleComponent component = moduleComponents.computeIfAbsent(feature,
				RiseModuleComponent::new);
			int componentHeight = component.updateHeight(width);
			if(y + componentHeight >= viewportTop() && y < listBottom())
				component.render(graphics, left, y, width, mouseX, mouseY,
					searchResult, accentColor(), theme, partialTicks, clip);
			y += componentHeight + MODULE_GAP;
			total += componentHeight + MODULE_GAP;
		}
		if(clip)
			graphics.disableScissor();

		contentHeight = total;
		int bottomPadding = searchResult ? 37 : 7;
		moduleScroll.setMinimum(Math.min(0,
			panelHeight - bottomPadding - contentHeight));
		int scrollbarTop = panelY + 7 + (searchResult ? 28 : 0);
		int scrollbarHeight = panelHeight - 14 - (searchResult ? 28 : 0);
		renderScrollbar(graphics, scrollbarTop, scrollbarHeight);
		if(features.isEmpty())
			graphics.drawCenteredString(MC.font,
				RiseFont.text("\u6ca1\u6709\u5339\u914d\u7684\u529f\u80fd"),
				(contentLeft() + contentRight()) / 2, panelY + 48,
				RiseColors.TRINARY_TEXT.argb());
	}

	private void renderScrollbar(GuiGraphics graphics, int top, int maxHeight)
	{
		double minimum = moduleScroll.minimum();
		if(minimum >= 0)
			return;
		int right = contentRight() - 4;
		int thumbHeight = Math.max(1, (int)Math.round(maxHeight
			- minimum / (minimum - maxHeight) * maxHeight));
		double ratio = moduleScroll.scroll() / minimum;
		int thumbY = top
			+ (int)Math.round((maxHeight - thumbHeight) * ratio);
		FlatUiRenderer.fill(graphics, right, thumbY, right + 1,
			thumbY + thumbHeight, 1, 0x3CFFFFFF);
	}

	private void renderCategoryTransition(GuiGraphics graphics)
	{
		long elapsed = System.currentTimeMillis() - categoryChangedAt;
		if(elapsed < 0 || elapsed > CATEGORY_TRANSITION_MS * 2L)
			return;
		float progress = elapsed < CATEGORY_TRANSITION_MS
			? elapsed / (float)CATEGORY_TRANSITION_MS
			: 1 - (elapsed - CATEGORY_TRANSITION_MS)
				/ (float)CATEGORY_TRANSITION_MS;
		int alpha = Mth.clamp(Math.round(progress * 255), 0, 255);
		graphics.fill(contentLeft(), panelY, contentRight(),
			panelY + panelHeight, alpha << 24
				| RiseColors.BACKGROUND.argb() & 0xFFFFFF);
	}

	private int transitionCategory()
	{
		long elapsed = System.currentTimeMillis() - categoryChangedAt;
		return elapsed >= 0 && elapsed < CATEGORY_TRANSITION_MS
			? previousCategory : selectedCategory;
	}

	private List<Feature> transitionFeatures()
	{
		long elapsed = System.currentTimeMillis() - categoryChangedAt;
		return elapsed >= 0 && elapsed < CATEGORY_TRANSITION_MS
			? previousFeatures : visibleFeatures;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button)
	{
		if(closing)
			return true;
		float scale = Math.max(0.01F, animationProgress);
		double localX = unscaleX(mouseX, scale);
		double localY = unscaleY(mouseY, scale);

		ClickGui gui = WURST.getGui();
		boolean overPopup = gui.isMouseOverWindow(localX, localY);
		gui.handleMouseClick((int)localX, (int)localY, button);
		if(overPopup)
			return true;

		if(inside(localX, localY, panelX, panelY, panelX + panelWidth,
				panelY + 15))
		{
			dragging = true;
			dragOffsetX = (int)localX - panelX;
			dragOffsetY = (int)localY - panelY;
			return true;
		}

		if(searchBox.visible && inside(localX, localY, searchBox.getX(),
			searchBox.getY(), searchBox.getX() + searchBox.getWidth(),
			searchBox.getY() + searchBox.getHeight()))
			return super.mouseClicked(localX, localY, button);

		int category = categoryAt(localX, localY);
		if(category >= 0 && button == GLFW.GLFW_MOUSE_BUTTON_LEFT)
		{
			switchCategory(category);
			return true;
		}

		if(handleModuleClick(localX, localY, button))
			return true;

		return super.mouseClicked(localX, localY, button);
	}

	private boolean handleModuleClick(double mouseX, double mouseY, int button)
	{
		int left = contentLeft() + 8;
		int width = contentRight() - 9 - left;
		int y = contentStart(selectedCategory == 0)
			+ (int)Math.round(moduleScroll.scroll());
		boolean handled = false;
		for(Feature feature : visibleFeatures)
		{
			RiseModuleComponent component = moduleComponents.computeIfAbsent(feature,
				RiseModuleComponent::new);
			component.updateHeight(width);
			if(component.mouseClicked(mouseX, mouseY, button, left, y, width))
				handled = true;
			y += component.height() + MODULE_GAP;
		}
		return handled;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button,
		double dragX, double dragY)
	{
		double localX = unscaleX(mouseX, Math.max(0.001F, animationProgress));
		double localY = unscaleY(mouseY, Math.max(0.001F, animationProgress));
		if(!dragging)
			return super.mouseDragged(localX, localY, button, dragX, dragY);
		panelX = (int)localX - dragOffsetX;
		panelY = (int)localY - dragOffsetY;
		return true;
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button)
	{
		double localX = unscaleX(mouseX, Math.max(0.001F, animationProgress));
		double localY = unscaleY(mouseY, Math.max(0.001F, animationProgress));
		WURST.getGui().handleMouseRelease(localX, localY, button);
		dragging = false;
		for(RiseModuleComponent component : moduleComponents.values())
			component.mouseReleased(button);
		return super.mouseReleased(localX, localY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double delta)
	{
		double localX = unscaleX(mouseX, Math.max(0.001F, animationProgress));
		double localY = unscaleY(mouseY, Math.max(0.001F, animationProgress));
		if(WURST.getGui().isMouseOverWindow(localX, localY))
		{
			WURST.getGui().handleMouseScroll(localX, localY, delta);
			return true;
		}
		if(inside(localX, localY, panelX, panelY, panelX + panelWidth,
			panelY + panelHeight))
		{
			moduleScroll.wheel(delta);
			return true;
		}
		return super.mouseScrolled(localX, localY, delta);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers)
	{
		if(keyCode == GLFW.GLFW_KEY_ESCAPE)
		{
			for(Feature feature : visibleFeatures)
			{
				RiseModuleComponent component = moduleComponents.get(feature);
				if(component != null)
					component.keyPressed(keyCode, modifiers);
			}
			onClose();
			return true;
		}
		for(Feature feature : visibleFeatures)
		{
			RiseModuleComponent component = moduleComponents.get(feature);
			if(component != null && component.keyPressed(keyCode, modifiers))
				return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean charTyped(char codePoint, int modifiers)
	{
		for(Feature feature : visibleFeatures)
		{
			RiseModuleComponent component = moduleComponents.get(feature);
			if(component != null && component.charTyped(codePoint))
				return true;
		}
		if(Character.isISOControl(codePoint) || hasActiveSettingText())
			return super.charTyped(codePoint, modifiers);

		boolean automaticCharacter = AUTO_SEARCH_CHARS.indexOf(
			Character.toLowerCase(codePoint)) >= 0;
		if(selectedCategory != 0 && automaticCharacter)
			switchCategory(0);
		if(selectedCategory == 0)
		{
			if(!typedWhileSearchOpen && automaticCharacter)
			{
				typedWhileSearchOpen = true;
				suppressSearchRefresh = true;
				try
				{
					searchBox.setValue("");
				}finally
				{
					suppressSearchRefresh = false;
				}
			}
			searchBox.setFocused(true);
			return searchBox.charTyped(codePoint, modifiers);
		}
		return super.charTyped(codePoint, modifiers);
	}

	private boolean hasActiveSettingText()
	{
		for(Feature feature : visibleFeatures)
		{
			RiseModuleComponent component = moduleComponents.get(feature);
			if(component != null && component.hasActiveText())
				return true;
		}
		return false;
	}

	@Override
	public void onClose()
	{
		if(closing)
			return;
		closing = true;
		dragging = false;
		searchBox.setFocused(false);
		for(RiseModuleComponent component : moduleComponents.values())
			component.closePopups();
		scaleAnimation.setEasing(RiseAnimation.Easing.LINEAR);
		scaleAnimation.setDuration(300);
		opacityAnimation.setEasing(RiseAnimation.Easing.LINEAR);
		opacityAnimation.setDuration(100);
	}

	@Override
	public void removed()
	{
		for(RiseModuleComponent component : moduleComponents.values())
			component.dispose();
		moduleComponents.clear();
		super.removed();
	}

	@Override
	public boolean isPauseScreen()
	{
		return false;
	}

	private int categoryAt(double mouseX, double mouseY)
	{
		int top = panelY + 38;
		int bottom = panelY + panelHeight - 24;
		if(!inside(mouseX, mouseY, panelX + 9, top,
			panelX + sidebarWidth - 7, bottom))
			return -1;
		int row = (int)(mouseY - top) / RiseSidebarCategory.HEIGHT;
		int index = row + categoryScroll;
		return index < CATEGORY_NAMES.length ? index : -1;
	}

	private int contentLeft()
	{
		return panelX + sidebarWidth;
	}

	private int contentRight()
	{
		return panelX + panelWidth;
	}

	private int viewportTop()
	{
		return panelY + 1;
	}

	private int contentStart(boolean search)
	{
		return panelY + (search ? SEARCH_CONTENT_OFFSET : 7);
	}

	private int listBottom()
	{
		return panelY + panelHeight - 7;
	}

	private void applyScale(GuiGraphics graphics, float scale)
	{
		float centerX = panelX + panelWidth / 2F;
		float centerY = panelY + panelHeight / 2F;
		graphics.pose().translate(centerX, centerY, 0);
		graphics.pose().scale(scale, scale, 1);
		graphics.pose().translate(-centerX, -centerY, 0);
	}

	private double unscaleX(double x, float scale)
	{
		double center = panelX + panelWidth / 2D;
		return center + (x - center) / scale;
	}

	private double unscaleY(double y, float scale)
	{
		double center = panelY + panelHeight / 2D;
		return center + (y - center) / scale;
	}

	private static boolean inside(double x, double y, int left, int top,
		int right, int bottom)
	{
		return x >= left && x < right && y >= top && y < bottom;
	}

	private int accentColor()
	{
		return WURST.getGui().getTheme().accent(1);
	}

	private static int withAlpha(int color, float opacity)
	{
		return color & 0xFFFFFF
			| Math.round((color >>> 24) * Mth.clamp(opacity, 0, 1)) << 24;
	}
}
