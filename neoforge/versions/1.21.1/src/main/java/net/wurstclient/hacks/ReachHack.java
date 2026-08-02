/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 * Copyright (c) 2015-2026 CCBlueX
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

@SearchTags({"range"})
public final class ReachHack extends Hack
{
	private final SliderSetting entityRange = new SliderSetting("Entity range",
		"Maximum range for selecting and attacking entities.", 6, 1, 15,
		0.05, ValueDisplay.DECIMAL).aliases("Range");

	private final SliderSetting blockRange = new SliderSetting("Block range",
		"Maximum range for selecting and interacting with blocks.", 5, 1, 15,
		0.05, ValueDisplay.DECIMAL);
	
	public ReachHack()
	{
		super("Reach");
		setCategory(Category.OTHER);
		addSetting(entityRange);
		addSetting(blockRange);
	}
	
	public float getReachDistance()
	{
		return Math.max(getEntityRange(), getBlockRange());
	}

	public float getEntityRange()
	{
		return entityRange.getValueF();
	}

	public float getBlockRange()
	{
		return blockRange.getValueF();
	}
	
	// See ClientPlayerInteractionManagerMixin.onGetReachDistance() and
	// ClientPlayerInteractionManagerMixin.hasExtendedReach()
}
