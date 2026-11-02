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

public final class MouseStrokesMod extends HUDMod
{
	private final HudAnimation left = new HudAnimation();
	private final HudAnimation right = new HudAnimation();

	public MouseStrokesMod()
	{
		super("MouseStrokes", "Mouse Strokes",
			"Shows the left and right mouse buttons.");
		setTranslationKey("mouse_strokes");
		setDefaultPosition(10, 510);
	}

	@Override
	protected void draw()
	{
		boolean leftDown = MC.options.keyAttack.isDown() && !isEditing();
		boolean rightDown = MC.options.keyUse.isDown() && !isEditing();
		left.setAnimation(leftDown ? 1F : 0F, 16);
		right.setAnimation(rightDown ? 1F : 0F, 16);

		button(0, left, "L");
		button(32, right, "R");
		setSize(60, 28);
	}

	private void button(float x, HudAnimation animation, String label)
	{
		drawBackground(x, 0, 28, 28);
		save();
		scale(x, 0, 28, 28, animation.getValue());
		drawRoundedRect(x, 0, 28, 28, 6,
			getFontColor((int)(120 * animation.getValue())));
		restore();
		drawCenteredText(label, x + 14, 9, 12, HudFonts.REGULAR);
	}
}
