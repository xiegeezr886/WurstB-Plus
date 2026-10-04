/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

/**
 * How a background is transformed while it is drawn.
 */
public record BackgroundPose(float scale, float offsetX, float offsetY)
{
	public static final BackgroundPose IDENTITY =
		new BackgroundPose(1F, 0F, 0F);
}
