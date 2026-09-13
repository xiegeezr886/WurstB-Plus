/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.seed.structure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

/**
 * Tests the region scan and the hit model.
 *
 * <p>
 * The structure placement math itself is not tested here on purpose: it is not
 * this project's code, it is vanilla's own
 * {@code RandomSpreadStructurePlacement#getPotentialStructureChunk}, which
 * {@link StructureFinder} calls directly. These tests use a fake sampler and
 * cover everything that this project actually contributes.
 */
final class StructureFinderTest
{
	private static final int SPACING = 32;
	private static final int SEPARATION = 8;
	
	@Test
	void everyRegionContributesAtMostOneChunk()
	{
		// a radius of 159 covers 10 regions per axis, and the 9 * 9 regions
		// that are well inside the radius all qualify
		List<ChunkPos> found = StructureFinder.scan(SPACING, 159,
			new ChunkPos(0, 0), fakeSampler(SPACING, SEPARATION));
		
		assertTrue(found.size() <= 100);
		assertTrue(found.size() >= 81);
		assertEquals(found.size(), new HashSet<>(found).size());
	}
	
	@Test
	void candidatesStayInsideTheirRegion()
	{
		int radius = 200;
		ChunkPos center = new ChunkPos(37, -91);
		List<ChunkPos> found = StructureFinder.scan(SPACING, radius, center,
			fakeSampler(SPACING, SEPARATION));
		
		assertFalse(found.isEmpty());
		
		int minRegionX = Math.floorDiv(center.x - radius, SPACING);
		int maxRegionX = Math.floorDiv(center.x + radius, SPACING);
		int minRegionZ = Math.floorDiv(center.z - radius, SPACING);
		int maxRegionZ = Math.floorDiv(center.z + radius, SPACING);
		
		for(ChunkPos pos : found)
		{
			int regionX = Math.floorDiv(pos.x, SPACING);
			int regionZ = Math.floorDiv(pos.z, SPACING);
			
			assertTrue(regionX >= minRegionX && regionX <= maxRegionX);
			assertTrue(regionZ >= minRegionZ && regionZ <= maxRegionZ);
			
			int offsetX = pos.x - regionX * SPACING;
			int offsetZ = pos.z - regionZ * SPACING;
			assertTrue(offsetX >= 0 && offsetX < SPACING - SEPARATION + 1);
			assertTrue(offsetZ >= 0 && offsetZ < SPACING - SEPARATION + 1);
		}
	}
	
	@Test
	void candidatesOutsideTheRadiusAreDropped()
	{
		int radius = 10;
		List<ChunkPos> found = StructureFinder.scan(SPACING, radius,
			new ChunkPos(0, 0), fakeSampler(SPACING, SEPARATION));
		
		for(ChunkPos pos : found)
		{
			assertTrue(Math.abs(pos.x) <= radius);
			assertTrue(Math.abs(pos.z) <= radius);
		}
	}
	
	@Test
	void negativeCoordinatesUseFloorDivision()
	{
		ArrayList<int[]> regions = new ArrayList<>();
		
		// a truncating division would pick region 0 for chunk -1, floor division
		// has to pick region -1
		List<ChunkPos> found = StructureFinder.scan(SPACING, 0,
			new ChunkPos(-1, -1), (regionX, regionZ) -> {
				regions.add(new int[]{regionX, regionZ});
				return new ChunkPos(regionX * SPACING + 1,
					regionZ * SPACING + 1);
			});
		
		assertEquals(1, regions.size());
		assertEquals(-1, regions.get(0)[0]);
		assertEquals(-1, regions.get(0)[1]);
		
		// that candidate is 31 chunks away, so a radius of 0 drops it
		assertTrue(found.isEmpty());
	}
	
	@Test
	void duplicatesAreRemoved()
	{
		List<ChunkPos> found = StructureFinder.scan(SPACING, 64,
			new ChunkPos(0, 0), (regionX, regionZ) -> new ChunkPos(0, 0));
		
		assertEquals(List.of(new ChunkPos(0, 0)), found);
	}
	
	@Test
	void theClosestCandidateComesFirst()
	{
		List<ChunkPos> found = StructureFinder.scan(SPACING, 64,
			new ChunkPos(0, 0), fakeSampler(SPACING, SEPARATION));
		
		assertFalse(found.isEmpty());
		
		int previous = -1;
		
		for(ChunkPos pos : found)
		{
			int distance = Math.max(Math.abs(pos.x), Math.abs(pos.z));
			assertTrue(distance >= previous);
			previous = distance;
		}
	}
	
	@Test
	void invalidInputGivesAnEmptyResult()
	{
		ChunkPos center = new ChunkPos(0, 0);
		
		assertTrue(StructureFinder.scan(0, 64, center,
			fakeSampler(SPACING, SEPARATION)).isEmpty());
		assertTrue(StructureFinder.scan(SPACING, -1, center,
			fakeSampler(SPACING, SEPARATION)).isEmpty());
		assertTrue(StructureFinder.scan(SPACING, 64, null,
			fakeSampler(SPACING, SEPARATION)).isEmpty());
		assertTrue(StructureFinder.scan(SPACING, 64, center, null).isEmpty());
	}
	
	@Test
	void aHitDescribesItsSetAndPosition()
	{
		StructureHit hit = new StructureHit(new ChunkPos(3, -2),
			"minecraft:villages", List.of("minecraft:village_plains"), true,
			true);
		
		assertEquals(3, hit.chunkX());
		assertEquals(-2, hit.chunkZ());
		assertEquals(3 * 16 + 8, hit.blockX());
		assertEquals(-2 * 16 + 8, hit.blockZ());
		assertTrue(hit.isPossible());
		assertTrue(hit.describe().contains("villages"));
		assertTrue(hit.describe().contains("village_plains"));
		assertTrue(hit.describe().contains("[3, -2]"));
	}
	
	@Test
	void aRejectedBiomeIsNotPossible()
	{
		StructureHit hit = new StructureHit(new ChunkPos(0, 0),
			"minecraft:shipwrecks", List.of("minecraft:shipwreck"), true,
			false);
		
		assertFalse(hit.isPossible());
		assertTrue(hit.describe().contains("wrong biome"));
	}
	
	@Test
	void anUncheckedBiomeStaysPossible()
	{
		StructureHit hit = new StructureHit(new ChunkPos(0, 0),
			"minecraft:shipwrecks", List.of("minecraft:shipwreck"), false,
			true);
		
		assertTrue(hit.isPossible());
		assertTrue(hit.describe().contains("biome unknown"));
	}
	
	@Test
	void theDistanceUsesTheChunkCentre()
	{
		StructureHit hit = new StructureHit(new ChunkPos(0, 0),
			"minecraft:shipwrecks", List.of("minecraft:shipwreck"), false,
			true);
		
		assertEquals(0.0D, hit.distanceSq(new BlockPos(8, 0, 8)));
		assertEquals(100.0D, hit.distanceSq(new BlockPos(18, 0, 8)));
	}
	
	@Test
	void shortNamesDropTheNamespace()
	{
		assertEquals("village_plains",
			StructureHit.shortName("minecraft:village_plains"));
		assertEquals("custom", StructureHit.shortName("custom"));
		assertEquals("?", StructureHit.shortName(null));
	}
	
	/**
	 * A deterministic stand-in for the vanilla placement: the offset is always
	 * inside the region, like the real one (its offsets are never negative,
	 * because RandomSpreadType only adds nextInt results).
	 */
	private static StructureFinder.RegionSampler fakeSampler(int spacing,
		int separation)
	{
		int range = spacing - separation;
		
		return (regionX, regionZ) -> {
			int offsetX = Math.floorMod(regionX * 31 + regionZ * 17, range);
			int offsetZ = Math.floorMod(regionX * 13 + regionZ * 29, range);
			return new ChunkPos(regionX * spacing + offsetX,
				regionZ * spacing + offsetZ);
		};
	}
}
