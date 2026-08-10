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
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.ReachPolicy;

@SearchTags({"range"})
public final class ReachHack extends Hack
{
	private final SliderSetting entityRange = new SliderSetting("Entity range",
		"Maximum range for selecting and attacking entities.", 6, 1, 15,
		0.05, ValueDisplay.DECIMAL).aliases("Range");

	private final SliderSetting blockRange = new SliderSetting("Block range",
		"Maximum range for selecting and interacting with blocks.", 5, 1, 15,
		0.05, ValueDisplay.DECIMAL);
	private final CheckboxSetting onlyWhileSprinting = new CheckboxSetting(
		"Only while sprinting", "Only extends entity range while sprinting.",
		false);
	private final CheckboxSetting disableInFluid = new CheckboxSetting(
		"Disable in fluids", "Uses vanilla entity range in water or lava.", true);
	
	public ReachHack()
	{
		super("Reach");
		setCategory(Category.OTHER);
		addSetting(entityRange);
		addSetting(blockRange);
		addSetting(onlyWhileSprinting);
		addSetting(disableInFluid);
	}
	
	public float getReachDistance()
	{
		return Math.max(getEntityRange(), getBlockRange());
	}

	public float getEntityRange()
	{
		boolean playerAvailable = MC.player != null;
		boolean inFluid = playerAvailable && (MC.player.isInWaterOrBubble()
			|| MC.player.isInLava());
		return ReachPolicy.resolveEntityRange(entityRange.getValueF(), 3,
			playerAvailable, playerAvailable && MC.player.isSprinting(),
			onlyWhileSprinting.isChecked(), inFluid, disableInFluid.isChecked());
	}

	public float getBlockRange()
	{
		return blockRange.getValueF();
	}
	
	// See ClientPlayerInteractionManagerMixin.onGetReachDistance() and
	// ClientPlayerInteractionManagerMixin.hasExtendedReach()
}
