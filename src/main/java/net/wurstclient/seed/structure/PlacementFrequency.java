/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.seed.structure;

import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;

/**
 * The {@code frequency} / {@code frequency_reducer} part of a structure
 * placement, re-implemented from the 1.20.1 bytecode of
 * {@code StructurePlacement#isPlacementChunk} and the four reducer lambdas of
 * {@code StructurePlacement.FrequencyReductionMethod}.
 *
 * <p>
 * It has to be re-implemented because the frequency and the reducer are
 * protected fields with no public accessor, while the random number generator
 * is available as {@link Rng}, which the tests compare against the real
 * {@code WorldgenRandom}.
 *
 * <p>
 * The argument order of the legacy variants is deliberately copied verbatim
 * from the bytecode, including the fact that the default variant passes the
 * placement's salt in the position of the chunk x coordinate.
 */
public final class PlacementFrequency
{
	/** {@code FrequencyReductionMethod.DEFAULT}. */
	public static final int DEFAULT = 0;
	
	/** {@code FrequencyReductionMethod.LEGACY_TYPE_1}. */
	public static final int LEGACY_TYPE_1 = 1;
	
	/** {@code FrequencyReductionMethod.LEGACY_TYPE_2}. */
	public static final int LEGACY_TYPE_2 = 2;
	
	/** {@code FrequencyReductionMethod.LEGACY_TYPE_3}. */
	public static final int LEGACY_TYPE_3 = 3;
	
	private static final int LEGACY_TYPE_2_SALT = 10387320;
	
	/**
	 * One generator per thread, so that the seed search can call this billions
	 * of times without allocating.
	 */
	private static final ThreadLocal<Rng> RNG = ThreadLocal.withInitial(Rng::new);
	
	private PlacementFrequency()
	{
		
	}
	
	/**
	 * @return the generator of the calling thread. Its state is overwritten by
	 *         every call to {@link #passes}, so it must not be kept around.
	 */
	public static Rng rng()
	{
		return RNG.get();
	}
	
	/**
	 * @param id
	 *            the {@code frequency_reducer} of the placement, e.g.
	 *            {@code "minecraft:legacy_type_1"}.
	 */
	public static int fromId(String id)
	{
		if(id == null)
			return DEFAULT;
		
		String name = StructureHit.shortName(id);
		
		if("legacy_type_1".equals(name))
			return LEGACY_TYPE_1;
		if("legacy_type_2".equals(name))
			return LEGACY_TYPE_2;
		if("legacy_type_3".equals(name))
			return LEGACY_TYPE_3;
		
		return DEFAULT;
	}
	
	/**
	 * @return whether this chunk survives the placement's frequency reduction.
	 *         A frequency of 1 never reduces anything and draws no random
	 *         numbers.
	 */
	public static boolean passes(int method, long levelSeed, int salt,
		int chunkX, int chunkZ, float frequency)
	{
		if(frequency >= 1.0F)
			return true;
		
		Rng random = rng();
		
		switch(method)
		{
			case LEGACY_TYPE_1:
			{
				// the only variant that groups chunks into 16 * 16 regions
				int regionX = chunkX >> 4;
				int regionZ = chunkZ >> 4;
				random.setSeed((long)(regionX ^ regionZ << 4) ^ levelSeed);
				random.nextInt();
				return random.nextInt((int)(1.0F / frequency)) == 0;
			}
			
			case LEGACY_TYPE_2:
			{
				random.setLargeFeatureWithSalt(levelSeed, chunkX, chunkZ,
					LEGACY_TYPE_2_SALT);
				return random.nextFloat() < frequency;
			}
			
			case LEGACY_TYPE_3:
			{
				random.setLargeFeatureSeed(levelSeed, chunkX, chunkZ);
				return random.nextDouble() < (double)frequency;
			}
			
			case DEFAULT:
			default:
			{
				random.setLargeFeatureWithSalt(levelSeed, salt, chunkX, chunkZ);
				return random.nextFloat() < frequency;
			}
		}
	}
	
	/**
	 * The Java linear congruential generator behind vanilla
	 * {@code LegacyRandomSource}, without the object churn - the seed search
	 * evaluates billions of candidate seeds.
	 *
	 * <p>
	 * Every formula was read from the 1.20.1 bytecode: the multiplier
	 * {@code 25214903917}, the addend {@code 11}, the 48 bit mask, the
	 * power-of-two shortcut of {@code nextInt}, and the scaling constants of
	 * {@code nextFloat} ({@code 2^-24}) and {@code nextDouble} ({@code 2^-53}).
	 * {@code RngEquivalenceTest} compares all of them against the real
	 * {@code WorldgenRandom}.
	 */
	public static final class Rng
	{
		private static final long MULTIPLIER = 25214903917L;
		private static final long ADDEND = 11L;
		private static final long MASK = 281474976710655L;
		private static final float FLOAT_UNIT = 5.9604645E-8F;
		private static final double DOUBLE_UNIT = 1.1102230246251565E-16D;
		
		private long state;
		
		public void setSeed(long seed)
		{
			state = (seed ^ MULTIPLIER) & MASK;
		}
		
		/** {@code WorldgenRandom#setLargeFeatureWithSalt}. */
		public void setLargeFeatureWithSalt(long levelSeed, int a, int b, int c)
		{
			setSeed((long)a * 341873128712L + (long)b * 132897987541L + levelSeed
				+ (long)c);
		}
		
		/** {@code WorldgenRandom#setLargeFeatureSeed}. */
		public void setLargeFeatureSeed(long levelSeed, int a, int b)
		{
			setSeed(levelSeed);
			long i = nextLong();
			long j = nextLong();
			setSeed((long)a * i ^ (long)b * j ^ levelSeed);
		}
		
		public int next(int bits)
		{
			state = (state * MULTIPLIER + ADDEND) & MASK;
			return (int)(state >>> 48 - bits);
		}
		
		public int nextInt()
		{
			return next(32);
		}
		
		public int nextInt(int bound)
		{
			if(bound <= 0)
				throw new IllegalArgumentException("Bound must be positive");
			
			// the power-of-two path draws the same number but scales it
			if((bound & bound - 1) == 0)
				return (int)((long)bound * (long)next(31) >> 31);
			
			int bits = next(31);
			int value = bits % bound;
			
			while(bits - value + (bound - 1) < 0)
			{
				bits = next(31);
				value = bits % bound;
			}
			
			return value;
		}
		
		public long nextLong()
		{
			long high = next(32);
			long low = next(32);
			return (high << 32) + low;
		}
		
		public float nextFloat()
		{
			return next(24) * FLOAT_UNIT;
		}
		
		public double nextDouble()
		{
			int high = next(26);
			int low = next(27);
			return (double)(((long)high << 27) + (long)low) * DOUBLE_UNIT;
		}
	}
	
	/**
	 * The reference implementation, used by the tests to prove that
	 * {@link Rng} behaves exactly like vanilla.
	 */
	static boolean passesWithVanillaRng(int method, long levelSeed, int salt,
		int chunkX, int chunkZ, float frequency)
	{
		if(frequency >= 1.0F)
			return true;
		
		switch(method)
		{
			case LEGACY_TYPE_1:
			{
				int regionX = chunkX >> 4;
				int regionZ = chunkZ >> 4;
				WorldgenRandom random = new WorldgenRandom(
					new LegacyRandomSource(0L));
				random.setSeed((long)(regionX ^ regionZ << 4) ^ levelSeed);
				random.nextInt();
				return random.nextInt((int)(1.0F / frequency)) == 0;
			}
			
			case LEGACY_TYPE_2:
			{
				WorldgenRandom random = new WorldgenRandom(
					new LegacyRandomSource(0L));
				random.setLargeFeatureWithSalt(levelSeed, chunkX, chunkZ,
					LEGACY_TYPE_2_SALT);
				return random.nextFloat() < frequency;
			}
			
			case LEGACY_TYPE_3:
			{
				WorldgenRandom random = new WorldgenRandom(
					new LegacyRandomSource(0L));
				random.setLargeFeatureSeed(levelSeed, chunkX, chunkZ);
				return random.nextDouble() < (double)frequency;
			}
			
			case DEFAULT:
			default:
			{
				WorldgenRandom random = new WorldgenRandom(
					new LegacyRandomSource(0L));
				random.setLargeFeatureWithSalt(levelSeed, salt, chunkX, chunkZ);
				return random.nextFloat() < frequency;
			}
		}
	}
}
