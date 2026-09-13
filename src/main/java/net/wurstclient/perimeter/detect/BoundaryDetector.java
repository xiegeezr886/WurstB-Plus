/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.perimeter.detect;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.wurstclient.perimeter.config.PerimeterDetectedArea;
import net.wurstclient.perimeter.config.PerimeterScanline;

/**
 * Detects an irregular, enclosed XZ excavation area from a boundary block at a
 * single Y level.
 *
 * <p>
 * This is a direct port of the reference Perimeter Digger detector. Cardinal or
 * diagonally touching boundary blocks form one component; the player's interior
 * region must be enclosed by exactly one outer boundary component; nested rings
 * alternate by parity, so the outer boundary columns are excluded while an
 * inner ring's own columns stay mineable and the next interior is excluded
 * again.
 */
public final class BoundaryDetector
{
	private static final int[][] CARDINAL =
		{{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
	private static final int[][] EIGHT_WAY = {{1, 0}, {-1, 0}, {0, 1}, {0, -1},
		{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
	
	public PerimeterDetectedArea detect(PerimeterGrid grid,
		String boundaryBlockId, int boundaryY, int playerX, int playerZ)
	{
		if(grid == null)
			throw new IllegalStateException("No world is currently loaded.");
		
		if(boundaryBlockId == null || boundaryBlockId.isBlank())
			throw new IllegalStateException("No boundary block was given.");
		
		DetectionContext context =
			new DetectionContext(grid, boundaryBlockId, boundaryY);
		context.scanRenderedChunks(grid.renderDistanceChunks(), playerX, playerZ);
		return context.build(playerX, playerZ);
	}
	
	private static final class DetectionContext
	{
		private final PerimeterGrid grid;
		private final String boundaryBlockId;
		private final int boundaryY;
		private final LongOpenHashSet loadedChunks = new LongOpenHashSet();
		private final LongArrayList loadedChunkCoordinates = new LongArrayList();
		private final LongOpenHashSet boundaryCells = new LongOpenHashSet();
		private final Long2IntOpenHashMap regionsByCell = new Long2IntOpenHashMap();
		private final List<LongArrayList> regionCells = new ArrayList<>();
		private final List<Boolean> regionTouchesEdge = new ArrayList<>();
		private final Long2IntOpenHashMap boundariesByCell =
			new Long2IntOpenHashMap();
		private final List<LongArrayList> boundaryComponents = new ArrayList<>();
		private final List<IntOpenHashSet> boundaryRegions = new ArrayList<>();
		
		private DetectionContext(PerimeterGrid grid, String boundaryBlockId,
			int boundaryY)
		{
			this.grid = grid;
			this.boundaryBlockId = boundaryBlockId;
			this.boundaryY = boundaryY;
			regionsByCell.defaultReturnValue(-1);
			boundariesByCell.defaultReturnValue(-1);
		}
		
		private void scanRenderedChunks(int renderDistance, int playerX,
			int playerZ)
		{
			int playerChunkX = playerX >> 4;
			int playerChunkZ = playerZ >> 4;
			
			for(int chunkX = playerChunkX - renderDistance; chunkX <= playerChunkX
				+ renderDistance; chunkX++)
				for(int chunkZ = playerChunkZ - renderDistance; chunkZ <= playerChunkZ
					+ renderDistance; chunkZ++)
				{
					if(!grid.isChunkLoaded(chunkX, chunkZ))
						continue;
					
					loadedChunks.add(pack(chunkX, chunkZ));
					loadedChunkCoordinates.add(pack(chunkX, chunkZ));
					
					int startX = chunkX << 4;
					int startZ = chunkZ << 4;
					
					for(int localX = 0; localX < 16; localX++)
						for(int localZ = 0; localZ < 16; localZ++)
						{
							int x = startX + localX;
							int z = startZ + localZ;
							
							if(boundaryBlockId
								.equals(grid.blockIdAt(x, boundaryY, z)))
								boundaryCells.add(pack(x, z));
						}
				}
			
			if(!isLoaded(playerX, playerZ))
				throw new IllegalStateException(
					"The player's column is not inside a loaded render chunk.");
		}
		
		private PerimeterDetectedArea build(int playerX, int playerZ)
		{
			long playerCell = pack(playerX, playerZ);
			
			if(boundaryCells.contains(playerCell))
				throw new IllegalStateException(
					"The player must stand inside the outer boundary, not on it.");
			
			labelRegions();
			labelBoundaryComponents();
			
			int playerRegion = regionsByCell.get(playerCell);
			
			if(playerRegion < 0 || regionTouchesEdge.get(playerRegion))
				throw new IllegalStateException(
					"The player's region is not enclosed inside the loaded render chunks.");
			
			int outerBoundary = findOuterBoundary(playerRegion);
			int[] parity = traverseNestedRegions(playerRegion, outerBoundary);
			return createArea(parity, outerBoundary);
		}
		
		private void labelRegions()
		{
			for(int index = 0; index < loadedChunkCoordinates.size(); index++)
			{
				long chunk = loadedChunkCoordinates.getLong(index);
				int chunkX = unpackX(chunk);
				int chunkZ = unpackZ(chunk);
				int startX = chunkX << 4;
				int startZ = chunkZ << 4;
				
				for(int localX = 0; localX < 16; localX++)
					for(int localZ = 0; localZ < 16; localZ++)
					{
						long cell = pack(startX + localX, startZ + localZ);
						
						if(!boundaryCells.contains(cell)
							&& regionsByCell.get(cell) < 0)
							labelRegion(cell);
					}
			}
		}
		
		private void labelRegion(long seed)
		{
			int id = regionCells.size();
			LongArrayList cells = new LongArrayList();
			LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
			boolean touchesEdge = false;
			
			regionsByCell.put(seed, id);
			queue.enqueue(seed);
			
			while(!queue.isEmpty())
			{
				long cell = queue.dequeueLong();
				cells.add(cell);
				int x = unpackX(cell);
				int z = unpackZ(cell);
				
				for(int[] direction : CARDINAL)
				{
					int nextX = x + direction[0];
					int nextZ = z + direction[1];
					
					if(!isLoaded(nextX, nextZ))
					{
						touchesEdge = true;
						continue;
					}
					
					long next = pack(nextX, nextZ);
					
					if(boundaryCells.contains(next)
						|| regionsByCell.get(next) >= 0)
						continue;
					
					regionsByCell.put(next, id);
					queue.enqueue(next);
				}
			}
			
			regionCells.add(cells);
			regionTouchesEdge.add(touchesEdge);
		}
		
		private void labelBoundaryComponents()
		{
			for(long seed : boundaryCells)
			{
				if(boundariesByCell.get(seed) >= 0)
					continue;
				
				int id = boundaryComponents.size();
				LongArrayList cells = new LongArrayList();
				IntOpenHashSet adjacentRegions = new IntOpenHashSet();
				LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
				
				boundariesByCell.put(seed, id);
				queue.enqueue(seed);
				
				while(!queue.isEmpty())
				{
					long cell = queue.dequeueLong();
					cells.add(cell);
					int x = unpackX(cell);
					int z = unpackZ(cell);
					
					for(int[] direction : CARDINAL)
					{
						int region = regionsByCell
							.get(pack(x + direction[0], z + direction[1]));
						
						if(region >= 0)
							adjacentRegions.add(region);
					}
					
					for(int[] direction : EIGHT_WAY)
					{
						long next = pack(x + direction[0], z + direction[1]);
						
						if(boundaryCells.contains(next)
							&& boundariesByCell.get(next) < 0)
						{
							boundariesByCell.put(next, id);
							queue.enqueue(next);
						}
					}
				}
				
				boundaryComponents.add(cells);
				boundaryRegions.add(adjacentRegions);
			}
		}
		
		private int findOuterBoundary(int playerRegion)
		{
			int found = -1;
			
			for(int boundary = 0; boundary < boundaryRegions.size(); boundary++)
			{
				IntOpenHashSet adjacent = boundaryRegions.get(boundary);
				
				if(!adjacent.contains(playerRegion))
					continue;
				
				boolean touchesOutside = adjacent.intStream()
					.anyMatch(region -> regionTouchesEdge.get(region));
				
				if(!touchesOutside)
					continue;
				
				if(found >= 0)
					throw new IllegalStateException(
						"Multiple outer boundary components surround the player region.");
				
				found = boundary;
			}
			
			if(found < 0)
				throw new IllegalStateException(
					"No closed outer boundary connected by cardinal or diagonal blocks was found.");
			
			return found;
		}
		
		private int[] traverseNestedRegions(int playerRegion, int outerBoundary)
		{
			List<IntArrayList> regionBoundaries = new ArrayList<>();
			
			for(int region = 0; region < regionCells.size(); region++)
				regionBoundaries.add(new IntArrayList());
			
			for(int boundary = 0; boundary < boundaryRegions.size(); boundary++)
				for(int region : boundaryRegions.get(boundary))
					regionBoundaries.get(region).add(boundary);
			
			int[] parity = new int[regionCells.size()];
			Arrays.fill(parity, -1);
			parity[playerRegion] = 0;
			ArrayDeque<Integer> queue = new ArrayDeque<>();
			queue.add(playerRegion);
			
			while(!queue.isEmpty())
			{
				int region = queue.removeFirst();
				
				for(int boundary : regionBoundaries.get(region))
				{
					if(boundary == outerBoundary)
						continue;
					
					IntOpenHashSet adjacent = boundaryRegions.get(boundary);
					
					if(adjacent.size() < 2)
						throw new IllegalStateException(
							"An inner boundary component is not closed.");
					
					for(int nextRegion : adjacent)
					{
						if(nextRegion == region)
							continue;
						
						int nextParity = 1 - parity[region];
						
						if(parity[nextRegion] < 0)
						{
							parity[nextRegion] = nextParity;
							queue.add(nextRegion);
						}else if(parity[nextRegion] != nextParity)
							throw new IllegalStateException(
								"Boundary nesting is topologically ambiguous.");
					}
				}
			}
			
			return parity;
		}
		
		private PerimeterDetectedArea createArea(int[] parity, int outerBoundary)
		{
			Int2ObjectOpenHashMap<IntArrayList> xByZ =
				new Int2ObjectOpenHashMap<>();
			
			for(int region = 0; region < parity.length; region++)
			{
				if(parity[region] != 0)
					continue;
				
				for(int index = 0; index < regionCells.get(region)
					.size(); index++)
					addCell(xByZ, regionCells.get(region).getLong(index));
			}
			
			for(int boundary = 0; boundary < boundaryComponents
				.size(); boundary++)
			{
				if(boundary == outerBoundary || boundaryRegions.get(boundary)
					.intStream().noneMatch(region -> parity[region] >= 0))
					continue;
				
				for(int index = 0; index < boundaryComponents.get(boundary)
					.size(); index++)
					addCell(xByZ,
						boundaryComponents.get(boundary).getLong(index));
			}
			
			if(xByZ.isEmpty())
				throw new IllegalStateException(
					"The detected mining area is empty.");
			
			PerimeterDetectedArea area = new PerimeterDetectedArea();
			area.boundaryBlock = boundaryBlockId;
			area.boundaryY = boundaryY;
			area.minX = Integer.MAX_VALUE;
			area.maxX = Integer.MIN_VALUE;
			area.minZ = Integer.MAX_VALUE;
			area.maxZ = Integer.MIN_VALUE;
			
			int[] zs = xByZ.keySet().toIntArray();
			Arrays.sort(zs);
			
			for(int z : zs)
			{
				int[] xs = xByZ.get(z).toIntArray();
				Arrays.sort(xs);
				int start = xs[0];
				int previous = xs[0];
				
				for(int index = 1; index <= xs.length; index++)
				{
					if(index < xs.length && xs[index] <= previous + 1)
					{
						previous = xs[index];
						continue;
					}
					
					area.scanlines.add(
						new PerimeterScanline(z, start, previous));
					area.columnCount += (long)previous - start + 1L;
					area.minX = Math.min(area.minX, start);
					area.maxX = Math.max(area.maxX, previous);
					area.minZ = Math.min(area.minZ, z);
					area.maxZ = Math.max(area.maxZ, z);
					
					if(index < xs.length)
					{
						start = xs[index];
						previous = xs[index];
					}
				}
			}
			
			return area;
		}
		
		private void addCell(Int2ObjectOpenHashMap<IntArrayList> xByZ, long cell)
		{
			xByZ.computeIfAbsent(unpackZ(cell), ignored -> new IntArrayList())
				.add(unpackX(cell));
		}
		
		private boolean isLoaded(int x, int z)
		{
			return loadedChunks.contains(pack(x >> 4, z >> 4));
		}
	}
	
	private static long pack(int x, int z)
	{
		return ((long)x << 32) | (z & 0xFFFFFFFFL);
	}
	
	private static int unpackX(long packed)
	{
		return (int)(packed >> 32);
	}
	
	private static int unpackZ(long packed)
	{
		return (int)packed;
	}
}
