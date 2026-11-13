/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
package net.wurstclient.hud2.nanovg;

import java.util.List;

/**
 * Fonts bundled with the NanoVG HUD.
 *
 * <p>Ported from Soar Client v7.1.2
 * ({@code me.eldodebug.soar.management.nanovg.font.Fonts}).
 */
public final class HudFonts
{
	private static final String PATH = "assets/wurstpenguin/nanovg/fonts/";

	public static final HudFont REGULAR =
		new HudFont("regular", PATH + "MiSans-Regular.ttf");
	public static final HudFont MEDIUM =
		new HudFont("medium", PATH + "MiSans-Medium.ttf");
	public static final HudFont DEMIBOLD =
		new HudFont("demi-bold", PATH + "MiSans-Demibold.ttf");
	public static final HudFont ICON =
		new HudFont("icon", PATH + "Icon.ttf");
	public static final HudFont NAVIGATOR_ICONS_1 = new HudFont("navigator-icons-1",
		"assets/wurstpenguin/navigator/icons/Icon-1.ttf");
	public static final HudFont NAVIGATOR_ICONS_2 = new HudFont("navigator-icons-2",
		"assets/wurstpenguin/navigator/icons/Icon-3.ttf");

	private HudFonts() {}

	public static List<HudFont> all()
	{
		return List.of(REGULAR, MEDIUM, DEMIBOLD, ICON, NAVIGATOR_ICONS_1, NAVIGATOR_ICONS_2);
	}
}
