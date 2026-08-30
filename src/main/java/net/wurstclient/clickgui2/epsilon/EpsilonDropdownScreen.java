package net.wurstclient.clickgui2.epsilon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import net.wurstclient.clickgui2.ClickGuiScreens;
import net.wurstclient.clickgui2.FlatRenderer;
import net.wurstclient.clickgui2.GuiIcon;
import net.wurstclient.clickgui2.GuiPreferences;
import net.wurstclient.clickgui2.GuiPreferences.TargetType;
import net.wurstclient.clickgui2.component.ClickGuiKeyLatch;
import net.wurstclient.clickgui2.component.GuiTextInput;
import net.wurstclient.clickgui2.component.VapeGuiContext;
import net.wurstclient.clickgui2.screens.NeteaseMusicScreen;
import net.wurstclient.clickgui2.supersoft.EpsilonMd3Theme;
import net.wurstclient.clickgui2.supersoft.UiTween;
import net.wurstclient.hud2.HudEditorScreen;
import net.wurstclient.keybinds.PossibleKeybind;
import org.lwjgl.glfw.GLFW;

/**
 * Epsilon 26.1.2 DropdownScreen 的直接移植。
 *
 * <p>左侧主面板（图标网格 30x30、4 列：各分类 + 收起全部 + 客户端设置项），
 * 右侧垂直排列的分类面板（初始隐藏，点主面板图标切换显示；头部左键拖动、
 * 右键折叠）；底部搜索框与提示文字（Ctrl+F 搜索 / 点击分类展开面板 /
 * 拖动标题移动面板）。</p>
 */
public final class EpsilonDropdownScreen extends Screen
	implements VapeGuiContext
{
	private static final WurstClient WURST = WurstClient.INSTANCE;
	private static final float ICON_SIZE = 30F;
	private static final float ICON_GAP = 7F;
	private static final int ICON_COLUMNS = 4;
	private static final float MAIN_WIDTH = 160F;
	private static final float CONTENT_PADDING = 7F;

	private final Screen parentScreen;
	private final UiTween scrimAnim = new UiTween(0, 200);
	private final UiTween screenClose = new UiTween(1, 300);
	private final ClickGuiKeyLatch closeKeyLatch = new ClickGuiKeyLatch();
	private final Map<Category, EpsilonCategoryPanel> categoryPanels =
		new HashMap<>();
	private final Map<String, UiTween> iconHovers = new HashMap<>();
	private final Map<String, UiTween> settingsHovers = new HashMap<>();
	private final List<Category> panelOrder = new ArrayList<>();

	private float mainX;
	private float mainY;
	private boolean mainDragging;
	private float mainDragOffsetX;
	private float mainDragOffsetY;
	private String searchQuery = "";
	private boolean searchFocused;
	private boolean closing;
	private boolean initialized;
	private Feature bindingFeature;
	private GuiTextInput activeTextInput;
	private boolean openingHudEditor;
	private long hudEditorOpenAtNanos;

	public EpsilonDropdownScreen(Screen parentScreen)
	{
		super(Component.literal(WurstClient.CLIENT_NAME));
		this.parentScreen = parentScreen;
	}

	@Override
	protected void init()
	{
		GuiIcon.configureFiltering(minecraft);
		if(!initialized)
		{
			scrimAnim.snap(0);
			initialized = true;
			buildPanels();
		}
		scrimAnim.update(1);
	}

	private void buildPanels()
	{
		mainX = EpsilonDropdownTheme.PANEL_MARGIN_X;
		mainY = EpsilonDropdownTheme.PANEL_MARGIN_Y;
		float x = mainX + MAIN_WIDTH + EpsilonDropdownTheme.PANEL_GAP;
		float y = EpsilonDropdownTheme.PANEL_MARGIN_Y;
		for(Category category : Category.values())
		{
			EpsilonCategoryPanel panel = new EpsilonCategoryPanel(category,
				this);
			panel.setPosition(x, y);
			panel.setVisible(false);
			panel.setOpened(false);
			categoryPanels.put(category, panel);
			panelOrder.add(category);
			y += EpsilonDropdownTheme.PANEL_HEADER_HEIGHT
				+ EpsilonDropdownTheme.PANEL_GAP;
		}
	}

	// ---------- 渲染 ----------

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY,
		float partialTicks)
	{
		float close = screenClose.update(closing ? 0 : 1);
		if(closing && close <= 0.001F)
		{
			minecraft.setScreen(parentScreen);
			return;
		}
		float scrim = scrimAnim.get();
		int scrimColor = EpsilonDropdownTheme.scrim();
		int scrimAlpha = Math.round((scrimColor >>> 24) * scrim * close);
		graphics.fill(0, 0, width, height, scrimAlpha << 24);

		for(Category category : panelOrder)
		{
			EpsilonCategoryPanel panel = categoryPanels.get(category);
			panel.setMaxPanelHeight(height * 0.72F);
			panel.render(graphics, mouseX, mouseY, partialTicks);
		}
		renderMainPanel(graphics, mouseX, mouseY);
		renderSearch(graphics, mouseX, mouseY);
		renderHints(graphics);
		renderHudButton(graphics, mouseX, mouseY);
	}

	private void renderMainPanel(GuiGraphics graphics, int mouseX, int mouseY)
	{
		// 面板阴影 + 主体
		FlatRenderer.fillRoundedRect(graphics, Math.round(mainX) - 2,
			Math.round(mainY) + 3, Math.round(mainX + MAIN_WIDTH) + 2,
			Math.round(mainY + mainPanelHeight()) + 6,
			Math.round(EpsilonDropdownTheme.PANEL_RADIUS + 2),
			EpsilonDropdownTheme.panelShadow());
		FlatRenderer.fillRoundedRect(graphics, Math.round(mainX),
			Math.round(mainY), Math.round(mainX + MAIN_WIDTH),
			Math.round(mainY + mainPanelHeight()),
			Math.round(EpsilonDropdownTheme.PANEL_RADIUS),
			EpsilonDropdownTheme.panelBackground());

		// 头部：客户端名 + 版本
		drawScaled(graphics, WurstClient.CLIENT_NAME, Math.round(mainX + 10),
			Math.round(mainY + 8), EpsilonMd3Theme.TEXT_PRIMARY,
			EpsilonDropdownTheme.HEADER_TEXT_SCALE);
		drawScaled(graphics, "v1.6.0", Math.round(mainX + 12),
			Math.round(mainY + 21), EpsilonMd3Theme.TEXT_MUTED, 0.48F);

		// 分隔线
		graphics.fill(Math.round(mainX + CONTENT_PADDING),
			Math.round(mainY + EpsilonDropdownTheme.PANEL_HEADER_HEIGHT),
			Math.round(mainX + MAIN_WIDTH - CONTENT_PADDING),
			Math.round(mainY + EpsilonDropdownTheme.PANEL_HEADER_HEIGHT) + 1,
			EpsilonDropdownTheme.groupDivider());

		// 图标网格：分类 + 收起全部
		float currentY = mainY + EpsilonDropdownTheme.PANEL_HEADER_HEIGHT
			+ CONTENT_PADDING;
		int index = 0;
		for(Category category : panelOrder)
		{
			float iconX = mainIconX(index);
			float iconY = currentY + index / ICON_COLUMNS
				* (ICON_SIZE + ICON_GAP);
			EpsilonCategoryPanel panel = categoryPanels.get(category);
			renderIconButton(graphics, iconX, iconY, panel.getIcon(),
				panel.isVisible(), category.getName(), mouseX, mouseY,
				"cat:" + category.getName());
			index++;
		}
		float collapseX = mainIconX(index);
		float collapseY = currentY + index / ICON_COLUMNS
			* (ICON_SIZE + ICON_GAP);
		renderIconButton(graphics, collapseX, collapseY, GuiIcon.CLOSE,
			false, "Collapse", mouseX, mouseY, "__collapse_all__");
		index++;

		// 客户端设置项
		int rows = (int)Math.ceil(index / (float)ICON_COLUMNS);
		float settingsY = mainY + EpsilonDropdownTheme.PANEL_HEADER_HEIGHT
			+ CONTENT_PADDING + rows * ICON_SIZE + Math.max(0, rows - 1)
				* ICON_GAP + 8 + CONTENT_PADDING;
		graphics.fill(Math.round(mainX + CONTENT_PADDING),
			Math.round(settingsY - 3),
			Math.round(mainX + MAIN_WIDTH - CONTENT_PADDING),
			Math.round(settingsY - 2), EpsilonDropdownTheme.groupDivider());
		renderClientSettings(graphics, settingsY, mouseX, mouseY);
	}

	private void renderIconButton(GuiGraphics graphics, float iconX,
		float iconY, GuiIcon icon, boolean active, String label, int mouseX,
		int mouseY, String hoverKey)
	{
		boolean hovered = mouseX >= iconX && mouseX < iconX + ICON_SIZE
			&& mouseY >= iconY && mouseY < iconY + ICON_SIZE;
		float hover = iconHovers.computeIfAbsent(hoverKey,
			k -> new UiTween(0, EpsilonDropdownTheme.ANIM_HOVER))
			.update(hovered ? 1 : 0);
		int bg = EpsilonMd3Theme.mix(active ? EpsilonMd3Theme.PRIMARY_CONTAINER
			: EpsilonMd3Theme.SURFACE_CONTAINER_HIGH,
			EpsilonMd3Theme.PRIMARY_CONTAINER, hover * 0.5F);
		FlatRenderer.fillRoundedRect(graphics, Math.round(iconX),
			Math.round(iconY), Math.round(iconX + ICON_SIZE),
			Math.round(iconY + ICON_SIZE), Math.round(EpsilonDropdownTheme.BUTTON_RADIUS),
			bg);
		icon.draw(graphics, Math.round(iconX + 11), Math.round(iconY + 11), 8,
			active ? EpsilonMd3Theme.ON_PRIMARY_CONTAINER
				: EpsilonMd3Theme.TEXT_PRIMARY);
		if(hovered)
		{
			float labelScale = 0.42F;
			int labelW = Minecraft.getInstance().font.width(label);
			float labelX = Mth.clamp(iconX + (ICON_SIZE - labelW) * 0.5F,
				mainX + 2, mainX + MAIN_WIDTH - labelW - 2);
			drawScaled(graphics, label, Math.round(labelX),
				Math.round(iconY + ICON_SIZE + 1), EpsilonMd3Theme.TEXT_MUTED,
				labelScale);
		}
	}

	private void renderClientSettings(GuiGraphics graphics, float top,
		int mouseX, int mouseY)
	{
		GuiPreferences preferences = WURST.getGuiPreferences();
		String[][] rows = {
			{"Chinese", String.valueOf(!WURST.getOtfs().translationsOtf
				.getForceEnglish().isChecked())},
			{"Vape mode", String.valueOf(preferences.isVapeMode())},
			{"Commands", String.valueOf(preferences.isCommandsEnabled())},
			{"Font", String.valueOf(preferences.isFontEnabled())},
			{"Music", ""}};
		float rowTop = top;
		for(String[] row : rows)
		{
			boolean hovered = mouseX >= mainX + 4 && mouseX < mainX + MAIN_WIDTH
				- 4 && mouseY >= rowTop && mouseY < rowTop + 18;
			float hover = settingsHovers.computeIfAbsent(row[0],
				k -> new UiTween(0, EpsilonDropdownTheme.ANIM_HOVER))
				.update(hovered ? 1 : 0);
			int bg = EpsilonMd3Theme.mix(
				EpsilonDropdownTheme.settingSurface(),
				EpsilonMd3Theme.SURFACE_CONTAINER_HIGH, hover);
			FlatRenderer.fillRoundedRect(graphics, Math.round(mainX + 4),
				Math.round(rowTop), Math.round(mainX + MAIN_WIDTH - 4),
				Math.round(rowTop + 17), Math.round(EpsilonDropdownTheme.BUTTON_RADIUS),
				bg);
			drawScaled(graphics, row[0], Math.round(mainX + 10),
				Math.round(rowTop + 5), EpsilonMd3Theme.TEXT_PRIMARY,
				EpsilonDropdownTheme.SETTING_TEXT_SCALE);
			rowTop += 19;
		}
	}

	private void renderSearch(GuiGraphics graphics, int mouseX, int mouseY)
	{
		float searchX = EpsilonDropdownTheme.PANEL_MARGIN_X;
		float searchY = height - EpsilonDropdownTheme.PANEL_MARGIN_Y - 20;
		float searchW = Mth.clamp(width - EpsilonDropdownTheme.PANEL_MARGIN_X
			* 2, 140, 200);
		boolean hovered = mouseX >= searchX && mouseX < searchX + searchW
			&& mouseY >= searchY && mouseY < searchY + 20;
		boolean focused = searchFocused;
		float hover = settingsHovers.computeIfAbsent("__search__",
			k -> new UiTween(0, 120)).update(hovered || focused ? 1 : 0);
		int bg = EpsilonMd3Theme.mix(EpsilonMd3Theme.SURFACE_CONTAINER_HIGH,
			EpsilonMd3Theme.PRIMARY_CONTAINER, focused ? 0.3F : hover * 0.2F);
		FlatRenderer.fillRoundedRect(graphics, Math.round(searchX),
			Math.round(searchY), Math.round(searchX + searchW),
			Math.round(searchY + 20), 5, bg);
		String display = searchQuery.isEmpty() && !focused ? "搜索 ..."
			: searchQuery;
		drawScaled(graphics, display, Math.round(searchX + 8),
			Math.round(searchY + 6),
			searchQuery.isEmpty() && !focused ? EpsilonMd3Theme.TEXT_MUTED
				: EpsilonMd3Theme.TEXT_PRIMARY,
			0.58F);
	}

	private void renderHints(GuiGraphics graphics)
	{
		String[] hints = {"Ctrl + F 打开搜索", "点击分类展开面板",
			"拖动标题移动面板"};
		float scale = 0.62F;
		float lineGap = 5;
		float lineHeight = Minecraft.getInstance().font.lineHeight;
		float xRight = width - EpsilonDropdownTheme.PANEL_MARGIN_X;
		float y = height - EpsilonDropdownTheme.PANEL_MARGIN_Y
			- hints.length * lineHeight - (hints.length - 1) * lineGap;
		for(String hint : hints)
		{
			int hintW = Minecraft.getInstance().font.width(hint);
			drawScaled(graphics, hint, Math.round(xRight - hintW),
				Math.round(y), EpsilonMd3Theme.TEXT_PRIMARY, scale);
			y += lineHeight + lineGap;
		}
	}

	private void renderHudButton(GuiGraphics graphics, int mouseX, int mouseY)
	{
		int buttonWidth = 72;
		int buttonHeight = 24;
		int left = width - 16 - buttonWidth;
		int top = height - 16 - buttonHeight;
		boolean hovered = mouseX >= left && mouseX < left + buttonWidth
			&& mouseY >= top && mouseY < top + buttonHeight;
		float hover = settingsHovers.computeIfAbsent("__hud__",
			k -> new UiTween(0, 120)).update(hovered ? 1 : 0);
		int bg = EpsilonMd3Theme.mix(EpsilonMd3Theme.SURFACE_CONTAINER_HIGH,
			EpsilonMd3Theme.SURFACE_CONTAINER_HIGHEST, hover);
		FlatRenderer.fillRoundedRect(graphics, left, top, left + buttonWidth,
			top + buttonHeight, 6, bg);
		graphics.drawCenteredString(font, "打开 HUD 编辑器",
			left + buttonWidth / 2, top + (buttonHeight - font.lineHeight) / 2,
			EpsilonMd3Theme.TEXT_PRIMARY);
	}

	private float mainPanelHeight()
	{
		int index = panelOrder.size() + 1;
		int rows = (int)Math.ceil(index / (float)ICON_COLUMNS);
		float contentHeight = CONTENT_PADDING + rows * ICON_SIZE
			+ Math.max(0, rows - 1) * ICON_GAP + 8 + CONTENT_PADDING;
		contentHeight += 5 * 19 + 4;
		return EpsilonDropdownTheme.PANEL_HEADER_HEIGHT + contentHeight
			+ EpsilonDropdownTheme.PANEL_BOTTOM_PADDING;
	}

	private float mainIconX(int index)
	{
		int rowStart = index / ICON_COLUMNS * ICON_COLUMNS;
		int rowCount = Math.min(ICON_COLUMNS,
			panelOrder.size() + 1 - rowStart);
		float rowWidth = rowCount * ICON_SIZE + Math.max(0, rowCount - 1)
			* ICON_GAP;
		float rowX = mainX + (MAIN_WIDTH - rowWidth) * 0.5F;
		return rowX + (index - rowStart) * (ICON_SIZE + ICON_GAP);
	}

	// ---------- 输入 ----------

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button)
	{
		if(closing)
			return true;
		if(button == GLFW.GLFW_MOUSE_BUTTON_LEFT && hudButtonContains(mouseX,
			mouseY))
		{
			openingHudEditor = true;
			hudEditorOpenAtNanos = System.nanoTime() + 90_000_000L;
			return true;
		}
		// 搜索框
		if(button == 0 && mouseX >= EpsilonDropdownTheme.PANEL_MARGIN_X
			&& mouseX < EpsilonDropdownTheme.PANEL_MARGIN_X
				+ Mth.clamp(width - EpsilonDropdownTheme.PANEL_MARGIN_X * 2,
					140, 200)
			&& mouseY >= height - EpsilonDropdownTheme.PANEL_MARGIN_Y - 20
			&& mouseY < height - EpsilonDropdownTheme.PANEL_MARGIN_Y)
		{
			searchFocused = true;
			return true;
		}
		if(button == 0 && searchFocused)
			searchFocused = false;

		// 主面板
		if(mouseX >= mainX && mouseX <= mainX + MAIN_WIDTH
			&& mouseY >= mainY && mouseY <= mainY + mainPanelHeight())
		{
			if(mouseY <= mainY + EpsilonDropdownTheme.PANEL_HEADER_HEIGHT)
			{
				if(button == 0)
				{
					mainDragging = true;
					mainDragOffsetX = (float)(mainX - mouseX);
					mainDragOffsetY = (float)(mainY - mouseY);
					return true;
				}
				return true;
			}
			if(button != 0)
				return true;
			if(mainIconClicked(mouseX, mouseY))
				return true;
			if(mainSettingClicked(mouseX, mouseY))
				return true;
			return true;
		}

		// 分类面板（逆序，顶层优先）
		for(int i = panelOrder.size() - 1; i >= 0; i--)
		{
			EpsilonCategoryPanel panel = categoryPanels.get(panelOrder.get(i));
			if(panel.mouseClicked(mouseX, mouseY, button))
				return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	private boolean mainIconClicked(double mouseX, double mouseY)
	{
		float currentY = mainY + EpsilonDropdownTheme.PANEL_HEADER_HEIGHT
			+ CONTENT_PADDING;
		int index = 0;
		for(Category category : panelOrder)
		{
			float iconX = mainIconX(index);
			float iconY = currentY + index / ICON_COLUMNS
				* (ICON_SIZE + ICON_GAP);
			if(mouseX >= iconX && mouseX < iconX + ICON_SIZE && mouseY >= iconY
				&& mouseY < iconY + ICON_SIZE)
			{
				EpsilonCategoryPanel panel = categoryPanels.get(category);
				boolean nowVisible = !panel.isVisible();
				panel.setVisible(nowVisible);
				panel.setOpened(nowVisible);
				return true;
			}
			index++;
		}
		float collapseX = mainIconX(index);
		float collapseY = currentY + index / ICON_COLUMNS
			* (ICON_SIZE + ICON_GAP);
		if(mouseX >= collapseX && mouseX < collapseX + ICON_SIZE
			&& mouseY >= collapseY && mouseY < collapseY + ICON_SIZE)
		{
			for(Category category : panelOrder)
			{
				EpsilonCategoryPanel panel = categoryPanels.get(category);
				panel.setVisible(false);
				panel.setOpened(false);
			}
			return true;
		}
		return false;
	}

	private boolean mainSettingClicked(double mouseX, double mouseY)
	{
		int index = panelOrder.size() + 1;
		int rows = (int)Math.ceil(index / (float)ICON_COLUMNS);
		float settingsY = mainY + EpsilonDropdownTheme.PANEL_HEADER_HEIGHT
			+ CONTENT_PADDING + rows * ICON_SIZE + Math.max(0, rows - 1)
				* ICON_GAP + 8 + CONTENT_PADDING;
		String[] ids = {"Chinese", "Vape mode", "Commands", "Font", "Music"};
		float rowTop = settingsY;
		for(String id : ids)
		{
			if(mouseX >= mainX + 4 && mouseX < mainX + MAIN_WIDTH - 4
				&& mouseY >= rowTop && mouseY < rowTop + 18)
			{
				switch(id)
				{
					case "Chinese" -> {
						var setting = WURST.getOtfs().translationsOtf
							.getForceEnglish();
						setting.setChecked(!setting.isChecked());
					}
					case "Vape mode" -> {
						GuiPreferences preferences =
							WURST.getGuiPreferences();
						ClickGuiScreens.setVapeMode(!preferences.isVapeMode());
					}
					case "Commands" -> {
						GuiPreferences preferences =
							WURST.getGuiPreferences();
						preferences.setCommandsEnabled(
							!preferences.isCommandsEnabled());
					}
					case "Font" -> {
						GuiPreferences preferences =
							WURST.getGuiPreferences();
						preferences.setFontEnabled(!preferences.isFontEnabled());
					}
					case "Music" -> minecraft.setScreen(
						new NeteaseMusicScreen(this));
					default -> {}
				}
				return true;
			}
			rowTop += 19;
		}
		return false;
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button)
	{
		if(button == 0 && mainDragging)
		{
			mainDragging = false;
			return true;
		}
		for(Category category : panelOrder)
		{
			EpsilonCategoryPanel panel = categoryPanels.get(category);
			if(panel.mouseReleased(mouseX, mouseY, button))
				return true;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button,
		double dragX, double dragY)
	{
		if(button != 0)
			return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
		if(mainDragging)
		{
			mainX = (float)(mouseX + mainDragOffsetX);
			mainY = (float)(mouseY + mainDragOffsetY);
			return true;
		}
		for(Category category : panelOrder)
		{
			EpsilonCategoryPanel panel = categoryPanels.get(category);
			if(panel.mouseDragged(mouseX, mouseY, button))
				return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double delta)
	{
		for(int i = panelOrder.size() - 1; i >= 0; i--)
		{
			EpsilonCategoryPanel panel = categoryPanels.get(panelOrder.get(i));
			if(panel.mouseScrolled(mouseX, mouseY, delta))
				return true;
		}
		return super.mouseScrolled(mouseX, mouseY, delta);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers)
	{
		if(activeTextInput != null && activeTextInput.acceptKey(keyCode))
			return true;
		if(bindingFeature != null)
			return finishBinding(keyCode, scanCode);
		if(keyCode == GLFW.GLFW_KEY_F
			&& (modifiers & GLFW.GLFW_MOD_CONTROL) != 0)
		{
			searchFocused = true;
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
				searchQuery = searchQuery.substring(0,
					searchQuery.length() - 1);
				syncSearchQuery();
				return true;
			}
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
		if(font.width(searchQuery + codePoint) < 180)
		{
			searchQuery += codePoint;
			syncSearchQuery();
		}
		return true;
	}

	private void syncSearchQuery()
	{
		for(Category category : panelOrder)
			categoryPanels.get(category).setSearchQuery(searchQuery);
	}

	@Override
	public void tick()
	{
		if(openingHudEditor && System.nanoTime() >= hudEditorOpenAtNanos)
		{
			openingHudEditor = false;
			minecraft.setScreen(new HudEditorScreen(this));
			return;
		}
		for(Category category : panelOrder)
			categoryPanels.get(category).tick();
		super.tick();
	}

	@Override
	public void onClose()
	{
		if(closing)
			return;
		closing = true;
		openingHudEditor = false;
		bindingFeature = null;
		searchFocused = false;
		if(activeTextInput != null)
		{
			activeTextInput.loseFocus();
			activeTextInput = null;
		}
	}

	@Override
	public boolean isPauseScreen()
	{
		return false;
	}

	// ---------- VapeGuiContext ----------

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
		return 1;
	}

	@Override
	public void enableScissor(GuiGraphics graphics, double left, double top,
		double right, double bottom)
	{
		graphics.enableScissor((int)Math.floor(left), (int)Math.floor(top),
			(int)Math.ceil(right - left), (int)Math.ceil(bottom - top));
	}

	// ---------- 工具 ----------

	private boolean hudButtonContains(double mouseX, double mouseY)
	{
		int buttonWidth = 72;
		int buttonHeight = 24;
		int left = width - 16 - buttonWidth;
		int top = height - 16 - buttonHeight;
		return mouseX >= left && mouseX < left + buttonWidth && mouseY >= top
			&& mouseY < top + buttonHeight;
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

	private void drawScaled(GuiGraphics graphics, String text, int x, int y,
		int color, float scale)
	{
// 默认 CozyUI 位图字体，9px 整数渲染
		Minecraft.getInstance().font.drawInBatch(
			net.wurstclient.clickgui2.PingFangFont.text(text), x, y, color, false,
			graphics.pose().last().pose(), graphics.bufferSource(),
			Font.DisplayMode.NORMAL, 0, 0);
	}
}
