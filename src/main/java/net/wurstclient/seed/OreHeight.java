/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.seed;

import net.minecraft.util.RandomSource;

/**
 * The {@code height_range} of a placed feature, re-implemented from vanilla
 * {@code UniformHeight}, {@code TrapezoidHeight}, {@code ConstantHeight} and
 * {@code VerticalAnchor}.
 *
 * <p>
 * The vanilla classes need a {@code WorldGenerationContext}, which can only be
 * built from a {@code ChunkGenerator} - and loading that class initialises a
 * static codec that requires the built-in registries. Since the only thing the
 * context provides is the minimum generation height and the generation depth,
 * both are passed in directly instead.
 *
 * <p>
 * Every formula below, including how many random numbers are drawn and in
 * which order, was read from the 1.20.1 bytecode.
 */
public final class OreHeight
{
	private enum Mode
	{
		CONSTANT,
		UNIFORM,
		TRAPEZOID
	}
	
	private final Anchor minAnchor;
	private final Anchor maxAnchor;
	private final int plateau;
	private final Mode mode;
	
	private OreHeight(Anchor minAnchor, Anchor maxAnchor, int plateau,
		Mode mode)
	{
		this.minAnchor = minAnchor;
		this.maxAnchor = maxAnchor;
		this.plateau = plateau;
		this.mode = mode;
	}
	
	/** {@code ConstantHeight.of(anchor)}. */
	public static OreHeight constant(Anchor anchor)
	{
		return new OreHeight(anchor, anchor, 0, Mode.CONSTANT);
	}
	
	/** {@code UniformHeight.of(minInclusive, maxInclusive)}. */
	public static OreHeight uniform(Anchor minInclusive, Anchor maxInclusive)
	{
		return new OreHeight(minInclusive, maxInclusive, 0, Mode.UNIFORM);
	}
	
	/** {@code TrapezoidHeight.of(minInclusive, maxInclusive, plateau)}. */
	public static OreHeight trapezoid(Anchor minInclusive, Anchor maxInclusive,
		int plateau)
	{
		return new OreHeight(minInclusive, maxInclusive, plateau,
			Mode.TRAPEZOID);
	}
	
	/**
	 * A y coordinate that still has to be resolved against the world height,
	 * i.e. vanilla {@code VerticalAnchor}.
	 */
	public static final class Anchor
	{
		private enum Kind
		{
			ABSOLUTE,
			ABOVE_BOTTOM,
			BELOW_TOP
		}
		
		private final Kind kind;
		private final int offset;
		
		private Anchor(Kind kind, int offset)
		{
			this.kind = kind;
			this.offset = offset;
		}
		
		/** {@code VerticalAnchor.absolute(y)}. */
		public static Anchor absolute(int y)
		{
			return new Anchor(Kind.ABSOLUTE, y);
		}
		
		/** {@code VerticalAnchor.aboveBottom(offset)}. */
		public static Anchor aboveBottom(int offset)
		{
			return new Anchor(Kind.ABOVE_BOTTOM, offset);
		}
		
		/** {@code VerticalAnchor.belowTop(offset)}. */
		public static Anchor belowTop(int offset)
		{
			return new Anchor(Kind.BELOW_TOP, offset);
		}
		
		/**
		 * @param minY
		 *            lowest block of the dimension, i.e.
		 *            {@code WorldGenerationContext#getMinGenY()}
		 * @param maxY
		 *            highest block of the dimension, i.e.
		 *            {@code getMinGenY() + getGenDepth() - 1}
		 */
		public int resolve(int minY, int maxY)
		{
			switch(kind)
			{
				case ABOVE_BOTTOM:
				return minY + offset;
				
				case BELOW_TOP:
				return maxY - offset;
				
				default:
				return offset;
			}
		}
		
		@Override
		public String toString()
		{
			switch(kind)
			{
				case ABOVE_BOTTOM:
				return offset + " above bottom";
				
				case BELOW_TOP:
				return offset + " below top";
				
				default:
				return Integer.toString(offset);
			}
		}
	}
	
	/**
	 * Draws exactly like the matching vanilla provider. An empty range is not
	 * an error in 1.20.1 - vanilla logs it and returns the minimum.
	 */
	public int sample(RandomSource random, int minY, int maxY)
	{
		int min = minAnchor.resolve(minY, maxY);
		int max = maxAnchor.resolve(minY, maxY);
		
		if(min > max)
			return min;
		
		switch(mode)
		{
			case CONSTANT:
			return min;
			
			case UNIFORM:
			// Mth.nextInt(random, min, max)
			return random.nextInt(max - min + 1) + min;
			
			default:
			int range = max - min;
			
			// Mth.nextInt(random, min, max)
			if(plateau >= range)
				return random.nextInt(range + 1) + min;
			
			int small = (range - plateau) / 2;
			int large = range - small;
			
			// Mth.nextInt(random, 0, large) is drawn first, exactly like
			// vanilla.
			return min + random.nextInt(large + 1) + random.nextInt(small + 1);
		}
	}
	
	@Override
	public String toString()
	{
		switch(mode)
		{
			case CONSTANT:
			return "constant " + minAnchor;
			
			case UNIFORM:
			return "uniform " + minAnchor + " to " + maxAnchor;
			
			default:
			return "trapezoid " + minAnchor + " to " + maxAnchor
				+ ", plateau " + plateau;
		}
	}
}
