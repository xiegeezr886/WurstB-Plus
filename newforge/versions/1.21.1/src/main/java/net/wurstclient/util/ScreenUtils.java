package net.wurstclient.util;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;

public enum ScreenUtils
{
	;

	public static List<AbstractWidget> getButtons(Screen screen)
	{
		return screen.children().stream()
			.filter(AbstractWidget.class::isInstance)
			.map(AbstractWidget.class::cast).toList();
	}
	
	public static void renderBackground(GuiGraphics context, Screen screen,
		int mouseX, int mouseY, float partialTicks)
	{
		Minecraft mc = screen.getMinecraft();
		
		if(mc == null || mc.level == null)
		{
			screen.renderBackground(context, mouseX, mouseY, partialTicks);
			return;
		}
		
		context.fillGradient(0, 0, screen.width, screen.height,
			-1072689136, -804253680);
	}
}
