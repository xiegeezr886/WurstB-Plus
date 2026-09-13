/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.seed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.world.level.levelgen.LegacyRandomSource;

/**
 * Pins down the random number consumption of {@link OreCount} and
 * {@link OreHeight}.
 *
 * <p>
 * Ore prediction only matches the real world if every provider draws exactly
 * the same numbers, in the same order, with the same bounds as vanilla. The
 * vanilla providers cannot be used as a reference here (they need the built-in
 * registries), so these tests record the calls instead and compare them with
 * the formulas that were read from the 1.20.1 bytecode.
 */
final class OreSamplingTest
{
	private static final int MIN_Y = -64;
	private static final int MAX_Y = 319;
	
	@Test
	void aConstantCountDrawsNothing()
	{
		RecordingRandom random = new RecordingRandom();
		
		assertEquals(7, OreCount.constant(7).sample(random));
		assertTrue(random.bounds.isEmpty());
	}
	
	@Test
	void aUniformCountDrawsOneNumberUpToTheInclusiveRange()
	{
		RecordingRandom random = new RecordingRandom();
		
		// UniformInt#sample is Mth.nextInt(random, min, max), i.e.
		// nextInt(max - min + 1) + min.
		assertEquals(1, OreCount.uniform(1, 9).sample(random));
		assertEquals(List.of(9), random.bounds);
	}
	
	@Test
	void aConstantHeightDrawsNothingAndResolvesItsAnchor()
	{
		RecordingRandom random = new RecordingRandom();
		OreHeight height =
			OreHeight.constant(OreHeight.Anchor.belowTop(1));
		
		assertEquals(MAX_Y - 1, height.sample(random, MIN_Y, MAX_Y));
		assertTrue(random.bounds.isEmpty());
	}
	
	@Test
	void aUniformHeightDrawsOneNumberAcrossTheWholeRange()
	{
		RecordingRandom random = new RecordingRandom();
		OreHeight height = OreHeight.uniform(OreHeight.Anchor.absolute(-64),
			OreHeight.Anchor.absolute(16));
		
		assertEquals(-64, height.sample(random, MIN_Y, MAX_Y));
		// 16 - (-64) + 1
		assertEquals(List.of(81), random.bounds);
	}
	
	@Test
	void aTrapezoidHeightDrawsTheLargeBoundFirst()
	{
		RecordingRandom random = new RecordingRandom();
		OreHeight height = OreHeight.trapezoid(OreHeight.Anchor.absolute(-64),
			OreHeight.Anchor.absolute(16), 8);
		
		assertEquals(-64, height.sample(random, MIN_Y, MAX_Y));
		
		// range = 80, small = (80 - 8) / 2 = 36, large = 80 - 36 = 44, so
		// vanilla draws nextInt(0, 44) before nextInt(0, 36).
		assertEquals(List.of(45, 37), random.bounds);
	}
	
	@Test
	void aTrapezoidHeightWithoutRoomForASlopeDrawsOnce()
	{
		RecordingRandom random = new RecordingRandom();
		OreHeight height = OreHeight.trapezoid(OreHeight.Anchor.absolute(-64),
			OreHeight.Anchor.absolute(16), 100);
		
		assertEquals(-64, height.sample(random, MIN_Y, MAX_Y));
		assertEquals(List.of(81), random.bounds);
	}
	
	@Test
	void anEmptyRangeReturnsTheMinimumWithoutDrawing()
	{
		RecordingRandom random = new RecordingRandom();
		OreHeight height = OreHeight.uniform(OreHeight.Anchor.absolute(10),
			OreHeight.Anchor.absolute(0));
		
		assertEquals(10, height.sample(random, MIN_Y, MAX_Y));
		assertTrue(random.bounds.isEmpty());
	}
	
	@Test
	void anchorsResolveAgainstTheWorldHeight()
	{
		assertEquals(-60, OreHeight.Anchor.absolute(-60).resolve(MIN_Y, MAX_Y));
		assertEquals(-59, OreHeight.Anchor.aboveBottom(5).resolve(MIN_Y, MAX_Y));
		assertEquals(318, OreHeight.Anchor.belowTop(1).resolve(MIN_Y, MAX_Y));
	}
	
	@Test
	void anchorsFollowADifferentWorldHeight()
	{
		// the nether: 0 to 127
		assertEquals(5, OreHeight.Anchor.aboveBottom(5).resolve(0, 127));
		assertEquals(126, OreHeight.Anchor.belowTop(1).resolve(0, 127));
	}
	
	/**
	 * Records the bounds of every {@code nextInt(int)} call and always returns
	 * the lowest possible value, which makes the expected results readable.
	 */
	private static final class RecordingRandom extends LegacyRandomSource
	{
		private final List<Integer> bounds = new ArrayList<>();
		
		private RecordingRandom()
		{
			super(0L);
		}
		
		@Override
		public int nextInt(int bound)
		{
			bounds.add(bound);
			return 0;
		}
	}
}
