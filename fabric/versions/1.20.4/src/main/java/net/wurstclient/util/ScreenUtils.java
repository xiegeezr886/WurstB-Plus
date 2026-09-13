package net.wurstclient.util;

import java.util.List;
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
}
