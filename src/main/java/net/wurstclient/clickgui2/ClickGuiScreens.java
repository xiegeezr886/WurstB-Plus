package net.wurstclient.clickgui2;

import net.minecraft.client.gui.screens.Screen;
import net.wurstclient.WurstClient;
import net.wurstclient.clickgui2.component.SuperSoftClickGuiScreen;
import net.wurstclient.clickgui2.component.VapeClickGuiScreen;
import net.wurstclient.clickgui2.epsilon.EpsilonDropdownScreen;

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
		// 图标纹理默认是最近邻过滤：不按原始尺寸（64px）绘制就会发糊、边缘锯齿。
		// 以前只有 SuperSoftClickGuiScreen.init() 里调过 configureFiltering，
		// 所以 Epsilon / Vape 两个风格的图标一直是像素化的。这里在三种风格共用的
		// 入口统一打开线性过滤。
		if(WurstClient.MC != null)
			GuiIcon.configureFiltering(WurstClient.MC);

		return switch(WurstClient.INSTANCE.getGuiPreferences()
			.getClickGuiStyle())
		{
			case VAPE -> new VapeClickGuiScreen();
			case SUPERSOFT -> new SuperSoftClickGuiScreen(parent);
			case EPSILON -> new EpsilonDropdownScreen(parent);
		};
	}

	public static void setVapeMode(boolean enabled)
	{
		setStyle(enabled ? ClickGuiStyle.VAPE : ClickGuiStyle.EPSILON, true);
	}

	public static void setStyle(ClickGuiStyle style)
	{
		setStyle(style, true);
	}

	public static void setStyle(ClickGuiStyle style, boolean reopen)
	{
		WurstClient wurst = WurstClient.INSTANCE;
		wurst.getGuiPreferences().setClickGuiStyle(style);
		if(wurst.getHax() != null
			&& wurst.getHax().clickGuiHack.getStyle() != style)
			wurst.getHax().clickGuiHack.applyStyle(style);
		if(reopen && WurstClient.MC != null)
			WurstClient.MC.setScreen(create());
	}

	public static void cycleStyle()
	{
		setStyle(WurstClient.INSTANCE.getGuiPreferences().getClickGuiStyle()
			.next(), true);
	}
}
