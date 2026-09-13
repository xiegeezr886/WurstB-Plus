/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.clickgui2.epsilon;

/**
 * 中央面板 GUI 的几何：所有数字都来自参考项目 Nekoyahouse/Epsilon（GPLv3）的
 * {@code PanelLayout} / {@code MD3Theme} / 各面板视图类，改这里等于改保真度。
 *
 * <p>
 * 面板 = 一张居中的大卡片，内部横排三栏：rail（分类）｜modules（模块列表）｜
 * detail（模块详情）。三栏各自是一张圆角卡片，纵向留 {@code OUTER_PADDING}、
 * 横向留 {@code SECTION_GAP}。
 *
 * <p>
 * 与参考项目一样是「常量优先」而不是自适应：面板宽被夹在 528~584、高被夹在
 * 300~324，所以在小窗口里面板不会缩到看不见。
 *
 * <p>
 * 故意不含 Minecraft 依赖（连 {@code Rect} 都是自己的），这样整套几何可以被单测
 * 覆盖——参考项目自己不做 GUI 测试，这里补上。
 */
public final class EpsilonPanelLayout
{
	/** 面板：`clamp(min(screenW*0.56, 584), 528, ∞)`。 */
	public static final float PANEL_WIDTH_FRACTION = 0.56F;
	public static final float PANEL_WIDTH_MAX = 584.0F;
	public static final float PANEL_WIDTH_MIN = 528.0F;
	
	/** 面板高：`clamp(min(screenH*0.56, 324), 300, ∞)`。 */
	public static final float PANEL_HEIGHT_FRACTION = 0.56F;
	public static final float PANEL_HEIGHT_MAX = 324.0F;
	public static final float PANEL_HEIGHT_MIN = 300.0F;
	
	/** 中栏：`min(164, panelW*0.292)`。 */
	public static final float MODULE_WIDTH_MAX = 164.0F;
	public static final float MODULE_WIDTH_FRACTION = 0.292F;
	
	/** rail 宽度在收起/展开之间做动画。 */
	public static final float RAIL_WIDTH_COLLAPSED = 42.0F;
	public static final float RAIL_WIDTH_EXPANDED = 120.0F;
	
	/** rail 内部：菜单按钮、标题、分隔线、分类项。 */
	public static final float RAIL_MENU_BUTTON = 28.0F;
	public static final float RAIL_MENU_INSET_X = 6.0F;
	public static final float RAIL_MENU_INSET_Y = 4.0F;
	public static final float RAIL_ITEM_HEIGHT = 34.0F;
	public static final float RAIL_ITEM_INSET = 5.0F;
	public static final float RAIL_ITEM_PITCH = 38.0F;
	public static final float RAIL_ITEMS_START_Y = 40.0F;
	public static final float RAIL_LABEL_INSET = 30.0F;
	public static final float RAIL_ICON_CENTER_X = 20.0F;
	public static final float RAIL_DIVIDER_INSET = 7.0F;
	public static final float RAIL_TITLE_X = 38.0F;
	public static final float RAIL_TITLE_Y = 7.0F;
	
	/** 中栏内部：标题两行、搜索框、视口。 */
	public static final float LIST_TITLE_X = 6.0F;
	public static final float LIST_TITLE_Y = 10.0F;
	public static final float LIST_SUBTITLE_Y = 21.0F;
	public static final float SEARCH_WIDTH = 76.0F;
	public static final float SEARCH_HEIGHT = 18.0F;
	public static final float SEARCH_INSET_X = 6.0F;
	public static final float SEARCH_INSET_Y = 8.0F;
	public static final float LIST_VIEWPORT_INSET_X = 3.0F;
	public static final float LIST_VIEWPORT_TOP = 34.0F;
	public static final float LIST_VIEWPORT_BOTTOM = 40.0F;
	public static final float MODULE_ROW_HEIGHT = 34.0F;
	public static final float MODULE_SWITCH_INSET = 5.0F;
	public static final float MODULE_LABEL_INSET = 9.0F;
	
	/** 详情栏内部：标题、header 卡片、设置视口。 */
	public static final float DETAIL_TITLE_X = 6.0F;
	public static final float DETAIL_TITLE_Y = 10.0F;
	public static final float DETAIL_TITLE_BAR = 18.0F;
	public static final float DETAIL_HEADER_TOP = 34.0F;
	public static final float DETAIL_HEADER_HEIGHT = 36.0F;
	public static final float DETAIL_KEYBIND_BLOCK = 18.0F;
	public static final float DETAIL_SEGMENT_WIDTH = 72.0F;
	public static final float DETAIL_SEGMENT_GAP = 6.0F;
	public static final float DETAIL_HEADER_INSET = 8.0F;
	public static final float DETAIL_VIEWPORT_TOP_GAP = 6.0F;
	public static final float DETAIL_VIEWPORT_BOTTOM_GAP = 10.0F;
	public static final float SETTING_ROW_HEIGHT = 28.0F;
	public static final float SETTING_SLIDER_TRACK_Y = 12.0F;
	public static final float SETTING_SLIDER_TRACK_WIDTH = 72.0F;
	public static final float SETTING_SLIDER_TRACK_HEIGHT = 6.0F;
	public static final float SETTING_SLIDER_RIGHT = 116.0F;
	public static final float SETTING_VALUE_FIELD_X = 40.0F;
	public static final float SETTING_VALUE_FIELD_Y = 4.0F;
	public static final float SETTING_VALUE_FIELD_HEIGHT = 18.0F;
	
	/** 滚动条：命中宽 10、滑块宽 3.5（hover 6）、右内缩 2.5、最小高 10。 */
	public static final float SCROLL_HIT_WIDTH = 10.0F;
	public static final float SCROLL_THUMB_WIDTH = 3.5F;
	public static final float SCROLL_THUMB_WIDTH_HOVER = 6.0F;
	public static final float SCROLL_THUMB_INSET = 2.5F;
	public static final float SCROLL_THUMB_MIN_HEIGHT = 10.0F;
	
	private EpsilonPanelLayout()
	{}
	
	public static float panelWidth(int screenWidth)
	{
		return Math.max(PANEL_WIDTH_MIN,
			Math.min(screenWidth * PANEL_WIDTH_FRACTION, PANEL_WIDTH_MAX));
	}
	
	public static float panelHeight(int screenHeight)
	{
		return Math.max(PANEL_HEIGHT_MIN,
			Math.min(screenHeight * PANEL_HEIGHT_FRACTION, PANEL_HEIGHT_MAX));
	}
	
	/** {@code expansion} 为 0 是收起(42)，1 是展开(120)。 */
	public static float railWidth(float expansion)
	{
		float amount = Math.max(0F, Math.min(1F, expansion));
		return RAIL_WIDTH_COLLAPSED
			+ (RAIL_WIDTH_EXPANDED - RAIL_WIDTH_COLLAPSED) * amount;
	}
	
	public static Layout compute(int screenWidth, int screenHeight,
		float railWidth)
	{
		float width = panelWidth(screenWidth);
		float height = panelHeight(screenHeight);
		float x = (screenWidth - width) / 2.0F;
		float y = (screenHeight - height) / 2.0F;
		
		float gap = EpsilonPanelTheme.SECTION_GAP;
		float columnHeight = height - EpsilonPanelTheme.OUTER_PADDING * 2.0F;
		float railX = x + EpsilonPanelTheme.OUTER_PADDING;
		float modulesX = railX + railWidth + gap;
		float moduleWidth =
			Math.min(MODULE_WIDTH_MAX, width * MODULE_WIDTH_FRACTION);
		float maxContentRight = x + width - EpsilonPanelTheme.OUTER_PADDING;
		float detailX = modulesX + moduleWidth + gap;
		float detailWidth = maxContentRight - detailX;
		float columnY = y + EpsilonPanelTheme.OUTER_PADDING;
		
		return new Layout(new Rect(x, y, width, height),
			new Rect(railX, columnY, railWidth, columnHeight),
			new Rect(modulesX, columnY, moduleWidth, columnHeight),
			new Rect(detailX, columnY, detailWidth, columnHeight));
	}
	
	public static Rect railMenuButton(Rect rail)
	{
		return new Rect(rail.x() + RAIL_MENU_INSET_X,
			rail.y() + RAIL_MENU_INSET_Y, RAIL_MENU_BUTTON, RAIL_MENU_BUTTON);
	}
	
	/** 第 {@code index} 个分类项；纵向自 {@code rail.y + 40} 起每项 38。 */
	public static Rect railItem(Rect rail, int index)
	{
		return new Rect(rail.x() + RAIL_ITEM_INSET,
			rail.y() + RAIL_ITEMS_START_Y + index * RAIL_ITEM_PITCH,
			Math.max(0F, rail.width() - RAIL_ITEM_INSET * 2.0F),
			RAIL_ITEM_HEIGHT);
	}
	
	/** 「客户端设置」永远贴 rail 底部。 */
	public static Rect railSettingsItem(Rect rail)
	{
		return new Rect(rail.x() + RAIL_ITEM_INSET,
			rail.bottom() - RAIL_ITEM_HEIGHT - RAIL_ITEM_INSET,
			Math.max(0F, rail.width() - RAIL_ITEM_INSET * 2.0F),
			RAIL_ITEM_HEIGHT);
	}
	
	public static Rect searchBox(Rect modules)
	{
		return new Rect(modules.right() - SEARCH_INSET_X - SEARCH_WIDTH,
			modules.y() + SEARCH_INSET_Y, SEARCH_WIDTH, SEARCH_HEIGHT);
	}
	
	public static Rect listViewport(Rect column)
	{
		return new Rect(column.x() + LIST_VIEWPORT_INSET_X,
			column.y() + LIST_VIEWPORT_TOP,
			Math.max(0F, column.width() - LIST_VIEWPORT_INSET_X * 2.0F),
			Math.max(0F,
				column.height() - LIST_VIEWPORT_BOTTOM));
	}
	
	/** 模块列表内容高：每项 34 + 3 的间距。 */
	public static float moduleContentHeight(int count)
	{
		return Math.max(0, count) * (MODULE_ROW_HEIGHT + EpsilonPanelTheme.ROW_GAP);
	}
	
	/**
	 * 第 {@code index} 行；{@code scroll} 是已经滚过的像素。有滚动条时行宽减
	 * {@code SCROLL_HIT_WIDTH}，与参考项目一致。
	 */
	public static Rect moduleRow(Rect viewport, int index, float scroll,
		boolean scrollbar)
	{
		float width = Math.max(0F, viewport.width()
			- (scrollbar ? SCROLL_HIT_WIDTH : 0F));
		return new Rect(viewport.x(),
			viewport.y()
				+ index * (MODULE_ROW_HEIGHT + EpsilonPanelTheme.ROW_GAP) - scroll,
			width, MODULE_ROW_HEIGHT);
	}
	
	/** 模块行里开关的位置：尾部对齐。 */
	public static Rect moduleSwitch(Rect row)
	{
		return new Rect(row.right() - MODULE_SWITCH_INSET
			- EpsilonPanelTheme.SWITCH_WIDTH,
			row.y() + (MODULE_ROW_HEIGHT - EpsilonPanelTheme.SWITCH_HEIGHT) / 2.0F,
			EpsilonPanelTheme.SWITCH_WIDTH, EpsilonPanelTheme.SWITCH_HEIGHT);
	}
	
	public static Rect detailHeader(Rect detail)
	{
		return new Rect(detail.x() + LIST_VIEWPORT_INSET_X,
			detail.y() + DETAIL_HEADER_TOP,
			Math.max(0F, detail.width() - LIST_VIEWPORT_INSET_X * 2.0F),
			DETAIL_HEADER_HEIGHT);
	}
	
	/** 键位方块：header 左侧 18×18。 */
	public static Rect detailKeybindBlock(Rect header)
	{
		return new Rect(header.x() + DETAIL_HEADER_INSET,
			header.y() + (DETAIL_HEADER_HEIGHT - DETAIL_KEYBIND_BLOCK) / 2.0F,
			DETAIL_KEYBIND_BLOCK, DETAIL_KEYBIND_BLOCK);
	}
	
	/** 切换/保持分段控件，紧跟键位方块。 */
	public static Rect detailBindSegment(Rect header)
	{
		return new Rect(detailKeybindBlock(header).right() + DETAIL_SEGMENT_GAP,
			header.y() + (DETAIL_HEADER_HEIGHT - EpsilonPanelTheme.CONTROL_HEIGHT)
				/ 2.0F,
			DETAIL_SEGMENT_WIDTH, EpsilonPanelTheme.CONTROL_HEIGHT);
	}
	
	/** 显示/隐藏分段控件，尾部对齐。 */
	public static Rect detailVisibilitySegment(Rect header)
	{
		return new Rect(header.right() - DETAIL_HEADER_INSET
			- DETAIL_SEGMENT_WIDTH,
			header.y() + (DETAIL_HEADER_HEIGHT - EpsilonPanelTheme.CONTROL_HEIGHT)
				/ 2.0F,
			DETAIL_SEGMENT_WIDTH, EpsilonPanelTheme.CONTROL_HEIGHT);
	}
	
	public static Rect detailViewport(Rect detail)
	{
		float top = detail.y() + DETAIL_HEADER_TOP + DETAIL_HEADER_HEIGHT
			+ DETAIL_VIEWPORT_TOP_GAP;
		return new Rect(detail.x() + LIST_VIEWPORT_INSET_X, top,
			Math.max(0F, detail.width() - LIST_VIEWPORT_INSET_X * 2.0F),
			Math.max(0F, detail.bottom() - top - DETAIL_VIEWPORT_BOTTOM_GAP));
	}
	
	/** 设置行内容高：每行 28 + 3 的间距。 */
	public static float settingContentHeight(int count)
	{
		return Math.max(0, count) * (SETTING_ROW_HEIGHT + EpsilonPanelTheme.ROW_GAP);
	}
	
	public static Rect settingRow(Rect viewport, int index, float scroll,
		boolean scrollbar)
	{
		float width = Math.max(0F, viewport.width()
			- (scrollbar ? SCROLL_HIT_WIDTH : 0F));
		return new Rect(viewport.x(),
			viewport.y() + index * (SETTING_ROW_HEIGHT + EpsilonPanelTheme.ROW_GAP)
				- scroll,
			width, SETTING_ROW_HEIGHT);
	}
	
	/** 设置行右侧滑条轨道。 */
	public static Rect settingSliderTrack(Rect row)
	{
		return new Rect(row.right() - EpsilonPanelTheme.ROW_TRAILING_INSET
			- SETTING_SLIDER_RIGHT, row.y() + SETTING_SLIDER_TRACK_Y,
			SETTING_SLIDER_TRACK_WIDTH, SETTING_SLIDER_TRACK_HEIGHT);
	}
	
	/** 设置行右侧数值框。 */
	public static Rect settingValueField(Rect row)
	{
		return new Rect(row.right() - EpsilonPanelTheme.ROW_TRAILING_INSET
			- SETTING_VALUE_FIELD_X, row.y() + SETTING_VALUE_FIELD_Y,
			SETTING_VALUE_FIELD_X, SETTING_VALUE_FIELD_HEIGHT);
	}
	
	/** 设置行尾部的开关。 */
	public static Rect settingSwitch(Rect row)
	{
		return new Rect(row.right() - EpsilonPanelTheme.ROW_TRAILING_INSET
			- EpsilonPanelTheme.SWITCH_WIDTH,
			row.y() + (SETTING_ROW_HEIGHT - EpsilonPanelTheme.SWITCH_HEIGHT) / 2.0F,
			EpsilonPanelTheme.SWITCH_WIDTH, EpsilonPanelTheme.SWITCH_HEIGHT);
	}
	
	/** 设置行尾部的枚举 chip，宽随文字变但不超过 96。 */
	public static Rect settingChip(Rect row, float textWidth)
	{
		float width = Math.min(96.0F, textWidth + 16.0F + 10.0F);
		return new Rect(row.right() - EpsilonPanelTheme.ROW_TRAILING_INSET - width,
			row.y() + (SETTING_ROW_HEIGHT - EpsilonPanelTheme.SWITCH_HEIGHT) / 2.0F,
			width, EpsilonPanelTheme.SWITCH_HEIGHT);
	}
	
	public static float maxScroll(float contentHeight, Rect viewport)
	{
		return Math.max(0F, contentHeight - viewport.height());
	}
	
	/**
	 * 滚动条滑块；内容装得下时返回 null（参考项目也是只在 {@code maxScroll > 0}
	 * 时才画滚动条）。
	 */
	public static Rect scrollThumb(Rect viewport, float contentHeight,
		float scroll)
	{
		if(maxScroll(contentHeight, viewport) <= 0F
			|| viewport.height() <= 0F)
			return null;
		
		float track = viewport.height();
		float height = Math.max(SCROLL_THUMB_MIN_HEIGHT,
			Math.min(track, track * track / contentHeight));
		float progress = Math.max(0F,
			Math.min(1F, scroll / maxScroll(contentHeight, viewport)));
		
		return new Rect(
			viewport.right() - SCROLL_THUMB_INSET - SCROLL_THUMB_WIDTH,
			viewport.y() + (track - height) * progress, SCROLL_THUMB_WIDTH,
			height);
	}
	
	/** 面板里的一个矩形，语义与参考项目的 {@code PanelLayout.Rect} 一致。 */
	public record Rect(float x, float y, float width, float height)
	{
		public float right()
		{
			return x + width;
		}
		
		public float bottom()
		{
			return y + height;
		}
		
		public float centerX()
		{
			return x + width / 2.0F;
		}
		
		public float centerY()
		{
			return y + height / 2.0F;
		}
		
		public boolean contains(double px, double py)
		{
			return px >= x && px <= right() && py >= y && py <= bottom();
		}
		
		public Rect inset(float amount)
		{
			return new Rect(x + amount, y + amount,
				Math.max(0F, width - amount * 2.0F),
				Math.max(0F, height - amount * 2.0F));
		}
		
		/** 竖直方向是否与视口相交（用于行的剔除）。 */
		public boolean intersectsVertically(Rect viewport)
		{
			return y < viewport.bottom() && bottom() > viewport.y();
		}
	}
	
	public record Layout(Rect panel, Rect rail, Rect modules, Rect detail)
	{}
}
