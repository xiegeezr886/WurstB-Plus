/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.clickgui2;

import net.minecraft.client.gui.screens.Screen;
import net.wurstclient.WurstClient;
import net.wurstclient.clickgui2.epsilon.EpsilonPanelNavigatorScreen;

/**
 * 导航器用哪个界面，形状与 {@link ClickGuiScreens} 对称：默认是 Epsilon 中央
 * 面板（{@link EpsilonPanelNavigatorScreen}），{@code riseMode} 打开时改用原有
 * 的 Rise 6.1.30 移植（{@link NavigatorScreen}）。
 *
 * <p>
 * {@code riseMode} 与 ClickGUI 的 {@code vapeMode} 一样是一个**独立的布尔偏好**
 * 加一个独立 setter，不是从某个枚举派生的；开关入口在导航器的客户端设置页，
 * ClickGUI 的设置列表里也有一个，这样切到 Rise 之后还能切回来。
 */
public final class NavigatorScreens
{
	private NavigatorScreens()
	{}
	
	public static Screen create()
	{
		return create(null);
	}
	
	public static Screen create(Screen parent)
	{
		return WurstClient.INSTANCE.getGuiPreferences().isRiseMode()
			? new NavigatorScreen() : new EpsilonPanelNavigatorScreen();
	}
	
	/** 与 {@link ClickGuiScreens#setVapeMode(boolean)} 对称的独立开关。 */
	public static void setRiseMode(boolean enabled)
	{
		setRiseMode(enabled, true);
	}
	
	public static void setRiseMode(boolean enabled, boolean reopen)
	{
		WurstClient wurst = WurstClient.INSTANCE;
		wurst.getGuiPreferences().setRiseMode(enabled);
		
		if(reopen && WurstClient.MC != null)
			WurstClient.MC.setScreen(create());
	}
}
