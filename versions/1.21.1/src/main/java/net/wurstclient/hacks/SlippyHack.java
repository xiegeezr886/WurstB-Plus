/*
 * Adapted from Meteor Client. Copyright (c) Meteor Development.
 * SPDX-License-Identifier: GPL-3.0
 */
package net.wurstclient.hacks;

import net.minecraft.world.level.block.Block;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.BlockListSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

@SearchTags({"slippy", "friction", "slipperiness", "方块摩擦"})
public final class SlippyHack extends Hack
{
	private final SliderSetting friction = new SliderSetting("Friction",
		"方块的基础摩擦系数；越接近 1，滑行越明显。", 1, 0.01, 1.1, 0.01,
		ValueDisplay.DECIMAL);
	private final EnumSetting<ListMode> listMode = new EnumSetting<>("List mode",
		"选择生效名单或排除名单。", ListMode.values(), ListMode.BLACKLIST);
	private final BlockListSetting ignoredBlocks = new BlockListSetting(
		"Ignored blocks", "这些方块保留原来的摩擦系数。");
	private final BlockListSetting allowedBlocks = new BlockListSetting(
		"Allowed blocks", "仅修改这些方块的摩擦系数。");

	public SlippyHack()
	{
		super("Slippy");
		setCategory(Category.MOVEMENT);
		addSetting(friction);
		addSetting(listMode);
		addSetting(ignoredBlocks);
		addSetting(allowedBlocks);
	}

	public float getFriction(Block block, float original)
	{
		if(!isEnabled())
			return original;
		boolean selected = listMode.getSelected() == ListMode.WHITELIST
			? allowedBlocks.contains(block) : !ignoredBlocks.contains(block);
		return selected ? friction.getValueF() : original;
	}

	private enum ListMode
	{
		WHITELIST, BLACKLIST
	}
}
