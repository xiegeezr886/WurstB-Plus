/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
package net.wurstclient.hud2;

import net.wurstclient.hud2.nanovg.HudFonts;

/**
 * Single-line text HUD module.
 *
 * <p>Ported from Soar Client v7.1.2
 * ({@code me.eldodebug.soar.management.mods.SimpleHUDMod}).
 */
public abstract class SimpleHUDMod extends HUDMod
{
	protected SimpleHUDMod(String id, String name, String description)
	{
		super(id, name, description);
	}

	@Override
	protected void draw()
	{
		String text = getText();
		if(text == null)
			return;

		String icon = getIcon();
		boolean hasIcon = icon != null && !icon.isEmpty();
		float iconWidth =
			hasIcon ? getTextWidth(icon, 9.5F, HudFonts.ICON) + 4 : 0;

		float backgroundWidth =
			getTextWidth(text, 9, HudFonts.REGULAR) + 10 + iconWidth;
		drawBackground(backgroundWidth, 18);
		drawText(text, 5.5F + iconWidth, 5.5F, 9, HudFonts.REGULAR);

		if(hasIcon)
			drawText(icon, 5.5F, 4F, 10.4F, HudFonts.ICON);

		setSize(Math.round(backgroundWidth), 18);
	}

	public abstract String getText();

	public String getIcon()
	{
		return null;
	}
}
