/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
package net.wurstclient.hacks;

import org.lwjgl.sdl.SDL_Rect;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.system.MemoryStack;

import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.hack.Hack;

@SearchTags({"borderless", "fullscreen", "borderless fullscreen",
	"windowed fullscreen", "windowed"})
public final class BorderlessFullscreenHack extends Hack
{
	private boolean applied;
	private int prevX;
	private int prevY;
	private int prevWidth;
	private int prevHeight;

	public BorderlessFullscreenHack()
	{
		super("BorderlessFullscreen");
		setCategory(Category.OTHER);
	}

	@Override
	protected void onEnable()
	{
		MC.execute(this::applyBorderless);
	}

	@Override
	protected void onDisable()
	{
		MC.execute(this::restoreWindow);
	}

	private void applyBorderless()
	{
		if(applied || MC.getWindow() == null)
			return;

		long handle = MC.getWindow().handle();
		int display = SDLVideo.SDL_GetPrimaryDisplay();
		int x;
		int y;
		int width;
		int height;

		try(MemoryStack stack = MemoryStack.stackPush())
		{
			SDL_Rect bounds = SDL_Rect.calloc(stack);
			if(!SDLVideo.SDL_GetDisplayBounds(display, bounds))
				return;

			x = bounds.x();
			y = bounds.y();
			width = bounds.w();
			height = bounds.h();
		}

		prevX = MC.getWindow().getX();
		prevY = MC.getWindow().getY();
		prevWidth = MC.getWindow().getWidth();
		prevHeight = MC.getWindow().getHeight();

		SDLVideo.SDL_SetWindowBordered(handle, false);
		SDLVideo.SDL_SetWindowPosition(handle, x, y);
		SDLVideo.SDL_SetWindowSize(handle, width, height);

		applied = true;
	}

	private void restoreWindow()
	{
		if(!applied || MC.getWindow() == null)
			return;

		long handle = MC.getWindow().handle();

		SDLVideo.SDL_SetWindowBordered(handle, true);
		SDLVideo.SDL_SetWindowPosition(handle, prevX, prevY);
		SDLVideo.SDL_SetWindowSize(handle, prevWidth, prevHeight);

		applied = false;
	}
}
