/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

@SearchTags({"high jump"})
public final class HighJumpHack extends Hack
{
	private final EnumSetting<Mode> mode = new EnumSetting<>("Mode",
		"Height adds jump motion; Multiplier scales the block jump factor.",
		Mode.values(), Mode.HEIGHT);
	private final SliderSetting multiplier = new SliderSetting("Jump multiplier",
		"Multiplier applied before jump boost effects.", 1, 0, 10, 0.1,
		ValueDisplay.DECIMAL);

	private final SliderSetting height = new SliderSetting("Height",
		"Jump height in blocks.\n"
			+ "This gets very inaccurate at higher values.",
		6, 1, 100, 1, ValueDisplay.INTEGER);
	
	public HighJumpHack()
	{
		super("HighJump");
		
		setCategory(Category.MOVEMENT);
		addSetting(height);
		addSetting(mode);
		addSetting(multiplier);
	}
	
	public float getAdditionalJumpMotion()
	{
		return isEnabled() && mode.getSelected() == Mode.HEIGHT
			? height.getValueF() * 0.1F : 0;
	}

	public float getJumpMultiplier()
	{
		return isEnabled() && mode.getSelected() == Mode.MULTIPLIER
			? multiplier.getValueF() : 1;
	}

	private enum Mode
	{
		HEIGHT, MULTIPLIER
	}
}
