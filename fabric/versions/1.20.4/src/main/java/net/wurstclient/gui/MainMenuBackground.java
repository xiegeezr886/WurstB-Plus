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
import net.minecraft.resources.ResourceLocation;
import net.wurstclient.hud2.nanovg.NanoVGUI;

/**
 * User-provided, preblurred wallpaper shared by menu screen backgrounds.
 *
 * <p>{@code alpha} lets in-game screens keep a hint of the world behind the
 * wallpaper while menu screens use the fully opaque title look.
 */
public final class MainMenuBackground
{
	public static final int MENU_ALPHA = 255;
	public static final int INGAME_ALPHA = 255;

	private static final ResourceLocation WALLPAPER = new ResourceLocation(
		"wurstpenguin", "backgrounds/menu.png");
	private static final float IMAGE_WIDTH = 1672;
	private static final float IMAGE_HEIGHT = 941;

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
		if(a == 0) return;
		float scale = Math.max(width / IMAGE_WIDTH, height / IMAGE_HEIGHT);
		float drawWidth = IMAGE_WIDTH * scale;
		float drawHeight = IMAGE_HEIGHT * scale;
		// Blur is baked into the asset; no per-frame blur or additional framebuffer.
		// Center-crop without stretching; the texture manager caches the image.
		NanoVGUI.pushScissor(0, 0, width, height);
		try
		{
			NanoVGUI.image(WALLPAPER, (width - drawWidth) / 2,
				(height - drawHeight) / 2, drawWidth, drawHeight, 0,
				a << 24 | 0xFFFFFF);
		}finally
		{
			NanoVGUI.popScissor();
		}
	}
}
