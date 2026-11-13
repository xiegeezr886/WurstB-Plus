/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
package net.wurstclient.hacks;

import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

@SearchTags({"fps limiter", "fps", "framerate", "frame rate", "limit fps"})
public final class FpsLimiterHack extends Hack implements UpdateListener
{
	private final SliderSetting maxFps = new SliderSetting("Max FPS",
		"The frame rate limit to apply while this hack is enabled."
			+ " Values of 260 or higher mean unlimited.",
		120, 30, 260, 10, ValueDisplay.INTEGER);

	private Integer oldLimit;

	public FpsLimiterHack()
	{
		super("FPSLimiter");
		setCategory(Category.OTHER);
		addSetting(maxFps);
	}

	@Override
	protected void onEnable()
	{
		oldLimit = MC.options.framerateLimit().get();
		EVENTS.add(UpdateListener.class, this);
		applyLimit();
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);

		if(oldLimit != null)
			MC.options.framerateLimit().set(oldLimit);

		oldLimit = null;
	}

	@Override
	public void onUpdate()
	{
		applyLimit();
	}

	private void applyLimit()
	{
		int value = maxFps.getValueI();

		if(MC.options.framerateLimit().get() != value)
			MC.options.framerateLimit().set(value);
	}
}
