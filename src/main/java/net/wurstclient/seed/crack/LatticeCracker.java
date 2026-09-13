/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.seed.crack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Recovers a 48 bit level seed from structure observations from scratch, i.e.
 * without being given a range of seeds to try.
 *
 * <h2>The constraint</h2>
 *
 * {@code RandomSpreadStructurePlacement#getPotentialStructureChunk} seeds a
 * {@code LegacyRandomSource} with
 * {@code regionX * 341873128712L + regionZ * 132897987541L + levelSeed + salt}
 * and then calls {@code nextInt(spacing - separation)} twice. Since
 * {@code setSeed} computes {@code state = (seed ^ 25214903917L) & MASK48}, such
 * a stream starts at {@code state0 = ((levelSeed + base) & MASK48) ^ a}, where
 * {@code base} is everything except the level seed. One {@link Constraint} is
 * therefore "the {@code draw}-th {@code next(31)} of that stream, turned into a
 * {@code nextInt(range)} result, equals {@code want}".
 *
 * <h2>How it is solved</h2>
 *
 * Write the 48 bit state as {@code state[n] = 2^17 * b[n] + e[n]}, where
 * {@code b[n] = state[n] >>> 17} is the raw draw and {@code e[n]} its low 17
 * bits. Then
 *
 * <pre>
 * e[n + 1] = (a * e[n] + c) mod 2^17
 * Q[n]     = (a * e[n] + c) >>> 17
 * b[n + 1] = (a * b[n] + Q[n]) mod 2^31
 * </pre>
 *
 * so the low 17 bits of every stream depend only on {@code e[0]}, which is a
 * function of {@code levelSeed mod 2^17} alone, and the draws obey an affine
 * recurrence modulo 2^31 whose additive constants {@code Q[n]} are fixed once
 * that low part is known.
 *
 * <p>
 * The draws are split again, {@code b[n] = 2^18 * u[n] + v[n]}, which turns
 * every constraint into one of three simple shapes:
 *
 * <ul>
 * <li>the even part of a non power-of-two bound pins {@code v[n]}, hence the
 * middle bits of the seed, by a congruence modulo a power of two;</li>
 * <li>the odd part of a non power-of-two bound becomes
 * {@code u[n] = (a^n * mu + const) mod 2^13} inside an arithmetic progression,
 * where {@code mu} is the top 13 bits of the seed;</li>
 * <li>a power-of-two bound becomes an interval, because vanilla then takes the
 * fast path {@code (int)((long)bound * (long)next(31) >> 31)} and only the high
 * bits of the draw are observable.</li>
 * </ul>
 *
 * Every constraint is therefore an arithmetic progression in {@code mu}, and
 * intersecting those progressions is a small CRT problem in {@code Z/2^13}
 * that leaves at most a handful of candidates. Each candidate is then replayed
 * through the vanilla LCG, so a returned seed is never a guess.
 *
 * <p>
 * The 17 bit low part is compressed first, without touching 2^48:
 *
 * <ul>
 * <li>for a pair of draws {@code b1 = nextInt(r1)} and {@code b2 = nextInt(r2)}
 * of the same stream the recurrence gives
 * {@code b2 = a * b1 + Q[1] - 2^31 * m}, which only has a solution when
 * {@code Q[1] = want2 - a * want1 (mod 2^min(v2(r1), v2(r2)))}. Since
 * {@code Q[1]} depends only on {@code e[1]}, that is a bitmap over the 2^17 low
 * parts, filled in one sweep;</li>
 * <li>the even parts of all bounds fix the middle bits of the seed modulo a
 * power of two for every low part, so a low part survives only if all of those
 * residues agree with each other.</li>
 * </ul>
 *
 * The remaining work is a loop over "low part x middle bits" pairs, each
 * solving the 13 bit progression intersection. It is embarrassingly parallel
 * and is spread over a small pool of worker threads.
 *
 * <p>
 * This is an exact attack that never walks 2^48 candidates: it is a
 * lattice/CRT style decomposition (bit blocks instead of an LLL basis, coset
 * intersection instead of Babai rounding). The classic LLL formulation is not
 * needed here because the observations expose modular residues of the draws
 * rather than their high bits.
 *
 * <h2>Limits</h2>
 *
 * Ranges must be between 2 and 64 and draw indices up to 32 are supported. The
 * {@code nextInt} rejection loop is ignored: it only triggers with probability
 * around {@code bound / 2^31}, and if it ever triggered, the observed value
 * would not be the first draw of the stream at all.
 *
 * <p>
 * How fast this is depends on how much of each bound is even, because that is
 * what compresses the low and middle bits of the seed: ranges like 26
 * (villages), 20 (shipwrecks), 24 (temples, outposts) or 16 (ancient cities)
 * give 1, 2, 3 and 4 bits each per draw, while odd ranges like 25 or 9 give
 * none. A set of only odd or only power-of-two ranges therefore degenerates to
 * a wide sweep, and {@link #crack} reports the timeout honestly instead of
 * pretending to have an answer.
 *
 * <p>
 * Instances are stateless, all mutable state lives inside a per-call worker, so
 * {@link #crack} can be called from any thread, including several at once.
 */
public final class LatticeCracker
{
	/** The multiplier of {@code java.util.Random}'s LCG. */
	static final long A = 25214903917L;
	
	/** The addend of {@code java.util.Random}'s LCG. */
	static final long C = 11L;
	
	/** Mask for the 48 bit LCG state. */
	static final long MASK48 = 281474976710655L;
	
	/** Mask for a 31 bit draw. */
	static final long MASK31 = 2147483647L;
	
	/** Number of low state bits that never reach a draw directly. */
	private static final int LOW_BITS = 17;
	
	private static final int LOW_MOD = 1 << LOW_BITS;
	
	private static final int LOW_MASK = LOW_MOD - 1;
	
	/** Number of low bits of a draw that are kept in {@code v[n]}. */
	private static final int MID_BITS = 18;
	
	private static final int MID_MOD = 1 << MID_BITS;
	
	private static final int MID_MASK = MID_MOD - 1;
	
	/** Number of high bits of a draw that are kept in {@code u[n]}. */
	private static final int HIGH_BITS = 13;
	
	private static final int HIGH_MOD = 1 << HIGH_BITS;
	
	private static final int HIGH_MASK = HIGH_MOD - 1;
	
	/** {@code a mod 2^17}, the part of {@code a} that XORs into the low bits. */
	private static final int A_LO = (int)(A & LOW_MASK);
	
	/** {@code a >>> 17}, the part of {@code a} that XORs into the high bits. */
	private static final int A_HI = (int)(A >>> LOW_BITS);
	
	/** {@code a mod 2^13}, the multiplier of the low 13 bit chain. */
	private static final int A13 = (int)(A & HIGH_MASK);
	
	/** {@code a mod 2^6}, used for the modular pin of the middle bits. */
	private static final int A6 = (int)(A & 63);
	
	/** Largest supported {@code nextInt} bound. */
	public static final int MAX_RANGE = 64;
	
	/** Largest supported draw index. */
	static final int MAX_DRAW = 32;
	
	/** Upper bound on how many candidates are collected. */
	static final int MAX_CANDIDATES = 16;
	
	/** Used when the caller passes a non-positive timeout. */
	static final long DEFAULT_TIMEOUT_MILLIS = 30000L;
	
	/** Worker threads used by one search. */
	private static final int MAX_WORKERS = 8;
	
	/**
	 * Above this many low parts the search stops after the first candidates
	 * instead of mapping the whole candidate set.
	 */
	private static final long FULL_SCAN_LIMIT = 1L << 16;
	
	private static final long PIN_CHECK_INTERVAL = 0x3FFF;
	
	private static final long CHECK_INTERVAL = 0xFFF;
	
	/** Inverses modulo 2^13, 0 for even values. */
	private static final int[] INVERSE_13 = inverseTable();
	
	private LatticeCracker()
	{
		
	}
	
	/**
	 * One observation: the {@code draw}-th {@code next(31)} of the stream
	 * seeded with {@code levelSeed + base}, turned into a
	 * {@code nextInt(range)} result, must equal {@code want}.
	 */
	public static final class Constraint
	{
		/** The LCG seed of this stream is {@code levelSeed + base}. */
		public final long base;
		
		/** 0 for the first {@code next(31)}, 1 for the second, and so on. */
		public final int draw;
		
		/** The {@code nextInt} bound, 2 to 64. */
		public final int range;
		
		/**
		 * The value {@code nextInt(range)} returned. For a power-of-two range
		 * that is the fast path result
		 * {@code (int)((long)range * (long)next(31) >> 31)}, otherwise it is
		 * {@code next(31) % range}.
		 */
		public final int want;
		
		public Constraint(long base, int draw, int range, int want)
		{
			this.base = base;
			this.draw = draw;
			this.range = range;
			this.want = want;
		}
		
		@Override
		public String toString()
		{
			return "draw " + draw + " of base " + base + " == " + want
				+ " (range " + range + ")";
		}
	}
	
	/** Outcome of a {@link #crack} call. */
	public static final class Result
	{
		/** Whether at least one seed reproducing every constraint was found. */
		public final boolean success;
		
		/** The recovered level seed, or -1 if nothing was found. */
		public final long seed;
		
		/** How many candidates were collected, capped by the search. */
		public final int candidates;
		
		/** Wall clock time of the search. */
		public final long millis;
		
		/** Human readable explanation of the algorithm path. */
		public final String detail;
		
		private Result(boolean success, long seed, int candidates, long millis,
			String detail)
		{
			this.success = success;
			this.seed = seed;
			this.candidates = candidates;
			this.millis = millis;
			this.detail = detail;
		}
		
		@Override
		public String toString()
		{
			return (success ? "cracked seed " + seed : "failed") + " in "
				+ millis + "ms (" + candidates + " candidate(s)): " + detail;
		}
	}
	
	/**
	 * Recovers the level seed behind the given constraints.
	 *
	 * @param constraints
	 *            the observations, at least one, best several
	 * @param timeoutMillis
	 *            upper bound on the search time; a non-positive value selects
	 *            {@link #DEFAULT_TIMEOUT_MILLIS}
	 * @return the outcome, never null, never a guess: a successful result has
	 *         been verified against the vanilla LCG
	 */
	public static Result crack(List<Constraint> constraints,
		long timeoutMillis)
	{
		long start = System.currentTimeMillis();
		long timeout =
			timeoutMillis > 0 ? timeoutMillis : DEFAULT_TIMEOUT_MILLIS;
		
		if(constraints == null || constraints.isEmpty())
			return fail(start, "no constraints given");
		
		for(Constraint constraint : constraints)
		{
			if(constraint.range < 2 || constraint.range > MAX_RANGE)
				return fail(start, "range " + constraint.range
					+ " is outside the supported 2.." + MAX_RANGE);
			
			if(constraint.draw < 0 || constraint.draw > MAX_DRAW)
				return fail(start, "draw " + constraint.draw
					+ " is outside the supported 0.." + MAX_DRAW);
			
			if(constraint.want < 0 || constraint.want >= constraint.range)
				return fail(start, "want " + constraint.want
					+ " cannot be a nextInt result for range "
					+ constraint.range);
		}
		
		Model model = new Model(constraints);
		long deadline = start + timeout;
		
		// ---- pin the low 17 bits of the seed ----
		int pinned = 0;
		long[] allowedLow = null;
		
		for(int stream = 0; stream < model.streamCount; stream++)
		{
			long[] mask = model.pinLowBits(stream, deadline);
			
			if(mask == null)
				continue;
			
			if(!anySet(mask))
				return fail(start, "the two draws of base "
					+ model.streamBase[stream] + " cannot come from the same"
					+ " stream: no low part of any seed makes both of them"
					+ " hit, so the observations are inconsistent");
			
			pinned++;
			allowedLow =
				allowedLow == null ? mask : intersect(allowedLow, mask);
		}
		
		if(allowedLow != null && !anySet(allowedLow))
			return fail(start, "the observations contradict each other: the"
				+ " modular pin of their second draws leaves no common low"
				+ " part for the seed");
		
		int lowCandidates =
			allowedLow == null ? LOW_MOD : countSet(allowedLow);
		long space = (long)lowCandidates * MID_MOD / model.nuModEstimate;
		boolean wide = space > FULL_SCAN_LIMIT;
		
		// ---- sweep the middle bits for every surviving low part ----
		final long[] lowMask = allowedLow;
		AtomicInteger next = new AtomicInteger();
		AtomicInteger stop = new AtomicInteger();
		AtomicLong pairs = new AtomicLong();
		ArrayList<Long> hits = new ArrayList<>();
		AtomicInteger found = new AtomicInteger();
		
		Runnable job = () -> {
			Worker worker = new Worker(model, lowMask, deadline, wide, next,
				stop, found, pairs, hits, constraints);
			worker.run();
		};
		
		int workers = Math.min(MAX_WORKERS,
			Math.max(1, Runtime.getRuntime().availableProcessors()));
		
		if(workers == 1)
			job.run();
		else
		{
			Thread[] threads = new Thread[workers];
			
			for(int i = 0; i < workers; i++)
			{
				threads[i] = new Thread(job, "Wurst seed cracker " + i);
				threads[i].setDaemon(true);
				threads[i].start();
			}
			
			for(Thread thread : threads)
				try
				{
					thread.join();
				}catch(InterruptedException e)
				{
					Thread.currentThread().interrupt();
					break;
				}
		}
		
		long millis = System.currentTimeMillis() - start;
		boolean timedOut = stop.get() == 2;
		boolean complete = stop.get() == 0;
		int hitsCount;
		long seed;
		
		synchronized(hits)
		{
			hitsCount = hits.size();
			
			if(hitsCount == 0)
				seed = -1;
			else
			{
				long[] sorted = new long[hitsCount];
				
				for(int i = 0; i < hitsCount; i++)
					sorted[i] = hits.get(i).longValue();
				
				Arrays.sort(sorted);
				seed = sorted[0];
			}
		}
		
		if(hitsCount > MAX_CANDIDATES)
			hitsCount = MAX_CANDIDATES;
		
		StringBuilder detail = new StringBuilder();
		
		if(pinned > 0)
			detail.append("pinned the low 17 bits to ").append(lowCandidates)
				.append('/').append(LOW_MOD).append(" from ").append(pinned)
				.append(" observation(s)");
		else
			detail.append("no two-draw modular pin available, swept all ")
				.append(LOW_MOD).append(" low parts");
		
		detail.append("; scanned ").append(pairs.get())
			.append(" (low, middle) pairs with ").append(workers)
			.append(" worker(s)");
		
		if(hitsCount > 0)
		{
			detail.append("; verified the first candidate against all ")
				.append(constraints.size()).append(" constraint(s)");
			detail.append(complete ? "; whole space scanned"
				: "; stopped early");
			
			if(timedOut)
				detail.append("; timeout hit, candidate list may be"
					+ " incomplete");
		}else if(timedOut)
			detail.append("; timed out after ").append(millis)
				.append("ms of ").append(timeout).append("ms - no seed found")
				.append(" so far");
		else
			detail.append("; whole space scanned, no seed reproduces every")
				.append(" constraint");
		
		return new Result(hitsCount > 0, seed, hitsCount, millis,
			detail.toString());
	}
	
	/**
	 * The value {@code nextInt(range)} returns for a raw {@code next(31)}
	 * result, exactly as vanilla computes it. The rejection loop of
	 * {@code nextInt} is ignored, see the class documentation.
	 */
	static int nextIntValue(int range, int bits)
	{
		if((range & range - 1) == 0)
			return (int)(range * (long)bits >> 31);
		
		return bits % range;
	}
	
	/**
	 * @return whether the given level seed reproduces every constraint.
	 */
	static boolean reproduces(long levelSeed, List<Constraint> constraints)
	{
		for(Constraint constraint : constraints)
		{
			long state = (levelSeed + constraint.base & MASK48) ^ A;
			
			for(int draw = 0; draw <= constraint.draw; draw++)
			{
				state = (A * state + C) & MASK48;
				
				if(draw < constraint.draw)
					continue;
				
				if(nextIntValue(constraint.range, (int)(state >>> LOW_BITS))
					!= constraint.want)
					return false;
			}
		}
		
		return true;
	}
	
	private static Result fail(long start, String detail)
	{
		return new Result(false, -1, 0, System.currentTimeMillis() - start,
			detail);
	}
	
	private static boolean anySet(long[] bits)
	{
		for(long word : bits)
			if(word != 0)
				return true;
		
		return false;
	}
	
	private static int countSet(long[] bits)
	{
		int total = 0;
		
		for(long word : bits)
			total += Long.bitCount(word);
		
		return total;
	}
	
	private static long[] intersect(long[] first, long[] second)
	{
		long[] result = new long[first.length];
		
		for(int i = 0; i < first.length; i++)
			result[i] = first[i] & second[i];
		
		return result;
	}
	
	private static int[] inverseTable()
	{
		int[] table = new int[HIGH_MOD];
		
		for(int value = 1; value < HIGH_MOD; value += 2)
		{
			int inverse = value;
			
			for(int i = 0; i < 4; i++)
				inverse *= 2 - value * inverse;
			
			table[value] = inverse & HIGH_MASK;
		}
		
		return table;
	}
	
	/** Inverse of a small odd value, or 0 if it does not exist. */
	private static int inverse(int value, int modulus)
	{
		int reduced = Math.floorMod(value, modulus);
		
		for(int candidate = 1; candidate < modulus; candidate++)
			if(reduced * candidate % modulus == 1)
				return candidate;
		
		return 0;
	}
	
	private static int gcd(int first, int second)
	{
		while(second != 0)
		{
			int next = first % second;
			first = second;
			second = next;
		}
		
		return first;
	}
	
	/**
	 * Everything that only depends on the constraints: stream bookkeeping plus
	 * the per-constraint constants of its progression. Immutable, so all
	 * workers can share it.
	 */
	private static final class Model
	{
		final Constraint[] constraints;
		final int count;
		
		/** Stream index of each constraint. */
		final int[] streamOf;
		
		/** State index of each constraint, i.e. {@code draw + 1}. */
		final int[] stateOf;
		
		final int[] range;
		final int[] want;
		
		/** Whether the bound is a power of two, i.e. an interval. */
		final boolean[] interval;
		
		/** Number of trailing zero bits of the bound. */
		final int[] alpha;
		
		/** Odd part of the bound, 1 for a power of two. */
		final int[] odd;
		
		/** Inverse of {@code 2^18} modulo {@code odd}. */
		final int[] invTwo;
		
		/** {@code a^n mod 2^13} and its inverse. */
		final int[] power;
		final int[] invPower;
		
		/** Step of the progression in {@code mu} space and its inverse. */
		final int[] step;
		final int[] invStep;
		
		/** Constraints per stream. */
		final int[][] streamItems;
		
		final long[] streamBase;
		final int[] streamLowBase;
		final int[] streamHighBase;
		final int[] streamMaxState;
		
		final int streamCount;
		final int maxState;
		
		/** Rough lower bound of how often the middle bits survive. */
		final int nuModEstimate;
		
		Model(List<Constraint> list)
		{
			constraints = list.toArray(new Constraint[0]);
			count = constraints.length;
			
			Map<Long, Integer> streamIds = new LinkedHashMap<>();
			
			for(Constraint constraint : constraints)
			{
				Long key = Long.valueOf(constraint.base);
				
				if(!streamIds.containsKey(key))
					streamIds.put(key, streamIds.size());
			}
			
			streamCount = streamIds.size();
			streamBase = new long[streamCount];
			streamLowBase = new int[streamCount];
			streamHighBase = new int[streamCount];
			streamMaxState = new int[streamCount];
			
			for(Map.Entry<Long, Integer> entry : streamIds.entrySet())
			{
				int index = entry.getValue();
				long base = entry.getKey().longValue();
				streamBase[index] = base;
				streamLowBase[index] = (int)(base & MASK48) & LOW_MASK;
				streamHighBase[index] =
					(int)((base & MASK48) >>> LOW_BITS);
			}
			
			streamOf = new int[count];
			stateOf = new int[count];
			range = new int[count];
			want = new int[count];
			interval = new boolean[count];
			alpha = new int[count];
			odd = new int[count];
			invTwo = new int[count];
			power = new int[count];
			invPower = new int[count];
			step = new int[count];
			invStep = new int[count];
			
			int[] perStream = new int[streamCount];
			int highest = 0;
			int estimate = 1;
			
			for(int i = 0; i < count; i++)
			{
				Constraint constraint = constraints[i];
				int stream = streamIds.get(Long.valueOf(constraint.base))
					.intValue();
				streamOf[i] = stream;
				stateOf[i] = constraint.draw + 1;
				range[i] = constraint.range;
				want[i] = constraint.want;
				interval[i] = (constraint.range & constraint.range - 1) == 0;
				alpha[i] = Integer.numberOfTrailingZeros(constraint.range);
				odd[i] = constraint.range >> alpha[i];
				power[i] = power13(stateOf[i]);
				invPower[i] = INVERSE_13[power[i]];
				invTwo[i] =
					odd[i] == 1 ? 0 : inverse(1 << MID_BITS, odd[i]);
				step[i] = interval[i] ? invPower[i]
					: invPower[i] * odd[i] & HIGH_MASK;
				invStep[i] = INVERSE_13[step[i]];
				perStream[stream]++;
				highest = Math.max(highest, stateOf[i]);
				
				if(alpha[i] > 0 && !interval[i])
					estimate = Math.max(estimate, 1 << alpha[i]);
			}
			
			maxState = highest;
			nuModEstimate = estimate;
			streamItems = new int[streamCount][];
			
			for(int i = 0; i < streamCount; i++)
			{
				streamItems[i] = new int[perStream[i]];
				perStream[i] = 0;
			}
			
			for(int i = 0; i < count; i++)
				streamItems[streamOf[i]][perStream[streamOf[i]]++] = i;
			
			for(int i = 0; i < streamCount; i++)
				for(int item : streamItems[i])
					streamMaxState[i] =
						Math.max(streamMaxState[i], stateOf[item]);
		}
		
		/**
		 * Lists every low part of the seed that can make the first two draws
		 * of this stream hit at once.
		 *
		 * <p>
		 * With {@code b1 = w1 + r1 * t}, {@code b2 = w2 + r2 * t'} the exact
		 * recurrence {@code b2 = a * b1 + Q[1] - 2^31 * m} turns into
		 * {@code a * r1 * t - r2 * t' - 2^31 * m = w2 - a * w1 - Q[1]}, which
		 * only has a solution when {@code gcd(a * r1, r2, 2^31)} divides the
		 * right hand side. That modulus is a power of two, because the
		 * {@code 2^31} mask destroys the odd part of the CRT.
		 *
		 * @return a bitmap over the 2^17 low parts, or null if this stream has
		 *         no usable pair of the first two draws
		 */
		long[] pinLowBits(int stream, long deadline)
		{
			int first = -1;
			int second = -1;
			
			for(int item : streamItems[stream])
			{
				if(interval[item] || constraints[item].draw > 1)
					continue;
				
				if(constraints[item].draw == 0 && first < 0)
					first = item;
				
				if(constraints[item].draw == 1 && second < 0)
					second = item;
			}
			
			if(first < 0 || second < 0)
				return null;
			
			int shared =
				gcd((int)(A * range[first] % range[second]), range[second]);
			int modulus = shared & -shared;
			
			if(modulus <= 1)
				return null;
			
			int need = (int)Math.floorMod(
				(long)want[second] - A * want[first], modulus);
			int lowBase = streamLowBase[stream];
			long[] mask = new long[LOW_MOD >>> 6];
			
			for(int low = 0; low < LOW_MOD; low++)
			{
				if((low & PIN_CHECK_INTERVAL) == 0
					&& System.currentTimeMillis() > deadline)
					break;
				
				long e0 = ((low + lowBase) & LOW_MASK) ^ A_LO;
				long q1 = (A * ((A * e0 + C) & LOW_MASK) + C) >>> LOW_BITS;
				
				if(q1 % modulus != need)
					continue;
				
				int candidate = (int)((e0 ^ A_LO) - lowBase) & LOW_MASK;
				mask[candidate >>> 6] |= 1L << (candidate & 63);
			}
			
			return mask;
		}
	}
	
	/**
	 * One searching thread. Holds all mutable per-candidate state, so several
	 * of them can work on the same {@link Model} at once.
	 */
	private static final class Worker
	{
		final Model model;
		final long[] allowedLow;
		final long deadline;
		final boolean wide;
		final AtomicInteger nextLow;
		final AtomicInteger stop;
		final AtomicInteger found;
		final AtomicLong pairs;
		final ArrayList<Long> hits;
		final List<Constraint> constraints;
		
		/** Distance between the scratch blocks of two streams. */
		final int stride;
		
		/** Low 17 bits of each state, refreshed per low part. */
		final long[] e;
		
		/** {@code (a * e[n] + c) >>> 17}, refreshed per low part. */
		final long[] q;
		
		/** {@code K[n] mod 2^6}, the pin constant of the n-th draw. */
		final int[] kMod;
		
		/** Low 18 bits of each draw, refreshed per middle part. */
		final int[] low18;
		
		/** Additive constant of the low 13 bit chain, per middle part. */
		final int[] affine;
		
		/** Offset and length of each progression, refreshed per candidate. */
		final int[] offset;
		final int[] length;
		
		/** Scratch order of the constraints, most restrictive first. */
		final int[] order;
		
		/** {@code t = (highBase + carry) mod 2^31}, per low part and stream. */
		final int[] streamT;
		
		/** {@code (nu + t) >>> 18 mod 2^13}, per middle part and stream. */
		final int[] streamKappa;
		
		Worker(Model model, long[] allowedLow, long deadline, boolean wide,
			AtomicInteger nextLow, AtomicInteger stop, AtomicInteger found,
			AtomicLong pairs, ArrayList<Long> hits,
			List<Constraint> constraints)
		{
			this.model = model;
			this.allowedLow = allowedLow;
			this.deadline = deadline;
			this.wide = wide;
			this.nextLow = nextLow;
			this.stop = stop;
			this.found = found;
			this.pairs = pairs;
			this.hits = hits;
			this.constraints = constraints;
			stride = model.maxState + 2;
			e = new long[stride * model.streamCount];
			q = new long[stride * model.streamCount];
			kMod = new int[stride * model.streamCount];
			low18 = new int[stride * model.streamCount];
			affine = new int[stride * model.streamCount];
			offset = new int[model.count];
			length = new int[model.count];
			order = new int[model.count];
			streamT = new int[model.streamCount];
			streamKappa = new int[model.streamCount];
			
			for(int i = 0; i < model.count; i++)
				order[i] = i;
			
			// most specific progression first, so checks fail fast
			for(int i = 1; i < model.count; i++)
				for(int j = i; j > 0
					&& specificity(order[j]) < specificity(order[j - 1]); j--)
				{
					int swap = order[j];
					order[j] = order[j - 1];
					order[j - 1] = swap;
				}
		}
		
		private int specificity(int item)
		{
			if(model.interval[item])
				return 1 << HIGH_BITS - model.alpha[item];
			
			return (1 << HIGH_BITS) / model.odd[item];
		}
		
		void run()
		{
			while(stop.get() == 0)
			{
				int low = nextLow.getAndIncrement();
				
				if(low >= LOW_MOD)
					return;
				
				if(allowedLow != null && (allowedLow[low >>> 6]
					& 1L << (low & 63)) == 0)
					continue;
				
				if(found.get() > 0 && wide)
				{
					stop.compareAndSet(0, 1);
					return;
				}
				
				if(System.currentTimeMillis() > deadline)
				{
					stop.compareAndSet(0, 2);
					return;
				}
				
				scan(low);
			}
		}
		
		/**
		 * Recomputes the low 17 bit chain of every stream for one low part and
		 * then walks the surviving middle bits.
		 */
		private void scan(int low)
		{
			int streamCount = model.streamCount;
			
			for(int stream = 0; stream < streamCount; stream++)
			{
				int block = stream * stride;
				long sum = low + model.streamLowBase[stream];
				e[block] = (sum & LOW_MASK) ^ A_LO;
				int t = (model.streamHighBase[stream]
					+ (int)(sum >>> LOW_BITS)) & (int)MASK31;
				streamT[stream] = t;
				
				int maxState = model.streamMaxState[stream];
				
				for(int n = 0; n <= maxState; n++)
				{
					long future = A * e[block + n] + C;
					q[block + n] = future >>> LOW_BITS;
					e[block + n + 1] = future & LOW_MASK;
				}
				
				kMod[block] = 0;
				
				for(int n = 1; n <= maxState; n++)
					kMod[block + n] = (A6 * kMod[block + n - 1]
						+ (int)(q[block + n - 1] & 63)) & 63;
			}
			
			// the even parts of the bounds fix the middle bits
			int nuResidue = 0;
			int nuMod = 1;
			
			for(int i = 0; i < model.count; i++)
			{
				if(model.interval[i] || model.alpha[i] == 0)
					continue;
				
				int modulus = 1 << model.alpha[i];
				int mask = modulus - 1;
				int wanted = requiredNu(i, mask);
				
				if(modulus <= nuMod)
				{
					if((wanted - nuResidue) % modulus != 0)
						return;
					
					continue;
				}
				
				if((nuResidue - wanted) % nuMod != 0)
					return;
				
				nuMod = modulus;
				nuResidue = wanted;
			}
			
			int range = MID_MOD;
			int sinceCheck = 0;
			
			for(int nu = nuResidue; nu < range; nu += nuMod)
			{
				pairs.incrementAndGet();
				
				if(++sinceCheck >= CHECK_INTERVAL)
				{
					sinceCheck = 0;
					
					if(System.currentTimeMillis() > deadline)
					{
						stop.compareAndSet(0, 2);
						return;
					}
					
					if(found.get() >= MAX_CANDIDATES)
					{
						stop.compareAndSet(0, 1);
						return;
					}
					
					if(stop.get() != 0)
						return;
				}
				
				middle(low, nu);
			}
		}
		
		/** Solves the progressions of one (low part, middle bits) pair. */
		private void middle(int low, int nu)
		{
			for(int stream = 0; stream < model.streamCount; stream++)
			{
				int block = stream * stride;
				long sum = (long)nu + streamT[stream];
				streamKappa[stream] = (int)(sum >>> MID_BITS) & HIGH_MASK;
				low18[block] = (int)sum & MID_MASK ^ A_HI;
				affine[block] = 0;
				
				for(int n = 1; n <= model.streamMaxState[stream]; n++)
				{
					long future = A * low18[block + n - 1] + q[block + n - 1];
					low18[block + n] = (int)future & MID_MASK;
					int beta = (int)(future >>> MID_BITS) & HIGH_MASK;
					affine[block + n] =
						(A13 * affine[block + n - 1] + beta) & HIGH_MASK;
				}
			}
			
			for(int i = 0; i < model.count; i++)
			{
				int stream = model.streamOf[i];
				int n = model.stateOf[i];
				int block = stream * stride;
				int base = (model.power[i] * streamKappa[stream]
					+ affine[block + n]) & HIGH_MASK;
				int v = low18[block + n];
				
				if(model.interval[i])
				{
					int bits = model.alpha[i];
					long lo = (long)model.want[i] << 31 - bits;
					long from = (lo - v + MID_MASK) >> MID_BITS;
					long to = (lo + (1L << 31 - bits) - 1 - v) >> MID_BITS;
					
					from = Math.max(from, 0);
					to = Math.min(to, HIGH_MASK);
					
					if(to < from)
						return;
					
					offset[i] = model.invPower[i]
						* (int)((from - base) & HIGH_MASK) & HIGH_MASK;
					length[i] = (int)(to - from) + 1;
					continue;
				}
				
				int residue = model.want[i] - v;
				residue %= model.odd[i];
				
				if(residue < 0)
					residue += model.odd[i];
				
				int gamma = residue * model.invTwo[i] % model.odd[i];
				offset[i] = model.invPower[i] * (gamma - base & HIGH_MASK)
					& HIGH_MASK;
				length[i] = (HIGH_MASK - gamma) / model.odd[i] + 1;
			}
			
			int pivot = order[0];
			int pivotLength = length[pivot];
			
			for(int i = 1; i < model.count; i++)
			{
				int other = order[i];
				
				if(length[other] >= pivotLength)
					continue;
				
				pivot = other;
				pivotLength = length[other];
			}
			
			if(pivotLength <= 0)
				return;
			
			int pivotOffset = offset[pivot];
			int pivotStep = model.step[pivot];
			
			for(int i = 0; i < pivotLength; i++)
			{
				int mu = pivotOffset + pivotStep * i & HIGH_MASK;
				boolean ok = true;
				
				for(int c = 0; c < model.count; c++)
				{
					int other = order[c];
					
					if(other == pivot)
						continue;
					
					int index = (mu - offset[other]) * model.invStep[other]
						& HIGH_MASK;
					
					if(index >= length[other])
					{
						ok = false;
						break;
					}
				}
				
				if(!ok)
					continue;
				
				long seed = low + (((long)mu << MID_BITS | nu) << LOW_BITS);
				
				if(!reproduces(seed, constraints))
					continue;
				
				synchronized(hits)
				{
					if(found.get() < MAX_CANDIDATES)
					{
						hits.add(Long.valueOf(seed));
						found.incrementAndGet();
					}
				}
				
				if(found.get() >= MAX_CANDIDATES)
				{
					stop.compareAndSet(0, 1);
					return;
				}
			}
		}
		
		private int requiredNu(int item, int mask)
		{
			int inverse = INVERSE_13[model.power[item]] & mask;
			int constant = kMod[model.streamOf[item] * stride
				+ model.stateOf[item]] & mask;
			int low0 =
				inverse * ((model.want[item] & mask) - constant & mask)
					& mask;
			int stream = model.streamOf[item];
			
			return (low0 ^ A_HI & mask) - (streamT[stream] & mask) & mask;
		}
	}
	
	/** {@code a^n mod 2^13}, used for the progression of the n-th draw. */
	private static int power13(int exponent)
	{
		int result = 1;
		
		for(int i = 0; i < exponent; i++)
			result = result * A13 & HIGH_MASK;
		
		return result;
	}
}
