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

@SearchTags({"slow swing", "slowswing", "swing speed", "hand swing"})
public final class SlowSwingHack extends Hack
{
	private final SliderSetting delay = new SliderSetting("Delay",
		"Hand swing duration in ticks.", 14, 2, 20, 1,
		ValueDisplay.INTEGER.withSuffix(" ticks"));

	public SlowSwingHack()
	{
		super("SlowSwing");
		setCategory(Category.RENDER);
		addSetting(delay);
	}

	public int getDelay()
	{
		return delay.getValueI();
	}

	// See LivingEntityMixin.
}
