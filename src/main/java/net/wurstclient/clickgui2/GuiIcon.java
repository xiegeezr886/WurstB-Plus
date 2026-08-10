package net.wurstclient.clickgui2;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public enum GuiIcon
{
	RENDER("render"),
	MOVEMENT("movement"),
	CLIENT("client"),
	COMBAT("combat"),
	WORLD("world"),
	MISC("misc"),
	FUN("fun"),
	PLAYER("player"),
	SEARCH("search"),
	CONFIG("config"),
	FONT("font"),
	SETTINGS("settings"),
	MENU("menu"),
	BOOK("book"),
	CLOSE("close"),
	PIN("pin"),
	WINDOW_TOGGLE("window_toggle"),
	CHEVRON("chevron");

	private static final int TEXTURE_SIZE = 64;

	private final ResourceLocation texture;

	GuiIcon(String name)
	{
		texture = new ResourceLocation("wurst", "textures/gui/icons/" + name
			+ ".png");
	}

	public void draw(GuiGraphics graphics, int x, int y, int size, int color)
	{
		float[] previousColor = RenderSystem.getShaderColor().clone();
		graphics.setColor((color >> 16 & 0xFF) / 255F * previousColor[0],
			(color >> 8 & 0xFF) / 255F * previousColor[1],
			(color & 0xFF) / 255F * previousColor[2],
			(color >>> 24) / 255F * previousColor[3]);
		graphics.blit(texture, x, y, size, size, 0, 0, TEXTURE_SIZE,
			TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE);
		graphics.setColor(previousColor[0], previousColor[1], previousColor[2],
			previousColor[3]);
	}

	public void drawRotated(GuiGraphics graphics, int x, int y, int size,
		int color, float degrees)
	{
		graphics.pose().pushPose();
		graphics.pose().translate(x + size / 2F, y + size / 2F, 0);
		graphics.pose().mulPose(Axis.ZP.rotationDegrees(degrees));
		graphics.pose().translate(-x - size / 2F, -y - size / 2F, 0);
		draw(graphics, x, y, size, color);
		graphics.pose().popPose();
	}

	public static void configureFiltering(Minecraft minecraft)
	{
		for(GuiIcon icon : values())
			minecraft.getTextureManager().getTexture(icon.texture)
				.setFilter(true, false);
	}
}
