/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PortalNauseaListener;
import net.wurstclient.events.PortalNauseaListener.PortalNauseaEvent;
import net.wurstclient.hack.Hack;

@SearchTags({"portal gui"})
public final class PortalGuiHack extends Hack implements PortalNauseaListener
{
	public PortalGuiHack()
	{
		super("PortalGUI");
		setCategory(Category.OTHER);
	}
	
	@Override
	protected void onEnable()
	{
		EVENTS.add(PortalNauseaListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(PortalNauseaListener.class, this);
	}
	
	@Override
	public void onPortalNausea(PortalNauseaEvent event)
	{
		if(isEnabled())
			event.setKeepScreen(true);
	}
	
	// See ClientPlayerEntityMixin.beforeUpdateNausea()
}
