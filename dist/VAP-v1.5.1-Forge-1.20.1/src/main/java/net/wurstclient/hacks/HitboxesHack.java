/*
 * Copyright (c) 2025 Penguin
 */
package net.wurstclient.hacks;

import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

@SearchTags({"hitbox", "HitBoxes", "expand", "Hitboxes"})
public final class HitboxesHack extends Hack
{
	private final SliderSetting expand = new SliderSetting("Expand",
		"How much to expand entity hitboxes.\n0.5 = slightly bigger, 2.0 = huge.",
		0.5, 0, 3.0, 0.05, ValueDisplay.DECIMAL);

	public HitboxesHack()
	{
		super("Hitboxes");
		setCategory(Category.COMBAT);
		addSetting(expand);
	}

	@Override
	public String getRenderName()
	{
		return getName() + " [+" + expand.getValueString() + "]";
	}

	public float getExtraSize()
	{
		return isEnabled() ? expand.getValueF() : 0;
	}
}
