/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.perimeter;

import java.util.Objects;

/**
 * Iterates over every block of a {@link PerimeterArea} without allocating a
 * list of positions, so that very large perimeters can be scanned a slice at a
 * time.
 *
 * <p>
 * The order is top layer first (descending Y), then ascending Z, then ascending
 * X, which matches how perimeter digging removes blocks: the open top layer is
 * mined before the layer below it.
 *
 * <p>
 * Usage:
 *
 * <pre>
 * PerimeterCursor cursor = new PerimeterCursor(area);
 * while(cursor.hasNext())
 * {
 * 	visit(cursor.x(), cursor.y(), cursor.z());
 * 	cursor.advance();
 * }
 * </pre>
 */
public final class PerimeterCursor
{
	private final PerimeterArea area;
	private final long total;
	private final long columnCount;
	private final int sizeX;
	
	private long index;
	
	public PerimeterCursor(PerimeterArea area)
	{
		this.area = Objects.requireNonNull(area);
		total = area.volume();
		columnCount = area.columnCount();
		sizeX = area.sizeX();
	}
	
	public PerimeterArea area()
	{
		return area;
	}
	
	/**
	 * Total number of positions this cursor will visit.
	 */
	public long total()
	{
		return total;
	}
	
	/**
	 * Number of positions already passed, from 0 to {@link #total()}.
	 */
	public long index()
	{
		return index;
	}
	
	public boolean hasNext()
	{
		return index < total;
	}
	
	public void reset()
	{
		index = 0;
	}
	
	public void advance()
	{
		advance(1);
	}
	
	public void advance(long steps)
	{
		if(steps < 0)
			throw new IllegalArgumentException("steps must not be negative");
		
		index = Math.min(total, index + steps);
	}
	
	public int x()
	{
		checkPosition();
		return area.minX() + (int)(cell() % sizeX);
	}
	
	public int y()
	{
		checkPosition();
		return area.maxY() - (int)(index / columnCount);
	}
	
	public int z()
	{
		checkPosition();
		return area.minZ() + (int)(cell() / sizeX);
	}
	
	/**
	 * Fraction of the region already passed, from 0 to 1.
	 */
	public double progress()
	{
		return total == 0 ? 1 : (double)index / (double)total;
	}
	
	private long cell()
	{
		return index % columnCount;
	}
	
	private void checkPosition()
	{
		if(!hasNext())
			throw new IllegalStateException(
				"cursor is exhausted (" + index + "/" + total + ")");
	}
}
