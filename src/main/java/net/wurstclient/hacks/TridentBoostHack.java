/* Adapted from Meteor Client. Copyright (c) Meteor Development. SPDX-License-Identifier: GPL-3.0 */
package net.wurstclient.hacks;

import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

@SearchTags({"trident boost", "riptide", "激流", "三叉戟"})
public final class TridentBoostHack extends Hack
{
	private final SliderSetting boost = new SliderSetting("Boost",
		"激流推进速度的倍率。", 2, 0.1, 10, 0.1, ValueDisplay.DECIMAL);
	private final CheckboxSetting outOfWater = new CheckboxSetting("Out of water",
		"允许在水和雨之外使用激流。", true);

	public TridentBoostHack()
	{
		super("TridentBoost");
		setCategory(Category.MOVEMENT);
		addSetting(boost);
		addSetting(outOfWater);
	}

	public double getMultiplier()
	{
		return isEnabled() ? boost.getValue() : 1;
	}

	public boolean allowOutOfWater()
	{
		return isEnabled() && outOfWater.isChecked();
	}
}
