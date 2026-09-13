/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.clickgui2.component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.wurstclient.Category;
import net.wurstclient.Feature;
import net.wurstclient.WurstClient;
import net.wurstclient.clickgui2.ClickGui;
import net.wurstclient.clickgui2.FeatureMenuSupport;

/**
 * 基于 VAPE 组件体系的 ClickGUI 屏幕。
 * 分类面板横向排布，支持窗口内滚动。
 */
public class VapeClickGuiScreen extends Screen
{
	private static final WurstClient WURST = WurstClient.INSTANCE;
	
	private static final int PANEL_WIDTH = 110;
	private static final int PANEL_GAP = 4;
	private static final int TOP_OFFSET = 24;
	private static final int MAX_CARDS = 18;
	
	private final List<CategoryPanelComponent> panels = new ArrayList<>();
	
	public VapeClickGuiScreen()
	{
		super(Component.literal(WurstClient.CLIENT_NAME));
	}
	
	@Override
	protected void init()
	{
		panels.clear();
		buildPanels();
	}
	
	private void buildPanels()
	{
		List<Feature> features = FeatureMenuSupport.getAllFeatures();
		Map<Category, List<Feature>> categorized = new EnumMap<>(Category.class);
		for(Category category : Category.values())
			categorized.put(category, new ArrayList<>());
		for(Feature feature : features)
			if(feature.getCategory() != null)
				categorized.get(feature.getCategory()).add(feature);
		
		int x = 8;
		int y = TOP_OFFSET;
		int rowHeight = 0;
		
		// 各分类面板（带 VAPE 配色）
		panels.add(createPanel(x, y, categorized.get(Category.RENDER),
			0xFF8730C5, "视觉类"));
		panels.add(createPanel(x += PANEL_WIDTH + PANEL_GAP, y,
			categorized.get(Category.MOVEMENT), 0xFF1DA044, "移动类"));
		panels.add(createPanel(x += PANEL_WIDTH + PANEL_GAP, y,
			categorized.get(Category.COMBAT), 0xFFD61846, "战斗类"));
		panels.add(createPanel(x += PANEL_WIDTH + PANEL_GAP, y,
			combined(categorized, Category.BLOCKS, Category.ITEMS),
			0xFF4466B0, "世界类"));
		panels.add(createPanel(x += PANEL_WIDTH + PANEL_GAP, y,
			combined(categorized, Category.CHAT, Category.FUN,
				Category.OTHER), 0xFFDB5A05, "其他类"));
	}
	
	private CategoryPanelComponent createPanel(int x, int y,
		List<Feature> features, int color, String title)
	{
		CategoryPanelComponent panel = new CategoryPanelComponent(x, y,
			PANEL_WIDTH, features, color, MAX_CARDS);
		return panel;
	}
	
	@SafeVarargs
	private static List<Feature> combined(
		Map<Category, List<Feature>> categorized, Category... categories)
	{
		ArrayList<Feature> result = new ArrayList<>();
		for(Category category : categories)
			result.addAll(categorized.get(category));
		return result;
	}
	
	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY,
		float partialTicks)
	{
		renderBackground(graphics);
		
		// 标题
		graphics.drawCenteredString(font, WurstClient.CLIENT_NAME + " 设置",
			width / 2, 6, 0xFFFFFFFF);
		
		for(CategoryPanelComponent panel : panels)
			panel.render(graphics, mouseX, mouseY, partialTicks);
	}
	
	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button)
	{
		for(CategoryPanelComponent panel : panels)
			if(panel.mouseClicked(mouseX, mouseY, button))
				return true;
		return super.mouseClicked(mouseX, mouseY, button);
	}
	
	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button)
	{
		for(CategoryPanelComponent panel : panels)
			if(panel.mouseReleased(mouseX, mouseY, button))
				return true;
		return super.mouseReleased(mouseX, mouseY, button);
	}
	
	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button,
		double dragX, double dragY)
	{
		for(CategoryPanelComponent panel : panels)
			if(panel.mouseDragged(mouseX, mouseY, button))
				return true;
		return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
	}
	
	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double delta)
	{
		for(CategoryPanelComponent panel : panels)
			if(panel.mouseScrolled(mouseX, mouseY, delta))
				return true;
		return super.mouseScrolled(mouseX, mouseY, delta);
	}
	
	@Override
	public boolean isPauseScreen()
	{
		return false;
	}
	
	@Override
	public void onClose()
	{
		for(CategoryPanelComponent panel : panels)
			panel.closeAllSettings();
		super.onClose();
	}
}
