package net.wurstclient.clickgui2;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.renderer.RenderType;

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
		texture = ResourceLocation.fromNamespaceAndPath("wurst", "textures/gui/icons/" + name
			+ ".png");
	}

	public void draw(GuiGraphics graphics, int x, int y, int size, int color)
	{
		RenderSystem.setShaderColor((color >> 16 & 0xFF) / 255F,
			(color >> 8 & 0xFF) / 255F, (color & 0xFF) / 255F,
			(color >>> 24) / 255F);
		graphics.blit(RenderType.GUI_TEXTURED, texture, x, y, 0, 0, size, size, TEXTURE_SIZE,
			TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE);
		RenderSystem.setShaderColor(1, 1, 1, 1);
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
