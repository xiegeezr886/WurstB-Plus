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
import net.wurstclient.hud2.nanovg.HudFonts;

public final class MusicInfoMod extends HUDMod
{
	public MusicInfoMod()
	{
		super("MusicInfo", "Music Info",
			"Shows the currently playing music.");
		setTranslationKey("music_info");
		setDefaultPosition(350, 410);
	}

	@Override
	protected void draw()
	{
		drawBackground(160, 34);
		drawText("Music", 6, 4, 9, HudFonts.DEMIBOLD);
		drawText("Nothing is playing", 6, 17, 9, HudFonts.REGULAR,
			getFontColor(200));
		setSize(160, 34);
	}
}
