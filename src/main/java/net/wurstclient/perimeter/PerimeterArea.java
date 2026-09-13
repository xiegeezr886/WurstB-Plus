/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.perimeter;

import java.util.Locale;
import java.util.Objects;

/**
 * An inclusive rectangular excavation region.
 *
 * <p>
 * Both XZ corners and both Y limits are inclusive, as in perimeter digging
 * plans: the block at {@code (x0, minY, z0)} and the block at
 * {@code (x1, maxY, z1)} are part of the region. Corners may be supplied in any
 * order, the region is normalized internally.
 *
 * <p>
 * This class deliberately avoids Minecraft types so that the geometry can be
 * unit tested without a running client.
 */
public final class PerimeterArea implements PerimeterRegion
{
	private final int minX, minY, minZ;
	private final int maxX, maxY, maxZ;
	
	private PerimeterArea(int x1, int y1, int z1, int x2, int y2, int z2)
	{
		minX = Math.min(x1, x2);
		minY = Math.min(y1, y2);
		minZ = Math.min(z1, z2);
		
		maxX = Math.max(x1, x2);
		maxY = Math.max(y1, y2);
		maxZ = Math.max(z1, z2);
	}
	
	public static PerimeterArea of(int x1, int y1, int z1, int x2, int y2,
		int z2)
	{
		return new PerimeterArea(x1, y1, z1, x2, y2, z2);
	}
	
	/**
	 * Builds an area from two XZ corners and a Y range, which is how
	 * perimeter plans are usually written down.
	 */
	public static PerimeterArea fromXZ(int x0, int z0, int x1, int z1,
		int yMin, int yMax)
	{
		return new PerimeterArea(x0, yMin, z0, x1, yMax, z1);
	}
	
	public int minX()
	{
		return minX;
	}
	
	public int minY()
	{
		return minY;
	}
	
	public int minZ()
	{
		return minZ;
	}
	
	public int maxX()
	{
		return maxX;
	}
	
	public int maxY()
	{
		return maxY;
	}
	
	public int maxZ()
	{
		return maxZ;
	}
	
	public int sizeX()
	{
		return maxX - minX + 1;
	}
	
	public int sizeY()
	{
		return maxY - minY + 1;
	}
	
	public int sizeZ()
	{
		return maxZ - minZ + 1;
	}
	
	/**
	 * Number of blocks in the region. Returned as a long because a large
	 * perimeter overflows an int.
	 */
	public long volume()
	{
		return (long)sizeX() * (long)sizeY() * (long)sizeZ();
	}
	
	/**
	 * Number of XZ columns, i.e. the number of blocks in a single layer.
	 */
	public long columnCount()
	{
		return (long)sizeX() * (long)sizeZ();
	}
	
	/**
	 * Number of full layers, which is the same as {@link #sizeY()}.
	 */
	public int layerCount()
	{
		return sizeY();
	}
	
	public boolean contains(int x, int y, int z)
	{
		return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ
			&& z <= maxZ;
	}
	
	public boolean containsColumn(int x, int z)
	{
		return containsXZ(x, z);
	}
	
	@Override
	public boolean containsXZ(int x, int z)
	{
		return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
	}
	
	/**
	 * Columns are ordered by ascending Z, then ascending X, which matches
	 * {@link PerimeterColumnArea}.
	 */
	@Override
	public boolean columnAt(long index, int[] out)
	{
		long columns = columnCount();
		
		if(index < 0 || index >= columns)
			return false;
		
		out[0] = minX + (int)(index % sizeX());
		out[1] = minZ + (int)(index / sizeX());
		return true;
	}
	
	/**
	 * Returns the same XZ region with a different Y range.
	 */
	public PerimeterArea withYRange(int newMinY, int newMaxY)
	{
		return new PerimeterArea(minX, newMinY, minZ, maxX, newMaxY, maxZ);
	}
	
	public String describe()
	{
		return "X " + minX + ".." + maxX + ", Z " + minZ + ".." + maxZ
			+ ", Y " + minY + ".." + maxY + " (" + sizeX() + "x" + sizeY()
			+ "x" + sizeZ() + " = "
			+ String.format(Locale.ROOT, "%,d", volume()) + " blocks)";
	}
	
	@Override
	public int hashCode()
	{
		return Objects.hash(minX, minY, minZ, maxX, maxY, maxZ);
	}
	
	@Override
	public boolean equals(Object obj)
	{
		if(this == obj)
			return true;
		
		if(!(obj instanceof PerimeterArea))
			return false;
		
		PerimeterArea other = (PerimeterArea)obj;
		return minX == other.minX && minY == other.minY && minZ == other.minZ
			&& maxX == other.maxX && maxY == other.maxY
			&& maxZ == other.maxZ;
	}
	
	@Override
	public String toString()
	{
		return "PerimeterArea[" + describe() + "]";
	}
}
