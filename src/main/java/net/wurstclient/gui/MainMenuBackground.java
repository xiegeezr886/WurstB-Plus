/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
package net.wurstclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.wurstclient.hud2.nanovg.NanoVGUI;

/**
 * The title-screen gradient, shared by every screen background.
 *
 * <p>{@code alpha} lets in-game screens keep a hint of the world behind the
 * same colours while menu screens use the fully opaque title look.
 */
public final class MainMenuBackground
{
	public static final int MENU_ALPHA = 255;
	public static final int INGAME_ALPHA = 255;

	private static final int TOP = 0xFF02090A;
	private static final int BOTTOM = 0xFF006366;

	private MainMenuBackground() {}

	public static void render(GuiGraphics graphics, Screen screen)
	{
		Minecraft mc = Minecraft.getInstance();
		int alpha = mc != null && mc.level != null ? INGAME_ALPHA : MENU_ALPHA;
		NanoVGUI.pass(graphics,
			() -> render(screen.width, screen.height, alpha));
	}

	public static void render(int width, int height, int alpha)
	{
		if(width <= 0 || height <= 0)
			return;

		int a = Math.max(0, Math.min(255, alpha));
		NanoVGUI.fillGradient(0, 0, width, height, withAlpha(TOP, a),
			withAlpha(BOTTOM, a));
		NanoVGUI.fillGradient(0, 0, width, height,
			withAlpha(0x000000, a * 0xD9 / 255), 0x00000000);
		NanoVGUI.fill(0, 0, width, height,
			withAlpha(0x000000, a * 0x18 / 255));
	}

	private static int withAlpha(int color, int alpha)
	{
		return Math.max(0, Math.min(255, alpha)) << 24 | color & 0xFFFFFF;
	}
}
