/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.perimeter;

/**
 * Read-only fluid lookup used by the liquid policies. Keeping it behind an
 * interface lets the policy decisions be unit tested without a world.
 */
public interface PerimeterFluidLookup
{
	boolean isFluid(int x, int y, int z);
	
	/**
	 * @return whether any of the six neighbours of the position holds a fluid,
	 *         which is what the avoid policy uses to leave fluid-adjacent
	 *         blocks alone.
	 */
	default boolean isFluidAdjacent(int x, int y, int z)
	{
		return isFluid(x + 1, y, z) || isFluid(x - 1, y, z)
			|| isFluid(x, y + 1, z) || isFluid(x, y - 1, z)
			|| isFluid(x, y, z + 1) || isFluid(x, y, z - 1);
	}
}
