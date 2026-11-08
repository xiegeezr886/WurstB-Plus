/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 * Adapted from Meteor Client. Copyright (c) Meteor Development.
 * SPDX-License-Identifier: GPL-3.0
 */
package net.wurstclient.hacks;

import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

@SearchTags({"time changer", "client time", "day time"})
public final class TimeChangerHack extends Hack
{
	private final SliderSetting time = new SliderSetting("Time",
		"The time of day to display on the client.", 0, -20000, 20000, 100,
		ValueDisplay.INTEGER);

	public TimeChangerHack()
	{
		super("TimeChanger");
		setCategory(Category.RENDER);
		addSetting(time);
	}

	/** Overrides reads; server time, clock rates and ticking stay intact. */
	public long changeTime(long original)
	{
		return isEnabled() ? time.getValueI() : original;
	}
}
