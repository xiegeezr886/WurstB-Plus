/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.seed.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;

/**
 * Closes the loop of the seed search: the observations are produced with the
 * <b>vanilla</b> random number generator and the vanilla offset formula, and
 * the search then has to find the seed they came from using only the project's
 * own allocation-free implementation.
 *
 * <p>
 * The placement data is the real one of some vanilla structure sets (villages,
 * shipwrecks, ancient cities), so the ranges and salts are the ones the game
 * uses.
 */
final class SeedSearchTest
{
	private static final long SEED = -4172144997902289642L;
	
	private static final StructureSpread VILLAGES = new StructureSpread(
		"minecraft:villages", 34, 8, 10387312, false, 1.0F,
		net.wurstclient.seed.structure.PlacementFrequency.DEFAULT);
	
	private static final StructureSpread SHIPWRECKS = new StructureSpread(
		"minecraft:shipwrecks", 24, 4, 165745295, false, 1.0F,
		net.wurstclient.seed.structure.PlacementFrequency.DEFAULT);
	
	private static final StructureSpread ANCIENT_CITIES = new StructureSpread(
		"minecraft:ancient_cities", 24, 8, 20083232, false, 1.0F,
		net.wurstclient.seed.structure.PlacementFrequency.DEFAULT);
	
	/** buried treasure: spacing 1, separation 0, so every chunk matches */
	private static final StructureSpread BURIED_TREASURES = new StructureSpread(
		"minecraft:buried_treasures", 1, 0, 0, false, 0.01F,
		net.wurstclient.seed.structure.PlacementFrequency.DEFAULT);
	
	private static final List<StructureSpread> SPREADS =
		List.of(VILLAGES, SHIPWRECKS, ANCIENT_CITIES, BURIED_TREASURES);
	
	@Test
	void twoObservationsFindTheSeedAmongTheCandidates()
	{
		List<Observation> observations = observations(SEED, VILLAGES, 2);
		List<SeedSearch.Target> targets =
			SeedSearch.targets(observations, SPREADS);
		
		assertEquals(2, targets.size());
		
		List<Long> found = search(SEED - 2000, SEED + 2000, targets, 1, 100);
		
		// a handful of observations is not always unique - observations of the
		// same structure set are correlated, so the search may return several
		// candidates and the player disambiguates them with another structure
		assertTrue(found.contains(SEED), "candidates were " + found);
		assertTrue(found.size() < 100, "candidates were " + found);
	}
	
	@Test
	void threeObservationsFindNothingWhenTheSeedIsNotInTheRange()
	{
		List<Observation> observations = observations(SEED, VILLAGES, 3);
		List<SeedSearch.Target> targets =
			SeedSearch.targets(observations, SPREADS);
		
		List<Long> found =
			search(SEED + 1_000_000, SEED + 1_004_000, targets, 1, 10);
		
		assertTrue(found.isEmpty(), "unexpected candidates: " + found);
	}
	
	@Test
	void observationsOfDifferentSetsCombine()
	{
		ArrayList<Observation> observations = new ArrayList<>();
		observations.addAll(observations(SEED, VILLAGES, 1));
		observations.addAll(observations(SEED, SHIPWRECKS, 1));
		observations.addAll(observations(SEED, ANCIENT_CITIES, 1));
		
		List<SeedSearch.Target> targets =
			SeedSearch.targets(observations, SPREADS);
		
		assertEquals(3, targets.size());
		
		// the largest range has to be checked first, that is what keeps the
		// inner loop short
		assertEquals(26, targets.get(0).spread.range());
		
		List<Long> found = search(SEED - 500_000, SEED + 500_000, targets, 1,
			10);
		
		assertEquals(List.of(SEED), found);
	}
	
	@Test
	void severalThreadsFindTheSameSeed()
	{
		List<Observation> observations = observations(SEED, VILLAGES, 2);
		List<SeedSearch.Target> targets =
			SeedSearch.targets(observations, SPREADS);
		
		List<Long> single = search(SEED - 100_000, SEED + 100_000, targets, 1,
			100);
		List<Long> parallel =
			search(SEED - 100_000, SEED + 100_000, targets, 4, 100);
		
		assertEquals(single, parallel);
		assertTrue(parallel.contains(SEED), "candidates were " + parallel);
	}
	
	@Test
	void moreObservationsNarrowTheCandidates()
	{
		int maxResults = 5000;
		int radius = 1_000_000;
		
		List<Long> few = search(SEED - radius, SEED + radius,
			SeedSearch.targets(observations(SEED, VILLAGES, 2), SPREADS), 1,
			maxResults);
		List<Long> many = search(SEED - radius, SEED + radius,
			SeedSearch.targets(observations(SEED, VILLAGES, 8), SPREADS), 1,
			maxResults);
		
		assertTrue(few.contains(SEED), "two observations missed the seed");
		assertTrue(many.contains(SEED), "eight observations missed the seed");
		
		// this is the property the tool relies on: every additional structure the
		// player finds cuts the number of candidates down
		assertTrue(many.size() < few.size(),
			"eight observations gave " + many.size() + " candidates, two gave "
				+ few.size());
	}
	
	@Test
	void theProgressCounterReachesTheEndOfTheRange()
	{
		List<SeedSearch.Target> targets = SeedSearch
			.targets(observations(SEED, VILLAGES, 2), SPREADS);
		AtomicLong progress = new AtomicLong();
		
		SeedSearch.search(1000, 301_000, targets, 1, 10, progress, () -> false);
		
		assertTrue(progress.get() >= 300_000,
			"progress was only " + progress.get());
	}
	
	@Test
	void cancellationStopsTheSearch()
	{
		List<SeedSearch.Target> targets = SeedSearch
			.targets(observations(SEED, VILLAGES, 2), SPREADS);
		AtomicLong progress = new AtomicLong();
		
		SeedSearch.search(0, 50_000_000, targets, 1, 10, progress,
			() -> progress.get() > 0);
		
		assertTrue(progress.get() < 50_000_000,
			"the search should have stopped early, progress was "
				+ progress.get());
	}
	
	@Test
	void aSpreadWithoutRangeIsNotUsable()
	{
		assertFalse(BURIED_TREASURES.isUsable());
		assertTrue(VILLAGES.isUsable());
		
		// every chunk matches a buried treasure, so it must not become a target
		List<SeedSearch.Target> targets = SeedSearch
			.targets(List.of(Observation.ofBlock("minecraft:buried_treasures",
				0, 0), Observation.ofBlock("minecraft:villages", 0, 0)), SPREADS);
		
		assertEquals(1, targets.size());
		assertEquals("minecraft:villages", targets.get(0).spread.setId);
	}
	
	@Test
	void aChunkOutsideTheSpacingWindowNeverMatches()
	{
		// villages can only be offset by 0..25 chunks inside their region, so a
		// chunk 30 chunks into a region cannot be one
		int chunkX = 30;
		int chunkZ = 0;
		
		for(long seed = -1000; seed <= 1000; seed++)
			assertFalse(VILLAGES.matches(seed, chunkX, chunkZ));
	}
	
	@Test
	void theFastPathAgreesWithTheVanillaObservations()
	{
		// the observations were produced by the vanilla generator, so the
		// project's own implementation has to accept the seed they came from
		for(Observation observation : observations(SEED, VILLAGES, 3))
			assertTrue(VILLAGES.matches(SEED, observation.chunkX,
				observation.chunkZ));
		
		for(Observation observation : observations(SEED, SHIPWRECKS, 3))
			assertTrue(SHIPWRECKS.matches(SEED, observation.chunkX,
				observation.chunkZ));
	}
	
	@Test
	void observationsAreWorthAboutNineBitsEach()
	{
		// two offsets of a range of 26
		double one = Observation.bits(observations(SEED, VILLAGES, 1), SPREADS);
		assertTrue(one > 9.3D && one < 9.5D, "bits were " + one);
		
		double two = Observation.bits(observations(SEED, VILLAGES, 2), SPREADS);
		assertTrue(two > 18.7D && two < 18.9D, "bits were " + two);
		
		double six = Observation.bits(observations(SEED, VILLAGES, 6), SPREADS);
		assertTrue(six > 56.0D, "bits were " + six);
	}
	
	@Test
	void aFrequencyGateMakesAnObservationStronger()
	{
		StructureSpread gated = new StructureSpread("minecraft:gated", 34, 8,
			10387312, false, 0.2F,
			net.wurstclient.seed.structure.PlacementFrequency.DEFAULT);
		
		assertTrue(Observation.bits(List.of(new Observation("minecraft:gated",
			0, 0)), List.of(gated)) > 9.0D);
	}
	
	/**
	 * Produces observations the way the game would: the placement offset comes
	 * from the vanilla generator and the vanilla formula.
	 */
	private static List<Observation> observations(long levelSeed,
		StructureSpread spread, int count)
	{
		ArrayList<Observation> observations = new ArrayList<>();
		
		for(int region = 0; observations.size() < count && region < 64; region++)
		{
			int regionX = region % 8;
			int regionZ = region / 8;
			int[] chunk = potentialChunk(levelSeed, spread, regionX, regionZ);
			
			observations.add(new Observation(spread.setId, chunk[0], chunk[1]));
		}
		
		return observations;
	}
	
	@Test
	void theFastTargetAgreesWithTheSpread()
	{
		// the search uses the precomputed Target, the prediction uses the
		// spread - they must never disagree
		for(long seed = -400; seed <= 400; seed++)
			for(int chunkX = -70; chunkX <= 70; chunkX += 17)
				for(int chunkZ = -70; chunkZ <= 70; chunkZ += 23)
				{
					SeedSearch.Target target =
						new SeedSearch.Target(VILLAGES, chunkX, chunkZ);
					
					assertEquals(VILLAGES.matches(seed, chunkX, chunkZ),
						target.matches(seed), "seed " + seed + ", chunk "
							+ chunkX + "/" + chunkZ);
				}
	}
	
	@Test
	void theSearchIsFast()
	{
		List<SeedSearch.Target> targets = SeedSearch
			.targets(observations(SEED, VILLAGES, 3), SPREADS);
		long candidates = 20_000_000L;
		
		long start = System.nanoTime();
		search(0, candidates - 1, targets, 1, 10);
		long millis = (System.nanoTime() - start) / 1_000_000L;
		
		long perSecond = millis <= 0 ? Long.MAX_VALUE
			: candidates * 1000L / millis;
		
		System.out.println("seed search throughput: " + perSecond
			+ " candidates per second per thread (" + millis + " ms for "
			+ candidates + ")");
		
		// a very loose bound: this only has to catch a catastrophic
		// regression, the printed number is the interesting part
		assertTrue(perSecond > 2_000_000L,
			"only " + perSecond + " candidates per second");
	}
	
	/**
	 * The vanilla forward model: {@code getPotentialStructureChunk} plus the
	 * offset formula of {@code RandomSpreadType}.
	 */
	private static int[] potentialChunk(long levelSeed, StructureSpread spread,
		int regionX, int regionZ)
	{
		WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
		random.setLargeFeatureWithSalt(levelSeed, regionX, regionZ, spread.salt);
		
		int range = spread.range();
		int offsetX = spread.triangular
			? (random.nextInt(range) + random.nextInt(range)) / 2
			: random.nextInt(range);
		int offsetZ = spread.triangular
			? (random.nextInt(range) + random.nextInt(range)) / 2
			: random.nextInt(range);
		
		return new int[]{regionX * spread.spacing + offsetX,
			regionZ * spread.spacing + offsetZ};
	}
	
	private static List<Long> search(long from, long to,
		List<SeedSearch.Target> targets, int threads, int maxResults)
	{
		return SeedSearch.search(from, to, targets, threads, maxResults,
			new AtomicLong(), () -> false);
	}
}
