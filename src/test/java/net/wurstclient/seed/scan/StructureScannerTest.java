/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.seed.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Tests the block heuristics of {@link StructureScanner} against synthetic
 * worlds.
 *
 * <p>
 * A real level is not needed and not wanted here: the scanner only ever asks
 * for block ids, so every scene can be written down exactly, including the
 * natural terrain that must <b>not</b> be reported. The scenes are deliberately
 * close to what the structures really contain - an igloo, for example, is built
 * from the blocks of {@code minecraft:igloo/top}.
 */
final class StructureScannerTest
{
	private static final String PRISMARINE = "minecraft:prismarine";
	private static final String SEA_LANTERN = "minecraft:sea_lantern";
	private static final String DEEPSLATE_BRICKS = "minecraft:deepslate_bricks";
	private static final String DEEPSLATE_TILES = "minecraft:deepslate_tiles";
	private static final String REINFORCED_DEEPSLATE =
		"minecraft:reinforced_deepslate";
	private static final String MUD_BRICKS = "minecraft:mud_bricks";
	private static final String SANDSTONE = "minecraft:sandstone";
	private static final String CUT_SANDSTONE = "minecraft:cut_sandstone";
	private static final String CHISELED_SANDSTONE =
		"minecraft:chiseled_sandstone";
	private static final String ORANGE_TERRACOTTA =
		"minecraft:orange_terracotta";
	private static final String SNOW_BLOCK = "minecraft:snow_block";
	private static final String CHEST = "minecraft:chest";
	
	@Test
	void anOceanMonumentIsRecognised()
	{
		FakeWorld world = new FakeWorld("minecraft:deep_ocean");
		world.fill(36, 40, -44, 41, 43, -39, PRISMARINE);
		world.set(37, 44, -43, SEA_LANTERN);
		world.set(38, 44, -43, SEA_LANTERN);
		world.set(39, 44, -43, SEA_LANTERN);
		
		List<StructureScanner.Sighting> found =
			StructureScanner.scan(world, 2, -3, 1);
		
		assertEquals(1, found.size());
		
		StructureScanner.Sighting sighting = found.get(0);
		assertEquals(StructureScanner.OCEAN_MONUMENTS, sighting.setId);
		assertEquals(2, sighting.chunkX);
		assertEquals(-3, sighting.chunkZ);
		assertEquals(2 * 16 + 8, sighting.blockX());
		assertEquals(-3 * 16 + 8, sighting.blockZ());
		assertTrue(sighting.score > 0);
		assertTrue(sighting.reason.contains("sea_lantern"));
		assertTrue(sighting.reason.contains("prismarine"));
		assertTrue(sighting.describe().contains("ocean_monuments"));
	}
	
	@Test
	void aPlainOceanIsNotAMonument()
	{
		FakeWorld world = new FakeWorld("minecraft:ocean");
		world.fill(0, 30, 0, 15, 37, 15, "minecraft:stone");
		world.fill(0, 38, 0, 15, 39, 15, "minecraft:sand");
		world.fill(0, 40, 0, 15, 60, 15, "minecraft:water");
		world.set(4, 41, 4, "minecraft:kelp");
		world.set(5, 41, 5, "minecraft:kelp_plant");
		world.set(6, 41, 6, "minecraft:seagrass");
		world.set(7, 40, 7, "minecraft:gravel");
		
		// one stray prismarine block is nowhere near the threshold
		world.set(8, 40, 8, PRISMARINE);
		
		assertTrue(StructureScanner.scan(world, 0, 0, 0).isEmpty());
	}
	
	@Test
	void prismarineAboveTheMonumentRangeIsIgnored()
	{
		FakeWorld world = new FakeWorld("minecraft:deep_ocean");
		world.fill(0, 100, 0, 15, 110, 15, PRISMARINE);
		world.set(4, 105, 4, SEA_LANTERN);
		world.set(5, 105, 4, SEA_LANTERN);
		world.set(6, 105, 4, SEA_LANTERN);
		
		assertEquals(0, countOf(StructureScanner.scan(world, 0, 0, 0),
			StructureScanner.OCEAN_MONUMENTS));
	}
	
	@Test
	void anAncientCityIsRecognised()
	{
		FakeWorld world = new FakeWorld("minecraft:deep_dark");
		world.fill(-78, -50, 66, -75, -47, 69, DEEPSLATE_BRICKS);
		world.fill(-74, -50, 66, -73, -47, 69, DEEPSLATE_TILES);
		world.fill(-72, -50, 66, -66, -48, 74, "minecraft:deepslate");
		world.set(-70, -49, 70, REINFORCED_DEEPSLATE);
		
		List<StructureScanner.Sighting> found =
			StructureScanner.scan(world, -5, 4, 0);
		
		assertEquals(1, found.size());
		
		StructureScanner.Sighting sighting = found.get(0);
		assertEquals(StructureScanner.ANCIENT_CITIES, sighting.setId);
		assertEquals(-5, sighting.chunkX);
		assertEquals(4, sighting.chunkZ);
		assertTrue(sighting.reason.contains("reinforced_deepslate"));
		assertTrue(sighting.reason.contains("deepslate_bricks"));
	}
	
	@Test
	void anAncientCityIsRecognisedWithoutReinforcedDeepslate()
	{
		FakeWorld world = new FakeWorld("minecraft:deep_dark");
		world.fill(98, -40, 98, 101, -37, 101, DEEPSLATE_BRICKS);
		world.fill(102, -40, 98, 103, -37, 101, DEEPSLATE_TILES);
		
		List<StructureScanner.Sighting> found =
			StructureScanner.scan(world, 6, 6, 0);
		
		assertEquals(1, found.size());
		assertEquals(StructureScanner.ANCIENT_CITIES, found.get(0).setId);
		assertTrue(found.get(0).reason.contains("deepslate_tiles"));
	}
	
	@Test
	void deepslateTerrainIsNotAnAncientCity()
	{
		FakeWorld world = new FakeWorld("minecraft:deep_dark");
		world.fill(0, -64, 0, 15, -1, 15, "minecraft:deepslate");
		world.fill(0, -40, 0, 15, -30, 15, "minecraft:cobbled_deepslate");
		world.fill(0, -30, 0, 15, -24, 15, "minecraft:tuff");
		world.fill(0, -24, 0, 15, -20, 15, "minecraft:gravel");
		world.fill(0, -20, 0, 15, -16, 15, "minecraft:blackstone");
		world.fill(0, -16, 0, 15, -12, 15, "minecraft:basalt");
		world.fill(0, -12, 0, 15, -8, 15, "minecraft:smooth_basalt");
		world.set(1, -7, 1, "minecraft:sculk");
		world.set(2, -7, 2, "minecraft:sculk_catalyst");
		
		assertTrue(StructureScanner.scan(world, 0, 0, 0).isEmpty());
	}
	
	@Test
	void deepslateBricksAboveYZeroAreNotAnAncientCity()
	{
		FakeWorld world = new FakeWorld("minecraft:plains");
		
		// a surface build out of deepslate bricks and tiles, above y=0
		world.fill(2, 60, 2, 9, 63, 9, DEEPSLATE_BRICKS);
		world.fill(2, 64, 2, 9, 66, 9, DEEPSLATE_TILES);
		
		assertEquals(0, countOf(StructureScanner.scan(world, 0, 0, 0),
			StructureScanner.ANCIENT_CITIES));
	}
	
	@Test
	void aDesertPyramidIsRecognised()
	{
		FakeWorld world = new FakeWorld("minecraft:desert");
		world.fill(112, 62, 112, 127, 62, 127, "minecraft:sand");
		world.fill(112, 63, 112, 127, 63, 127, SANDSTONE);
		world.fill(116, 64, 116, 121, 67, 121, CUT_SANDSTONE);
		world.fill(118, 68, 118, 119, 69, 119, CHISELED_SANDSTONE);
		world.fill(114, 64, 114, 115, 65, 115, ORANGE_TERRACOTTA);
		
		List<StructureScanner.Sighting> found =
			StructureScanner.scan(world, 7, 7, 0);
		
		assertEquals(1, found.size());
		
		StructureScanner.Sighting sighting = found.get(0);
		assertEquals(StructureScanner.DESERT_PYRAMIDS, sighting.setId);
		assertEquals(7, sighting.chunkX);
		assertEquals(7, sighting.chunkZ);
		assertTrue(sighting.reason.contains("chiseled_sandstone"));
		assertTrue(sighting.reason.contains("orange_terracotta"));
	}
	
	@Test
	void sandstoneTerrainIsNotADesertPyramid()
	{
		FakeWorld world = new FakeWorld("minecraft:desert");
		world.fill(0, 60, 0, 15, 62, 15, SANDSTONE);
		world.fill(0, 63, 0, 15, 68, 15, "minecraft:sand");
		
		assertTrue(StructureScanner.scan(world, 0, 0, 0).isEmpty());
	}
	
	@Test
	void shapedSandstoneWithoutTerracottaIsNotADesertPyramid()
	{
		FakeWorld world = new FakeWorld("minecraft:desert");
		world.fill(0, 60, 0, 15, 70, 15, CUT_SANDSTONE);
		world.fill(0, 71, 0, 15, 71, 15, CHISELED_SANDSTONE);
		
		assertEquals(0, countOf(StructureScanner.scan(world, 0, 0, 0),
			StructureScanner.DESERT_PYRAMIDS));
	}
	
	@Test
	void badlandsTerracottaIsNotADesertPyramid()
	{
		FakeWorld world = new FakeWorld("minecraft:badlands");
		
		// the banded terracotta of a badlands biome contains the orange one
		world.fill(0, 60, 0, 15, 70, 15, ORANGE_TERRACOTTA);
		world.fill(0, 55, 0, 15, 59, 15, "minecraft:terracotta");
		
		assertEquals(0, countOf(StructureScanner.scan(world, 0, 0, 0),
			StructureScanner.DESERT_PYRAMIDS));
	}
	
	@Test
	void snowyPlainsAreNotAnIgloo()
	{
		FakeWorld world = new FakeWorld("minecraft:snowy_plains");
		world.fill(0, 60, 0, 15, 62, 15, "minecraft:dirt");
		world.fill(0, 63, 0, 15, 70, 15, SNOW_BLOCK);
		world.fill(0, 71, 0, 15, 71, 15, "minecraft:snow");
		world.set(3, 72, 3, "minecraft:ice");
		world.set(4, 72, 4, "minecraft:spruce_log");
		world.fill(-16, 63, 0, -1, 70, 15, SNOW_BLOCK);
		
		assertTrue(StructureScanner.scan(world, 0, 0, 1).isEmpty());
	}
	
	@Test
	void anIglooIsRecognisedByItsInterior()
	{
		FakeWorld world = new FakeWorld("minecraft:snowy_plains");
		
		// minecraft:igloo/top: 94 snow blocks, 9 white carpets, 3 light gray
		// carpets, a red bed, a redstone torch and an oak trapdoor
		world.fill(0, 63, 0, 9, 66, 9, SNOW_BLOCK);
		world.fill(2, 63, 2, 4, 63, 4, "minecraft:white_carpet");
		world.set(6, 63, 6, "minecraft:light_gray_carpet");
		world.set(7, 63, 7, "minecraft:red_bed");
		world.set(5, 65, 5, "minecraft:redstone_torch");
		world.set(1, 63, 1, "minecraft:oak_trapdoor");
		
		List<StructureScanner.Sighting> found =
			StructureScanner.scan(world, 0, 0, 0);
		
		assertEquals(1, found.size());
		assertEquals(StructureScanner.IGLOOS, found.get(0).setId);
		assertTrue(found.get(0).reason.contains("snow_block"));
		assertTrue(found.get(0).reason.contains("white_carpet"));
	}
	
	@Test
	void whiteWoolCountsAsAnIglooInterior()
	{
		FakeWorld world = new FakeWorld("minecraft:snowy_plains");
		world.fill(0, 63, 0, 5, 66, 5, SNOW_BLOCK);
		world.fill(1, 63, 1, 2, 63, 2, "minecraft:white_wool");
		
		List<StructureScanner.Sighting> found =
			StructureScanner.scan(world, 0, 0, 0);
		
		assertEquals(1, found.size());
		assertEquals(StructureScanner.IGLOOS, found.get(0).setId);
		assertTrue(found.get(0).reason.contains("white_wool"));
	}
	
	@Test
	void aTrailRuinIsRecognised()
	{
		FakeWorld world = new FakeWorld("minecraft:taiga");
		world.fill(0, -10, 0, 15, -8, 15, MUD_BRICKS);
		world.fill(0, -11, 0, 15, -11, 15, "minecraft:gravel");
		
		List<StructureScanner.Sighting> found =
			StructureScanner.scan(world, 0, 0, 0);
		
		assertEquals(1, found.size());
		assertEquals(StructureScanner.TRAIL_RUINS, found.get(0).setId);
		assertTrue(found.get(0).reason.contains("mud_bricks"));
	}
	
	@Test
	void mudTerrainIsNotATrailRuin()
	{
		FakeWorld world = new FakeWorld("minecraft:mangrove_swamp");
		world.fill(0, 60, 0, 15, 70, 15, "minecraft:mud");
		world.fill(0, 55, 0, 15, 59, 15, "minecraft:muddy_mangrove_roots");
		world.set(1, 60, 1, "minecraft:mangrove_propagule");
		
		assertTrue(StructureScanner.scan(world, 0, 0, 0).isEmpty());
	}
	
	@Test
	void aBuriedTreasureIsRecognisedInABeach()
	{
		FakeWorld world = new FakeWorld("minecraft:plains");
		world.setBiome(0, 0, "minecraft:beach");
		world.fill(0, 50, 0, 15, 55, 15, SANDSTONE);
		world.fill(0, 56, 0, 15, 62, 15, "minecraft:sand");
		world.set(3, 56, 5, CHEST);
		
		List<StructureScanner.Sighting> found =
			StructureScanner.scan(world, 0, 0, 0);
		
		assertEquals(1, found.size());
		
		StructureScanner.Sighting sighting = found.get(0);
		assertEquals(StructureScanner.BURIED_TREASURES, sighting.setId);
		assertEquals(0, sighting.chunkX);
		assertEquals(0, sighting.chunkZ);
		assertTrue(sighting.reason.contains("chest"));
		assertTrue(sighting.reason.contains("beach"));
	}
	
	@Test
	void aChestInAMeadowIsNotABuriedTreasure()
	{
		FakeWorld world = new FakeWorld("minecraft:plains");
		world.fill(0, 60, 0, 15, 62, 15, "minecraft:grass_block");
		world.set(3, 63, 5, CHEST);
		
		assertEquals(0, countOf(StructureScanner.scan(world, 0, 0, 0),
			StructureScanner.BURIED_TREASURES));
	}
	
	@Test
	void aChestWithAnUnknownBiomeIsNotABuriedTreasure()
	{
		FakeWorld world = new FakeWorld(null);
		world.fill(0, 56, 0, 15, 62, 15, "minecraft:sand");
		world.set(3, 56, 5, CHEST);
		
		assertEquals(0, countOf(StructureScanner.scan(world, 0, 0, 0),
			StructureScanner.BURIED_TREASURES));
	}
	
	@Test
	void aViewWithoutBiomesDoesNotReportBuriedTreasures()
	{
		FakeWorld world = new FakeWorld("minecraft:beach");
		world.fill(0, 56, 0, 15, 62, 15, "minecraft:sand");
		world.set(3, 56, 5, CHEST);
		
		// the default biomeId of the interface returns null, so the beach
		// cannot be confirmed
		StructureScanner.BlockView blocksOnly = new StructureScanner.BlockView()
		{
			@Override
			public String blockId(int x, int y, int z)
			{
				return world.blockId(x, y, z);
			}
			
			@Override
			public boolean isLoaded(int chunkX, int chunkZ)
			{
				return world.isLoaded(chunkX, chunkZ);
			}
		};
		
		assertEquals(0, countOf(StructureScanner.scan(blocksOnly, 0, 0, 0),
			StructureScanner.BURIED_TREASURES));
	}
	
	@Test
	void severalChestsInABeachAreNotABuriedTreasure()
	{
		FakeWorld world = new FakeWorld("minecraft:beach");
		world.fill(0, 56, 0, 15, 62, 15, "minecraft:sand");
		world.set(3, 56, 5, CHEST);
		world.set(4, 56, 5, CHEST);
		
		assertEquals(0, countOf(StructureScanner.scan(world, 0, 0, 0),
			StructureScanner.BURIED_TREASURES));
	}
	
	@Test
	void unloadedChunksAreSkipped()
	{
		FakeWorld world = new FakeWorld("minecraft:deep_ocean");
		world.fill(36, 40, -44, 41, 43, -39, PRISMARINE);
		world.set(37, 44, -43, SEA_LANTERN);
		world.set(38, 44, -43, SEA_LANTERN);
		
		assertEquals(1, countOf(StructureScanner.scan(world, 2, -3, 0),
			StructureScanner.OCEAN_MONUMENTS));
		
		world.unload(2, -3);
		assertTrue(StructureScanner.scan(world, 2, -3, 1).isEmpty());
	}
	
	@Test
	void anEmptyWorldGivesNoSightings()
	{
		FakeWorld world = new FakeWorld("minecraft:plains");
		
		assertTrue(StructureScanner.scan(world, 0, 0, 2).isEmpty());
		assertTrue(StructureScanner.scan(null, 0, 0, 2).isEmpty());
		assertTrue(StructureScanner.scan(world, 0, 0, -1).isEmpty());
	}
	
	@Test
	void theRadiusIsInChunksPerAxis()
	{
		FakeWorld world = new FakeWorld("minecraft:deep_ocean");
		world.fill(20, 40, 20, 23, 43, 22, PRISMARINE);
		world.set(21, 44, 21, SEA_LANTERN);
		world.set(22, 44, 21, SEA_LANTERN);
		
		assertEquals(0, countOf(StructureScanner.scan(world, 0, 0, 0),
			StructureScanner.OCEAN_MONUMENTS));
		assertEquals(1, countOf(StructureScanner.scan(world, 0, 0, 1),
			StructureScanner.OCEAN_MONUMENTS));
	}
	
	@Test
	void theRadiusIsClamped()
	{
		RecordingView view = new RecordingView();
		
		StructureScanner.scan(view, 0, 0, 1000);
		
		assertEquals(StructureScanner.MAX_RADIUS_CHUNKS, view.maxChunkX);
		assertEquals(StructureScanner.MAX_RADIUS_CHUNKS, view.maxChunkZ);
		assertEquals(-StructureScanner.MAX_RADIUS_CHUNKS, view.minChunkX);
		assertEquals(-StructureScanner.MAX_RADIUS_CHUNKS, view.minChunkZ);
		
		int perAxis = 2 * StructureScanner.MAX_RADIUS_CHUNKS + 1;
		assertEquals(perAxis * perAxis, view.queries);
	}
	
	@Test
	void theBestSightingComesFirst()
	{
		FakeWorld world = new FakeWorld("minecraft:deep_ocean");
		
		// chunk [0, 0]: 16 prismarine and 2 sea lanterns
		world.fill(0, 40, 0, 3, 43, 0, PRISMARINE);
		world.set(1, 44, 1, SEA_LANTERN);
		world.set(2, 44, 2, SEA_LANTERN);
		
		// chunk [1, 0]: 48 prismarine and 4 sea lanterns
		world.fill(16, 40, 0, 19, 43, 2, PRISMARINE);
		world.set(17, 44, 1, SEA_LANTERN);
		world.set(18, 44, 1, SEA_LANTERN);
		world.set(17, 44, 2, SEA_LANTERN);
		world.set(18, 44, 2, SEA_LANTERN);
		
		List<StructureScanner.Sighting> found =
			StructureScanner.scan(world, 0, 0, 1);
		
		assertEquals(2, found.size());
		assertEquals(1, found.get(0).chunkX);
		assertEquals(0, found.get(1).chunkX);
		assertTrue(found.get(0).score > found.get(1).score);
	}
	
	@Test
	void theSupportedSetsAreListed()
	{
		List<String> sets = StructureScanner.supportedSets();
		
		assertEquals(6, sets.size());
		assertTrue(sets.contains(StructureScanner.OCEAN_MONUMENTS));
		assertTrue(sets.contains(StructureScanner.ANCIENT_CITIES));
		assertTrue(sets.contains(StructureScanner.TRAIL_RUINS));
		assertTrue(sets.contains(StructureScanner.DESERT_PYRAMIDS));
		assertTrue(sets.contains(StructureScanner.IGLOOS));
		assertTrue(sets.contains(StructureScanner.BURIED_TREASURES));
	}
	
	@Test
	void aSightingDescribesItself()
	{
		StructureScanner.Sighting sighting = new StructureScanner.Sighting(
			StructureScanner.OCEAN_MONUMENTS, 3, -2, 41,
			"3x sea_lantern + 17x prismarine");
		
		assertEquals(3, sighting.chunkX);
		assertEquals(-2, sighting.chunkZ);
		assertEquals(56, sighting.blockX());
		assertEquals(-24, sighting.blockZ());
		assertEquals(41, sighting.score);
		assertTrue(sighting.describe().contains("ocean_monuments"));
		assertTrue(sighting.describe().contains("[3, -2]"));
		assertTrue(sighting.describe().contains("41"));
		assertNotNull(sighting.toString());
	}
	
	private static int countOf(List<StructureScanner.Sighting> sightings,
		String setId)
	{
		int count = 0;
		
		for(StructureScanner.Sighting sighting : sightings)
			if(setId.equals(sighting.setId))
				count++;
		
		return count;
	}
	
	/**
	 * A world that is written down block by block. Positions that were never
	 * set are air, which the scanner ignores like any other unknown block.
	 */
	private static final class FakeWorld implements StructureScanner.BlockView
	{
		private final HashMap<Long, String> blocks = new HashMap<>();
		private final HashSet<Long> unloaded = new HashSet<>();
		private final HashMap<Long, String> biomes = new HashMap<>();
		private final String defaultBiome;
		
		private FakeWorld(String defaultBiome)
		{
			this.defaultBiome = defaultBiome;
		}
		
		private void set(int x, int y, int z, String id)
		{
			blocks.put(key(x, y, z), id);
		}
		
		private void fill(int minX, int minY, int minZ, int maxX, int maxY,
			int maxZ, String id)
		{
			for(int x = minX; x <= maxX; x++)
				for(int y = minY; y <= maxY; y++)
					for(int z = minZ; z <= maxZ; z++)
						set(x, y, z, id);
		}
		
		private void unload(int chunkX, int chunkZ)
		{
			unloaded.add(chunkKey(chunkX, chunkZ));
		}
		
		private void setBiome(int chunkX, int chunkZ, String id)
		{
			biomes.put(chunkKey(chunkX, chunkZ), id);
		}
		
		@Override
		public String blockId(int x, int y, int z)
		{
			return blocks.get(key(x, y, z));
		}
		
		@Override
		public boolean isLoaded(int chunkX, int chunkZ)
		{
			return !unloaded.contains(chunkKey(chunkX, chunkZ));
		}
		
		@Override
		public String biomeId(int x, int z)
		{
			Long chunk = chunkKey(Math.floorDiv(x, 16), Math.floorDiv(z, 16));
			
			if(biomes.containsKey(chunk))
				return biomes.get(chunk);
			
			return defaultBiome;
		}
		
		private static long key(int x, int y, int z)
		{
			return ((long)(x & 0x3FFFFFF) << 38) | ((long)(y & 0xFFF) << 26)
				| (z & 0x3FFFFFF);
		}
		
		private static long chunkKey(int chunkX, int chunkZ)
		{
			return ((long)chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
		}
	}
	
	/**
	 * A view that has no chunks at all and writes down which chunks were asked
	 * for, so that the radius can be checked without scanning a real world.
	 */
	private static final class RecordingView
		implements StructureScanner.BlockView
	{
		private int minChunkX = Integer.MAX_VALUE;
		private int maxChunkX = Integer.MIN_VALUE;
		private int minChunkZ = Integer.MAX_VALUE;
		private int maxChunkZ = Integer.MIN_VALUE;
		private int queries;
		
		@Override
		public String blockId(int x, int y, int z)
		{
			return null;
		}
		
		@Override
		public boolean isLoaded(int chunkX, int chunkZ)
		{
			minChunkX = Math.min(minChunkX, chunkX);
			maxChunkX = Math.max(maxChunkX, chunkX);
			minChunkZ = Math.min(minChunkZ, chunkZ);
			maxChunkZ = Math.max(maxChunkZ, chunkZ);
			queries++;
			return false;
		}
	}
}
