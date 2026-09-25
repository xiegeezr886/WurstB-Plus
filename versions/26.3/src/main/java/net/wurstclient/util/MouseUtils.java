/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

import org.lwjgl.sdl.SDLMouse;
import org.lwjgl.system.MemoryStack;

/**
 * Whether a mouse button is physically held down, bypassing any simulated state.
 *
 * <p>Minecraft 26.3 takes mouse input from SDL rather than GLFW, and SDL has no
 * per-button query: {@code SDL_GetMouseState} reports every button as one bit of
 * a mask. SDL numbers buttons 1..8 (left, middle, right, then 4..8), so button
 * {@code n} is bit {@code n-1}. This replaces the old
 * {@code GLFW.glfwGetMouseButton(windowHandle, button) == 1} calls.
 */
public enum MouseUtils
{
	;
	
	private static final int MAX_BUTTON = 8;
	
	public static boolean isButtonDown(int button)
	{
		if(button < 1 || button > MAX_BUTTON)
			return false;
		
		// Both out-parameters of SDL_GetMouseState are optional in C, but
		// LWJGL's typed overload rejects null (Checks.checkSafe), so scratch
		// buffers come from the stack instead of the heap - this is called from
		// tick listeners, so a per-call allocation would be wasteful.
		try(MemoryStack stack = MemoryStack.stackPush())
		{
			int mask = SDLMouse.SDL_GetMouseState(stack.callocFloat(1),
				stack.callocFloat(1));
			return (mask & (1 << (button - 1))) != 0;
		}
	}
}
