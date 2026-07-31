/*
 * Compact centered module dashboard for WurstB+ Plus.
 * Copyright (c) 2025 Penguin
 */
package net.wurstclient.clickgui2;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
	private static final int MODULE_HEIGHT = 25;
	private static final int MODULE_GAP = 4;
	private static final int CATEGORY_HEIGHT = 13;
	private static final float TEXT_SCALE = 0.75F;
	private static final float SEARCH_TEXT_SCALE = 0.72F;

	private static final int OVERLAY = 0x18000000;
	private static final int PANEL = 0xFF050505;
	private static final int SIDEBAR = 0xFF090909;
	private static final int CONTENT = 0xFF050505;
	private static final int ROW = 0xFF0D0D0D;
	private static final int ROW_HOVER = 0xFF151515;
	private static final int DIVIDER = 0xFF1C1C1C;
	private static final int ACCENT = 0xFF006366;
	private static final int TEXT = 0xFFF2F4F7;
	private static final int MUTED = 0xFF727B88;
	private static final int DIM = 0xFF4C5562;

	private static final String[] CATEGORY_NAMES = {
		"\u5168\u90e8", "\u65b9\u5757", "\u79fb\u52a8", "\u6218\u6597",
		"\u6e32\u67d3", "\u804a\u5929", "\u5a31\u4e50", "\u7269\u54c1",
		"\u5176\u4ed6", "\u754c\u9762"};
	private static final GuiIcon[] CATEGORY_ICONS = {GuiIcon.MENU, GuiIcon.WORLD,
		GuiIcon.MOVEMENT, GuiIcon.COMBAT, GuiIcon.RENDER, GuiIcon.PLAYER,
		GuiIcon.FUN, GuiIcon.BOOK, GuiIcon.MISC, GuiIcon.CLIENT};

	private final List<Feature> visibleFeatures = new ArrayList<>();
	private ScaledEditBox searchBox;
	private NavigatorSettingsPanel settingsPanel;
	private Feature selectedFeature;
	private int selectedCategory = 3;
	private int moduleScroll;
	private int categoryScroll;
	private int panelX;
	private int panelY;
	private int panelWidth;
	private int panelHeight;
	private int sidebarWidth;
	private boolean dragging;
	private int dragOffsetX;
	private int dragOffsetY;
	private boolean scrollbarDragging;

	public NavigatorScreen()
	{
		super(Component.literal(WurstClient.CLIENT_NAME));
	}

	@Override
	protected void init()
	{
		updateDimensions();
		GuiIcon.configureFiltering(MC);
		panelX = (width - panelWidth) / 2;
		panelY = (height - panelHeight) / 2;

		String query = searchBox == null ? "" : searchBox.getValue();
		searchBox = new ScaledEditBox(MC.font, 0, 0, sidebarWidth - 24,
			16, Component.literal("\u641c\u7d22"), SEARCH_TEXT_SCALE);
		searchBox.setBordered(false);
		searchBox.setMaxLength(80);
		searchBox.setHint(Component.literal("\u641c\u7d22"));
		searchBox.setTextColor(TEXT);
		searchBox.setTextColorUneditable(MUTED);
		searchBox.setValue(query);
		searchBox.setResponder(ignored -> refreshFeatures());
		addRenderableWidget(searchBox);
		WURST.getGui().init();
		refreshFeatures();
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

	private void refreshFeatures()
	{
		String query = searchBox == null ? "" : searchBox.getValue().trim();
		visibleFeatures.clear();
		if(!query.isEmpty())
			visibleFeatures.addAll(FeatureMenuSupport.searchFeatures(
				FeatureMenuSupport.getAllFeatures(), query));
		else if(selectedCategory == 0)
			visibleFeatures.addAll(FeatureMenuSupport.getAllFeatures());
		else if(selectedCategory <= Category.values().length)
		{
			Category category = Category.values()[selectedCategory - 1];
			for(Feature feature : FeatureMenuSupport.getAllFeatures())
				if(feature.getCategory() == category)
					visibleFeatures.add(feature);
		}else
			visibleFeatures.addAll(getInterfaceFeatures());

		if(query.isEmpty())
			visibleFeatures.sort(Comparator.comparing(Feature::getDisplayName,
				String.CASE_INSENSITIVE_ORDER));
		if(selectedFeature == null || !visibleFeatures.contains(selectedFeature))
			selectedFeature = visibleFeatures.isEmpty() ? null
				: visibleFeatures.get(0);
		if(settingsPanel != null
			&& !visibleFeatures.contains(settingsPanel.getFeature()))
			closeSettings();
		moduleScroll = 0;
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
		graphics.fill(0, 0, width, height, OVERLAY);
		FlatUiRenderer.panel(graphics, panelX, panelY, panelX + panelWidth,
			panelY + panelHeight, 8, PANEL, 0xFF202020);
		FlatUiRenderer.fill(graphics, panelX + 1, panelY + 1,
			panelX + sidebarWidth, panelY + panelHeight - 1, 7, SIDEBAR);
		graphics.fill(panelX + sidebarWidth, panelY + 1,
			panelX + panelWidth - 1, panelY + panelHeight - 1, CONTENT);
		graphics.fill(panelX + sidebarWidth, panelY + 10,
			panelX + sidebarWidth + 1, panelY + panelHeight - 10, DIVIDER);

		renderSidebar(graphics, mouseX, mouseY, partialTicks);
		if(settingsPanel == null)
			renderModules(graphics, mouseX, mouseY);
		else
			renderSettings(graphics, mouseX, mouseY, partialTicks);

		WURST.getGui().render(graphics, mouseX, mouseY, partialTicks);
	}

	private void renderSidebar(GuiGraphics graphics, int mouseX, int mouseY,
		float partialTicks)
	{
		Font font = MC.font;
		String brand = WurstClient.CLIENT_NAME;
		String brandPrefix = "WurstB+ ";
		String brandAccent = "Plus";
		float brandScale = Math.min(1,
			(sidebarWidth - 16F) / Math.max(1, font.width(brand)));
		graphics.pose().pushPose();
		graphics.pose().translate(panelX + 8, panelY + 9, 0);
		graphics.pose().scale(brandScale, 1, 1);
		graphics.drawString(font, brandPrefix, 0, 0, TEXT, false);
		graphics.drawString(font, brandAccent, font.width(brandPrefix), 0,
			ACCENT, false);
		graphics.pose().popPose();

		int searchX = panelX + 12;
		int searchY = panelY + 27;
		GuiIcon.SEARCH.draw(graphics, searchX, searchY + 2, 8, MUTED);
		searchBox.setX(searchX + 12);
		searchBox.setY(searchY);
		searchBox.setWidth(Math.max(12, sidebarWidth - 40));
		searchBox.visible = settingsPanel == null;
		if(searchBox.visible)
			searchBox.render(graphics, mouseX, mouseY, partialTicks);

		int top = panelY + 43;
		int bottom = panelY + panelHeight - 8;
		int visibleRows = Math.max(1, (bottom - top) / CATEGORY_HEIGHT);
		categoryScroll = Mth.clamp(categoryScroll, 0,
			Math.max(0, CATEGORY_NAMES.length - visibleRows));
		graphics.enableScissor(panelX + 4, top, panelX + sidebarWidth - 4,
			bottom);
		for(int row = 0; row < visibleRows; row++)
		{
			int index = row + categoryScroll;
			if(index >= CATEGORY_NAMES.length)
				break;
			int y = top + row * CATEGORY_HEIGHT;
			boolean selected = searchBox.getValue().isBlank()
				&& selectedCategory == index;
			boolean hovering = inside(mouseX, mouseY, panelX + 8, y,
				panelX + sidebarWidth - 8, y + 13);
			if(selected || hovering)
			{
				int selectionRight = Math.min(panelX + sidebarWidth - 8,
					panelX + 26
						+ Math.round(font.width(CATEGORY_NAMES[index])
							* TEXT_SCALE));
				FlatUiRenderer.fill(graphics, panelX + 8, y,
					selectionRight, y + 12, 2,
					selected ? accentColor() : 0xFF151515);
			}
			int color = selected ? TEXT : MUTED;
			CATEGORY_ICONS[index].draw(graphics, panelX + 10, y + 2, 8, color);
			drawText(graphics, font, CATEGORY_NAMES[index], panelX + 21, y + 3,
				TEXT);
		}
		graphics.disableScissor();
	}

	private void renderModules(GuiGraphics graphics, int mouseX, int mouseY)
	{
		int left = panelX + sidebarWidth + 6;
		int right = panelX + panelWidth - 5;
		int listTop = panelY + 3;
		int listBottom = panelY + panelHeight - 4;
		int visibleRows = Math.max(1,
			(listBottom - listTop + MODULE_GAP) / (MODULE_HEIGHT + MODULE_GAP));
		moduleScroll = Mth.clamp(moduleScroll, 0,
			Math.max(0, visibleFeatures.size() - visibleRows));
		graphics.enableScissor(left, listTop, right, listBottom);
		for(int row = 0; row < visibleRows; row++)
		{
			int index = row + moduleScroll;
			if(index >= visibleFeatures.size())
				break;
			int y = listTop + row * (MODULE_HEIGHT + MODULE_GAP);
			renderModule(graphics, visibleFeatures.get(index), left, y, right,
				mouseX, mouseY);
		}
		graphics.disableScissor();
		if(visibleFeatures.size() > visibleRows)
		{
			int trackHeight = listBottom - listTop;
			int thumbHeight = Math.max(12,
				Math.round(trackHeight * visibleRows
					/ (float)visibleFeatures.size()));
			int maxScroll = visibleFeatures.size() - visibleRows;
			int thumbY = listTop + Math.round((trackHeight - thumbHeight)
				* moduleScroll / (float)Math.max(1, maxScroll));
			graphics.fill(right - 2, listTop, right, listBottom, 0xFF111111);
			graphics.fill(right - 2, thumbY, right, thumbY + thumbHeight,
				accentColor());
		}

		if(visibleFeatures.isEmpty())
			graphics.drawCenteredString(MC.font, "\u6ca1\u6709\u5339\u914d\u7684\u529f\u80fd",
				(left + right) / 2, listTop + 28, MUTED);
	}

	private void renderModule(GuiGraphics graphics, Feature feature, int left,
		int y, int right, int mouseX, int mouseY)
	{
		boolean hovering = inside(mouseX, mouseY, left, y, right,
			y + MODULE_HEIGHT);
		FlatUiRenderer.fill(graphics, left, y, right, y + MODULE_HEIGHT, 4,
			hovering ? ROW_HOVER : ROW);

		Font font = MC.font;
		int textWidth = Math.max(40, right - left - 56);
		String name = font.plainSubstrByWidth(feature.getDisplayName(),
			Math.round(textWidth / TEXT_SCALE));
		String description = font.plainSubstrByWidth(
			FeatureMenuSupport.getOneLineDescription(feature),
			Math.round(textWidth / TEXT_SCALE));
		drawText(graphics, font, name, left + 7, y + 4,
			feature.isEnabled() ? accentColor() : TEXT);
		drawText(graphics, font, description, left + 7, y + 15, MUTED);
		if(!feature.getSettings().isEmpty())
			GuiIcon.CHEVRON.drawRotated(graphics, right - 13, y + 9, 7, MUTED,
				-90);

	}

	private void renderSettings(GuiGraphics graphics, int mouseX, int mouseY,
		float partialTicks)
	{
		Feature feature = settingsPanel.getFeature();
		int left = panelX + sidebarWidth + 6;
		int right = panelX + panelWidth - 5;
		int top = panelY + 8;
		boolean backHover = inside(mouseX, mouseY, left, top - 3, left + 20,
			top + 17);
		FlatUiRenderer.fill(graphics, left, top - 3, left + 20, top + 17, 3,
			backHover ? ROW_HOVER : ROW);
		GuiIcon.CHEVRON.drawRotated(graphics, left + 6, top + 3, 8, TEXT, 90);
		graphics.drawString(MC.font, feature.getDisplayName(), left + 29, top,
			feature.isEnabled() ? accentColor() : TEXT, false);
		graphics.drawString(MC.font,
			settingsPanel.getVisibleSettingCount() + " settings", left + 29,
			top + 14, MUTED, false);

		int toggleX = right - 32;
		FlatUiRenderer.fill(graphics, toggleX, top + 3, right, top + 15, 6,
			feature.isEnabled() ? accentColor() : DIM);
		int knobX = feature.isEnabled() ? right - 11 : toggleX + 2;
		FlatUiRenderer.fill(graphics, knobX, top + 5, knobX + 8, top + 13, 4,
			TEXT);

		int bodyTop = panelY + 36;
		int bodyBottom = panelY + panelHeight - 7;
		FlatUiRenderer.fill(graphics, left, bodyTop, right, bodyBottom, 4, ROW);
		settingsPanel.layout(left + 6, bodyTop + 5, right - left - 12,
			bodyBottom - bodyTop - 10);
		FlatTheme theme = WURST.getGui().getTheme();
		settingsPanel.renderContent(graphics, mouseX, mouseY, partialTicks, theme);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button)
	{
		ClickGui gui = WURST.getGui();
		boolean overPopup = gui.isMouseOverWindow(mouseX, mouseY);
		gui.handleMouseClick((int)mouseX, (int)mouseY, button);
		if(overPopup)
			return true;

		if(searchBox.visible && inside(mouseX, mouseY, searchBox.getX() - 12,
			searchBox.getY() - 3, searchBox.getX() + searchBox.getWidth() + 3,
			searchBox.getY() + searchBox.getHeight() + 3))
			return super.mouseClicked(mouseX, mouseY, button);

		int category = categoryAt(mouseX, mouseY);
		if(category >= 0)
		{
			closeSettings();
			selectedCategory = category;
			searchBox.setValue("");
			refreshFeatures();
			return true;
		}

		if(settingsPanel != null)
		{
			int left = panelX + sidebarWidth + 6;
			int right = panelX + panelWidth - 5;
			int top = panelY + 8;
			if(button == GLFW.GLFW_MOUSE_BUTTON_LEFT
				&& inside(mouseX, mouseY, left, top - 3, left + 20, top + 17))
			{
				closeSettings();
				return true;
			}
			if(button == GLFW.GLFW_MOUSE_BUTTON_LEFT
				&& inside(mouseX, mouseY, right - 36, top - 2, right + 2,
					top + 20))
			{
				FeatureMenuSupport.runPrimaryAction(settingsPanel.getFeature());
				return true;
			}
			if(settingsPanel.mouseClicked(mouseX, mouseY, button))
				return true;
		}else
		{
			Feature feature = featureAt(mouseX, mouseY);
			if(feature != null)
			{
				selectedFeature = feature;
				if(button == GLFW.GLFW_MOUSE_BUTTON_RIGHT
					|| feature.getPrimaryAction().isEmpty())
					openSettings(feature);
				else if(button == GLFW.GLFW_MOUSE_BUTTON_LEFT)
					FeatureMenuSupport.runPrimaryAction(feature);
				return true;
			}
		}

		int moduleLeft = panelX + sidebarWidth + 6;
		int moduleRight = panelX + panelWidth - 5;
		int listTop = panelY + 3;
		int listBottom = panelY + panelHeight - 4;
		int visibleRows = Math.max(1,
			(listBottom - listTop + MODULE_GAP) / (MODULE_HEIGHT + MODULE_GAP));
		int trackHeight = listBottom - listTop;
		int thumbHeight = Math.max(12,
			Math.round(trackHeight * visibleRows
				/ (float)Math.max(1, visibleFeatures.size())));
		int maxScroll = Math.max(0,
			visibleFeatures.size() - visibleRows);
		int thumbY = listTop + Math.round((trackHeight - thumbHeight)
			* moduleScroll / (float)Math.max(1, maxScroll));

		if(button == GLFW.GLFW_MOUSE_BUTTON_LEFT
			&& visibleFeatures.size() > visibleRows
			&& inside(mouseX, mouseY, moduleRight - 3, listTop, moduleRight + 1,
				listBottom))
		{
			if(mouseY < thumbY)
				moduleScroll = Math.max(0, moduleScroll - visibleRows);
			else if(mouseY > thumbY + thumbHeight)
				moduleScroll = Math.min(maxScroll, moduleScroll + visibleRows);
			else
				scrollbarDragging = true;
			return true;
		}

		if(button == GLFW.GLFW_MOUSE_BUTTON_LEFT
			&& inside(mouseX, mouseY, panelX + sidebarWidth, panelY,
				panelX + panelWidth, panelY + 40))
		{
			dragging = true;
			dragOffsetX = (int)mouseX - panelX;
			dragOffsetY = (int)mouseY - panelY;
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button,
		double dragX, double dragY)
	{
		if(button != GLFW.GLFW_MOUSE_BUTTON_LEFT)
			return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);

		if(scrollbarDragging)
		{
			int listTop = panelY + 3;
			int listBottom = panelY + panelHeight - 4;
			int visibleRows = Math.max(1,
				(listBottom - listTop + MODULE_GAP)
					/ (MODULE_HEIGHT + MODULE_GAP));
			int trackHeight = listBottom - listTop;
			int thumbHeight = Math.max(12,
				Math.round(trackHeight * visibleRows
					/ (float)Math.max(1, visibleFeatures.size())));
			int maxScroll = Math.max(0,
				visibleFeatures.size() - visibleRows);
			float ratio = (float)((int)mouseY - listTop - thumbHeight / 2)
				/ Math.max(1, trackHeight - thumbHeight);
			moduleScroll = Mth.clamp(Math.round(ratio * maxScroll), 0,
				maxScroll);
			return true;
		}

		if(!dragging)
			return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
		panelX = Mth.clamp((int)mouseX - dragOffsetX, 0,
			Math.max(0, width - panelWidth));
		panelY = Mth.clamp((int)mouseY - dragOffsetY, 0,
			Math.max(0, height - panelHeight));
		return true;
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button)
	{
		WURST.getGui().handleMouseRelease(mouseX, mouseY, button);
		if(button == GLFW.GLFW_MOUSE_BUTTON_LEFT)
		{
			dragging = false;
			scrollbarDragging = false;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double delta)
	{
		if(WURST.getGui().isMouseOverWindow(mouseX, mouseY))
		{
			WURST.getGui().handleMouseScroll(mouseX, mouseY, delta);
			return true;
		}
		if(settingsPanel != null
			&& settingsPanel.mouseScrolled(mouseX, mouseY, delta))
			return true;

		if(mouseX < panelX + sidebarWidth)
		{
			int visibleRows = Math.max(1,
				(panelHeight - 51) / CATEGORY_HEIGHT);
			categoryScroll = Mth.clamp(categoryScroll + (delta > 0 ? -1 : 1),
				0, Math.max(0, CATEGORY_NAMES.length - visibleRows));
			return true;
		}

		int visibleRows = Math.max(1,
			(panelHeight - 7 + MODULE_GAP) / (MODULE_HEIGHT + MODULE_GAP));
		moduleScroll = Mth.clamp(moduleScroll + (delta > 0 ? -1 : 1), 0,
			Math.max(0, visibleFeatures.size() - visibleRows));
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
	public void removed()
	{
		closeSettings();
		super.removed();
	}

	@Override
	public boolean isPauseScreen()
	{
		return false;
	}

	private int categoryAt(double mouseX, double mouseY)
	{
		int top = panelY + 43;
		int bottom = panelY + panelHeight - 8;
		if(!inside(mouseX, mouseY, panelX + 8, top,
			panelX + sidebarWidth - 8, bottom))
			return -1;
		int row = (int)(mouseY - top) / CATEGORY_HEIGHT;
		int index = row + categoryScroll;
		return index < CATEGORY_NAMES.length ? index : -1;
	}

	private Feature featureAt(double mouseX, double mouseY)
	{
		int left = panelX + sidebarWidth + 6;
		int right = panelX + panelWidth - 5;
		int top = panelY + 3;
		int bottom = panelY + panelHeight - 4;
		if(!inside(mouseX, mouseY, left, top, right, bottom))
			return null;
		int row = (int)(mouseY - top) / (MODULE_HEIGHT + MODULE_GAP);
		int localY = (int)(mouseY - top) % (MODULE_HEIGHT + MODULE_GAP);
		if(localY >= MODULE_HEIGHT)
			return null;
		int index = row + moduleScroll;
		return index < visibleFeatures.size() ? visibleFeatures.get(index) : null;
	}

	private void openSettings(Feature feature)
	{
		if(feature.getSettings().isEmpty())
			return;
		closeSettings();
		settingsPanel = new NavigatorSettingsPanel(feature);
		searchBox.setFocused(false);
	}

	private void closeSettings()
	{
		if(settingsPanel == null)
			return;
		settingsPanel.dispose();
		settingsPanel = null;
	}

	private static boolean inside(double x, double y, int left, int top,
		int right, int bottom)
	{
		return x >= left && x < right && y >= top && y < bottom;
	}

	private static void drawText(GuiGraphics graphics, Font font, String text,
		int x, int y, int color)
	{
		graphics.pose().pushPose();
		graphics.pose().translate(x, y, 0);
		graphics.pose().scale(TEXT_SCALE, TEXT_SCALE, 1);
		graphics.drawString(font, text, 0, 0, color, false);
		graphics.pose().popPose();
	}

	private int accentColor()
	{
		return WURST.getGui().getTheme().accent(1);
	}
}
