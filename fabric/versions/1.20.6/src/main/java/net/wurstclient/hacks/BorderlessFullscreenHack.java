/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
package net.wurstclient.hacks;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVidMode;

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

		long handle = MC.getWindow().getWindow();
		long monitor = GLFW.glfwGetPrimaryMonitor();
		GLFWVidMode mode = GLFW.glfwGetVideoMode(monitor);

		if(mode == null)
			return;

		prevX = MC.getWindow().getX();
		prevY = MC.getWindow().getY();
		prevWidth = MC.getWindow().getWidth();
		prevHeight = MC.getWindow().getHeight();

		int[] monitorX = new int[1];
		int[] monitorY = new int[1];
		GLFW.glfwGetMonitorPos(monitor, monitorX, monitorY);

		GLFW.glfwSetWindowAttrib(handle, GLFW.GLFW_DECORATED, GLFW.GLFW_FALSE);
		GLFW.glfwSetWindowMonitor(handle, 0L, monitorX[0], monitorY[0],
			mode.width(), mode.height(), GLFW.GLFW_DONT_CARE);

		applied = true;
	}

	private void restoreWindow()
	{
		if(!applied || MC.getWindow() == null)
			return;

		long handle = MC.getWindow().getWindow();

		GLFW.glfwSetWindowAttrib(handle, GLFW.GLFW_DECORATED, GLFW.GLFW_TRUE);
		GLFW.glfwSetWindowMonitor(handle, 0L, prevX, prevY, prevWidth,
			prevHeight, GLFW.GLFW_DONT_CARE);

		applied = false;
	}
}
