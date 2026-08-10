package net.wurstclient.gui.title;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.mojang.realmsclient.RealmsMainScreen;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.ModListScreen;
import net.minecraftforge.versions.forge.ForgeVersion;
import net.wurstclient.WurstClient;
import net.wurstclient.altmanager.screens.AltManagerScreen;
import net.wurstclient.gui.visual.VisualTheme;
import net.wurstclient.gui.visual.VisualRenderer;
import net.wurstclient.util.ScreenRegistry;

public final class WurstTitleMenu
{
	private static final int ICON_TEXTURE_SIZE = 88;
	private static final int ACCENT = VisualTheme.ACCENT;
	private static final int TEXT = VisualTheme.TEXT;
	private static final int MUTED_TEXT = VisualTheme.TEXT_DIMMED;
	private static final int DIM_TEXT = VisualTheme.TEXT_MUTED;

	private static final ResourceLocation SINGLEPLAYER = icon("singleplayer");
	private static final ResourceLocation MULTIPLAYER = icon("multiplayer");
	private static final ResourceLocation REALMS = icon("realms");
	private static final ResourceLocation OPTIONS = icon("options");
	private static final ResourceLocation USER = icon("user");
	private static final ResourceLocation INFO = icon("info");
	private static final ResourceLocation EXIT = icon("exit");

	private final Screen parent;
	private final List<WurstTitleButton> buttons = new ArrayList<>();

	private Minecraft minecraft;
	private int margin;
	private int cardX;
	private int cardY;
	private int cardWidth;
	private int cardHeight;
	private int cardGap;
	private int utilityY;

	public WurstTitleMenu(Screen parent)
	{
		this.parent = parent;
	}

	public void init(Minecraft minecraft, int screenWidth, int screenHeight,
		Consumer<AbstractWidget> addWidget)
	{
		this.minecraft = minecraft;
		configureIconFiltering();
		buttons.clear();
		updateLayout(screenWidth, screenHeight);

		int y = cardY;
		addButton(addWidget, cardX, y, cardWidth, cardHeight, "单人游戏",
			SINGLEPLAYER, () -> ScreenRegistry.WORLD_SELECTION.open(parent),
			false, false);
		y += cardHeight + cardGap;
		addButton(addWidget, cardX, y, cardWidth, cardHeight, "多人游戏",
			MULTIPLAYER,
			() -> ScreenRegistry.MULTIPLAYER.open(parent), false,
			false);
		y += cardHeight + cardGap;
		addButton(addWidget, cardX, y, cardWidth, cardHeight, "Minecraft Realms",
			REALMS, () -> minecraft.setScreen(new RealmsMainScreen(parent)), false,
			false);
		y += cardHeight + cardGap;
		addButton(addWidget, cardX, y, cardWidth, cardHeight, "游戏设置",
			OPTIONS, () -> ScreenRegistry.OPTIONS.open(parent), false, false);

		int compactGap = 7;
		int compactWidth = (cardWidth - compactGap * 2) / 3;
		int compactHeight = 30;
		addButton(addWidget, cardX, utilityY, compactWidth, compactHeight,
			"账号", USER, () -> minecraft.setScreen(new AltManagerScreen(parent,
				WurstClient.INSTANCE.getAltManager())), true, false);
		addButton(addWidget, cardX + compactWidth + compactGap, utilityY,
			compactWidth, compactHeight, "模组", INFO,
			() -> minecraft.setScreen(new ModListScreen(parent)), true, false);
		addButton(addWidget, cardX + (compactWidth + compactGap) * 2, utilityY,
			cardWidth - (compactWidth + compactGap) * 2, compactHeight,
			"退出", EXIT, minecraft::stop, true, true);
	}

	private void addButton(Consumer<AbstractWidget> addWidget, int x, int y,
		int width, int height, String text, ResourceLocation icon,
		Runnable action, boolean compact, boolean dangerous)
	{
		WurstTitleButton button = new WurstTitleButton(x, y, width, height,
			Component.literal(text), icon, action, compact, dangerous);
		buttons.add(button);
		addWidget.accept(button);
	}

	public void render(GuiGraphics graphics, int mouseX, int mouseY,
		float partialTicks, int screenWidth, int screenHeight)
	{
		drawBackground(graphics, screenWidth, screenHeight);
		drawBrand(graphics, screenWidth);
		drawFooter(graphics, screenWidth, screenHeight);
	}

	private void updateLayout(int screenWidth, int screenHeight)
	{
		margin = Mth.clamp(screenWidth / 35, 14, 30);
		cardWidth = Mth.clamp(Math.round(screenWidth * 0.29F), 260, 340);
		cardHeight = screenHeight < 280 ? 34 : 46;
		cardGap = screenHeight < 280 ? 5 : 9;
		cardX = (screenWidth - cardWidth) / 2;
		int cardsHeight = cardHeight * 4 + cardGap * 3;
		cardY = Math.max(screenHeight < 280 ? 44 : 70,
			(screenHeight - cardsHeight - 36) / 2);
		utilityY = cardY + cardsHeight + (screenHeight < 280 ? 6 : 12);
	}

	private void drawBackground(GuiGraphics graphics, int screenWidth,
		int screenHeight)
	{
		VisualRenderer.gridBackground(graphics, screenWidth, screenHeight);
	}

	private void drawBrand(GuiGraphics graphics, int screenWidth)
	{
		Font font = minecraft.font;
		String prefix = "WurstB+ ";
		float scale = 1.65F;
		int x = cardX;
		int y = 35;
		graphics.pose().pushPose();
		graphics.pose().translate(x, y, 0);
		graphics.pose().scale(scale, scale, 1);
		graphics.drawString(font, prefix, 0, 0, TEXT, false);
		graphics.drawString(font, "Plus", font.width(prefix), 0, ACCENT, false);
		graphics.pose().popPose();
	}

	private void drawFooter(GuiGraphics graphics, int screenWidth,
		int screenHeight)
	{
		Font font = minecraft.font;
		String runtime = "Minecraft "
			+ SharedConstants.getCurrentVersion().getName() + "  /  Forge "
			+ ForgeVersion.getVersion();
		graphics.drawString(font, runtime, margin, screenHeight - 18,
			MUTED_TEXT, false);
		String brand = "WurstB+ Plus";
		graphics.drawString(font, brand,
			screenWidth - margin - font.width(brand),
			screenHeight - 18, DIM_TEXT, false);
	}

	private static ResourceLocation icon(String name)
	{
		return new ResourceLocation("wurst", "textures/gui/fdp/" + name
			+ ".png");
	}

	private void configureIconFiltering()
	{
		for(ResourceLocation icon : List.of(SINGLEPLAYER, MULTIPLAYER, REALMS,
			OPTIONS, USER, INFO, EXIT))
			minecraft.getTextureManager().getTexture(icon).setFilter(true, false);
	}
}
