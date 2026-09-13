/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.perimeter;

/**
 * Read-only block lookup used to notice which blocks the builder has already
 * removed.
 */
public interface PerimeterBlockView
{
	boolean isAir(int x, int y, int z);
}
