/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.perimeter;

/**
 * A minable set of XZ columns plus an inclusive Y range.
 *
 * <p>
 * Implementations are either a plain rectangle ({@link PerimeterArea}) or an
 * irregular set of scanlines produced by boundary detection
 * ({@link PerimeterColumnArea}). Everything here is client-independent so the
 * geometry can be unit tested.
 */
public interface PerimeterRegion
{
	int minX();
	
	int maxX();
	
	int minY();
	
	int maxY();
	
	int minZ();
	
	int maxZ();
	
	/**
	 * @return how many XZ columns belong to the region.
	 */
	long columnCount();
	
	/**
	 * @return whether the given XZ column is part of the region, regardless of
	 *         Y.
	 */
	boolean containsXZ(int x, int z);
	
	/**
	 * Writes the column with the given index into {@code out} as
	 * {@code out[0] = x}, {@code out[1] = z}.
	 *
	 * <p>
	 * Column order is stable for a given region: ascending Z, then ascending X.
	 * This lets the digger walk millions of columns without materialising them.
	 *
	 * @return false when the index is outside {@code [0, columnCount())}.
	 */
	boolean columnAt(long index, int[] out);
	
	/**
	 * @return the column count multiplied by the number of Y layers, saturating
	 *         at {@link Long#MAX_VALUE}.
	 */
	default long estimatedBlockCount()
	{
		try
		{
			return Math.multiplyExact(columnCount(), (long)sizeY());
		}catch(ArithmeticException e)
		{
			return Long.MAX_VALUE;
		}
	}
	
	default int sizeZ()
	{
		return maxZ() - minZ() + 1;
	}
	
	default int sizeY()
	{
		return maxY() - minY() + 1;
	}
	
	default boolean contains(int x, int y, int z)
	{
		return y >= minY() && y <= maxY() && containsXZ(x, z);
	}
}
