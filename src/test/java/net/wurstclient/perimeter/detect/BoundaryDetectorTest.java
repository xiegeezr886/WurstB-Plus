/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.perimeter.detect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import net.wurstclient.perimeter.config.PerimeterDetectedArea;

final class BoundaryDetectorTest
{
	private static final String BOUNDARY = "minecraft:red_wool";
	private static final int BOUNDARY_Y = 80;
	
	@Test
	void detectsTheInteriorOfASquareRing()
	{
		Set<Long> ring = new HashSet<>();
		
		for(int x = 0; x <= 4; x++)
			for(int z = 0; z <= 4; z++)
				if(x == 0 || x == 4 || z == 0 || z == 4)
					ring.add(pack(x, z));
		
		PerimeterDetectedArea area =
			detect(ring, 2, 2);
		
		assertEquals(9L, area.columnCount);
		assertEquals(1, area.minX);
		assertEquals(3, area.maxX);
		assertEquals(1, area.minZ);
		assertEquals(3, area.maxZ);
		assertEquals(BOUNDARY, area.boundaryBlock);
		assertEquals(BOUNDARY_Y, area.boundaryY);
		assertEquals(3, area.scanlines.size());
		
		for(int z = 1; z <= 3; z++)
		{
			assertEquals(1, area.scanlines.get(z - 1).minX);
			assertEquals(3, area.scanlines.get(z - 1).maxX);
		}
	}
	
	@Test
	void excludesTheOuterBoundaryColumns()
	{
		Set<Long> ring = new HashSet<>();
		
		for(int x = 0; x <= 4; x++)
			for(int z = 0; z <= 4; z++)
				if(x == 0 || x == 4 || z == 0 || z == 4)
					ring.add(pack(x, z));
		
		PerimeterDetectedArea area = detect(ring, 2, 2);
		PerimeterGrid grid = new FakeGrid(ring, 1);
		
		assertFalse(contains(area, grid, 0, 2));
		assertFalse(contains(area, grid, 4, 2));
		assertFalse(contains(area, grid, 2, 0));
		assertFalse(contains(area, grid, 2, 4));
		assertTrue(contains(area, grid, 1, 1));
	}
	
	@Test
	void innerRingsKeepTheirColumnsAndExcludeTheNextInterior()
	{
		Set<Long> boundary = new HashSet<>();
		
		// 7x7 outer ring
		for(int x = 0; x <= 6; x++)
			for(int z = 0; z <= 6; z++)
				if(x == 0 || x == 6 || z == 0 || z == 6)
					boundary.add(pack(x, z));
		
		// inner ring from (2,2) to (4,4)
		for(int x = 2; x <= 4; x++)
			for(int z = 2; z <= 4; z++)
				if(x == 2 || x == 4 || z == 2 || z == 4)
					boundary.add(pack(x, z));
		
		// the player stands in the region between the two rings
		PerimeterDetectedArea area = detect(boundary, 1, 1);
		
		// ring cells stay mineable, the innermost interior does not
		assertEquals(24L, area.columnCount);
		assertTrue(contains(area, new FakeGrid(boundary, 1), 2, 3));
		assertFalse(contains(area, new FakeGrid(boundary, 1), 3, 3));
		assertTrue(contains(area, new FakeGrid(boundary, 1), 1, 3));
		assertFalse(contains(area, new FakeGrid(boundary, 1), 0, 3));
	}
	
	@Test
	void diagonallyTouchingBoundaryBlocksFormOneRing()
	{
		Set<Long> diamond = new HashSet<>();
		
		for(int x = -4; x <= 4; x++)
			for(int z = -4; z <= 4; z++)
				if(Math.abs(x) + Math.abs(z) == 3)
					diamond.add(pack(x, z));
		
		PerimeterDetectedArea area = detect(diamond, 0, 0);
		
		assertEquals(13L, area.columnCount);
		assertEquals(-2, area.minX);
		assertEquals(2, area.maxX);
		assertEquals(-2, area.minZ);
		assertEquals(2, area.maxZ);
	}
	
	@Test
	void rejectsAPlayerStandingOnTheBoundary()
	{
		Set<Long> ring = new HashSet<>();
		
		for(int x = 0; x <= 4; x++)
			for(int z = 0; z <= 4; z++)
				if(x == 0 || x == 4 || z == 0 || z == 4)
					ring.add(pack(x, z));
		
		IllegalStateException exception = assertThrows(
			IllegalStateException.class, () -> detect(ring, 0, 2));
		
		assertTrue(exception.getMessage().contains("inside the outer boundary"));
	}
	
	@Test
	void rejectsAnUnenclosedArea()
	{
		Set<Long> openRing = new HashSet<>();
		
		for(int x = 0; x <= 4; x++)
			for(int z = 0; z <= 4; z++)
				if((x == 0 || x == 4 || z == 0 || z == 4)
					&& !(x == 4 && z == 2))
					openRing.add(pack(x, z));
		
		IllegalStateException exception = assertThrows(
			IllegalStateException.class, () -> detect(openRing, 2, 2));
		
		assertTrue(exception.getMessage().contains("not enclosed"),
			exception.getMessage());
	}
	
	@Test
	void rejectsAnEmptyBoundaryBlock()
	{
		IllegalStateException exception = assertThrows(
			IllegalStateException.class, () -> new BoundaryDetector()
				.detect(new FakeGrid(Set.of(), 1), " ", BOUNDARY_Y, 0, 0));
		
		assertTrue(exception.getMessage().contains("boundary block"));
	}
	
	private static PerimeterDetectedArea detect(Set<Long> boundary, int playerX,
		int playerZ)
	{
		return new BoundaryDetector().detect(new FakeGrid(boundary, 1),
			BOUNDARY, BOUNDARY_Y, playerX, playerZ);
	}
	
	private static boolean contains(PerimeterDetectedArea area,
		PerimeterGrid grid, int x, int z)
	{
		for(var scanline : area.scanlines)
			if(scanline.z == z && x >= scanline.minX && x <= scanline.maxX)
				return true;
		
		return false;
	}
	
	private static long pack(int x, int z)
	{
		return ((long)x << 32) | (z & 0xFFFFFFFFL);
	}
	
	/**
	 * A synthetic world where only the given XZ cells contain the boundary
	 * block, and every chunk inside the render distance is loaded.
	 */
	private static final class FakeGrid implements PerimeterGrid
	{
		private final Set<Long> boundary;
		private final int renderDistance;
		
		private FakeGrid(Set<Long> boundary, int renderDistance)
		{
			this.boundary = boundary;
			this.renderDistance = renderDistance;
		}
		
		@Override
		public String blockIdAt(int x, int y, int z)
		{
			if(y == BOUNDARY_Y && boundary.contains(pack(x, z)))
				return BOUNDARY;
			
			return "minecraft:air";
		}
		
		@Override
		public int renderDistanceChunks()
		{
			return renderDistance;
		}
		
		@Override
		public boolean isChunkLoaded(int chunkX, int chunkZ)
		{
			return Math.abs(chunkX) <= renderDistance
				&& Math.abs(chunkZ) <= renderDistance;
		}
	}
}
