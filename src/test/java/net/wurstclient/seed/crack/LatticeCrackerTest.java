package net.wurstclient.seed.crack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.wurstclient.seed.crack.LatticeCracker.Constraint;
import net.wurstclient.seed.crack.LatticeCracker.Result;

/**
 * Closed loop proof for {@link LatticeCracker}: the observations are generated
 * with the vanilla {@link LegacyRandomSource}, the cracker has to recover the
 * seed they came from, and every returned seed is replayed through the vanilla
 * generator again.
 *
 * <p>
 * The vanilla generator is the only Minecraft class used here on purpose.
 * Bootstrap is not started, because that hangs in the unit test environment.
 */
final class LatticeCrackerTest
{
	private static final long A = 25214903917L;
	
	private static final long C = 11L;
	
	private static final long MASK48 = 281474976710655L;
	
	/**
	 * Realistic structure sets, as {@code spacing}, {@code separation} and
	 * {@code salt}. The resulting ranges are 26 (villages), 20 (shipwrecks),
	 * 12 (ocean ruins), 24 (temples, outposts), 16 (ancient cities, a power of
	 * two), 26 again, 25 (ruined portals, odd) and 9 (odd).
	 */
	private static final int[][] SETS = {{34, 8, 10387312},
		{24, 4, 165745295}, {20, 8, 14357617}, {32, 8, 14357617},
		{24, 8, 20083232}, {34, 8, 83469867}, {40, 15, 34222645},
		{20, 11, 10387313}};
	
	/**
	 * The {@code nextInt} semantics the cracker models must be the vanilla
	 * ones, including the fast path for power-of-two bounds.
	 */
	@Test
	void nextIntModelMatchesVanilla()
	{
		Random random = new Random(12345L);
		
		for(int i = 0; i < 500; i++)
		{
			long seed = random.nextLong() & MASK48;
			int range = 2 + random.nextInt(63);
			
			LegacyRandomSource vanilla = new LegacyRandomSource(0L);
			vanilla.setSeed(seed);
			int expected = vanilla.nextInt(range);
			
			long state = (seed ^ A) & MASK48;
			state = (A * state + C) & MASK48;
			int bits = (int)(state >>> 17);
			int model = LatticeCracker.nextIntValue(range, bits);
			
			assertEquals(expected, model,
				"nextInt(" + range + ") for seed " + seed);
		}
	}
	
	/**
	 * One observation, i.e. two constraints, carries about 9 bits, so it cannot
	 * pin a 48 bit seed down. The contract is that the returned seed really
	 * reproduces the observation.
	 */
	@Test
	void oneObservationProducesAValidSeed()
	{
		Random random = new Random(101L);
		long levelSeed = random.nextLong() & MASK48;
		ArrayList<Constraint> constraints =
			observations(levelSeed, new int[]{0}, random);
		
		assertEquals(2, constraints.size());
		
		Result result = LatticeCracker.crack(constraints, 30000L);
		
		assertTrue(result.success, result.detail);
		assertTrue(replaysVanilla(result.seed, constraints),
			"returned seed " + result.seed + " must reproduce the"
				+ " observation");
	}
	
	/** Two observations, four constraints, covering ranges 26 and 20. */
	@Test
	void twoObservationsProduceAValidSeed()
	{
		Random random = new Random(202L);
		long levelSeed = random.nextLong() & MASK48;
		ArrayList<Constraint> constraints =
			observations(levelSeed, new int[]{0, 1}, random);
		
		assertEquals(4, constraints.size());
		
		Result result = LatticeCracker.crack(constraints, 30000L);
		
		assertTrue(result.success, result.detail);
		assertTrue(replaysVanilla(result.seed, constraints));
	}
	
	/** Three observations, six constraints, including a power-of-two range. */
	@Test
	void threeObservationsProduceAValidSeed()
	{
		Random random = new Random(303L);
		long levelSeed = random.nextLong() & MASK48;
		ArrayList<Constraint> constraints =
			observations(levelSeed, new int[]{0, 1, 4}, random);
		
		assertEquals(6, constraints.size());
		
		Result result = LatticeCracker.crack(constraints, 30000L);
		
		assertTrue(result.success, result.detail);
		assertTrue(replaysVanilla(result.seed, constraints));
	}
	
	/** Four observations, eight constraints, adding the odd range 25. */
	@Test
	void fourObservationsProduceAValidSeed()
	{
		Random random = new Random(404L);
		long levelSeed = random.nextLong() & MASK48;
		ArrayList<Constraint> constraints =
			observations(levelSeed, new int[]{0, 1, 4, 6}, random);
		
		assertEquals(8, constraints.size());
		
		Result result = LatticeCracker.crack(constraints, 30000L);
		
		assertTrue(result.success, result.detail);
		assertTrue(replaysVanilla(result.seed, constraints));
	}
	
	/**
	 * Eight observations are worth about 66 bits, so the seed is unique and the
	 * cracker has to return exactly the seed the observations came from.
	 */
	@Test
	void eightObservationsRecoverTheExactSeed()
	{
		Random random = new Random(505L);
		long levelSeed = random.nextLong() & MASK48;
		ArrayList<Constraint> constraints = observations(levelSeed,
			new int[]{0, 1, 2, 3, 4, 5, 1, 4}, random);
		
		assertEquals(16, constraints.size());
		
		Result result = LatticeCracker.crack(constraints, 30000L);
		
		assertTrue(result.success, result.detail);
		assertEquals(levelSeed, result.seed, result.detail);
		assertTrue(replaysVanilla(result.seed, constraints));
	}
	
	/**
	 * The same, but with the odd ranges 25 and 9 in the set. Those have no
	 * power-of-two factor, so they contribute no shortcut, only constraints.
	 */
	@Test
	void oddRangesAreSupported()
	{
		Random random = new Random(606L);
		long levelSeed = random.nextLong() & MASK48;
		ArrayList<Constraint> constraints = observations(levelSeed,
			new int[]{0, 1, 2, 3, 4, 5, 6, 7}, random);
		
		assertTrue(containsRange(constraints, 16), "power of two range needed");
		assertTrue(containsRange(constraints, 9), "odd range needed");
		assertTrue(containsRange(constraints, 25), "odd range needed");
		
		Result result = LatticeCracker.crack(constraints, 30000L);
		
		assertTrue(result.success, result.detail);
		assertEquals(levelSeed, result.seed, result.detail);
		assertTrue(replaysVanilla(result.seed, constraints));
	}
	
	/**
	 * A single wrong offset must not produce a seed: the search has to exhaust
	 * the space and report the failure instead of guessing.
	 */
	@Test
	void unsatisfiableConstraintsAreRejected()
	{
		Random random = new Random(707L);
		long levelSeed = random.nextLong() & MASK48;
		ArrayList<Constraint> constraints = observations(levelSeed,
			new int[]{0, 1, 2, 3, 4, 5, 6, 7}, random);
		
		Constraint broken = constraints.get(2);
		constraints.set(2, new Constraint(broken.base, broken.draw,
			broken.range, (broken.want + 1) % broken.range));
		
		Result result = LatticeCracker.crack(constraints, 30000L);
		
		assertFalse(result.success, result.detail);
		assertEquals(-1L, result.seed);
		assertEquals(0, result.candidates);
		assertTrue(result.detail.contains("no seed"), result.detail);
	}
	
	/**
	 * A set that is strong but has no power-of-two factor anywhere is the
	 * documented worst case. The cracker must respect the timeout and say so,
	 * rather than hanging or inventing an answer.
	 */
	@Test
	void timeoutIsRespectedOnAHardSet()
	{
		Random random = new Random(808L);
		long levelSeed = random.nextLong() & MASK48;
		ArrayList<Constraint> constraints = new ArrayList<>();
		
		for(int i = 0; i < 7; i++)
		{
			int[] set = {40, 15, 34222645 + 7 * i};
			Observation observation = observe(levelSeed, set,
				random.nextInt(2000) + 3 * i,
				random.nextInt(2000) - 1000 - 2 * i);
			constraints.addAll(CrackInput.constraintsFor(set[0], set[1],
				set[2], observation.chunkX, observation.chunkZ));
		}
		
		assertFalse(containsRange(constraints, 16));
		
		long start = System.currentTimeMillis();
		Result result = LatticeCracker.crack(constraints, 3000L);
		long elapsed = System.currentTimeMillis() - start;
		
		assertTrue(elapsed < 6000, "took " + elapsed + "ms");
		
		if(result.success)
			assertTrue(replaysVanilla(result.seed, constraints));
		else
			assertTrue(result.detail.contains("timed out"), result.detail);
	}
	
	/** Unsupported input is reported, never silently answered. */
	@Test
	void invalidInputIsRejected()
	{
		Result empty = LatticeCracker.crack(Collections.emptyList(), 1000L);
		assertFalse(empty.success);
		assertNotNull(empty.detail);
		
		Result tooWide = LatticeCracker.crack(
			Collections.singletonList(new Constraint(0L, 0, 100, 0)), 1000L);
		assertFalse(tooWide.success);
		assertTrue(tooWide.detail.contains("range 100"), tooWide.detail);
		
		Result badWant = LatticeCracker.crack(
			Collections.singletonList(new Constraint(0L, 0, 26, 26)), 1000L);
		assertFalse(badWant.success);
		assertTrue(badWant.detail.contains("want 26"), badWant.detail);
		
		Result badDraw = LatticeCracker.crack(
			Collections.singletonList(new Constraint(0L, 99, 26, 0)), 1000L);
		assertFalse(badDraw.success);
		assertTrue(badDraw.detail.contains("draw 99"), badDraw.detail);
	}
	
	/**
	 * The cracker is meant to be callable from a background thread, so two
	 * searches must be able to run at the same time without sharing state.
	 */
	@Test
	void concurrentSearchesDoNotInterfere() throws Exception
	{
		Random random = new Random(909L);
		long firstSeed = random.nextLong() & MASK48;
		long secondSeed = random.nextLong() & MASK48;
		ArrayList<Constraint> first = observations(firstSeed,
			new int[]{0, 1, 2, 3, 4, 5, 6, 7}, random);
		ArrayList<Constraint> second = observations(secondSeed,
			new int[]{7, 6, 5, 4, 3, 2, 1, 0}, random);
		
		AtomicLong firstResult = new AtomicLong();
		AtomicLong secondResult = new AtomicLong();
		
		Thread firstThread = new Thread(() -> firstResult.set(
			LatticeCracker.crack(first, 30000L).seed));
		Thread secondThread = new Thread(() -> secondResult.set(
			LatticeCracker.crack(second, 30000L).seed));
		
		firstThread.start();
		secondThread.start();
		firstThread.join(60000);
		secondThread.join(60000);
		
		assertEquals(firstSeed, firstResult.get());
		assertEquals(secondSeed, secondResult.get());
	}
	
	/** The chunk helper has to agree with the vanilla placement. */
	@Test
	void structureChunkMatchesTheVanillaPlacement()
	{
		long levelSeed = 0x123456789ABCL;
		int[] set = SETS[0];
		
		for(int i = 0; i < 20; i++)
		{
			int regionX = i * 7 - 40;
			int regionZ = 13 - i * 3;
			
			int[] expected = placedChunk(levelSeed, set, regionX, regionZ);
			int[] actual = CrackInput.structureChunk(set[0], set[1], set[2],
				levelSeed, regionX, regionZ);
			
			assertTrue(Arrays.equals(expected, actual),
				"region " + regionX + ", " + regionZ);
		}
	}
	
	/** The observation helper has to build the constraints the cracker wants. */
	@Test
	void crackInputBuildsTwoConstraints()
	{
		Random random = new Random(111L);
		long levelSeed = random.nextLong() & MASK48;
		Observation observation = observe(levelSeed, SETS[0], 5, -3);
		List<Constraint> constraints = CrackInput.constraintsFor(SETS[0][0],
			SETS[0][1], SETS[0][2], observation.chunkX, observation.chunkZ);
		
		assertEquals(2, constraints.size());
		assertEquals(26, constraints.get(0).range);
		assertEquals(observation.wantX, constraints.get(0).want);
		assertEquals(0, constraints.get(0).draw);
		assertEquals(observation.wantZ, constraints.get(1).want);
		assertEquals(1, constraints.get(1).draw);
		assertEquals(constraints.get(0).base, constraints.get(1).base);
		
		// a triangular placement is not supported and must say so
		assertTrue(CrackInput
			.constraintsFor(SETS[0][0], SETS[0][1], SETS[0][2],
				observation.chunkX, observation.chunkZ, true)
			.isEmpty());
	}
	
	/* ---------------- helpers ---------------- */
	
	/** One structure, placed by the vanilla generator. */
	private static final class Observation
	{
		final int chunkX;
		final int chunkZ;
		final int wantX;
		final int wantZ;
		
		Observation(int chunkX, int chunkZ, int wantX, int wantZ)
		{
			this.chunkX = chunkX;
			this.chunkZ = chunkZ;
			this.wantX = wantX;
			this.wantZ = wantZ;
		}
	}
	
	private static Observation observe(long levelSeed, int[] set, int regionX,
		int regionZ)
	{
		int range = set[0] - set[1];
		long base = (long)regionX * 341873128712L
			+ (long)regionZ * 132897987541L + set[2];
		
		LegacyRandomSource random = new LegacyRandomSource(0L);
		random.setSeed(levelSeed + base);
		int wantX = random.nextInt(range);
		int wantZ = random.nextInt(range);
		
		return new Observation(regionX * set[0] + wantX,
			regionZ * set[0] + wantZ, wantX, wantZ);
	}
	
	/** The chunk vanilla would place this structure in. */
	private static int[] placedChunk(long levelSeed, int[] set, int regionX,
		int regionZ)
	{
		Observation observation = observe(levelSeed, set, regionX, regionZ);
		return new int[]{observation.chunkX, observation.chunkZ};
	}
	
	/**
	 * Builds the constraints of several observations, each in its own region
	 * so that no stream repeats.
	 */
	private static ArrayList<Constraint> observations(long levelSeed,
		int[] sets, Random random)
	{
		ArrayList<Constraint> constraints = new ArrayList<>();
		
		for(int i = 0; i < sets.length; i++)
		{
			int[] set = SETS[sets[i]];
			Observation observation = observe(levelSeed, set,
				random.nextInt(2000) + 7 * i,
				random.nextInt(2000) - 1000 - 5 * i);
			constraints.addAll(CrackInput.constraintsFor(set[0], set[1],
				set[2], observation.chunkX, observation.chunkZ));
		}
		
		return constraints;
	}
	
	/** Replays the constraints through the vanilla generator. */
	private static boolean replaysVanilla(long levelSeed,
		List<Constraint> constraints)
	{
		for(Constraint constraint : constraints)
		{
			LegacyRandomSource random = new LegacyRandomSource(0L);
			random.setSeed(levelSeed + constraint.base);
			int value = 0;
			
			for(int draw = 0; draw <= constraint.draw; draw++)
				value = random.nextInt(constraint.range);
			
			if(value != constraint.want)
				return false;
		}
		
		return true;
	}
	
	private static boolean containsRange(List<Constraint> constraints,
		int range)
	{
		for(Constraint constraint : constraints)
			if(constraint.range == range)
				return true;
		
		return false;
	}
}
