/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
package net.wurstclient.hud2.mods;

import net.wurstclient.hud2.HUDMod;
import net.wurstclient.hud2.HudAnimation;
import net.wurstclient.hud2.nanovg.HudFonts;
import net.wurstclient.settings.CheckboxSetting;

public final class KeystrokesMod extends HUDMod
{
	private final CheckboxSetting spaceSetting =
		new CheckboxSetting("Space", "Shows the space bar.", true);
	private final CheckboxSetting unmarkedSetting =
		new CheckboxSetting("Unmarked", "Hides the key labels.", false);

	private final HudAnimation[] animations = new HudAnimation[5];

	public KeystrokesMod()
	{
		super("Keystrokes", "Keystrokes",
			"Shows the movement keys and mouse buttons.");
		setTranslationKey("keystrokes");
		setDefaultPosition(10, 420);
		addSetting(spaceSetting);
		addSetting(unmarkedSetting);
		for(int i = 0; i < animations.length; i++)
			animations[i] = new HudAnimation();
	}

	@Override
	protected void draw()
	{
		boolean open = MC.screen != null;
		animations[0].setAnimation(
			!open && MC.options.keyUp.isDown() ? 1F : 0F, 16);
		animations[1].setAnimation(
			!open && MC.options.keyLeft.isDown() ? 1F : 0F, 16);
		animations[2].setAnimation(
			!open && MC.options.keyDown.isDown() ? 1F : 0F, 16);
		animations[3].setAnimation(
			!open && MC.options.keyRight.isDown() ? 1F : 0F, 16);
		animations[4].setAnimation(
			!open && MC.options.keyJump.isDown() ? 1F : 0F, 16);

		key(32, 0, animations[0], "W");
		key(0, 32, animations[1], "A");
		key(32, 32, animations[2], "S");
		key(64, 32, animations[3], "D");

		if(spaceSetting.isChecked())
		{
			drawBackground(0, 64, 92, 22);
			save();
			scale(0, 64, 92, 22, animations[4].getValue());
			drawRoundedRect(0, 64, 92, 22, 6,
				getFontColor((int)(120 * animations[4].getValue())));
			restore();
			if(!unmarkedSetting.isChecked())
				drawCenteredText("SPACE", 46, 68, 10, HudFonts.REGULAR);
		}

		setSize(92, spaceSetting.isChecked() ? 86 : 60);
	}

	private void key(float x, float y, HudAnimation animation, String name)
	{
		drawBackground(x, y, 28, 28);
		save();
		scale(x, y, 28, 28, animation.getValue());
		drawRoundedRect(x, y, 28, 28, 6,
			getFontColor((int)(120 * animation.getValue())));
		restore();
		if(!unmarkedSetting.isChecked())
			drawCenteredText(name, x + 14, y + 9, 12, HudFonts.REGULAR);
	}
}
