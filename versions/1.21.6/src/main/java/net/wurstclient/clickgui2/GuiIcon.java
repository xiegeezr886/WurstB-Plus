package net.wurstclient.clickgui2;

import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.wurstclient.util.render.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

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

	private final Identifier texture;

	GuiIcon(String name)
	{
		texture = Identifier.fromNamespaceAndPath("wurst", "textures/gui/icons/" + name
			+ ".png");
	}

	public void draw(GuiGraphicsExtractor graphics, int x, int y, int size, int color)
	{
		graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0, 0, size,
			size, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE, color);
	}

	public void drawRotated(GuiGraphicsExtractor graphics, int x, int y, int size,
		int color, float degrees)
	{
		graphics.pose().pushMatrix();
		graphics.pose().translate(x + size / 2F, y + size / 2F);
		graphics.pose().rotate((float)Math.toRadians(degrees));
		graphics.pose().translate(-x - size / 2F, -y - size / 2F);
		draw(graphics, x, y, size, color);
		graphics.pose().popMatrix();
	}

	public static void configureFiltering(Minecraft minecraft)
	{
	}
}
