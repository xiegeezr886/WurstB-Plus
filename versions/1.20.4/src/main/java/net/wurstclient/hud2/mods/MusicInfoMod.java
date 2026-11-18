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
        var music = net.wurstclient.neoraven.music.MusicManager.get();
        var song = music.current();
        if(song == null && !isEditing()) return;
        drawBackground(190, 38);
        save(); scissor(5,2,180,34);
        drawText(song == null ? "音乐播放器" : song.name,6,5,10,HudFonts.DEMIBOLD);
        drawText(net.wurstclient.neoraven.music.MusicView.lyric(),6,21,8,HudFonts.REGULAR,getFontColor(200));
        restore();
        setSize(190,38);
	}
}
