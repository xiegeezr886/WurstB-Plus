/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.wurstclient.Category;
import net.wurstclient.DontBlock;
import net.wurstclient.SearchTags;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.minecraft.client.multiplayer.ClientLevel;
import net.wurstclient.events.WorldChangeListener;

@SearchTags({"auto reconnect", "AutoRejoin", "auto rejoin"})
@DontBlock
public final class AutoReconnectHack extends Hack implements WorldChangeListener
{
	private final SliderSetting waitTime =
		new SliderSetting("Wait time", "Time before reconnecting in seconds.",
			5, 0, 60, 0.5, ValueDisplay.DECIMAL.withSuffix("s"));
	private int temporaryWaitTicks = -1;
	private boolean previouslyEnabled;
	
	public AutoReconnectHack()
	{
		super("AutoReconnect");
		setCategory(Category.OTHER);
		addSetting(waitTime);
	}
	
	public int getWaitTicks()
	{
		if(temporaryWaitTicks >= 0)
			return temporaryWaitTicks;
		return (int)(waitTime.getValue() * 20);
	}

	/** One reconnect session without replacing the user's persisted wait setting. */
	public void requestOnce(int seconds)
	{
		if(temporaryWaitTicks < 0)
		{
			previouslyEnabled = isEnabled();
			EVENTS.add(WorldChangeListener.class, this);
		}
		temporaryWaitTicks = Math.max(0, seconds) * 20;
		setEnabled(true);
	}

	@Override
	public void onWorldChange(ClientLevel world)
	{
		if(world == null || temporaryWaitTicks < 0)
			return;
		temporaryWaitTicks = -1;
		EVENTS.remove(WorldChangeListener.class, this);
		if(!previouslyEnabled)
			setEnabled(false);
	}

	@Override
	protected void onDisable()
	{
		temporaryWaitTicks = -1;
		EVENTS.remove(WorldChangeListener.class, this);
	}
	
	// See DisconnectedScreenMixin
}
