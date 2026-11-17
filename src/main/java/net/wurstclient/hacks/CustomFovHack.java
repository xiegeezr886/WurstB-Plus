/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 *
 * Ported from InvincibleMachineGun (GPL-3.0).
 */
package net.wurstclient.hacks;

import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

@SearchTags({"custom fov", "fov", "field of view", "zoom"})
public final class CustomFovHack extends Hack
{
	private final SliderSetting fov = new SliderSetting("FOV",
		"The field of view used while this hack is enabled.", 110, 30, 170, 1,
		ValueDisplay.INTEGER.withSuffix("\u00b0"));

	private final SliderSetting itemFov = new SliderSetting("Item FOV",
		"The independent field of view used for held items.",
		70, 30, 170, 1, ValueDisplay.INTEGER.withSuffix("\u00b0"));

	public CustomFovHack()
	{
		super("CustomFov");
		setCategory(Category.RENDER);
		addSetting(fov);
		addSetting(itemFov);
	}

	public double changeFov(double original, boolean world)
	{
		if(isEnabled())
		{
			original = world ? fov.getValue() : itemFov.getValue();
			if(!world)
				return original;
		}
        if(!isEnabled()) original = WURST.getHax().scaffoldWalkHack.getSilenceTellyFov(original);
		return WURST.getOtfs().zoomOtf.changeFovBasedOnZoom(original);
	}
}
