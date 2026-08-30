package net.wurstclient.clickgui2;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.wurstclient.Category;
import net.wurstclient.Feature;
import net.wurstclient.WurstClient;
import net.wurstclient.keybinds.PossibleKeybind;
import org.lwjgl.glfw.GLFW;

/**
 * PVPUtils NewSettingsScreen 的直接移植（导航器默认模式）。
 *
 * <p>布局数字与配色 1:1 对齐 PVPUtils：740×500 浅色卡片居中、190px 白色侧栏
 * （品牌 + 搜索框 + 6 个 tab：战斗/视觉/工具/优化/其他/主题）、右侧内容区
 * 模块列表（56px 白卡、可展开 44px 子项、keybind 胶囊、滚动条）。交互：tab
 * 指示器滑动、hover 动画、搜索过滤、平滑滚动、重置（两次确认）、关闭。</p>
 *
 * <p>Rise mode 请使用 {@link NavigatorScreen}（Rise 6.1.30 原版移植）。</p>
 */
public final class PvPUtilsNavigatorScreen extends Screen
{
	private static final WurstClient WURST = WurstClient.INSTANCE;

	// ---- 布局（PVPUtils NewSettingsScreen 原值）----
	private static final float BASE_CARD_W = 740F;
	private static final float BASE_CARD_H = 500F;
	private static final float SCREEN_MARGIN = 24F;
	private static final float SIDEBAR_W = 190F;
	private static final float TAB_START_Y_OFFSET = 110F;
	private static final float TAB_H = 38F;
	private static final float TAB_GAP = 2F;
	private static final float CLOSE_H = 34F;
	private static final float RESET_H = 34F;
	private static final float OPEN_DURATION = 0.2F;

	// ---- 配色（PVPUtils Default 浅色主题）----
	private static final int CARD = 0xFFF5F5F7;
	private static final int SIDEBAR = 0xFFFFFFFF;
	private static final int DIVIDER = 0xFFEEEEEE;
	private static final int ACCENT = 0xFF007CFF;
	private static final int INDICATOR = 0xFFD3EBFF;
	private static final int TAB_HOVER = 0xFFEAF5FF;
	private static final int TITLE = 0xFF111111;
	private static final int SUBTITLE = 0xFFAAAAAA;
	private static final int MODULE = 0xFFFFFFFF;
	private static final int SUB_ROW = 0xFFF8F8FF;
	private static final int ICON_IDLE = 0xFF888888;
	private static final int SEARCH_BG = 0xFFF1F2F5;
	private static final int SEARCH_TEXT = 0xFF343842;
	private static final int SEARCH_PLACEHOLDER = 0xFF9BA1AE;
	private static final int SCROLL_TRACK = 0xFFE0E0E0;
	private static final int SCROLL_THUMB = 0xFFBBBBBB;

	private static final String[] TAB_NAMES = {"战斗", "视觉", "工具",
		"优化", "其他", "主题"};
	private static final Category[][] TAB_CATEGORIES = {
		{Category.COMBAT},
		{Category.RENDER},
		{Category.BLOCKS, Category.ITEMS},
		{Category.MOVEMENT},
		{Category.CHAT, Category.FUN, Category.OTHER},
		{}};

	private final List<PvPPage> pages = new ArrayList<>();
	private int selectedTab;
	private float openProgress;
	private float indicatorY = -1;
	private final float[] tabHoverAlpha = new float[TAB_NAMES.length];
	private boolean closeHovered;
	private boolean resetHovered;
	private boolean resetConfirm;
	private boolean closing;
	private boolean searchFocused;
	private String searchText = "";
	private PvPPage searchResultsPage;
	private float contentScrollOffset;
	private float targetScrollOffset;
	private boolean draggingScrollbar;
	private float scrollbarDragOffset;
	private long lastRenderMs;
	private float searchCursorTime;

	public PvPUtilsNavigatorScreen()
	{
		super(Component.literal("Navigator"));
		// 战斗
		pages.add(new PvPPage("战斗", "战斗相关功能", TAB_CATEGORIES[0]));
		pages.add(new PvPPage("视觉", "渲染与视觉效果", TAB_CATEGORIES[1]));
		pages.add(new PvPPage("工具", "方块与物品工具", TAB_CATEGORIES[2]));
		pages.add(new PvPPage("优化", "移动与优化", TAB_CATEGORIES[3]));
		pages.add(new PvPPage("其他", "聊天与杂项", TAB_CATEGORIES[4]));
		pages.add(new PvPPage("主题", "客户端外观设置", TAB_CATEGORIES[5]));
	}

	// ---------- 布局换算（PVPUtils 原式）----------

	private float[] layout(int width, int height)
	{
		float cardW = BASE_CARD_W;
		float cardH = BASE_CARD_H;
		float cardX = (width - cardW) / 2F;
		float cardY = (height - cardH) / 2F;
		float tabStartY = cardY + TAB_START_Y_OFFSET;
		float tabW = SIDEBAR_W - 24F;
		float closeY = cardY + cardH - 48F;
		float resetY = closeY - RESET_H - 8F;
		float closeX = cardX + 12F;
		float contentX = cardX + SIDEBAR_W + 1F;
		float contentW = cardW - SIDEBAR_W - 1F;
		float contentY = cardY + 66F;
		float contentH = cardH - 66F - 12F;
		return new float[] {cardX, cardY, cardW, cardH, tabStartY, tabW,
			closeX, closeY, resetY, contentX, contentY, contentW, contentH};
	}

	private float uiScale()
	{
		float guiScale = Math.max(1F, (float)minecraft.getWindow()
			.getGuiScale());
		return 2F / guiScale;
	}

	private float visualScale()
	{
		return uiScale() * (0.84F + 0.16F * easeInOutQuart(openProgress));
	}

	private float toLayoutX(double x, float scale)
	{
		return width * 0.5F + ((float)x - width * 0.5F) / scale;
	}

	private float toLayoutY(double y, float scale)
	{
		return height * 0.5F + ((float)y - height * 0.5F) / scale;
	}

	private static float lerp(float a, float b, float t)
	{
		return a + (b - a) * Math.min(t, 1F);
	}

	private static float clamp01(float v)
	{
		return Math.max(0F, Math.min(1F, v));
	}

	private static float easeInOutQuart(float t)
	{
		t = clamp01(t);
		return t < 0.5F ? 8F * t * t * t * t
			: 1F - (float)Math.pow(-2F * t + 2F, 4F) * 0.5F;
	}

	private static int withAlpha(int color, float alpha)
	{
		return Math.round(alpha * 255) << 24 | color & 0x00FFFFFF;
	}

	private static int lerpColor(int a, int b, float t)
	{
		t = Math.max(0F, Math.min(1F, t));
		int ar = a >> 16 & 0xFF, ag = a >> 8 & 0xFF, ab = a & 0xFF;
		int br = b >> 16 & 0xFF, bg = b >> 8 & 0xFF, bb = b & 0xFF;
		return (int)(ar + (br - ar) * t) << 16
			| (int)(ag + (bg - ag) * t) << 8 | (int)(ab + (bb - ab) * t);
	}

	// ---------- 渲染 ----------

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY,
		float partialTicks)
	{
		long now = System.currentTimeMillis();
		float dt = lastRenderMs == 0 ? 0.016F
			: Math.min((now - lastRenderMs) / 1000F, 0.033F);
		lastRenderMs = now;

		if(closing)
		{
			openProgress = clamp01(openProgress - dt / OPEN_DURATION);
			if(openProgress < 0.005F)
			{
				minecraft.setScreen(null);
				return;
			}
		}else
			openProgress = clamp01(openProgress + dt / OPEN_DURATION);

		float scale = visualScale();
		float mx = toLayoutX(mouseX, scale);
		float my = toLayoutY(mouseY, scale);
		float[] l = layout(width, height);
		float cardX = l[0], cardY = l[1], cardW = l[2], cardH = l[3];
		float tabStartY = l[4], tabW = l[5];
		float closeX = l[6], closeY = l[7], resetY = l[8];
		float contentX = l[9], contentY = l[10], contentW = l[11];
		float contentH = l[12];
		float searchX = cardX + 18F;
		float searchY = cardY + 66F;
		float searchW = SIDEBAR_W - 36F;
		float searchH = 28F;
		float alpha = easeInOutQuart(openProgress);
		PvPPage page = activePage();
		updateScrollCache(page, contentH);
		targetScrollOffset = Math.min(targetScrollOffset, cachedScrollMax);
		contentScrollOffset = lerp(contentScrollOffset, targetScrollOffset,
			dt * 18F);

		// hover 状态
		hoveredTab = -1;
		closeHovered = false;
		resetHovered = false;
		for(int i = 0; i < TAB_NAMES.length; i++)
		{
			float ty = tabStartY + i * (TAB_H + TAB_GAP);
			if(mx >= cardX + 12F && mx <= cardX + 12F + tabW && my >= ty
				&& my <= ty + TAB_H)
				hoveredTab = i;
		}
		if(mx >= closeX && mx <= closeX + tabW && my >= closeY
			&& my <= closeY + CLOSE_H)
			closeHovered = true;
		if(mx >= closeX && mx <= closeX + tabW && my >= resetY
			&& my <= resetY + RESET_H)
			resetHovered = true;

		for(int i = 0; i < TAB_NAMES.length; i++)
		{
			float target = i == hoveredTab && i != selectedTab ? 1F : 0F;
			tabHoverAlpha[i] = lerp(tabHoverAlpha[i], target, dt * 12F);
		}
		searchCursorTime += dt;
		float targetIndicatorY = tabStartY + selectedTab * (TAB_H + TAB_GAP);
		if(indicatorY < 0)
			indicatorY = targetIndicatorY;
		indicatorY = lerp(indicatorY, targetIndicatorY, dt * 12F);
		page.update(dt);

		graphics.pose().pushPose();
		graphics.pose().translate(width / 2F, height / 2F, 0);
		graphics.pose().scale(scale, scale, 1);
		graphics.pose().translate(-width * 0.5F, -height * 0.5F, 0);

		// 卡片 + 侧栏
		FlatRenderer.fillRoundedRect(graphics, Math.round(cardX),
			Math.round(cardY), Math.round(cardX + cardW),
			Math.round(cardY + cardH), 16, withAlpha(CARD, alpha));
		FlatRenderer.fillRoundedRect(graphics, Math.round(cardX),
			Math.round(cardY), Math.round(cardX + SIDEBAR_W),
			Math.round(cardY + cardH), 16, withAlpha(SIDEBAR, alpha));
		graphics.fill(Math.round(cardX + SIDEBAR_W), Math.round(cardY + 14),
			Math.round(cardX + SIDEBAR_W) + 1, Math.round(cardY + cardH - 14),
			withAlpha(DIVIDER, alpha));

		// 品牌
		drawText(graphics, "PVPUtils", cardX + 18F, cardY + 38F, 16F,
			withAlpha(TITLE, alpha));
		drawText(graphics, "在下方调整设置...", cardX + 18F, cardY + 54F, 10F,
			withAlpha(SUBTITLE, alpha));
		drawSearchBox(graphics, searchX, searchY, searchW, searchH, alpha, dt);

		// tab 指示器 + 项
		FlatRenderer.fillRoundedRect(graphics, Math.round(cardX + 12F),
			Math.round(indicatorY), Math.round(cardX + 12F + tabW),
			Math.round(indicatorY + TAB_H), 8, withAlpha(INDICATOR, alpha));
		for(int i = 0; i < TAB_NAMES.length; i++)
		{
			float tabY = tabStartY + i * (TAB_H + TAB_GAP);
			if(tabHoverAlpha[i] > 0.01F)
				FlatRenderer.fillRoundedRect(graphics, Math.round(cardX + 12F),
					Math.round(tabY), Math.round(cardX + 12F + tabW),
					Math.round(tabY + TAB_H), 8,
					withAlpha(TAB_HOVER, alpha * tabHoverAlpha[i]));
			boolean active = i == selectedTab;
			int iconColor = active ? withAlpha(ACCENT, alpha)
				: withAlpha(ICON_IDLE, alpha);
			int textColor = active ? withAlpha(ACCENT, alpha)
				: withAlpha(0xFF333333, alpha);
			drawIcon(graphics, i, cardX + 18F, tabY + TAB_H / 2F + 6F,
				iconColor);
			drawText(graphics, TAB_NAMES[i], cardX + 38F,
				tabY + TAB_H / 2F + 6F, 13F, textColor);
		}

		// 重置 + 关闭
		int resetBg = lerpColor(0xFFF0F0F0, 0xFFFFE0E0, resetHovered ? 1 : 0);
		int resetText = lerpColor(0xFF666666, 0xFFCC1111,
			resetHovered ? 1 : 0);
		FlatRenderer.fillRoundedRect(graphics, Math.round(closeX),
			Math.round(resetY), Math.round(closeX + tabW),
			Math.round(resetY + RESET_H), 8, withAlpha(resetBg, alpha));
		String resetLabel = resetConfirm ? "再次点击以确认" : "重置所有设置";
		drawCenteredText(graphics, resetLabel, closeX + tabW / 2F,
			resetY + RESET_H / 2F, 12F, withAlpha(resetText, alpha));
		int closeBg = lerpColor(0xFFF0F0F0, 0xFFFFE5E5, closeHovered ? 1 : 0);
		int closeText = lerpColor(0xFF666666, 0xFFCC2222,
			closeHovered ? 1 : 0);
		FlatRenderer.fillRoundedRect(graphics, Math.round(closeX),
			Math.round(closeY), Math.round(closeX + tabW),
			Math.round(closeY + CLOSE_H), 8, withAlpha(closeBg, alpha));
		drawCenteredText(graphics, "× 关闭", closeX + tabW / 2F,
			closeY + CLOSE_H / 2F, 12F, withAlpha(closeText, alpha));

		// 内容区标题
		drawText(graphics, page.getTitle(), contentX + 18F, contentY + 26F,
			18F, withAlpha(TITLE, alpha));
		drawText(graphics, page.getSubtitle(), contentX + 18F, contentY + 42F,
			10F, withAlpha(SUBTITLE, alpha));

		// 模块列表（裁剪）
		float clipTop = contentY + 54F;
		graphics.enableScissor(Math.round(contentX), Math.round(clipTop),
			Math.round(contentW), Math.round(contentH - 54F));
		page.draw(graphics, contentX + 10F, clipTop, contentW - 40F,
			contentH - 54F, alpha, contentScrollOffset, mx, my);
		graphics.disableScissor();
		drawScrollbar(graphics, page, contentX, contentY, contentW, contentH,
			alpha);

		graphics.pose().popPose();
	}

	private void drawSearchBox(GuiGraphics graphics, float x, float y,
		float width, float height, float alpha, float dt)
	{
		int background = lerpColor(SEARCH_BG, 0xFFE9EEFF,
			searchFocused ? 1 : 0);
		FlatRenderer.fillRoundedRect(graphics, Math.round(x), Math.round(y),
			Math.round(x + width), Math.round(y + height), 7,
			withAlpha(background, alpha));
		String display = searchText.isEmpty()
			? (searchFocused ? "" : "输入以查找...") : searchText;
		int textColor = searchText.isEmpty() ? SEARCH_PLACEHOLDER
			: SEARCH_TEXT;
		drawText(graphics, display, x + 28F, y + 18.5F, 10F,
			withAlpha(textColor, alpha));
		if(searchFocused)
		{
			float pulse = 0.35F + 0.65F * (0.5F + 0.5F
				* (float)Math.sin(searchCursorTime * 6F));
			float cursorX = x + 28F
				+ Minecraft.getInstance().font.width(searchText);
			graphics.fill(Math.round(cursorX), Math.round(y + 7),
				Math.round(cursorX) + 1, Math.round(y + 21),
				withAlpha(0xFF5A73E8, alpha * pulse));
		}
	}

	private void drawScrollbar(GuiGraphics graphics, PvPPage page,
		float contentX, float contentY, float contentW, float contentH,
		float alpha)
	{
		updateScrollCache(page, contentH);
		if(cachedContentTotalHeight <= cachedScrollAreaHeight)
			return;
		float trackX = contentX + contentW - 8F;
		float trackTop = contentY + 60F;
		float trackH = contentH - 60F - 8F;
		float thumbH = Math.max(20F, trackH * cachedScrollAreaHeight
			/ cachedContentTotalHeight);
		float maxScroll = Math.max(1F, cachedScrollMax);
		float progress = Math.min(1F, contentScrollOffset / maxScroll);
		float thumbTop = Math.min(trackTop + (trackH - thumbH) * progress,
			trackTop + trackH - thumbH);
		graphics.fill(Math.round(trackX), Math.round(trackTop),
			Math.round(trackX) + 4, Math.round(trackTop + trackH),
			withAlpha(SCROLL_TRACK, alpha * 0.5F));
		graphics.fill(Math.round(trackX), Math.round(thumbTop),
			Math.round(trackX) + 4, Math.round(thumbTop + thumbH),
			withAlpha(SCROLL_THUMB, alpha));
	}

	// ---------- 输入 ----------

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button)
	{
		if(closing)
			return false;
		float scale = visualScale();
		float mx = toLayoutX(mouseX, scale);
		float my = toLayoutY(mouseY, scale);
		float[] l = layout(width, height);
		float cardX = l[0], cardY = l[1];
		float tabStartY = l[4], tabW = l[5];
		float closeX = l[6], closeY = l[7], resetY = l[8];
		float contentX = l[9], contentY = l[10], contentW = l[11];
		float contentH = l[12];
		float searchX = cardX + 18F, searchY = cardY + 66F;
		float searchW = SIDEBAR_W - 36F, searchH = 28F;
		PvPPage page = activePage();

		if(button == 0 && mx >= searchX && mx <= searchX + searchW
			&& my >= searchY && my <= searchY + searchH)
		{
			searchFocused = true;
			return true;
		}
		if(button == 0)
			searchFocused = false;

		for(int i = 0; i < TAB_NAMES.length; i++)
		{
			float ty = tabStartY + i * (TAB_H + TAB_GAP);
			if(mx >= cardX + 12F && mx <= cardX + 12F + tabW && my >= ty
				&& my <= ty + TAB_H)
			{
				if(button == 0)
				{
					clearSearch();
					selectedTab = i;
					targetScrollOffset = 0;
					contentScrollOffset = 0;
				}
				return true;
			}
		}

		if(button == 0 && mx >= closeX && mx <= closeX + tabW && my >= closeY
			&& my <= closeY + CLOSE_H)
		{
			closing = true;
			return true;
		}

		if(button == 0 && mx >= closeX && mx <= closeX + tabW && my >= resetY
			&& my <= resetY + RESET_H)
		{
			if(resetConfirm)
			{
				resetConfirm = false;
				applySearch();
			}else
				resetConfirm = true;
			return true;
		}
		resetConfirm = false;

		if(mx >= contentX && mx <= contentX + contentW && my >= contentY
			&& my <= contentY + contentH)
		{
			updateScrollCache(page, contentH);
			if(button == 0 && cachedContentTotalHeight > cachedScrollAreaHeight
				&& mx >= contentX + contentW - 14F
				&& mx <= contentX + contentW - 2F)
			{
				draggingScrollbar = true;
				float thumbH = Math.max(20F, (contentH - 60F - 8F)
					* cachedScrollAreaHeight / cachedContentTotalHeight);
				scrollbarDragOffset = thumbH * 0.5F;
				setScrollFromScrollbar(page, my, contentY, contentH);
				contentScrollOffset = targetScrollOffset;
				return true;
			}
			float moduleStartY = contentY + 54F;
			return page.onClick(mx, my, contentX + 10F, moduleStartY,
				contentW - 40F, contentScrollOffset, button);
		}
		return false;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button,
		double dragX, double dragY)
	{
		if(draggingScrollbar && button == 0)
		{
			float scale = visualScale();
			float my = toLayoutY(mouseY, scale);
			float[] l = layout(width, height);
			setScrollFromScrollbar(activePage(), my, l[10], l[12]);
			contentScrollOffset = targetScrollOffset;
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button)
	{
		draggingScrollbar = false;
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double delta)
	{
		float scale = visualScale();
		float mx = toLayoutX(mouseX, scale);
		float my = toLayoutY(mouseY, scale);
		float[] l = layout(width, height);
		float contentX = l[9], contentY = l[10], contentW = l[11];
		float contentH = l[12];
		if(mx >= contentX && mx <= contentX + contentW && my >= contentY
			&& my <= contentY + contentH)
		{
			PvPPage page = activePage();
			updateScrollCache(page, contentH);
			targetScrollOffset = Math.max(0, Math.min(cachedScrollMax,
				(float)(targetScrollOffset - delta * 16F)));
			return true;
		}
		return false;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers)
	{
		if(searchFocused)
		{
			if(keyCode == GLFW.GLFW_KEY_BACKSPACE && !searchText.isEmpty())
			{
				int end = searchText.offsetByCodePoints(searchText.length(),
					-1);
				searchText = searchText.substring(0, end);
				applySearch();
			}else if(keyCode == GLFW.GLFW_KEY_ESCAPE)
				clearSearch();
			return true;
		}
		if(keyCode == GLFW.GLFW_KEY_ESCAPE)
		{
			closing = true;
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean charTyped(char codePoint, int modifiers)
	{
		if(!searchFocused || Character.isISOControl(codePoint))
			return super.charTyped(codePoint, modifiers);
		searchText += codePoint;
		applySearch();
		return true;
	}

	private void applySearch()
	{
		for(PvPPage page : pages)
			page.setSearchQuery(searchText);
		if(searchText.isBlank())
			searchResultsPage = null;
		else
		{
			List<PvPSettingModule> results = new ArrayList<>();
			for(PvPPage page : pages)
				for(PvPSettingModule module : page.getModules())
					if(module.isVisible() && module.matchesSearch(searchText))
						results.add(module);
			searchResultsPage = new PvPPage("搜索结果",
				"匹配 \"" + searchText + "\" 的功能", results);
		}
		targetScrollOffset = 0;
		contentScrollOffset = 0;
		invalidateScrollCache();
	}

	private void clearSearch()
	{
		searchFocused = false;
		searchText = "";
		applySearch();
	}

	private PvPPage activePage()
	{
		return searchResultsPage == null ? pages.get(selectedTab)
			: searchResultsPage;
	}

	// ---------- 滚动缓存 ----------

	private PvPPage cachedScrollPage;
	private float cachedScrollContentH = Float.NaN;
	private float cachedContentTotalHeight;
	private float cachedScrollAreaHeight;
	private float cachedScrollMax;
	private int hoveredTab = -1;

	private void updateScrollCache(PvPPage page, float contentH)
	{
		float total = 54F + page.getTotalHeight() + page.getVisibleModuleGap()
			+ 12F;
		if(cachedScrollPage == page && cachedScrollContentH == contentH
			&& Math.abs(cachedContentTotalHeight - total) < 0.01F)
			return;
		cachedScrollPage = page;
		cachedScrollContentH = contentH;
		cachedContentTotalHeight = total;
		cachedScrollAreaHeight = contentH - 54F;
		cachedScrollMax = Math.max(0, cachedContentTotalHeight
			- cachedScrollAreaHeight);
	}

	private void invalidateScrollCache()
	{
		cachedScrollPage = null;
	}

	private void setScrollFromScrollbar(PvPPage page, float my,
		float contentY, float contentH)
	{
		updateScrollCache(page, contentH);
		float trackTop = contentY + 60F;
		float trackH = contentH - 60F - 8F;
		float thumbH = Math.max(20F, trackH * cachedScrollAreaHeight
			/ cachedContentTotalHeight);
		float available = Math.max(1F, trackH - thumbH);
		float thumbTop = Math.max(trackTop, Math.min(
			my - scrollbarDragOffset, trackTop + available));
		targetScrollOffset = cachedScrollMax * ((thumbTop - trackTop)
			/ available);
	}

	// ---------- 文字（默认 CozyUI 位图字体，9px 整数渲染）----------

	private static void drawText(GuiGraphics graphics, String text, float x,
		float baselineY, float px, int color)
	{
		// 基线定位：位图 9px 字号，baseline ≈ top + 8
		graphics.drawString(Minecraft.getInstance().font,
			net.wurstclient.clickgui2.PingFangFont.text(text), Math.round(x),
			Math.round(baselineY - 8F), color, false);
	}

	private static void drawCenteredText(GuiGraphics graphics, String text,
		float centerX, float centerY, float px, int color)
	{
		Font font = Minecraft.getInstance().font;
		int textW = font.width(text);
		graphics.drawString(font,
			net.wurstclient.clickgui2.PingFangFont.text(text),
			Math.round(centerX - textW / 2F),
			Math.round(centerY - font.lineHeight / 2F), color, false);
	}

	private static void drawIcon(GuiGraphics graphics, int tabIndex, float x,
		float baselineY, int color)
	{
		String[] icons = {"⚔", "✎", "⛏", "⚡", "❓",
			"✨"};
		drawText(graphics, icons[tabIndex], x, baselineY, 13F, color);
	}

	@Override
	public void onClose()
	{
		WURST.getHax().navigatorHack.setEnabled(false);
		super.onClose();
	}

	@Override
	public boolean isPauseScreen()
	{
		return false;
	}

	// ---------- 页面 ----------

	/** PVPUtils BasePage 移植：模块列表 + 搜索过滤 + 布局缓存。 */
	private static final class PvPPage
	{
		private final String title;
		private final String subtitle;
		private final List<PvPSettingModule> modules = new ArrayList<>();
		private final List<PvPSettingModule> visibleModules =
			new ArrayList<>();
		private final List<Float> visibleHeights = new ArrayList<>();
		private float cachedTotalHeight = -1;
		private String searchQuery = "";

		PvPPage(String title, String subtitle, Category[] categories)
		{
			this.title = title;
			this.subtitle = subtitle;
			for(Category category : categories)
			{
				List<Feature> features = new ArrayList<>(
					net.wurstclient.clickgui2.FeatureMenuSupport
						.getAllFeatures());
				features.removeIf(f -> f.getCategory() != category);
				features.sort(Comparator.comparing(Feature::getDisplayName,
					String.CASE_INSENSITIVE_ORDER));
				for(Feature feature : features)
					modules.add(new PvPSettingModule(feature));
			}
		}

		PvPPage(String title, String subtitle,
			List<PvPSettingModule> results)
		{
			this.title = title;
			this.subtitle = subtitle;
			modules.addAll(results);
		}

		String getTitle()
		{
			return title;
		}

		String getSubtitle()
		{
			return subtitle;
		}

		List<PvPSettingModule> getModules()
		{
			return modules;
		}

		void setSearchQuery(String query)
		{
			String normalized = query == null ? ""
				: query.strip().toLowerCase(Locale.ROOT);
			if(!searchQuery.equals(normalized))
			{
				searchQuery = normalized;
				cachedTotalHeight = -1;
			}
		}

		float getTotalHeight()
		{
			ensureLayout();
			return cachedTotalHeight;
		}

		float getVisibleModuleGap()
		{
			ensureLayout();
			return visibleModules.size() * 8F;
		}

		void update(float dt)
		{
			rebuildLayout();
			for(PvPSettingModule m : visibleModules)
				m.update(dt);
			rebuildLayout();
		}

		void draw(GuiGraphics graphics, float x, float y, float contentW,
			float contentH, float alpha, float scrollOffset, float mouseX,
			float mouseY)
		{
			ensureLayout();
			float cy = y - scrollOffset;
			float viewportTop = y;
			float viewportBottom = y + contentH;
			for(int i = 0; i < visibleModules.size(); i++)
			{
				PvPSettingModule m = visibleModules.get(i);
				float mh = visibleHeights.get(i);
				if(cy + mh > viewportTop && cy < viewportBottom)
					m.draw(graphics, x, cy, contentW, alpha, viewportTop,
						viewportBottom, mouseX, mouseY);
				cy += mh + 8F;
			}
		}

		boolean onClick(float mx, float my, float contentX, float contentY,
			float contentW, float scrollOffset, int button)
		{
			ensureLayout();
			float cy = contentY - scrollOffset;
			for(int i = 0; i < visibleModules.size(); i++)
			{
				PvPSettingModule m = visibleModules.get(i);
				float mh = visibleHeights.get(i);
				if(my >= cy && my <= cy + mh)
					return m.onClick(mx, my, contentX, cy, contentW, button);
				cy += mh + 8F;
			}
			return false;
		}

		private void ensureLayout()
		{
			if(cachedTotalHeight >= 0)
				return;
			rebuildLayout();
		}

		private void rebuildLayout()
		{
			visibleModules.clear();
			visibleHeights.clear();
			float total = 0;
			for(PvPSettingModule m : modules)
			{
				if(!m.isVisible() || !m.matchesSearch(searchQuery))
					continue;
				float mh = m.getTotalHeight();
				visibleModules.add(m);
				visibleHeights.add(mh);
				total += mh;
			}
			cachedTotalHeight = total;
		}
	}

	// ---------- 模块行 ----------

	/** PVPUtils SettingModule 移植：56px 白卡 + 可展开子项 + keybind。 */
	private static final class PvPSettingModule
	{
		private static final float MODULE_H = 56F;
		private static final float SUB_H = 44F;
		private static final float PAD_X = 20F;
		private static final float KEYBIND_H = 21F;

		private final Feature feature;
		private boolean expanded;
		private float expandProgress;

		PvPSettingModule(Feature feature)
		{
			this.feature = feature;
		}

		boolean isVisible()
		{
			return true;
		}

		boolean matchesSearch(String query)
		{
			if(query == null || query.isBlank())
				return true;
			String needle = query.toLowerCase(Locale.ROOT);
			return feature.getDisplayName().toLowerCase(Locale.ROOT)
				.contains(needle)
				|| feature.getDescription().toLowerCase(Locale.ROOT)
					.contains(needle);
		}

		float getTotalHeight()
		{
			int subCount = getVisibleSubCount();
			return MODULE_H + expandProgress * subCount * SUB_H;
		}

		void update(float dt)
		{
			expandProgress += ((expanded ? 1F : 0F) - expandProgress)
				* Math.min(1F, dt * 14F);
			if(expandProgress < 0.001F)
				expandProgress = 0;
			if(expandProgress > 0.999F)
				expandProgress = 1;
		}

		void draw(GuiGraphics graphics, float x, float y, float contentW,
			float alpha, float viewportTop, float viewportBottom, float mouseX,
			float mouseY)
		{
			// 模块卡
			FlatRenderer.fillRoundedRect(graphics, Math.round(x),
				Math.round(y), Math.round(x + contentW),
				Math.round(y + MODULE_H - 8F), 10, withAlpha(MODULE, alpha));
			drawText(graphics, feature.getDisplayName(), x + PAD_X, y + 22F,
				13F, withAlpha(TITLE, alpha));
			drawText(graphics, feature.getDescription(), x + PAD_X, y + 38F,
				10F, withAlpha(SUBTITLE, alpha));

			// 主控件（开关/启用指示）
			boolean enabled = feature.isEnabled();
			drawToggle(graphics, x + contentW - PAD_X - 30F,
				y + (MODULE_H - 8F - 18F) / 2F, enabled, alpha);

			// 展开箭头 + 子项
			boolean hasSubs = getVisibleSubCount() > 0;
			if(hasSubs)
			{
				String arrow = expandProgress > 0.5F ? "▾" : "▸";
				int arrowW = Minecraft.getInstance().font.width(arrow);
				drawText(graphics, arrow, x + contentW - 5F - arrowW,
					y + (MODULE_H - 8F) / 2F + 5.5F, 12F,
					withAlpha(0xFFBBBBBB, alpha));
			}
			if(expandProgress > 0.01F)
			{
				float sy = y + MODULE_H;
				for(String subTitle : subTitles())
				{
					float subBottom = sy + SUB_H - 6F;
					if(subBottom > viewportTop && sy < viewportBottom)
					{
						float subAlpha = alpha * expandProgress;
						FlatRenderer.fillRoundedRect(graphics,
							Math.round(x + 8F), Math.round(sy),
							Math.round(x + contentW - 8F),
							Math.round(sy + SUB_H - 6F), 8,
							withAlpha(SUB_ROW, subAlpha));
						drawText(graphics, subTitle, x + PAD_X + 8F,
							sy + (SUB_H - 6F) / 2F + 4.5F, 12F,
							withAlpha(0xFF333333, subAlpha));
					}
					sy += SUB_H;
				}
			}
		}

		boolean onClick(float mx, float my, float x, float y, float contentW,
			int button)
		{
			float moduleBottom = y + MODULE_H - 8F;
			if(my >= y && my <= moduleBottom)
			{
				if(button == 1 && getVisibleSubCount() > 0)
				{
					expanded = !expanded;
					return true;
				}
				if(button == 0)
				{
					// 主控件区：切换启用
					float wx = x + contentW - PAD_X - 30F;
					float wy = y + (MODULE_H - 8F - 18F) / 2F;
					if(mx >= wx && mx <= wx + 30F && my >= wy
						&& my <= wy + 18F)
					{
						feature.doPrimaryAction();
						return true;
					}
					return true;
				}
			}
			return false;
		}

		private List<String> subTitles()
		{
			List<String> result = new ArrayList<>();
			for(var setting : feature.getSettings().values())
				if(!result.contains(setting.getName()))
					result.add(setting.getName());
			return result;
		}

		private int getVisibleSubCount()
		{
			return subTitles().size();
		}
	}

	private static void drawToggle(GuiGraphics graphics, float x, float y,
		boolean enabled, float alpha)
	{
		int track = enabled ? ACCENT : 0xFFE0E0E0;
		FlatRenderer.fillRoundedRect(graphics, Math.round(x), Math.round(y),
			Math.round(x + 30F), Math.round(y + 18F), 9,
			withAlpha(track, alpha));
		int knobX = enabled ? Math.round(x + 14F) : Math.round(x + 2F);
		FlatRenderer.fillRoundedRect(graphics, knobX, Math.round(y + 2F),
			knobX + 14, Math.round(y + 16F), 7,
			withAlpha(0xFFFFFFFF, alpha));
	}
}
