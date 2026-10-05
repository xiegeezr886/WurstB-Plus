package net.wurstclient.gui.title;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.gui.ModListScreen;
import net.wurstclient.WurstClient;
import net.wurstclient.altmanager.screens.AltManagerScreen;
import net.wurstclient.background.BackgroundManager;
import net.wurstclient.clickgui2.FlatRenderer;
import net.wurstclient.gui.title.TitleMenuLayout.Rect;
import net.wurstclient.gui.title.WurstTitleButton.Style;
import net.wurstclient.gui.visual.VisualRenderer;
import net.wurstclient.gui.visual.VisualTheme;
import net.wurstclient.util.ScreenRegistry;

/**
 * 标题主界面的内容，由 {@code TitleScreenMixin} 在标题界面上调用。
 *
 * <p>
 * 版式按用户给的参考图（{@code docs/title-menu.md} 记了量到的像素）：全屏壁纸 +
 * 底部渐变压暗，左上角账号胶囊，右上角齿轮，左下角白色字标，底部一整条动作条，
 * 里面 4 个等宽按钮。几何全部在 {@link TitleMenuLayout} 里算，这里只管画。</p>
 *
 * <p>
 * 交互元素仍然是原版控件（{@code AbstractButton} 的子类），所以输入、悬停、
 * 旁白都是原版那一套；弹出的齿轮菜单用 {@code visible} 开关，不动态增删控件，
 * 免得在渲染中途改控件表。</p>
 */
public final class WurstTitleMenu
{
	private static final ResourceLocation SINGLEPLAYER = fdp("singleplayer");
	private static final ResourceLocation MULTIPLAYER = fdp("multiplayer");
	private static final ResourceLocation OPTIONS = fdp("options");
	private static final ResourceLocation POWER = fdp("power");
	private static final ResourceLocation USER = fdp("user");
	private static final ResourceLocation INFO = fdp("info");
	private static final ResourceLocation SETTINGS = icon("settings");
	private static final ResourceLocation RENDER = icon("render");
	private static final ResourceLocation LOGO = new ResourceLocation("wurst",
		"textures/gui/wurstb_logo_white.png");


	/** 壁纸整体压暗，保证白字在任何壁纸上都读得出来。 */
	private static final int BACKGROUND_SCRIM = 0x33000000;
	/** 底部渐变压暗，参考图上动作条那一段明显比上方暗。 */
	private static final int BOTTOM_SCRIM = 0x66000000;
	private static final float BOTTOM_SCRIM_START = 0.45F;

	/** 参考图上按 50% 黑压出来的胶囊与动作条底色。 */
	private static final int CHIP_FILL = 0x80000000;
	private static final int RAIL_FILL = 0x80000000;
	private static final int MENU_FILL = 0xF01B1E24;
	private static final int TEXT_MUTED = 0xB3FFFFFF;

	private final Screen parent;
	private final List<WurstTitleButton> menuRows = new ArrayList<>();
	private final TitleLogoVector logoTexture = new TitleLogoVector();

	private Minecraft minecraft;
	private TitleMenuLayout layout;
	private Component playerName;
	private Component welcome;
	private ResourceLocation skin;
	private boolean menuOpen;

	public WurstTitleMenu(Screen parent)
	{
		this.parent = parent;
	}

	public void init(Minecraft minecraft, int screenWidth, int screenHeight,
		Consumer<AbstractWidget> addWidget)
	{
		this.minecraft = minecraft;
		menuRows.clear();
		menuOpen = false;

		playerName = Component.literal(minecraft.getUser().getName());
		welcome = Component.translatable("wurst.title.welcome");
		skin = resolveSkin(minecraft);
		configureTextures(minecraft);

		Font font = minecraft.font;
		int chipTextWidth = Math.max(font.width(playerName),
			font.width(welcome));
		layout = new TitleMenuLayout(screenWidth, screenHeight, chipTextWidth);

		// 底部动作条
		addWidget.accept(action(0, "wurst.title.singleplayer", SINGLEPLAYER,
			() -> ScreenRegistry.WORLD_SELECTION.open(parent)));
		addWidget.accept(action(1, "wurst.title.multiplayer", MULTIPLAYER,
			() -> ScreenRegistry.MULTIPLAYER.open(parent)));
		addWidget.accept(action(2, "wurst.title.settings", OPTIONS,
			() -> ScreenRegistry.OPTIONS.open(parent)));
		addWidget.accept(action(3, "wurst.title.quit", POWER, minecraft::stop));

		// 右上角齿轮
		Rect gear = layout.gear();
		addWidget.accept(new WurstTitleButton(gear.x(), gear.y(),
			gear.width(), gear.height(),
			Component.translatable("wurst.title.menu"), SETTINGS,
			this::toggleMenu, Style.ICON, layout.gearRadius(),
			layout.gearIconSize()));

		// 齿轮菜单：默认收起来，靠 visible 开关
		addMenuRow(addWidget, 0, "wurst.title.background", RENDER,
			() -> minecraft.setScreen(new BackgroundSelectScreen(parent)));
		addMenuRow(addWidget, 1, "wurst.title.accounts", USER,
			() -> minecraft.setScreen(new AltManagerScreen(parent,
				WurstClient.INSTANCE.getAltManager())));
		addMenuRow(addWidget, 2, "wurst.title.mods", INFO,
			() -> minecraft.setScreen(new ModListScreen(parent)));
	}

	private WurstTitleButton action(int index, String translationKey,
		ResourceLocation icon, Runnable run)
	{
		Rect rect = layout.action(index);
		return new WurstTitleButton(rect.x(), rect.y(), rect.width(),
			rect.height(), Component.translatable(translationKey), icon, run,
			Style.ACTION, layout.buttonRadius(), layout.actionIconSize());
	}

	private void addMenuRow(Consumer<AbstractWidget> addWidget, int index,
		String translationKey, ResourceLocation icon, Runnable run)
	{
		Rect rect = layout.menuRows().get(index);
		WurstTitleButton row = new WurstTitleButton(rect.x(), rect.y(),
			rect.width(), rect.height(), Component.translatable(translationKey),
			icon, run, Style.ROW, layout.menuRadius(),
			layout.menuIconSize());
		row.visible = false;
		menuRows.add(row);
		addWidget.accept(row);
	}

	private void toggleMenu()
	{
		menuOpen = !menuOpen;

		for(WurstTitleButton row : menuRows)
			row.visible = menuOpen;
	}

	public void render(GuiGraphics graphics, int mouseX, int mouseY,
		float partialTicks, int screenWidth, int screenHeight)
	{
		if(layout == null)
			return;

		drawBackground(graphics, mouseX, mouseY, screenWidth, screenHeight);
		drawChip(graphics);
		drawLogo(graphics);
		drawRail(graphics);

		if(menuOpen)
			drawMenu(graphics);
	}

	/**
	 * 用户选的壁纸；没选或还没解码好时退回内置网格。两种都要压暗，菜单才在
	 * 任何背景上都读得出来。
	 */
	private void drawBackground(GuiGraphics graphics, int mouseX, int mouseY,
		int screenWidth, int screenHeight)
	{
		if(BackgroundManager.get().render(graphics, screenWidth, screenHeight,
			mouseX, mouseY))
			graphics.fill(0, 0, screenWidth, screenHeight, BACKGROUND_SCRIM);
		else
			VisualRenderer.gridBackground(graphics, screenWidth, screenHeight);

		graphics.fillGradient(0,
			Math.round(screenHeight * BOTTOM_SCRIM_START), screenWidth,
			screenHeight, 0x00000000, BOTTOM_SCRIM);
	}

	private void drawChip(GuiGraphics graphics)
	{
		Rect chip = layout.chip();
		FlatRenderer.fillRoundedRect(graphics, chip.x(), chip.y(), chip.right(),
			chip.bottom(), layout.chipRadius(), CHIP_FILL);

		Rect avatar = layout.avatar();
		if(skin != null)
			PlayerFaceRenderer.draw(graphics, skin, avatar.x(), avatar.y(),
				avatar.width());
		else
			FlatRenderer.fillRoundedRect(graphics, avatar.x(), avatar.y(),
				avatar.right(), avatar.bottom(), avatar.width() / 2,
				0x40FFFFFF);

		Font font = minecraft.font;
		graphics.drawString(font, playerName, layout.chipTextX(),
			layout.chipNameY(), VisualTheme.TEXT, false);
		graphics.drawString(font, welcome, layout.chipTextX(),
			layout.chipSubtitleY(), TEXT_MUTED, false);
	}

	/**
	 * 左下角的白色字标。
	 *
	 * <p>
	 * 贴图由 {@link TitleLogoVector} 按**实际绘制像素数**光栅化：资源里是描摹出来的
	 * 矢量路径，任何窗口尺寸都是等尺寸渲染，所以不存在位图缩小被 GPU 的 2×2 采样
	 * 啃掉发丝的问题（那条坑的实机对比见 {@code docs/title-logo-native-vs-baked.png}）。</p>
	 *
	 * <p>
	 * 这里刻意不设 {@code setFilter}：等尺寸采样用不到过滤，而资源贴图的过滤在
	 * 懒加载时会被 MC 按默认值重置，写了反而容易误以为它生效。</p>
	 *
	 * <p>
	 * 原版 {@code GuiGraphics.setColor} 改的是<b>全局 shader 颜色</b>，会给整帧
	 * 后面的东西染色，所以这里不画投影，只画一遍本体。</p>
	 */
	private void drawLogo(GuiGraphics graphics)
	{
		Rect logo = layout.logo();
		int pixels = TitleLogoVector.physicalWidth(logo.width(),
			minecraft.getWindow().getGuiScale());
		TitleLogoVector.Bound bound = logoTexture.bind(pixels);

		graphics.blit(bound.location(), logo.x(), logo.y(), logo.width(),
			logo.height(), 0F, 0F, bound.width(), bound.height(), bound.width(),
			bound.height());
	}


	/** 动作条的底板；4 个按钮是控件，画在这上面。 */
	private void drawRail(GuiGraphics graphics)
	{
		Rect rail = layout.rail();
		FlatRenderer.fillRoundedRect(graphics, rail.x(), rail.y(), rail.right(),
			rail.bottom(), layout.railRadius(), RAIL_FILL);
	}

	private void drawMenu(GuiGraphics graphics)
	{
		Rect menu = layout.menu();
		FlatRenderer.fillRoundedRect(graphics, menu.x(), menu.y(), menu.right(),
			menu.bottom(), layout.menuRadius(), MENU_FILL);
	}

	/**
	 * 玩家皮肤：本地账号的皮肤在登录时就写进了 profile 的 textures 属性，
	 * {@code getInsecureSkinLocation} 只是把它登记成贴图，不会发网络请求，也
	 * 不会返回 null（认不出来时给默认皮肤）。
	 */
	private static ResourceLocation resolveSkin(Minecraft minecraft)
	{
		try
		{
			GameProfile profile = minecraft.getUser().getGameProfile();
			return minecraft.getSkinManager().getInsecureSkinLocation(profile);
		}catch(RuntimeException e)
		{
			return null;
		}
	}

	private static void configureTextures(Minecraft minecraft)
	{
		for(ResourceLocation location : List.of(SINGLEPLAYER, MULTIPLAYER,
			OPTIONS, POWER, USER, INFO, SETTINGS, RENDER, LOGO))
			minecraft.getTextureManager().getTexture(location)
				.setFilter(true, false);
	}

	private static ResourceLocation fdp(String name)
	{
		return new ResourceLocation("wurst",
			"textures/gui/fdp/" + name + ".png");
	}

	private static ResourceLocation icon(String name)
	{
		return new ResourceLocation("wurst",
			"textures/gui/icons/" + name + ".png");
	}
}
