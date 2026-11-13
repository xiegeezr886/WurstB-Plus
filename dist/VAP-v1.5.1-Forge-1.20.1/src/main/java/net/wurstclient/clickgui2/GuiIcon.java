package net.wurstclient.clickgui2;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.wurstclient.hud2.nanovg.NanoVGUI;

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

	private final ResourceLocation texture;

	GuiIcon(String name)
	{
		texture = new ResourceLocation("wurst", "textures/gui/icons/" + name
			+ ".png");
	}

	public void draw(GuiGraphics graphics, int x, int y, int size, int color)
	{
		NanoVGUI.image(texture, x, y, size, size, 0, color);
	}

	public void drawRotated(GuiGraphics graphics, int x, int y, int size,
		int color, float degrees)
	{
		NanoVGUI.imageRotated(texture, x + size / 2F, y + size / 2F, size,
			size, 0, degrees, color);
	}

	public static void configureFiltering(Minecraft minecraft)
	{
		// NanoVG owns the sampling state of the images it uploads.
	}
}
