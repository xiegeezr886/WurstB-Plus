/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.perimeter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;

import net.wurstclient.perimeter.config.PerimeterDetectedArea;
import net.wurstclient.perimeter.config.PerimeterScanline;

/**
 * An irregular region produced by boundary detection: one or more inclusive X
 * runs per Z row, merged and indexed so that membership tests and ordered
 * column iteration stay cheap for very large perimeters.
 */
public final class PerimeterColumnArea implements PerimeterRegion
{
	private final int minX;
	private final int maxX;
	private final int minY;
	private final int maxY;
	private final int minZ;
	private final int maxZ;
	private final long columnCount;
	
	/** Ascending Z values, one per row. */
	private final int[] zs;
	
	/** Per row, the flattened and merged {@code minX, maxX} pairs. */
	private final int[][] runs;
	
	/** Prefix sums of per-row column counts, length {@code zs.length + 1}. */
	private final long[] rowOffsets;
	
	public PerimeterColumnArea(PerimeterDetectedArea config, int minY,
		int maxY)
	{
		if(config == null || config.scanlines == null
			|| config.scanlines.isEmpty())
			throw new IllegalArgumentException("The mining area is empty.");
		
		if(minY > maxY)
			throw new IllegalArgumentException("The mining Y range is inverted.");
		
		TreeMap<Integer, List<int[]>> grouped = new TreeMap<>();
		
		for(PerimeterScanline scanline : config.scanlines)
		{
			if(scanline == null || scanline.minX > scanline.maxX)
				throw new IllegalArgumentException(
					"The mining area contains an invalid scanline.");
			
			grouped.computeIfAbsent(scanline.z, ignored -> new ArrayList<>())
				.add(new int[]{scanline.minX, scanline.maxX});
		}
		
		zs = new int[grouped.size()];
		runs = new int[grouped.size()][];
		rowOffsets = new long[grouped.size() + 1];
		
		int actualMinX = Integer.MAX_VALUE;
		int actualMaxX = Integer.MIN_VALUE;
		long actualColumnCount = 0L;
		int row = 0;
		
		for(var entry : grouped.entrySet())
		{
			List<int[]> intervals = entry.getValue();
			intervals.sort((first, second) -> Integer.compare(first[0],
				second[0]));
			
			List<Integer> merged = new ArrayList<>();
			int start = intervals.get(0)[0];
			int end = intervals.get(0)[1];
			
			for(int index = 1; index < intervals.size(); index++)
			{
				int[] next = intervals.get(index);
				
				if((long)next[0] <= (long)end + 1L)
					end = Math.max(end, next[1]);
				else
				{
					merged.add(start);
					merged.add(end);
					actualColumnCount =
						Math.addExact(actualColumnCount, (long)end - start + 1L);
					actualMinX = Math.min(actualMinX, start);
					actualMaxX = Math.max(actualMaxX, end);
					start = next[0];
					end = next[1];
				}
			}
			
			merged.add(start);
			merged.add(end);
			actualColumnCount =
				Math.addExact(actualColumnCount, (long)end - start + 1L);
			actualMinX = Math.min(actualMinX, start);
			actualMaxX = Math.max(actualMaxX, end);
			
			int[] values = new int[merged.size()];
			for(int index = 0; index < merged.size(); index++)
				values[index] = merged.get(index);
			
			zs[row] = entry.getKey();
			runs[row] = values;
			rowOffsets[row] = actualColumnCount - countRow(values);
			row++;
		}
		
		rowOffsets[grouped.size()] = actualColumnCount;
		
		this.minX = actualMinX;
		this.maxX = actualMaxX;
		this.minY = minY;
		this.maxY = maxY;
		this.minZ = grouped.firstKey();
		this.maxZ = grouped.lastKey();
		this.columnCount = actualColumnCount;
	}
	
	private static long countRow(int[] values)
	{
		long count = 0L;
		
		for(int index = 0; index < values.length; index += 2)
			count += (long)values[index + 1] - values[index] + 1L;
		
		return count;
	}
	
	@Override
	public int minX()
	{
		return minX;
	}
	
	@Override
	public int maxX()
	{
		return maxX;
	}
	
	@Override
	public int minY()
	{
		return minY;
	}
	
	@Override
	public int maxY()
	{
		return maxY;
	}
	
	@Override
	public int minZ()
	{
		return minZ;
	}
	
	@Override
	public int maxZ()
	{
		return maxZ;
	}
	
	@Override
	public long columnCount()
	{
		return columnCount;
	}
	
	@Override
	public boolean containsXZ(int x, int z)
	{
		int row = rowOf(z);
		
		if(row < 0)
			return false;
		
		return contains(runs[row], x);
	}
	
	private static boolean contains(int[] rowRuns, int x)
	{
		int low = 0;
		int high = rowRuns.length / 2 - 1;
		
		while(low <= high)
		{
			int middle = (low + high) >>> 1;
			int start = rowRuns[middle * 2];
			int end = rowRuns[middle * 2 + 1];
			
			if(x < start)
				high = middle - 1;
			else if(x > end)
				low = middle + 1;
			else
				return true;
		}
		
		return false;
	}
	
	@Override
	public boolean columnAt(long index, int[] out)
	{
		if(index < 0 || index >= columnCount)
			return false;
		
		int row = rowOfColumn(index);
		long local = index - rowOffsets[row];
		int[] rowRuns = runs[row];
		
		for(int position = 0; position < rowRuns.length; position += 2)
		{
			long length = (long)rowRuns[position + 1] - rowRuns[position] + 1L;
			
			if(local < length)
			{
				out[0] = rowRuns[position] + (int)local;
				out[1] = zs[row];
				return true;
			}
			
			local -= length;
		}
		
		return false;
	}
	
	/**
	 * @return the number of X runs across all rows.
	 */
	public int runCount()
	{
		int count = 0;
		
		for(int[] rowRuns : runs)
			count += rowRuns.length / 2;
		
		return count;
	}
	
	public String describe()
	{
		return "X " + minX + ".." + maxX + ", Z " + minZ + ".." + maxZ
			+ ", Y " + minY + ".." + maxY + " (" + columnCount + " columns, "
			+ runCount() + " runs, "
			+ String.format(java.util.Locale.ROOT, "%,d", estimatedBlockCount())
			+ " blocks)";
	}
	
	/**
	 * @return the Z value of each stored row, ascending.
	 */
	public int[] rows()
	{
		return Arrays.copyOf(zs, zs.length);
	}
	
	private int rowOf(int z)
	{
		int index = Arrays.binarySearch(zs, z);
		return index < 0 ? -1 : index;
	}
	
	private int rowOfColumn(long columnIndex)
	{
		int low = 0;
		int high = zs.length - 1;
		
		while(low < high)
		{
			int middle = (low + high + 1) >>> 1;
			
			if(rowOffsets[middle] <= columnIndex)
				low = middle;
			else
				high = middle - 1;
		}
		
		return low;
	}
}
