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

@SearchTags({"weather changer", "client weather", "rain", "thunder"})
public final class WeatherChangerHack extends Hack
{
	private final SliderSetting rainLevel = new SliderSetting("Rain level",
		"The rain level to display on the client.", 0, 0, 1, 0.01,
		ValueDisplay.PERCENTAGE);
	private final SliderSetting thunderLevel = new SliderSetting("Thunder level",
		"The thunder level to display on the client.", 0, 0, 1, 0.01,
		ValueDisplay.PERCENTAGE);

	public WeatherChangerHack()
	{
		super("WeatherChanger");
		setCategory(Category.RENDER);
		addSetting(rainLevel);
		addSetting(thunderLevel);
	}

	public float changeRain(float original)
	{
		return isEnabled() ? rainLevel.getValueF() : original;
	}

	public float changeThunder(float original)
	{
		// Vanilla's thunder getter returns thunder intensity multiplied by rain.
		return isEnabled() ? thunderLevel.getValueF() * rainLevel.getValueF()
			: original;
	}
}
