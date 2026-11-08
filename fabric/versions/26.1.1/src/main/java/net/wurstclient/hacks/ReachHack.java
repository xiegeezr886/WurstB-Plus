/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 * Copyright (c) 2015-2026 CCBlueX
 * Adapted from Meteor Client. Copyright (c) Meteor Development.
 * SPDX-License-Identifier: GPL-3.0
 */
package net.wurstclient.hacks;

import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

@SearchTags({"range", "extra reach"})
public final class ReachHack extends Hack
{
	private final SliderSetting entityRange = new SliderSetting("Entity range",
		"Maximum range for selecting and attacking entities.", 6, 1, 15,
		0.05, ValueDisplay.DECIMAL).aliases("Range");
	private final SliderSetting blockRange = new SliderSetting("Block range",
		"Maximum range for selecting and interacting with blocks.", 5, 1, 15,
		0.05, ValueDisplay.DECIMAL);
	private final CheckboxSetting extraMode = new CheckboxSetting("Extra reach",
		"Add the extra distances to the game's current reach instead of using the absolute ranges.", false);
	private final SliderSetting extraEntityRange = new SliderSetting(
		"Extra entity reach", "Distance to add to the current entity reach.",
		0, 0, 15, 0.05, ValueDisplay.DECIMAL);
	private final SliderSetting extraBlockRange = new SliderSetting(
		"Extra block reach", "Distance to add to the current block reach.",
		0, 0, 15, 0.05, ValueDisplay.DECIMAL);

	public ReachHack()
	{
		super("Reach");
		setCategory(Category.OTHER);
		addSetting(entityRange);
		addSetting(blockRange);
		addSetting(extraMode);
		addSetting(extraEntityRange);
		addSetting(extraBlockRange);
	}

	public double changeEntityRange(double original)
	{
		if(!isEnabled())
			return original;
		return extraMode.isChecked() ? original + extraEntityRange.getValue()
			: entityRange.getValue();
	}

	public double changeBlockRange(double original)
	{
		if(!isEnabled())
			return original;
		return extraMode.isChecked() ? original + extraBlockRange.getValue()
			: blockRange.getValue();
	}

	public float getReachDistance()
	{
		return Math.max(getEntityRange(), getBlockRange());
	}

	public float getEntityRange()
	{
		return MC.player == null ? 0 : (float)MC.player.entityInteractionRange();
	}

	public float getBlockRange()
	{
		return MC.player == null ? 0 : (float)MC.player.blockInteractionRange();
	}
}
