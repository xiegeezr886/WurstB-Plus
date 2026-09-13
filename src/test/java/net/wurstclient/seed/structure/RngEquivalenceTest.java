/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.seed.structure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;

/**
 * Proves that the allocation-free generator behind the seed search
 * ({@link PlacementFrequency.Rng}) behaves exactly like the vanilla
 * {@code WorldgenRandom}, and that the re-implemented frequency reduction
 * decides exactly like the vanilla one.
 *
 * <p>
 * This is what makes a search over billions of candidates trustworthy: the
 * fast path is compared against the real generator instead of against another
 * copy of itself.
 */
final class RngEquivalenceTest
{
	private static final long[] SEEDS = {0L, 1L, -1L, 12345L,
		-4172144997902289642L, 987654321L, 281474976710655L,
		-281474976710656L};
	
	private static final int[] BOUNDS = {2, 3, 7, 9, 16, 20, 24, 25, 26, 27,
		60, 250, 1000};
	
	@Test
	void theRawDrawsMatchVanilla()
	{
		for(long seed : SEEDS)
		{
			WorldgenRandom vanilla = new WorldgenRandom(
				new LegacyRandomSource(0L));
			PlacementFrequency.Rng mine = new PlacementFrequency.Rng();
			
			vanilla.setSeed(seed);
			mine.setSeed(seed);
			
			for(int i = 0; i < 64; i++)
			{
				assertEquals(vanilla.next(31), mine.next(31),
					"next(31) after " + i + " draws of seed " + seed);
				assertEquals(vanilla.nextInt(), mine.nextInt(),
					"nextInt() after " + i + " draws of seed " + seed);
				assertEquals(vanilla.nextLong(), mine.nextLong(),
					"nextLong() after " + i + " draws of seed " + seed);
				assertEquals(vanilla.nextFloat(), mine.nextFloat(),
					"nextFloat() after " + i + " draws of seed " + seed);
				assertEquals(vanilla.nextDouble(), mine.nextDouble(),
					"nextDouble() after " + i + " draws of seed " + seed);
			}
		}
	}
	
	@Test
	void boundedIntsMatchVanillaIncludingThePowerOfTwoPath()
	{
		for(long seed : SEEDS)
			for(int bound : BOUNDS)
			{
				WorldgenRandom vanilla = new WorldgenRandom(
					new LegacyRandomSource(0L));
				PlacementFrequency.Rng mine = new PlacementFrequency.Rng();
				
				vanilla.setSeed(seed);
				mine.setSeed(seed);
				
				for(int i = 0; i < 64; i++)
					assertEquals(vanilla.nextInt(bound), mine.nextInt(bound),
						"nextInt(" + bound + ") after " + i + " draws of seed "
							+ seed);
			}
	}
	
	@Test
	void bothSeedingMethodsMatchVanilla()
	{
		for(long seed : SEEDS)
			for(int a = -40; a <= 40; a += 13)
			{
				int b = a * 7 + 3;
				
				WorldgenRandom vanilla = new WorldgenRandom(
					new LegacyRandomSource(0L));
				PlacementFrequency.Rng mine = new PlacementFrequency.Rng();
				
				vanilla.setLargeFeatureWithSalt(seed, a, b, 10387312);
				mine.setLargeFeatureWithSalt(seed, a, b, 10387312);
				
				for(int i = 0; i < 16; i++)
					assertEquals(vanilla.nextLong(), mine.nextLong(),
						"with salt: long " + i);
				
				vanilla.setLargeFeatureSeed(seed, a, b);
				mine.setLargeFeatureSeed(seed, a, b);
				
				for(int i = 0; i < 16; i++)
					assertEquals(vanilla.nextLong(), mine.nextLong(),
						"large feature seed: long " + i);
				for(int i = 0; i < 16; i++)
					assertEquals(vanilla.nextInt(26), mine.nextInt(26),
						"large feature seed: nextInt(26) " + i);
			}
	}
	
	@Test
	void theFrequencyReductionMatchesVanilla()
	{
		int checked = 0;
		
		for(int method = 0; method <= 3; method++)
			for(float frequency : new float[]{0.004F, 0.01F, 0.2F, 0.5F,
				0.999F})
				for(long seed : new long[]{12345L, -4172144997902289642L,
					987654321L})
					for(int chunkX = -40; chunkX <= 40; chunkX += 3)
						for(int chunkZ = -20; chunkZ <= 20; chunkZ += 7)
						{
							boolean fast = PlacementFrequency.passes(method,
								seed, 10387312, chunkX, chunkZ, frequency);
							boolean vanilla =
								PlacementFrequency.passesWithVanillaRng(method,
									seed, 10387312, chunkX, chunkZ, frequency);
							
							assertEquals(vanilla, fast, "method " + method
								+ ", frequency " + frequency + ", seed "
								+ seed + ", chunk " + chunkX + "/" + chunkZ);
							checked++;
						}
		
		assertTrue(checked > 5000, "only " + checked + " cases were checked");
	}
	
	@Test
	void aFrequencyOfOneIsAlwaysTrue()
	{
		for(int method = 0; method <= 3; method++)
			assertTrue(PlacementFrequency.passes(method, 42L, 7, 13, -9, 1.0F));
	}
	
	@Test
	void anInvalidBoundIsRejected()
	{
		PlacementFrequency.Rng mine = new PlacementFrequency.Rng();
		mine.setSeed(0L);
		
		assertTrue(throwsIllegalArgument(() -> mine.nextInt(0)));
		assertTrue(throwsIllegalArgument(() -> mine.nextInt(-5)));
	}
	
	private static boolean throwsIllegalArgument(Runnable runnable)
	{
		try
		{
			runnable.run();
			return false;
		}catch(IllegalArgumentException e)
		{
			return true;
		}
	}
}
