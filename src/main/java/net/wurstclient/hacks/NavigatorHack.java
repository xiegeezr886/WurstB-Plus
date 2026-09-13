/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.client.gui.screens.Screen;
import net.wurstclient.DontBlock;
import net.wurstclient.SearchTags;
import net.wurstclient.WurstClient;
import net.wurstclient.clickgui2.NavigatorScreens;
import net.wurstclient.hack.DontSaveState;
import net.wurstclient.hack.Hack;
import net.wurstclient.hud2.NotificationSeverity;

@DontSaveState
@DontBlock
@SearchTags({"SearchGUI", "search gui", "QuickMenu", "quick menu"})
public final class NavigatorHack extends Hack
{
	public NavigatorHack()
	{
		super("Navigator");
	}

	@Override
	protected void onEnable()
	{
		Screen target = NavigatorScreens.create(MC.screen);
		
		/*
		 * 模式可能在别处被切过（导航器的客户端设置页、ClickGUI 的设置列表），
		 * 所以按目标类型判断，而不是写死某一个界面。
		 */
		if(MC.screen == null || MC.screen.getClass() != target.getClass())
			MC.setScreen(target);
		
		if(WURST.getHudManager() != null)
			WURST.getHudManager().addNotification("Info", getDisplayName(),
				NotificationSeverity.INFO);
		setEnabled(false);
	}
}
