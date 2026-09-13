/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.wurstclient.DontBlock;
import net.wurstclient.SearchTags;
import net.wurstclient.clickgui2.NavigatorScreen;
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
		if(!(MC.screen instanceof NavigatorScreen))
			MC.setScreen(new NavigatorScreen());
		if(WURST.getHudManager() != null)
			WURST.getHudManager().addNotification("Info", getDisplayName(),
				NotificationSeverity.INFO);
		setEnabled(false);
	}
}
