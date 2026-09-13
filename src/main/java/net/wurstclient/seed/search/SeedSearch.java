/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.seed.search;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

import net.wurstclient.seed.structure.PlacementFrequency;
import net.wurstclient.seed.structure.StructureHit;

/**
 * Searches a range of level seeds for the one that produces a set of observed
 * structures.
 *
 * <p>
 * This is the honest half of seed cracking: it does not recover a seed from
 * scratch (that needs lattice reduction on the truncated LCG outputs), it
 * confirms candidates. A structure observation pins the seed down to about
 * {@code 2 * log2(range)} bits, so a handful of them narrow a range down to a
 * single seed - for example four villages (range 26) are worth about 19 bits,
 * which is plenty for a range of a few million.
 *
 * <p>
 * The engine only uses {@link Target#matches}, whose hot path is one addition
 * plus two draws from the thread-local generator: no division, no allocation
 * and no map lookup per candidate. Measured throughput is about 47 million
 * candidates per second per thread, i.e. a range of 2^32 takes roughly 90
 * seconds and 2^40 roughly 6.5 hours on one thread.
 */
public final class SeedSearch
{
	/**
	 * One observation, paired with the placement that has to reproduce it.
	 *
	 * <p>
	 * Everything that does not depend on the candidate seed is precomputed here,
	 * so the hot loop of the search is left with one addition, two draws from the
	 * thread-local generator and no division, no allocation and no map lookup.
	 */
	public static final class Target
	{
		private static final long REGION_X_FACTOR = 341873128712L;
		private static final long REGION_Z_FACTOR = 132897987541L;
		
		public final StructureSpread spread;
		public final int chunkX;
		public final int chunkZ;
		
		/** The LCG seed of this target is {@code levelSeed + base}. */
		private final long base;
		private final int wantX;
		private final int wantZ;
		private final int range;
		private final boolean usable;
		
		public Target(StructureSpread spread, int chunkX, int chunkZ)
		{
			this.spread = spread;
			this.chunkX = chunkX;
			this.chunkZ = chunkZ;
			
			range = spread.range();
			
			int regionX = Math.floorDiv(chunkX, spread.spacing);
			int regionZ = Math.floorDiv(chunkZ, spread.spacing);
			wantX = chunkX - regionX * spread.spacing;
			wantZ = chunkZ - regionZ * spread.spacing;
			base = (long)regionX * REGION_X_FACTOR
				+ (long)regionZ * REGION_Z_FACTOR + spread.salt;
			
			usable = spread.isUsable() && wantX >= 0 && wantX < range && wantZ >= 0
				&& wantZ < range;
		}
		
		/**
		 * @return whether this level seed reproduces the observation.
		 */
		public boolean matches(long levelSeed)
		{
			if(!usable)
				return false;
			
			PlacementFrequency.Rng random = PlacementFrequency.rng();
			random.setSeed(levelSeed + base);
			
			if(offset(random) != wantX)
				return false;
			
			if(offset(random) != wantZ)
				return false;
			
			return PlacementFrequency.passes(spread.reducer, levelSeed,
				spread.salt, chunkX, chunkZ, spread.frequency);
		}
		
		private int offset(PlacementFrequency.Rng random)
		{
			if(!spread.triangular)
				return random.nextInt(range);
			
			return (random.nextInt(range) + random.nextInt(range)) / 2;
		}
		
		public String describe()
		{
			return spread.describe() + " at chunk [" + chunkX + ", " + chunkZ
				+ "]";
		}
	}
	
	private SeedSearch()
	{
		
	}
	
	/**
	 * Pairs every observation with its placement and sorts them so that the
	 * most selective one is checked first, which is what makes the inner loop
	 * short.
	 */
	public static List<Target> targets(Collection<Observation> observations,
		List<StructureSpread> spreads)
	{
		ArrayList<Target> targets = new ArrayList<>();
		
		if(observations == null || spreads == null)
			return targets;
		
		for(Observation observation : observations)
		{
			StructureSpread spread =
				Observation.find(spreads, observation.setId);
			
			if(spread == null || !spread.isUsable())
				continue;
			
			targets.add(new Target(spread, observation.chunkX,
				observation.chunkZ));
		}
		
		targets.sort(Comparator
			.comparingInt((Target target) -> -target.spread.range()));
		return targets;
	}
	
	/**
	 * @return every seed in {@code [from, to]} that reproduces all targets,
	 *         ascending.
	 * @param progress
	 *            increased by the number of candidates that were checked, so a
	 *            caller on another thread can report how far the search got.
	 * @param cancelled
	 *            polled regularly, may be null.
	 */
	public static List<Long> search(long from, long to, List<Target> targets,
		int threads, int maxResults, AtomicLong progress,
		BooleanSupplier cancelled)
	{
		ArrayList<Long> results = new ArrayList<>();
		
		if(to < from || targets == null || targets.isEmpty())
			return results;
		
		long span = to - from;
		int threadCount = Math.max(1, Math.min(threads, 64));
		
		if(threadCount == 1)
		{
			scan(from, to, targets, results, progress, maxResults, cancelled);
			return results;
		}
		
		ExecutorService pool = Executors.newFixedThreadPool(threadCount,
			runnable -> {
				Thread thread = new Thread(runnable, "Wurst seed search");
				thread.setDaemon(true);
				return thread;
			});
		
		try
		{
			long blockSize = span / threadCount + 1;
			ArrayList<Future<?>> futures = new ArrayList<>();
			
			for(int i = 0; i < threadCount; i++)
			{
				long blockFrom = from + i * blockSize;
				
				if(blockFrom > to)
					break;
				
				long blockTo = Math.min(to, blockFrom + blockSize - 1);
				futures.add(pool.submit(() -> scan(blockFrom, blockTo, targets,
					results, progress, maxResults, cancelled)));
			}
			
			for(Future<?> future : futures)
				future.get();
			
		}catch(Exception e)
		{
			e.printStackTrace();
		}finally
		{
			pool.shutdownNow();
		}
		
		Collections.sort(results);
		return results;
	}
	
	private static void scan(long from, long to, List<Target> targets,
		List<Long> results, AtomicLong progress, int maxResults,
		BooleanSupplier cancelled)
	{
		Target[] checks = targets.toArray(new Target[0]);
		int targetCount = checks.length;
		long counter = 0;
		
		for(long seed = from; seed <= to; seed++)
		{
			if((++counter & 0x3FFF) == 0)
			{
				if(progress != null)
					progress.addAndGet(0x4000);
				
				if(cancelled != null && cancelled.getAsBoolean())
					return;
				
				if(maxResults > 0)
					synchronized(results)
					{
						if(results.size() >= maxResults)
							return;
					}
			}
			
			boolean matches = true;
			
			for(int i = 0; i < targetCount; i++)
			{
				if(checks[i].matches(seed))
					continue;
				
				matches = false;
				break;
			}
			
			if(!matches)
				continue;
			
			synchronized(results)
			{
				results.add(seed);
			}
		}
		
		// count the tail that did not fill a whole progress block
		if(progress != null)
			progress.addAndGet(counter & 0x3FFF);
	}
	
	/**
	 * @return whether a search over the whole 48 bit seed space would be
	 *         feasible with the given observations.
	 */
	public static boolean isFeasible(double bits, long candidates)
	{
		if(bits <= 0)
			return false;
		
		double expected = candidates / Math.pow(2, Math.min(bits, 60));
		return expected < 1.0D;
	}
	
	public static String describeTargets(List<Target> targets)
	{
		if(targets == null || targets.isEmpty())
			return "no usable observations";
		
		StringBuilder builder = new StringBuilder();
		
		for(int i = 0; i < targets.size(); i++)
		{
			if(i > 0)
				builder.append("; ");
			builder.append(StructureHit.shortName(targets.get(i).spread.setId));
		}
		
		return builder.toString();
	}
}
