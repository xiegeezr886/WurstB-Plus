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
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

@SearchTags({"fps spoofer", "fake fps", "fps", "spoof fps"})
public final class FPSSpooferHack extends Hack
{
	private final SliderSetting multiplier = new SliderSetting("Multiplier",
		"Multiplies the displayed FPS by this factor.", 2, 1, 30, 1,
		ValueDisplay.INTEGER);

	public FPSSpooferHack()
	{
		super("FPSSpoofer");
		setCategory(Category.OTHER);
		addSetting(multiplier);
	}

	public int getMultiplier()
	{
		return multiplier.getValueI();
	}

	// See MinecraftClientMixin.onGetFps().
}
