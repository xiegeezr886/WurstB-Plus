/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.perimeter.detect;

/**
 * Everything boundary detection needs from the world, so the algorithm itself
 * stays independent of Minecraft and can be unit tested against a synthetic
 * grid.
 */
public interface PerimeterGrid
{
	/**
	 * @return the block id at the position, for example
	 *         {@code minecraft:red_wool}, or null when the position is not
	 *         loaded.
	 */
	String blockIdAt(int x, int y, int z);
	
	/**
	 * @return the client's render distance in chunks, which defines how far
	 *         detection scans.
	 */
	int renderDistanceChunks();
	
	/**
	 * @return whether the chunk is currently loaded by the client.
	 */
	boolean isChunkLoaded(int chunkX, int chunkZ);
}
