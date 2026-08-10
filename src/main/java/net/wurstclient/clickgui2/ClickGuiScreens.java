package net.wurstclient.clickgui2;

import net.minecraft.client.gui.screens.Screen;
import net.wurstclient.WurstClient;
import net.wurstclient.clickgui2.component.SuperSoftClickGuiScreen;
import net.wurstclient.clickgui2.component.VapeClickGuiScreen;

public final class ClickGuiScreens
{
	private ClickGuiScreens()
	{
	}

	public static Screen create()
	{
		return create(null);
	}

	public static Screen create(Screen parent)
	{
		if(WurstClient.INSTANCE.getGuiPreferences().isVapeMode())
			return new VapeClickGuiScreen();
		return new SuperSoftClickGuiScreen(parent);
	}

	public static void setVapeMode(boolean enabled)
	{
		WurstClient.INSTANCE.getGuiPreferences().setVapeMode(enabled);
		WurstClient.MC.setScreen(create());
	}
}
