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
import net.wurstclient.WurstClient;
import net.wurstclient.events.DeathListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.waypoints.Waypoint;

@SearchTags({"auto respawn", "AutoRevive", "auto revive"})
public final class AutoRespawnHack extends Hack implements DeathListener
{
	private final CheckboxSetting button =
		new CheckboxSetting("Death screen button", "Shows a button on the death"
			+ " screen that lets you quickly enable AutoRespawn.", true);
	private final CheckboxSetting deathWaypoint = new CheckboxSetting("Death waypoint",
		"自动复活前，在导航点列表记录最近一次死亡坐标。", true);
	
	public AutoRespawnHack()
	{
		super("AutoRespawn");
		setCategory(Category.COMBAT);
		addSetting(button);
		addSetting(deathWaypoint);
	}
	
	@Override
	protected void onEnable()
	{
		EVENTS.add(DeathListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(DeathListener.class, this);
	}
	
	@Override
	public void onDeath()
	{
		if(MC.player == null || MC.level == null)
			return;
		if(deathWaypoint.isChecked())
			WURST.getWaypointsManager().add(new Waypoint("Last death",
				MC.level.dimension().location(), MC.player.blockPosition(), 0xFF3030));
		MC.player.respawn();
		MC.setScreen(null);
	}
	
	public boolean shouldShowButton()
	{
		return WurstClient.INSTANCE.isEnabled() && !isEnabled()
			&& button.isChecked();
	}
}
