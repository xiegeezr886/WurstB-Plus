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

import org.junit.jupiter.api.Test;

/**
 * Checks the frequency reduction with rates, which is what it is for: a wrong
 * transcription still compiles, but it would make buried treasure appear in
 * every chunk or in none.
 *
 * <p>
 * The expected rates are the ones of the vanilla placements (buried treasure
 * uses 0.01, mineshafts 0.004), and the bands are wide enough to survive the
 * sampling of a fixed seed.
 */
final class PlacementFrequencyTest
{
	private static final long SEED = -4172144997902289642L;
	private static final int SALT = 10387320;
	// 16 columns * 1500 rows: the first legacy reducer packs its seed as
	// regionX ^ regionZ << 4, so the region x coordinate has to stay below 16
	// for all sampled regions to be distinct
	private static final int SAMPLES = 24000;
	
	@Test
	void aFrequencyOfOneNeverReduces()
	{
		for(int i = 0; i < 1000; i++)
			assertTrue(PlacementFrequency.passes(PlacementFrequency.DEFAULT,
				SEED, SALT, i, -i, 1.0F));
		
		for(int i = 0; i < 1000; i++)
			assertTrue(PlacementFrequency.passes(PlacementFrequency.LEGACY_TYPE_1,
				SEED, SALT, i, -i, 1.0F));
	}
	
	@Test
	void theDefaultReducerKeepsAboutOnePercentForBuriedTreasure()
	{
		int kept = count(PlacementFrequency.DEFAULT, 0.01F);
		
		assertTrue(kept > 150 && kept < 350,
			"expected about 240 of " + SAMPLES + " but kept " + kept);
	}
	
	@Test
	void theDefaultReducerKeepsAboutPointFourPercentForMineshafts()
	{
		int kept = count(PlacementFrequency.DEFAULT, 0.004F);
		
		assertTrue(kept > 40 && kept < 170,
			"expected about 96 of " + SAMPLES + " but kept " + kept);
	}
	
	@Test
	void theFirstLegacyReducerGroupsChunksIntoRegions()
	{
		int kept = count(PlacementFrequency.LEGACY_TYPE_1, 0.004F);
		
		// one in 250 regions passes, so the rate is the same as the default one
		assertTrue(kept > 40 && kept < 170,
			"expected about 96 of " + SAMPLES + " but kept " + kept);
		
		// the decision of the first legacy reducer only depends on the 16 * 16
		// chunk region, never on the chunk inside it
		boolean first = PlacementFrequency.passes(
			PlacementFrequency.LEGACY_TYPE_1, SEED, SALT, 40, 72, 0.004F);
		
		for(int x = 32; x < 48; x++)
			for(int z = 64; z < 80; z++)
				assertEquals(first, PlacementFrequency.passes(
					PlacementFrequency.LEGACY_TYPE_1, SEED, SALT, x, z,
					0.004F));
	}
	
	@Test
	void theOtherLegacyReducersKeepAboutHalf()
	{
		int second = count(PlacementFrequency.LEGACY_TYPE_2, 0.5F);
		int third = count(PlacementFrequency.LEGACY_TYPE_3, 0.5F);
		
		assertTrue(second > 10800 && second < 13200,
			"expected about 12000 but kept " + second);
		assertTrue(third > 10800 && third < 13200,
			"expected about 12000 but kept " + third);
	}
	
	@Test
	void theDecisionIsDeterministic()
	{
		for(int i = 0; i < 500; i++)
			assertEquals(
				PlacementFrequency.passes(PlacementFrequency.DEFAULT, SEED,
					SALT, i, -i, 0.01F),
				PlacementFrequency.passes(PlacementFrequency.DEFAULT, SEED,
					SALT, i, -i, 0.01F));
	}
	
	@Test
	void aDifferentSeedGivesADifferentPattern()
	{
		// not seed + 1: two LCG states that differ by one stay within about
		// 1502 / 2^24 of each other on the first draw, so they would agree
		// almost always
		long otherSeed = SEED ^ 0x9E3779B97F4A7C15L;
		int same = 0;
		
		for(int i = 0; i < 1000; i++)
			if(PlacementFrequency.passes(PlacementFrequency.DEFAULT, SEED,
				SALT, i, 0, 0.5F) == PlacementFrequency.passes(
					PlacementFrequency.DEFAULT, otherSeed, SALT, i, 0, 0.5F))
				same++;
		
		assertTrue(same < 600, "different seeds should disagree often");
	}
	
	@Test
	void reducerIdsAreMapped()
	{
		assertEquals(PlacementFrequency.LEGACY_TYPE_1,
			PlacementFrequency.fromId("minecraft:legacy_type_1"));
		assertEquals(PlacementFrequency.LEGACY_TYPE_1,
			PlacementFrequency.fromId("legacy_type_1"));
		assertEquals(PlacementFrequency.LEGACY_TYPE_2,
			PlacementFrequency.fromId("minecraft:legacy_type_2"));
		assertEquals(PlacementFrequency.LEGACY_TYPE_3,
			PlacementFrequency.fromId("minecraft:legacy_type_3"));
		assertEquals(PlacementFrequency.DEFAULT,
			PlacementFrequency.fromId("minecraft:default"));
		assertEquals(PlacementFrequency.DEFAULT,
			PlacementFrequency.fromId(null));
		assertEquals(PlacementFrequency.DEFAULT,
			PlacementFrequency.fromId("something_else"));
	}
	
	@Test
	void aVeryLowFrequencyStillKeepsSomething()
	{
		int kept = count(PlacementFrequency.DEFAULT, 0.2F);
		
		assertFalse(kept == 0);
		assertTrue(kept > 4000 && kept < 5600,
			"expected about 4800 but kept " + kept);
	}
	
	/**
	 * Samples one chunk per 16 * 16 chunk region, so that the first legacy
	 * reducer - which decides per region - gets independent samples.
	 */
	private static int count(int method, float frequency)
	{
		int kept = 0;
		
		for(int i = 0; i < SAMPLES; i++)
		{
			int x = i % 16 * 16 + 3;
			int z = i / 16 * 16 + 7;
			
			if(PlacementFrequency.passes(method, SEED, SALT, x, z, frequency))
				kept++;
		}
		
		return kept;
	}
}
