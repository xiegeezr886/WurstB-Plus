/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.seed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.mojang.serialization.Codec;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

/**
 * These tests only check properties that do not need real world generation:
 * that the same input always yields the same output, that the output is
 * inside the world height range and that different seeds differ.
 *
 * <p>
 * <b>The exact coordinates are deliberately not asserted.</b> Verifying them
 * would require generating the same chunk with a real Minecraft server and
 * comparing the block by block result, which cannot be done in a unit test -
 * and it would also depend on the per-generation-step feature index, which is
 * only known inside a running game (see {@link OreRules}).
 */
final class OrePredictorTest
{
	private static final int MIN_Y = -64;
	private static final int MAX_Y = 319;
	private static final int HEIGHT = MAX_Y - MIN_Y + 1;
	private static final long SEED = 12345L;
	

	@Test
	void theSameInputAlwaysGivesTheSameCoordinates()
	{
		OrePredictor predictor =
			new OrePredictor(SEED, "minecraft:overworld", new TestContext());
		ChunkPos chunk = new ChunkPos(0, 0);
		
		List<BlockPos> first = predictor.predictChunk(chunk);
		List<BlockPos> second = predictor.predictChunk(chunk);
		
		assertFalse(first.isEmpty());
		assertEquals(first, second);
		assertEquals(predictor.predictChunkWithIds(chunk).keySet(),
			predictor.predictChunkWithIds(chunk).keySet());
		assertEquals(seed(), predictor.seed());
		assertEquals("minecraft:overworld", predictor.dimensionId());
	}
	
	@Test
	void predictionsStayInsideTheWorldHeightRange()
	{
		OrePredictor predictor =
			new OrePredictor(SEED, "minecraft:overworld", new TestContext());
		Map<BlockPos, String> predictions =
			predictor.predictChunkWithIds(new ChunkPos(0, 0));
		
		assertFalse(predictions.isEmpty());
		
		for(BlockPos pos : predictions.keySet())
		{
			assertTrue(pos.getY() >= MIN_Y, "too low: " + pos);
			assertTrue(pos.getY() <= MAX_Y, "too high: " + pos);
			assertTrue(pos.getX() >= -16 && pos.getX() <= 31,
				"outside the chunk: " + pos);
			assertTrue(predictor.oreIds().contains(predictions.get(pos)),
				"unknown block: " + predictions.get(pos));
		}
	}
	
	@Test
	void differentSeedsGiveDifferentPredictions()
	{
		ChunkPos chunk = new ChunkPos(0, 0);
		OrePredictor first =
			new OrePredictor(1L, "minecraft:overworld", new TestContext());
		OrePredictor second =
			new OrePredictor(2L, "minecraft:overworld", new TestContext());
		
		assertNotEquals(first.predictChunk(chunk), second.predictChunk(chunk));
	}
	
	@Test
	void differentChunksGiveDifferentPredictions()
	{
		OrePredictor predictor =
			new OrePredictor(SEED, "minecraft:overworld", new TestContext());
		
		assertNotEquals(predictor.predictChunk(new ChunkPos(0, 0)),
			predictor.predictChunk(new ChunkPos(1, 0)));
	}
	
	@Test
	void theNetherUsesTheNetherRules()
	{
		OrePredictor predictor =
			new OrePredictor(SEED, "the_nether", new TestContext());
		
		assertEquals("minecraft:the_nether", predictor.dimensionId());
		assertTrue(predictor.oreIds().contains("minecraft:ancient_debris"));
		assertTrue(predictor.oreIds().contains("minecraft:nether_quartz_ore"));
		assertFalse(
			predictor.oreIds().contains("minecraft:deepslate_diamond_ore"));
		assertFalse(predictor.predictChunk(new ChunkPos(0, 0)).isEmpty());
	}
	
	@Test
	void theEndHasNoOreRules()
	{
		OrePredictor predictor =
			new OrePredictor(SEED, "minecraft:the_end", new TestContext());
		
		assertTrue(predictor.oreIds().isEmpty());
		assertTrue(predictor.predictChunk(new ChunkPos(0, 0)).isEmpty());
	}
	
	@Test
	void anUnknownDimensionHasNoRules()
	{
		OrePredictor predictor =
			new OrePredictor(SEED, "nonsense", new TestContext());
		
		assertEquals("minecraft:nonsense", predictor.dimensionId());
		assertTrue(predictor.oreIds().isEmpty());
		assertTrue(predictor.predictChunk(new ChunkPos(0, 0)).isEmpty());
	}
	
	@Test
	void oreRulesCoverEveryVanillaOre()
	{
		assertEquals(28, OreRules.forDimension("minecraft:overworld").size());
		assertEquals(10, OreRules.forDimension("minecraft:the_nether").size());
		assertTrue(OreRules.forDimension("the_end").isEmpty());
		assertTrue(OreRules.dimensions().contains("overworld"));
		
		OreRule diamond = OreRules.forDimension("overworld").stream()
			.filter(rule -> "minecraft:ore_diamond".equals(rule.featureId))
			.findFirst().orElseThrow();
		
		assertEquals("minecraft:diamond_ore", diamond.blockId);
		assertEquals("minecraft:deepslate_diamond_ore", diamond.deepslateBlockId);
		assertEquals(OreRules.STEP_UNDERGROUND_ORES, diamond.step);
		assertEquals(4, diamond.size);
		assertEquals(0.5F, diamond.discardOnAirChance);
		assertFalse(diamond.scattered);
		assertEquals(7, diamond.count.sample(
			new net.minecraft.world.level.levelgen.LegacyRandomSource(0L)));
		
		OreRule debris = OreRules.forDimension("the_nether").stream()
			.filter(rule -> "minecraft:ore_debris_small".equals(rule.featureId))
			.findFirst().orElseThrow();
		
		assertTrue(debris.scattered);
		assertEquals("minecraft:ancient_debris", debris.blockId);
	}
	
	@Test
	void aMissingContextIsRejected()
	{
		assertThrows(NullPointerException.class,
			() -> new OrePredictor(SEED, "overworld", null));
	}
	
	private static long seed()
	{
		return SEED;
	}
	
	/**
	 * A fake ore context: the whole world height is solid, which is what
	 * keeps the predictions independent of the player's current blocks.
	 */
	private static final class TestContext implements OrePredictor.OreContext
	{
		@Override
		public int minY()
		{
			return MIN_Y;
		}
		
		@Override
		public int maxY()
		{
			return MAX_Y;
		}
		
		@Override
		public boolean isAir(int x, int y, int z)
		{
			return false;
		}
	}
}
