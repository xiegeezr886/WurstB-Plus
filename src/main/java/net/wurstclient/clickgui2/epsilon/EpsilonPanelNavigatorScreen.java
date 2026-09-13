/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.clickgui2.epsilon;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
import net.wurstclient.clickgui2.NavigatorScreen;
import net.wurstclient.clickgui2.epsilon.EpsilonPanelLayout.Layout;
import net.wurstclient.clickgui2.epsilon.EpsilonPanelLayout.Rect;
import net.wurstclient.keybinds.PossibleKeybind;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.Setting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.TextFieldSetting;
import org.lwjgl.glfw.GLFW;

/**
 * 导航器的「Epsilon 中央布局」模式：一张居中的大卡片，内部横排三栏
 * rail（分类）｜modules（模块列表）｜detail（模块详情），另有合并中右两栏的
 * 客户端设置页。
 *
 * <p>
 * 布局、配色与交互均按参考项目 <b>Nekoyahouse/Epsilon</b>（GPLv3）的
 * {@code PanelScreen} 及其面板视图移植；几何在 {@link EpsilonPanelLayout}、
 * 暗色 MD3 调色板在 {@link EpsilonPanelTheme}，两者都是纯类并有单测，
 * 这里只负责绘制与输入。原项目用自研的 Lumin 渲染栈与 TTF 字体，本工程用
 * {@link FlatRenderer} 的圆角矩形与原版字体重画，字号按 {@code scale * 18}
 * 换算成像素。
 *
 * <p>
 * 与参考项目的已知差异（都写在 {@code docs/epsilon-panel-port.md}）：
 * 枚举设置是点击循环而不是弹出下拉；设置分组不画卡片只在组头上折叠；
 * 没有音效、跑马灯与 IME 预编辑；标题栏用本工程的名字而不是 "Epsilon"。
 */
public final class EpsilonPanelNavigatorScreen extends Screen
{
	private static final WurstClient WURST = WurstClient.INSTANCE;
	
	private static final String RAIL_TITLE = "Navigator";
	private static final String RAIL_SUBTITLE = "WurstB+ 1.6";
	private static final String LIST_SUBTITLE = "功能列表";
	private static final String SEARCH_HINT = "搜索...";
	private static final String SETTINGS_LABEL = "客户端设置";
	
	/** 参考项目的动画时长（毫秒）。 */
	private static final long RAIL_EXPAND_MS = 240L;
	private static final long HOVER_MS = 120L;
	private static final long SWITCH_MS = 620L;
	
	/** 滚轮的惯性模型，与参考项目一致。 */
	private static final float SCROLL_STEP = 24.0F;
	private static final float SCROLL_FRICTION = 0.86F;
	private static final float SCROLL_STOP = 0.3F;
	
	/** 字号换算：参考项目的 scale 是相对值，这里按 scale*18 得到像素高。 */
	private static final float TEXT_BASE = 18.0F;
	
	private static final Object RAIL_KEY = new Object();
	private static final String ROW_HOVER_PREFIX = "row:";
	private static final String SETTING_HOVER_PREFIX = "set:";
	
	private Layout frame;
	
	private boolean railExpanded = true;
	private Category selectedCategory = Category.COMBAT;
	private Feature selectedModule;
	private boolean clientSettingsMode;
	
	private final StringBuilder search = new StringBuilder();
	private boolean searchFocused;
	
	private boolean modulesDirty = true;
	private final List<Feature> modules = new ArrayList<>();
	private final List<Setting> settings = new ArrayList<>();
	
	private float listScroll;
	private float listVelocity;
	private float detailScroll;
	private float detailVelocity;
	
	private final Map<Object, Float> anims = new IdentityHashMap<>();
	private final Map<Object, Long> animTimes = new IdentityHashMap<>();
	private final Map<Feature, SwitchAnim> switches = new IdentityHashMap<>();
	
	private final Map<Setting, Rect> sliderTracks = new IdentityHashMap<>();
	private final Map<Setting, Rect> enumChips = new IdentityHashMap<>();
	private final Map<Setting, Rect> checkboxRows = new IdentityHashMap<>();
	
	private SliderSetting draggingSlider;
	private Rect draggingScrollbar;
	private boolean draggingScrollIsList;
	
	private boolean listeningForBind;
	private String listenCommand;
	
	private final Map<Category, Integer> categoryCounts =
		new IdentityHashMap<>();
	
	public EpsilonPanelNavigatorScreen()
	{
		super(Component.literal("Navigator"));
	}
	
	@Override
	public boolean isPauseScreen()
	{
		return false;
	}
	
	@Override
	public void removed()
	{
		listeningForBind = false;
		draggingSlider = null;
		draggingScrollbar = null;
		super.removed();
	}
	
	// ------------------------------------------------------------------
	// 数据
	// ------------------------------------------------------------------
	
	/** 当前分类（或搜索结果）里要显示的模块，启用优先再按名字排序。 */
	private void rebuildModules()
	{
		modulesDirty = false;
		modules.clear();
		
		String query = search.toString().trim().toLowerCase(Locale.ROOT);
		boolean searching = !query.isEmpty();
		
		for(Feature feature : FeatureMenuSupport.getAllFeatures())
		{
			if(!searching && feature.getCategory() != selectedCategory)
				continue;
			
			if(searching && !matches(feature, query))
				continue;
			
			modules.add(feature);
		}
		
		modules.sort(Comparator
			.comparing((Feature feature) -> !feature.isEnabled())
			.thenComparing(feature -> feature.getName().toLowerCase(Locale.ROOT)));
		
		if(selectedModule == null || !modules.contains(selectedModule))
			selectedModule = modules.isEmpty() ? null : modules.get(0);
		
		rebuildSettings();
	}
	
	private static boolean matches(Feature feature, String query)
	{
		if(feature.getName().toLowerCase(Locale.ROOT).contains(query))
			return true;
		
		if(feature.getDisplayName().toLowerCase(Locale.ROOT).contains(query))
			return true;
		
		return feature.getCategory().getName().toLowerCase(Locale.ROOT)
			.contains(query);
	}
	
	/** 详情栏要显示的设置；分组展开时带上子项。 */
	private void rebuildSettings()
	{
		settings.clear();
		sliderTracks.clear();
		enumChips.clear();
		checkboxRows.clear();
		
		if(selectedModule == null)
			return;
		
		for(Setting setting : selectedModule.getSettings().values())
			collectSetting(setting);
	}
	
	private void collectSetting(Setting setting)
	{
		if(!setting.isVisible())
			return;
		
		settings.add(setting);
		
		if(setting.hasChildren() && setting.isExpanded())
			for(Setting child : setting.getChildren())
				collectSetting(child);
	}
	
	private String keybindOf(Feature feature)
	{
		String command = feature.getPossibleKeybinds().stream().findFirst()
			.map(PossibleKeybind::getCommand).orElse(null);
		
		if(command == null)
			return "";
		
		String key = WURST.getKeybinds().getKeyForCommand(command);
		return key == null ? "" : key;
	}
	
	private String keybindCommandOf(Feature feature)
	{
		return feature.getPossibleKeybinds().stream().findFirst()
			.map(PossibleKeybind::getCommand).orElse(null);
	}
	
	// ------------------------------------------------------------------
	// 绘制
	// ------------------------------------------------------------------
	
	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY,
		float partialTick)
	{
		graphics.fill(0, 0, width, height, 0x66000000);
		
		if(modulesDirty)
			rebuildModules();
		
		/*
		 * 滚轮惯性：与参考项目一样每帧按速度推进再衰减，所以放在渲染里而不是
		 * tick() 里——tick 只有 20Hz，放那儿会一顿一顿的。
		 */
		listScroll += listVelocity * partialTick;
		detailScroll += detailVelocity * partialTick;
		listVelocity = Math.abs(listVelocity) < SCROLL_STOP ? 0F
			: listVelocity * SCROLL_FRICTION;
		detailVelocity = Math.abs(detailVelocity) < SCROLL_STOP ? 0F
			: detailVelocity * SCROLL_FRICTION;
		
		float expansion = anim(RAIL_KEY, railExpanded ? 1F : 0F, RAIL_EXPAND_MS);
		frame = EpsilonPanelLayout.compute(width, height,
			EpsilonPanelLayout.railWidth(expansion));
		
		Layout layout = frame;
		drawShadow(graphics, layout.panel(),
			EpsilonPanelTheme.PANEL_RADIUS, 24);
		rrect(graphics, layout.panel(), EpsilonPanelTheme.PANEL_RADIUS,
			EpsilonPanelTheme.SURFACE);
		rrect(graphics, layout.rail(), EpsilonPanelTheme.SECTION_RADIUS,
			EpsilonPanelTheme.SURFACE_DIM);
		
		if(clientSettingsMode)
		{
			// 客户端设置页把中栏与详情栏合并成一张卡片
			graphics.fill(Math.round(layout.modules().x()),
				Math.round(layout.modules().y()),
				Math.round(layout.detail().right()),
				Math.round(layout.detail().bottom()),
				EpsilonPanelTheme.SURFACE_DIM);
		}else
		{
			rrect(graphics, layout.modules(), EpsilonPanelTheme.SECTION_RADIUS,
				EpsilonPanelTheme.SURFACE_DIM);
			rrect(graphics, layout.detail(), EpsilonPanelTheme.SECTION_RADIUS,
				EpsilonPanelTheme.SURFACE_DIM);
		}
		
		drawRail(graphics, mouseX, mouseY);
		
		if(clientSettingsMode)
			drawClientSettings(graphics, mouseX, mouseY);
		else
		{
			drawModuleList(graphics, mouseX, mouseY);
			drawDetail(graphics, mouseX, mouseY);
		}
		
		super.render(graphics, mouseX, mouseY, partialTick);
	}
	
	private void drawRail(GuiGraphics graphics, int mouseX, int mouseY)
	{
		Rect rail = frame.rail();
		float expansion = anim(RAIL_KEY, railExpanded ? 1F : 0F, RAIL_EXPAND_MS);
		
		// 菜单按钮
		Rect menu = EpsilonPanelLayout.railMenuButton(rail);
		boolean menuHover = menu.contains(mouseX, mouseY);
		float menuProgress = anim("menu", menuHover ? 1F : 0F, HOVER_MS);
		rrect(graphics, menu, 12,
			EpsilonPanelTheme.mix(EpsilonPanelTheme.SURFACE_CONTAINER,
				EpsilonPanelTheme.SURFACE_CONTAINER_HIGH, menuProgress));
		
		for(int i = 0; i < 3; i++)
			graphics.fill(Math.round(menu.centerX() - 6F),
				Math.round(menu.y() + 5F + i * 4F),
				Math.round(menu.centerX() + 6F),
				Math.round(menu.y() + 6.6F + i * 4F),
				EpsilonPanelTheme.TEXT_PRIMARY);
		
		// 标题与副标题只在 rail 够宽时才有位置
		if(expansion > 0.05F)
		{
			float labelAlpha = expansion;
			text(graphics, RAIL_TITLE, rail.x() + EpsilonPanelLayout.RAIL_TITLE_X,
				rail.y() + EpsilonPanelLayout.RAIL_TITLE_Y, 0.78F,
				EpsilonPanelTheme.withAlpha(EpsilonPanelTheme.TEXT_PRIMARY,
					labelAlpha));
			float subtitleY = rail.y() + EpsilonPanelLayout.RAIL_TITLE_Y
				+ textHeight(0.78F) + 3F;
			text(graphics, RAIL_SUBTITLE,
				rail.x() + EpsilonPanelLayout.RAIL_TITLE_X, subtitleY, 0.52F,
				EpsilonPanelTheme.withAlpha(EpsilonPanelTheme.TEXT_SECONDARY,
					210F / 255F * labelAlpha));
			
			float dividerY = subtitleY + textHeight(0.52F) + 4F;
			graphics.fill(
				Math.round(rail.x() + EpsilonPanelLayout.RAIL_DIVIDER_INSET),
				Math.round(dividerY),
				Math.round(rail.right()
					- EpsilonPanelLayout.RAIL_DIVIDER_INSET),
				Math.round(dividerY) + 1,
				EpsilonPanelTheme.withAlpha(EpsilonPanelTheme.OUTLINE_SOFT,
					120F / 255F * labelAlpha));
		}
		
		Category[] categories = Category.values();
		
		for(int i = 0; i < categories.length; i++)
		{
			Rect item = EpsilonPanelLayout.railItem(rail, i);
			
			if(item.y() + item.height() > rail.bottom() - 40F)
				break;
			
			drawRailItem(graphics, item, categories[i], mouseX, mouseY,
				expansion, !clientSettingsMode
					&& categories[i] == selectedCategory);
		}
		
		Rect settingsItem = EpsilonPanelLayout.railSettingsItem(rail);
		drawSettingsItem(graphics, settingsItem, mouseX, mouseY, expansion);
	}
	
	private void drawRailItem(GuiGraphics graphics, Rect item, Category category,
		int mouseX, int mouseY, float expansion, boolean selected)
	{
		boolean hovered = item.contains(mouseX, mouseY);
		float hover = anim("rail:" + category, hovered ? 1F : 0F, HOVER_MS);
		
		if(selected)
			rrect(graphics, item, EpsilonPanelTheme.CARD_RADIUS,
				EpsilonPanelTheme.SECONDARY_CONTAINER);
		else if(hover > 0.01F)
			rrect(graphics, item, EpsilonPanelTheme.CARD_RADIUS,
				EpsilonPanelTheme.withAlpha(
					EpsilonPanelTheme.SURFACE_CONTAINER_HIGH,
					200F / 255F * hover));
		
		int iconColor = selected ? EpsilonPanelTheme.ON_SECONDARY_CONTAINER
			: hovered ? EpsilonPanelTheme.TEXT_PRIMARY
				: EpsilonPanelTheme.TEXT_SECONDARY;
		int nameColor = selected ? EpsilonPanelTheme.ON_SECONDARY_CONTAINER
			: EpsilonPanelTheme.TEXT_PRIMARY;
		int countColor = selected ? EpsilonPanelTheme.ON_SECONDARY_CONTAINER
			: EpsilonPanelTheme.TEXT_SECONDARY;
		
		// 图标：用一个方块加一道横线当作占位字形，不依赖图标字体
		float iconX = item.x() + EpsilonPanelLayout.RAIL_ICON_CENTER_X;
		graphics.fill(Math.round(iconX - 4F),
			Math.round(item.centerY() - 4F), Math.round(iconX + 4F),
			Math.round(item.centerY() + 4F), iconColor);
		graphics.fill(Math.round(iconX - 6F),
			Math.round(item.centerY() + 6F), Math.round(iconX + 6F),
			Math.round(item.centerY() + 7F), iconColor);
		
		if(expansion <= 0.05F)
			return;
		
		float slide = (1F - expansion) * 5F;
		int labelColor =
			EpsilonPanelTheme.withAlpha(nameColor, expansion);
		text(graphics, category.getName(),
			item.x() + EpsilonPanelLayout.RAIL_LABEL_INSET + slide,
			item.centerY() - textHeight(0.62F) / 2F, 0.62F, labelColor);
		
		String count = Integer.toString(countModules(category));
		text(graphics, count,
			item.right() - 12F - textWidth(count, 0.58F),
			item.centerY() - textHeight(0.58F) / 2F, 0.58F,
			EpsilonPanelTheme.withAlpha(countColor,
				220F / 255F * expansion));
	}
	
	private void drawSettingsItem(GuiGraphics graphics, Rect item, int mouseX,
		int mouseY, float expansion)
	{
		boolean hovered = item.contains(mouseX, mouseY);
		float hover = anim("rail:settings", hovered ? 1F : 0F, HOVER_MS);
		
		if(clientSettingsMode)
			rrect(graphics, item, EpsilonPanelTheme.CARD_RADIUS,
				EpsilonPanelTheme.SECONDARY_CONTAINER);
		else if(hover > 0.01F)
			rrect(graphics, item, EpsilonPanelTheme.CARD_RADIUS,
				EpsilonPanelTheme.withAlpha(
					EpsilonPanelTheme.SURFACE_CONTAINER_HIGH,
					200F / 255F * hover));
		
		int color = clientSettingsMode
			? EpsilonPanelTheme.ON_SECONDARY_CONTAINER
			: hovered ? EpsilonPanelTheme.TEXT_PRIMARY
				: EpsilonPanelTheme.TEXT_SECONDARY;
		
		float iconX = item.x() + EpsilonPanelLayout.RAIL_ICON_CENTER_X;
		graphics.fill(Math.round(iconX - 4F),
			Math.round(item.centerY() - 4F), Math.round(iconX + 4F),
			Math.round(item.centerY() + 4F), color);
		graphics.fill(Math.round(iconX - 1F),
			Math.round(item.centerY() - 1F), Math.round(iconX + 1F),
			Math.round(item.centerY() + 1F),
			EpsilonPanelTheme.SURFACE_DIM);
		
		if(expansion <= 0.05F)
			return;
		
		text(graphics, SETTINGS_LABEL,
			item.x() + EpsilonPanelLayout.RAIL_LABEL_INSET
				+ (1F - expansion) * 5F,
			item.centerY() - textHeight(0.62F) / 2F, 0.62F,
			EpsilonPanelTheme.withAlpha(color, expansion));
	}
	
	private int countModules(Category category)
	{
		return categoryCounts.computeIfAbsent(category, key -> {
			int count = 0;
			
			for(Feature feature : FeatureMenuSupport.getAllFeatures())
				if(feature.getCategory() == key)
					count++;
			
			return count;
		});
	}
	
	private void drawModuleList(GuiGraphics graphics, int mouseX, int mouseY)
	{
		Rect column = frame.modules();
		Rect viewport = EpsilonPanelLayout.listViewport(column);
		
		text(graphics, selectedCategory.getName(),
			column.x() + EpsilonPanelLayout.LIST_TITLE_X,
			column.y() + EpsilonPanelLayout.LIST_TITLE_Y, 0.78F,
			EpsilonPanelTheme.TEXT_PRIMARY);
		text(graphics, LIST_SUBTITLE,
			column.x() + EpsilonPanelLayout.LIST_TITLE_X,
			column.y() + EpsilonPanelLayout.LIST_SUBTITLE_Y, 0.56F,
			EpsilonPanelTheme.TEXT_SECONDARY);
		
		drawSearchBox(graphics, mouseX, mouseY);
		
		float content = EpsilonPanelLayout.moduleContentHeight(modules.size());
		boolean scrollbar = EpsilonPanelLayout.maxScroll(content, viewport) > 0F;
		listScroll = clampScroll(listScroll, content, viewport);
		
		graphics.enableScissor(Math.round(viewport.x()),
			Math.round(viewport.y()), Math.round(viewport.right()),
			Math.round(viewport.bottom()));
		
		for(int i = 0; i < modules.size(); i++)
		{
			Rect row =
				EpsilonPanelLayout.moduleRow(viewport, i, listScroll, scrollbar);
			
			if(!row.intersectsVertically(viewport))
				continue;
			
			drawModuleRow(graphics, row, modules.get(i), mouseX, mouseY);
		}
		
		graphics.disableScissor();
		
		drawScrollbar(graphics, viewport, content, listScroll, mouseX, mouseY);
	}
	
	private void drawSearchBox(GuiGraphics graphics, int mouseX, int mouseY)
	{
		Rect box = EpsilonPanelLayout.searchBox(frame.modules());
		boolean hovered = box.contains(mouseX, mouseY);
		float hover = anim("search", hovered ? 1F : 0F, HOVER_MS);
		
		int fill = searchFocused
			? EpsilonPanelTheme.mix(EpsilonPanelTheme.SURFACE_CONTAINER_HIGH,
				EpsilonPanelTheme.PRIMARY_CONTAINER, 0.42F)
			: EpsilonPanelTheme.mix(EpsilonPanelTheme.SURFACE_CONTAINER_LOW,
				EpsilonPanelTheme.SURFACE_CONTAINER_HIGHEST, hover * 0.85F);
		rrect(graphics, box, EpsilonPanelTheme.CONTROL_RADIUS, fill);
		
		String value = search.toString();
		boolean empty = value.isEmpty();
		text(graphics, empty ? SEARCH_HINT : value, box.x() + 8F,
			box.centerY() - textHeight(0.52F) / 2F, 0.52F,
			empty ? EpsilonPanelTheme.mix(EpsilonPanelTheme.TEXT_MUTED,
				EpsilonPanelTheme.TEXT_PRIMARY, searchFocused ? 1F : 0F)
				: EpsilonPanelTheme.TEXT_PRIMARY);
		
		if(searchFocused)
		{
			float caretX = box.x() + 8F + textWidth(value, 0.52F) + 1F;
			graphics.fill(Math.round(caretX), Math.round(box.y() + 4F),
				Math.round(caretX) + 1, Math.round(box.bottom() - 4F),
				EpsilonPanelTheme.PRIMARY);
		}
	}
	
	private void drawModuleRow(GuiGraphics graphics, Rect row, Feature feature,
		int mouseX, int mouseY)
	{
		boolean hovered = row.contains(mouseX, mouseY);
		float hover = anim(ROW_HOVER_PREFIX + feature.getName(),
			hovered ? 1F : 0F, HOVER_MS);
		boolean selected = feature == selectedModule;
		
		rrect(graphics, row, EpsilonPanelTheme.CARD_RADIUS,
			EpsilonPanelTheme.rowSurface(hover));
		
		if(selected)
			rrect(graphics, row, EpsilonPanelTheme.CARD_RADIUS,
				EpsilonPanelTheme.withAlpha(EpsilonPanelTheme.PRIMARY, 42));
		
		float textX = row.x() + EpsilonPanelLayout.MODULE_LABEL_INSET;
		float nameHeight = textHeight(0.70F);
		float subtitleHeight = textHeight(0.60F);
		float blockTop = row.centerY() - (nameHeight + 2F + subtitleHeight) / 2F;
		
		int nameColor = selected
			? EpsilonPanelTheme.mix(EpsilonPanelTheme.TEXT_PRIMARY,
				EpsilonPanelTheme.ON_PRIMARY_CONTAINER, 1F)
			: EpsilonPanelTheme.TEXT_PRIMARY;
		text(graphics, feature.getDisplayName(), textX, blockTop, 0.70F,
			nameColor);
		text(graphics, feature.getCategory().getName(), textX,
			blockTop + nameHeight + 2F, 0.60F,
			selected
				? EpsilonPanelTheme.withAlpha(
					EpsilonPanelTheme.ON_PRIMARY_CONTAINER, 180)
				: EpsilonPanelTheme.TEXT_SECONDARY);
		
		// 键位文字：止于开关左侧
		Rect toggle = EpsilonPanelLayout.moduleSwitch(row);
		String key = keybindOf(feature);
		if(!key.isEmpty())
			text(graphics, key,
				toggle.x() - 8F - textWidth(key, 0.60F),
				row.centerY() - textHeight(0.60F) / 2F, 0.60F,
				EpsilonPanelTheme.TEXT_MUTED);
		
		drawSwitch(graphics, toggle, switchProgress(feature), hovered);
	}
	
	private void drawDetail(GuiGraphics graphics, int mouseX, int mouseY)
	{
		Rect detail = frame.detail();
		Rect header = EpsilonPanelLayout.detailHeader(detail);
		Rect viewport = EpsilonPanelLayout.detailViewport(detail);
		
		String title =
			selectedModule == null ? "未选择模块" : selectedModule.getDisplayName();
		text(graphics, title,
			detail.x() + EpsilonPanelLayout.DETAIL_TITLE_X,
			detail.y() + EpsilonPanelLayout.DETAIL_TITLE_Y
				+ (EpsilonPanelLayout.DETAIL_TITLE_BAR - textHeight(0.78F)) / 2F,
			0.78F, EpsilonPanelTheme.TEXT_PRIMARY);
		
		rrect(graphics, header, EpsilonPanelTheme.CARD_RADIUS,
			EpsilonPanelTheme.SURFACE_CONTAINER);
		
		if(selectedModule != null)
		{
			drawKeybindBlock(graphics, header, mouseX, mouseY);
			drawSegments(graphics, header);
		}
		
		if(selectedModule == null)
			return;
		
		float content =
			EpsilonPanelLayout.settingContentHeight(settings.size());
		boolean scrollbar = EpsilonPanelLayout.maxScroll(content, viewport) > 0F;
		detailScroll = clampScroll(detailScroll, content, viewport);
		
		graphics.enableScissor(Math.round(viewport.x()),
			Math.round(viewport.y()), Math.round(viewport.right()),
			Math.round(viewport.bottom()));
		
		for(int i = 0; i < settings.size(); i++)
		{
			Rect row = EpsilonPanelLayout.settingRow(viewport, i,
				detailScroll, scrollbar);
			
			if(!row.intersectsVertically(viewport))
				continue;
			
			drawSettingRow(graphics, row, settings.get(i), mouseX, mouseY);
		}
		
		graphics.disableScissor();
		
		drawScrollbar(graphics, viewport, content, detailScroll, mouseX, mouseY);
	}
	
	private void drawKeybindBlock(GuiGraphics graphics, Rect header, int mouseX,
		int mouseY)
	{
		Rect block = EpsilonPanelLayout.detailKeybindBlock(header);
		float focus = listeningForBind ? 1F
			: anim("keybind", block.contains(mouseX, mouseY) ? 1F : 0F,
				HOVER_MS);
		
		if(focus > 0.01F)
			rrect(graphics, block.inset(-3F), 11,
				EpsilonPanelTheme.withAlpha(EpsilonPanelTheme.PRIMARY,
					Math.round(28F * focus)));
		
		rrect(graphics, block, 8,
			EpsilonPanelTheme.mix(EpsilonPanelTheme.SECONDARY_CONTAINER,
				EpsilonPanelTheme.PRIMARY_CONTAINER, focus));
		
		String key = listeningForBind ? "..." : keybindOf(selectedModule);
		if(key.isEmpty())
			key = "无";
		
		text(graphics, key, block.centerX() - textWidth(key, 0.42F) / 2F,
			block.centerY() - textHeight(0.42F) / 2F, 0.42F,
			EpsilonPanelTheme.mix(EpsilonPanelTheme.ON_SECONDARY_CONTAINER,
				EpsilonPanelTheme.ON_PRIMARY_CONTAINER, focus));
	}
	
	/**
	 * 参考项目在这里放两个分段控件（切换/保持、显示/隐藏）。WurstB 的按键只有
	 * 切换语义，模块也没有「是否显示」标志，所以只保留一个真能用的
	 * 「开启/关闭」，位置沿用参考项目的第一个分段控件。
	 */
	private void drawSegments(GuiGraphics graphics, Rect header)
	{
		drawSegmented(graphics, EpsilonPanelLayout.detailBindSegment(header),
			new String[]{"开启", "关闭"}, selectedModule.isEnabled() ? 0 : 1);
	}
	
	/** 分段控件：外框 + 内底 + 指示块 + 分隔线。 */
	private void drawSegmented(GuiGraphics graphics, Rect rect, String[] labels,
		int active)
	{
		rrect(graphics, rect, EpsilonPanelTheme.CONTROL_RADIUS,
			EpsilonPanelTheme.OUTLINE_SOFT);
		
		Rect inner = rect.inset(1F);
		rrect(graphics, inner, EpsilonPanelTheme.CONTROL_RADIUS - 1F,
			EpsilonPanelTheme.segmentedSurface());
		
		float segment = inner.width() / labels.length;
		rrect(graphics, new Rect(inner.x() + segment * active + 1.5F,
			inner.y() + 1.5F, segment - 3F,
			inner.height() - 3F).inset(0F), 5F,
			EpsilonPanelTheme.SECONDARY_CONTAINER);
		
		for(int i = 1; i < labels.length; i++)
		{
			float x = inner.x() + segment * i;
			graphics.fill(Math.round(x), Math.round(inner.y() + 3F),
				Math.round(x) + 1, Math.round(inner.bottom() - 3F),
				EpsilonPanelTheme.OUTLINE_SOFT);
		}
		
		for(int i = 0; i < labels.length; i++)
			text(graphics, labels[i],
				inner.x() + segment * i + segment / 2F
					- textWidth(labels[i], 0.52F) / 2F,
				inner.centerY() - textHeight(0.52F) / 2F, 0.52F,
				i == active ? EpsilonPanelTheme.ON_SECONDARY_CONTAINER
					: EpsilonPanelTheme.TEXT_MUTED);
	}
	
	private void drawSettingRow(GuiGraphics graphics, Rect row, Setting setting,
		int mouseX, int mouseY)
	{
		boolean hovered = row.contains(mouseX, mouseY);
		float hover = anim(SETTING_HOVER_PREFIX + setting.getName(),
			hovered ? 1F : 0F, HOVER_MS);
		float indent = setting.getDepth() * 8F;
		Rect content = new Rect(row.x() + indent, row.y(),
			Math.max(0F, row.width() - indent), row.height());
		
		rrect(graphics, row, EpsilonPanelTheme.CARD_RADIUS,
			EpsilonPanelTheme.rowSurface(hover));
		
		float labelX = content.x() + EpsilonPanelTheme.ROW_CONTENT_INSET;
		int labelColor = setting.isVisible()
			? EpsilonPanelTheme.TEXT_PRIMARY : EpsilonPanelTheme.TEXT_MUTED;
		
		if(setting instanceof SliderSetting slider)
		{
			text(graphics, setting.getName(), labelX,
				row.centerY() - textHeight(0.68F) / 2F, 0.68F, labelColor);
			
			Rect track = EpsilonPanelLayout.settingSliderTrack(content);
			sliderTracks.put(slider, track);
			Rect field = EpsilonPanelLayout.settingValueField(content);
			
			rrect(graphics, track, 3F, EpsilonPanelTheme.SECONDARY_CONTAINER);
			float progress = (float)Math.max(0D,
				Math.min(1D, slider.getPercentage()));
			float filled = Math.max(2F, track.width() * progress - 2.5F);
			rrect(graphics, new Rect(track.x(), track.y(), filled,
				track.height()), 3F, EpsilonPanelTheme.PRIMARY);
			
			float knobX = track.x() + track.width() * progress;
			float knobWidth = draggingSlider == slider ? 0F : 2F;
			rrect(graphics, new Rect(knobX - knobWidth / 2F,
				track.centerY() - 7F, Math.max(1F, knobWidth), 14F), 1F,
				EpsilonPanelTheme.PRIMARY);
			
			rrect(graphics, field, EpsilonPanelTheme.CONTROL_RADIUS,
				EpsilonPanelTheme.SURFACE_CONTAINER_LOW);
			String value = slider.getValueString();
			text(graphics, value,
				field.centerX() - textWidth(value, 0.60F) / 2F,
				field.centerY() - textHeight(0.60F) / 2F, 0.60F,
				EpsilonPanelTheme.TEXT_PRIMARY);
			return;
		}
		
		if(setting instanceof CheckboxSetting checkbox)
		{
			text(graphics, setting.getName(), labelX,
				row.centerY() - textHeight(0.68F) / 2F, 0.68F, labelColor);
			Rect toggle = EpsilonPanelLayout.settingSwitch(content);
			checkboxRows.put(setting, toggle);
			drawSwitch(graphics, toggle,
				anim("check:" + setting.getName(),
					checkbox.isChecked() ? 1F : 0F, SWITCH_MS, true),
				hovered);
			return;
		}
		
		if(setting instanceof EnumSetting<?> enumSetting)
		{
			text(graphics, setting.getName(), labelX,
				row.centerY() - textHeight(0.68F) / 2F, 0.68F, labelColor);
			
			String value = String.valueOf(enumSetting.getSelected());
			Rect chip = EpsilonPanelLayout.settingChip(content,
				textWidth(value, 0.60F));
			enumChips.put(setting, chip);
			
			rrect(graphics, chip, EpsilonPanelTheme.CONTROL_RADIUS,
				EpsilonPanelTheme.SECONDARY_CONTAINER);
			text(graphics, value, chip.x() + 8F,
				chip.centerY() - textHeight(0.60F) / 2F, 0.60F,
				EpsilonPanelTheme.ON_SECONDARY_CONTAINER);
			
			float arrowX = chip.right() - 7.5F;
			float arrowY = chip.centerY();
			graphics.fill(Math.round(arrowX - 3F), Math.round(arrowY - 1.5F),
				Math.round(arrowX + 3F), Math.round(arrowY - 0.5F),
				EpsilonPanelTheme.ON_SECONDARY_CONTAINER);
			return;
		}
		
		String value = setting instanceof TextFieldSetting textField
			? textField.getValue() : setting.getDescription();
		text(graphics, setting.getName(), labelX,
			row.centerY() - textHeight(0.68F) / 2F, 0.68F, labelColor);
		float nameWidth = textWidth(setting.getName(), 0.68F);
		text(graphics, trim(value,
			content.right() - labelX - nameWidth - 12F, 0.60F),
			labelX + nameWidth + 6F,
			row.centerY() - textHeight(0.60F) / 2F, 0.60F,
			EpsilonPanelTheme.TEXT_SECONDARY);
	}
	
	private void drawClientSettings(GuiGraphics graphics, int mouseX,
		int mouseY)
	{
		Rect detail = frame.detail();
		Rect viewport = EpsilonPanelLayout.listViewport(detail);
		
		text(graphics, SETTINGS_LABEL,
			frame.modules().x() + EpsilonPanelLayout.LIST_TITLE_X,
			frame.modules().y() + EpsilonPanelLayout.LIST_TITLE_Y, 0.78F,
			EpsilonPanelTheme.TEXT_PRIMARY);
		text(graphics, "面板与 ClickGUI 的独立开关",
			frame.modules().x() + EpsilonPanelLayout.LIST_TITLE_X,
			frame.modules().y() + EpsilonPanelLayout.LIST_SUBTITLE_Y, 0.56F,
			EpsilonPanelTheme.TEXT_SECONDARY);
		
		String[] labels = {"Rise 模式", "GUI 风格"};
		String[] values = {WURST.getGuiPreferences().isRiseMode() ? "开" : "关",
			WURST.getGuiPreferences().getClickGuiStyle().displayName()};
		
		graphics.enableScissor(Math.round(viewport.x()),
			Math.round(viewport.y()), Math.round(viewport.right()),
			Math.round(viewport.bottom()));
		
		for(int i = 0; i < labels.length; i++)
		{
			Rect row = EpsilonPanelLayout.settingRow(viewport, i, 0F, false);
			boolean hovered = row.contains(mouseX, mouseY);
			float hover = anim("client:" + labels[i], hovered ? 1F : 0F,
				HOVER_MS);
			
			rrect(graphics, row, EpsilonPanelTheme.CARD_RADIUS,
				EpsilonPanelTheme.rowSurface(hover));
			text(graphics, labels[i],
				row.x() + EpsilonPanelTheme.ROW_CONTENT_INSET,
				row.centerY() - textHeight(0.68F) / 2F, 0.68F,
				EpsilonPanelTheme.TEXT_PRIMARY);
			
			Rect chip = EpsilonPanelLayout.settingChip(row,
				textWidth(values[i], 0.60F));
			rrect(graphics, chip, EpsilonPanelTheme.CONTROL_RADIUS,
				EpsilonPanelTheme.SECONDARY_CONTAINER);
			text(graphics, values[i], chip.x() + 8F,
				chip.centerY() - textHeight(0.60F) / 2F, 0.60F,
				EpsilonPanelTheme.ON_SECONDARY_CONTAINER);
		}
		
		graphics.disableScissor();
		
		Rect hint = EpsilonPanelLayout.settingRow(viewport, 3, 0F, false);
		text(graphics, "Rise 模式打开后，导航器(右 Shift)改用原有的 Rise 界面。",
			hint.x() + EpsilonPanelTheme.ROW_CONTENT_INSET, hint.y(), 0.56F,
			EpsilonPanelTheme.TEXT_MUTED);
		text(graphics, "左键 rail 底部此项可返回本界面所在的面板模式。",
			hint.x() + EpsilonPanelTheme.ROW_CONTENT_INSET,
			hint.y() + textHeight(0.56F) + 3F, 0.56F,
			EpsilonPanelTheme.TEXT_MUTED);
	}
	
	/** 开关：轨道 + 描边 + hover 光晕 + 可拉伸手柄。 */
	private void drawSwitch(GuiGraphics graphics, Rect rect, float progress,
		boolean hovered)
	{
		float track = Math.max(0F, Math.min(1F, progress));
		
		rrect(graphics, rect, rect.height() / 2F,
			EpsilonPanelTheme.switchTrack(track));
		
		int outline = EpsilonPanelTheme.switchOutline(hovered ? 1F : 0F, track);
		if((outline >>> 24) > 0)
			FlatRenderer.drawRoundedOutline(graphics, Math.round(rect.x()),
				Math.round(rect.y()), Math.round(rect.right()),
				Math.round(rect.bottom()), Math.round(rect.height() / 2F),
				outline);
		
		if(hovered)
			rrect(graphics, new Rect(rect.centerX() - 10F, rect.centerY() - 10F,
				20F, 20F), 10F,
				EpsilonPanelTheme.withAlpha(EpsilonPanelTheme.TEXT_PRIMARY,
					18));
		
		float size = 8F + 4F * track;
		float width = size + 3.5F * 4F * track * (1F - track);
		float inset = 4F - 2F * track;
		rrect(graphics, new Rect(rect.x() + inset, rect.centerY() - size / 2F,
			Math.max(1F, width), size), size / 2F,
			EpsilonPanelTheme.switchKnob(track));
	}
	
	private void drawScrollbar(GuiGraphics graphics, Rect viewport,
		float content, float scroll, int mouseX, int mouseY)
	{
		Rect thumb = EpsilonPanelLayout.scrollThumb(viewport, content, scroll);
		
		if(thumb == null)
			return;
		
		Rect hit = new Rect(viewport.right() - EpsilonPanelLayout.SCROLL_HIT_WIDTH,
			viewport.y(), EpsilonPanelLayout.SCROLL_HIT_WIDTH,
			viewport.height());
		float hover = anim("scroll:" + Math.round(viewport.y()),
			hit.contains(mouseX, mouseY) ? 1F : 0F, HOVER_MS);
		float width = EpsilonPanelLayout.SCROLL_THUMB_WIDTH
			+ (EpsilonPanelLayout.SCROLL_THUMB_WIDTH_HOVER
				- EpsilonPanelLayout.SCROLL_THUMB_WIDTH) * hover;
		
		rrect(graphics, new Rect(thumb.right() - width, thumb.y(), width,
			thumb.height()), width / 2F,
			EpsilonPanelTheme.scrollThumb(hover));
	}
	
	private void drawShadow(GuiGraphics graphics, Rect rect, int radius,
		int spread)
	{
		for(int i = spread; i >= 1; i--)
			FlatRenderer.fillRoundedRect(graphics,
				Math.round(rect.x()) - i, Math.round(rect.y()) - i + 2,
				Math.round(rect.right()) + i,
				Math.round(rect.bottom()) + i + 2, radius + i,
				EpsilonPanelTheme.withAlpha(EpsilonPanelTheme.SHADOW,
					EpsilonPanelTheme.PANEL_SHADOW_ALPHA / (spread + 1)));
	}
	
	// ------------------------------------------------------------------
	// 文本
	// ------------------------------------------------------------------
	
	private float textScale(float scale)
	{
		return Math.max(0.25F, scale * TEXT_BASE) / Math.max(1F, font.lineHeight);
	}
	
	private void text(GuiGraphics graphics, String value, float x, float y,
		float scale, int color)
	{
		if(value == null || value.isEmpty() || (color >>> 24) == 0)
			return;
		
		float factor = textScale(scale);
		graphics.pose().pushPose();
		graphics.pose().translate(x, y, 0F);
		graphics.pose().scale(factor, factor, 1F);
		graphics.drawString(font, value, 0, 0, color, false);
		graphics.pose().popPose();
	}
	
	private float textWidth(String value, float scale)
	{
		if(value == null || value.isEmpty())
			return 0F;
		
		return font.width(value) * textScale(scale);
	}
	
	private float textHeight(float scale)
	{
		return font.lineHeight * textScale(scale);
	}
	
	private String trim(String value, float maxWidth, float scale)
	{
		if(value == null)
			return "";
		
		if(textWidth(value, scale) <= maxWidth)
			return value;
		
		String result = value;
		
		while(result.length() > 1
			&& textWidth(result + "...", scale) > maxWidth)
			result = result.substring(0, result.length() - 1);
		
		return result + "...";
	}
	
	private static void rrect(GuiGraphics graphics, Rect rect, float radius,
		int color)
	{
		if(rect.width() <= 0F || rect.height() <= 0F || (color >>> 24) == 0)
			return;
		
		FlatRenderer.fillRoundedRect(graphics, Math.round(rect.x()),
			Math.round(rect.y()), Math.round(rect.right()),
			Math.round(rect.bottom()), Math.max(0, Math.round(radius)), color);
	}
	
	// ------------------------------------------------------------------
	// 动画
	// ------------------------------------------------------------------
	
	/**
	 * 朝目标推进的缓动。每帧走「剩余距离 × easeOutCubic(经过时间/时长)」，
	 * 所以不需要为每个元素记录起始值，也不会把动画卡在中途。
	 */
	private float anim(Object key, float target, long durationMs)
	{
		return anim(key, target, durationMs, false);
	}
	
	private float anim(Object key, float target, long durationMs,
		boolean elastic)
	{
		long now = Util.getMillis();
		Float stored = anims.get(key);
		Long time = animTimes.get(key);
		
		if(stored == null || time == null)
		{
			anims.put(key, target);
			animTimes.put(key, now);
			return target;
		}
		
		float current = stored;
		float delta = target - current;
		
		if(Math.abs(delta) < 0.001F || durationMs <= 0L)
		{
			anims.put(key, target);
			animTimes.put(key, now);
			return target;
		}
		
		float elapsed = Math.max(1L, now - time) / (float)durationMs;
		float step = elastic ? easeOutElastic(Math.min(1F, elapsed))
			: easeOutCubic(Math.min(1F, elapsed));
		float next = current + delta * step;
		
		anims.put(key, next);
		animTimes.put(key, now);
		return next;
	}
	
	/** 开关用：从当前值出发的一次定时动画，带轻微回弹。 */
	private float switchProgress(Feature feature)
	{
		SwitchAnim state = switches.get(feature);
		float target = feature.isEnabled() ? 1F : 0F;
		long now = Util.getMillis();
		
		if(state == null)
		{
			switches.put(feature,
				new SwitchAnim(target, target, now, 0L));
			return target;
		}
		
		if(state.to() != target)
		{
			state = new SwitchAnim(state.value(now), target, now, SWITCH_MS);
			switches.put(feature, state);
		}
		
		return state.value(now);
	}
	
	private static float easeOutCubic(float t)
	{
		float inverse = 1F - Math.max(0F, Math.min(1F, t));
		return 1F - inverse * inverse * inverse;
	}
	
	private static float easeOutElastic(float t)
	{
		float x = Math.max(0F, Math.min(1F, t));
		
		if(x <= 0F)
			return 0F;
		
		if(x >= 1F)
			return 1F;
		
		double c = 2D * Math.PI / 3D;
		return (float)(Math.pow(2D, -10D * x) * Math.sin((x * 10D - 0.75D) * c)
			+ 1D);
	}
	
	private record SwitchAnim(float from, float to, long start, long duration)
	{
		private float value(long now)
		{
			if(duration <= 0L)
				return to;
			
			float t = Math.max(0F,
				Math.min(1F, (now - start) / (float)duration));
			return from + (to - from) * easeOutElastic(t);
		}
	}
	
	private static float clampScroll(float scroll, float content,
		Rect viewport)
	{
		return Math.max(0F,
			Math.min(EpsilonPanelLayout.maxScroll(content, viewport), scroll));
	}
	
	// ------------------------------------------------------------------
	// 输入
	// ------------------------------------------------------------------
	
	private double currentMouseX;
	private double currentMouseY;
	
	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button)
	{
		if(frame == null)
			frame = EpsilonPanelLayout.compute(width, height,
				EpsilonPanelLayout.railWidth(railExpanded ? 1F : 0F));
		
		if(button != 0)
			return super.mouseClicked(mouseX, mouseY, button);
		
		// 路由顺序与参考项目一致：详情/列表 → rail
		if(clientSettingsMode)
		{
			if(handleClientSettingsClick(mouseX, mouseY))
				return true;
		}else
		{
			if(handleDetailClick(mouseX, mouseY))
				return true;
			
			if(handleListClick(mouseX, mouseY))
				return true;
		}
		
		if(handleRailClick(mouseX, mouseY))
			return true;
		
		searchFocused = false;
		return super.mouseClicked(mouseX, mouseY, button);
	}
	
	private boolean handleRailClick(double mouseX, double mouseY)
	{
		Rect rail = frame.rail();
		
		if(EpsilonPanelLayout.railMenuButton(rail).contains(mouseX, mouseY))
		{
			railExpanded = !railExpanded;
			return true;
		}
		
		Category[] categories = Category.values();
		
		for(int i = 0; i < categories.length; i++)
		{
			Rect item = EpsilonPanelLayout.railItem(rail, i);
			
			if(item.y() + item.height() > rail.bottom() - 40F)
				break;
			
			if(!item.contains(mouseX, mouseY))
				continue;
			
			clientSettingsMode = false;
			selectedCategory = categories[i];
			listScroll = 0F;
			listVelocity = 0F;
			modulesDirty = true;
			searchFocused = false;
			return true;
		}
		
		if(EpsilonPanelLayout.railSettingsItem(rail).contains(mouseX, mouseY))
		{
			clientSettingsMode = true;
			searchFocused = false;
			return true;
		}
		
		return false;
	}
	
	private boolean handleListClick(double mouseX, double mouseY)
	{
		Rect viewport = EpsilonPanelLayout.listViewport(frame.modules());
		
		if(EpsilonPanelLayout.searchBox(frame.modules()).contains(mouseX,
			mouseY))
		{
			searchFocused = true;
			return true;
		}
		
		if(!viewport.contains(mouseX, mouseY))
			return false;
		
		float content = EpsilonPanelLayout.moduleContentHeight(modules.size());
		
		if(EpsilonPanelLayout.scrollThumb(viewport, content, listScroll) != null
			&& mouseX >= viewport.right()
				- EpsilonPanelLayout.SCROLL_HIT_WIDTH)
		{
			draggingScrollbar = viewport;
			draggingScrollIsList = true;
			return true;
		}
		
		searchFocused = false;
		
		for(int i = 0; i < modules.size(); i++)
		{
			Rect row = EpsilonPanelLayout.moduleRow(viewport, i, listScroll,
				EpsilonPanelLayout.maxScroll(content, viewport) > 0F);
			
			if(!row.contains(mouseX, mouseY))
				continue;
			
			Feature feature = modules.get(i);
			
			// 落在开关上就开关模块，否则只切换选中
			if(EpsilonPanelLayout.moduleSwitch(row).contains(mouseX, mouseY))
			{
				feature.doPrimaryAction();
				notifyToggle(feature);
				return true;
			}
			
			if(feature != selectedModule)
			{
				selectedModule = feature;
				detailScroll = 0F;
				detailVelocity = 0F;
				rebuildSettings();
			}
			
			return true;
		}
		
		return false;
	}
	
	private void notifyToggle(Feature feature)
	{
		if(WURST.getHudManager() != null)
			WURST.getHudManager().addNotification(feature);
	}
	
	private boolean handleDetailClick(double mouseX, double mouseY)
	{
		Rect detail = frame.detail();
		
		if(selectedModule == null)
			return false;
		
		if(EpsilonPanelLayout.detailKeybindBlock(
			EpsilonPanelLayout.detailHeader(detail)).contains(mouseX, mouseY))
		{
			String command = keybindCommandOf(selectedModule);
			
			if(command == null)
				return true;
			
			listeningForBind = true;
			listenCommand = command;
			return true;
		}
		
		if(EpsilonPanelLayout.detailBindSegment(
			EpsilonPanelLayout.detailHeader(detail)).contains(mouseX, mouseY))
		{
			selectedModule.doPrimaryAction();
			notifyToggle(selectedModule);
			return true;
		}
		
		Rect viewport = EpsilonPanelLayout.detailViewport(detail);
		
		if(!viewport.contains(mouseX, mouseY))
			return false;
		
		float content = EpsilonPanelLayout.settingContentHeight(settings.size());
		boolean scrollbar = EpsilonPanelLayout.maxScroll(content, viewport) > 0F;
		
		if(scrollbar
			&& mouseX >= viewport.right()
				- EpsilonPanelLayout.SCROLL_HIT_WIDTH)
		{
			draggingScrollbar = viewport;
			draggingScrollIsList = false;
			return true;
		}
		
		for(int i = 0; i < settings.size(); i++)
		{
			Rect row = EpsilonPanelLayout.settingRow(viewport, i, detailScroll,
				scrollbar);
			
			if(!row.contains(mouseX, mouseY))
				continue;
			
			Setting setting = settings.get(i);
			
			if(setting instanceof SliderSetting slider)
			{
				Rect track = sliderTracks.get(slider);
				
				if(track != null && mouseX >= track.x() - 2F
					&& mouseX <= track.right() + 2F && mouseY >= track.y() - 6F
					&& mouseY <= track.bottom() + 6F)
				{
					draggingSlider = slider;
					applySlider(slider, track, mouseX);
					return true;
				}
				
			}else if(setting instanceof CheckboxSetting checkbox)
			{
				Rect toggle = checkboxRows.get(setting);
				
				if(toggle != null && toggle.contains(mouseX, mouseY))
				{
					checkbox.setChecked(!checkbox.isChecked());
					return true;
				}
				
			}else if(setting instanceof EnumSetting<?> enumSetting)
			{
				Rect chip = enumChips.get(setting);
				
				if(chip != null && chip.contains(mouseX, mouseY))
				{
					enumSetting.selectNext();
					return true;
				}
			}
			
			// 有子项的行当组头用：点一下折叠/展开
			if(setting.hasChildren())
			{
				setting.setExpanded(!setting.isExpanded());
				rebuildSettings();
			}
			
			return true;
		}
		
		return false;
	}
	
	private static void applySlider(SliderSetting slider, Rect track,
		double mouseX)
	{
		double progress = (mouseX - track.x()) / Math.max(1F, track.width());
		slider.setValue(
			slider.getMinimum() + slider.getRange() * clamp01(progress));
	}
	
	private static double clamp01(double value)
	{
		return Math.max(0D, Math.min(1D, value));
	}
	
	private boolean handleClientSettingsClick(double mouseX, double mouseY)
	{
		Rect viewport = EpsilonPanelLayout.listViewport(frame.detail());
		
		if(!viewport.contains(mouseX, mouseY))
			return false;
		
		Rect riseRow = EpsilonPanelLayout.settingRow(viewport, 0, 0F, false);
		
		if(riseRow.contains(mouseX, mouseY))
		{
			// 与 vapeMode 一样是独立开关；打开即切到原有的 Rise 导航器
			WURST.getGuiPreferences().setRiseMode(true);
			WurstClient.MC.setScreen(new NavigatorScreen());
			return true;
		}
		
		Rect styleRow = EpsilonPanelLayout.settingRow(viewport, 1, 0F, false);
		
		if(styleRow.contains(mouseX, mouseY))
		{
			net.wurstclient.clickgui2.ClickGuiScreens.cycleStyle();
			return true;
		}
		
		return false;
	}
	
	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button,
		double dragX, double dragY)
	{
		if(button != 0)
			return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
		
		if(draggingSlider != null)
		{
			Rect track = sliderTracks.get(draggingSlider);
			
			if(track != null)
				applySlider(draggingSlider, track, mouseX);
			
			return true;
		}
		
		if(draggingScrollbar != null)
		{
			float content = draggingScrollIsList
				? EpsilonPanelLayout.moduleContentHeight(modules.size())
				: EpsilonPanelLayout.settingContentHeight(settings.size());
			float maxScroll =
				EpsilonPanelLayout.maxScroll(content, draggingScrollbar);
			Rect thumb = EpsilonPanelLayout.scrollThumb(draggingScrollbar,
				content, draggingScrollIsList ? listScroll : detailScroll);
			
			if(thumb != null && maxScroll > 0F)
			{
				float track = draggingScrollbar.height() - thumb.height();
				// 以滑块中心对齐光标，与参考项目的抓取方式一致
				float progress = track <= 0F ? 0F
					: (float)((mouseY - draggingScrollbar.y()
						- thumb.height() / 2F) / track);
				float scroll = (float)clamp01(progress) * maxScroll;
				
				if(draggingScrollIsList)
				{
					listScroll = scroll;
					listVelocity = 0F;
				}else
				{
					detailScroll = scroll;
					detailVelocity = 0F;
				}
			}
			
			return true;
		}
		
		return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
	}
	
	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button)
	{
		if(button == 0)
		{
			draggingSlider = null;
			draggingScrollbar = null;
		}
		
		return super.mouseReleased(mouseX, mouseY, button);
	}
	
	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double delta)
	{
		if(delta == 0D || frame == null)
			return super.mouseScrolled(mouseX, mouseY, delta);
		
		Rect viewport = clientSettingsMode
			? EpsilonPanelLayout.listViewport(frame.detail())
			: EpsilonPanelLayout.detailViewport(frame.detail());
		
		if(detailScrollable() && viewport.contains(mouseX, mouseY))
		{
			detailVelocity -= (float)delta * SCROLL_STEP;
			return true;
		}
		
		listVelocity -= (float)delta * SCROLL_STEP;
		return true;
	}
	
	private boolean detailScrollable()
	{
		if(clientSettingsMode)
			return false;
		
		return EpsilonPanelLayout.settingContentHeight(settings.size()) > 0F;
	}
	
	@Override
	public boolean charTyped(char codePoint, int modifiers)
	{
		if(!searchFocused)
			return super.charTyped(codePoint, modifiers);
		
		if(search.length() >= 32 || codePoint < ' ')
			return true;
		
		search.append(codePoint);
		modulesDirty = true;
		return true;
	}
	
	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers)
	{
		// 1. 等待绑定：吃掉所有键
		if(listeningForBind)
		{
			if(keyCode == GLFW.GLFW_KEY_ESCAPE)
			{
				listeningForBind = false;
				return true;
			}
			
			if(listenCommand != null)
			{
				if(keyCode == GLFW.GLFW_KEY_BACKSPACE
					|| keyCode == GLFW.GLFW_KEY_DELETE)
					WURST.getKeybinds().unbindCommand(listenCommand);
				else
				{
					String name =
						InputConstants.getKey(keyCode, scanCode).getName();
					WURST.getKeybinds().bindCommand(name, listenCommand);
				}
			}
			
			listeningForBind = false;
			return true;
		}
		
		// 2. 搜索框
		if(searchFocused)
		{
			if(keyCode == GLFW.GLFW_KEY_ESCAPE)
			{
				searchFocused = false;
				return true;
			}
			
			if(keyCode == GLFW.GLFW_KEY_BACKSPACE && search.length() > 0)
			{
				search.setLength(search.length() - 1);
				modulesDirty = true;
				return true;
			}
			
			// Enter 在参考项目里被消费但不触发动作
			if(keyCode == GLFW.GLFW_KEY_ENTER
				|| keyCode == GLFW.GLFW_KEY_KP_ENTER)
				return true;
		}
		
		// 3. 关闭
		if(keyCode == GLFW.GLFW_KEY_ESCAPE)
		{
			onClose();
			return true;
		}
		
		return super.keyPressed(keyCode, scanCode, modifiers);
	}
}
