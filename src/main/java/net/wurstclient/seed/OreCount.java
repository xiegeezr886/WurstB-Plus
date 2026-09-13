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
 * The {@code count} of a placed feature, re-implemented from vanilla
 * {@code UniformInt} and {@code ConstantInt}.
 *
 * <p>
 * Those two classes cannot be used directly: {@code IntProvider} initialises a
 * static codec that needs the built-in registries, so merely reading the ore
 * table would drag the whole vanilla bootstrap into unit tests. Both formulas
 * below were taken from the 1.20.1 bytecode, and the distinction between
 * "constant" and "uniform with min == max" is kept because vanilla draws a
 * random number in the uniform case but not in the constant case.
 */
public final class OreCount
{
	private final int min;
	private final int max;
	private final boolean constant;
	
	private OreCount(int min, int max, boolean constant)
	{
		this.min = min;
		this.max = max;
		this.constant = constant;
	}
	
	/** {@code ConstantInt.of(value)}. */
	public static OreCount constant(int value)
	{
		return new OreCount(value, value, true);
	}
	
	/** {@code UniformInt.of(minInclusive, maxInclusive)}. */
	public static OreCount uniform(int minInclusive, int maxInclusive)
	{
		return new OreCount(minInclusive, maxInclusive, false);
	}
	
	/**
	 * Draws exactly as many random numbers as the matching vanilla provider:
	 * none for a constant, one for a uniform range.
	 */
	public int sample(RandomSource random)
	{
		if(constant)
			return min;
		
		return random.nextInt(max - min + 1) + min;
	}
	
	public int min()
	{
		return min;
	}
	
	public int max()
	{
		return max;
	}
	
	public boolean isConstant()
	{
		return constant;
	}
	
	@Override
	public String toString()
	{
		return constant ? Integer.toString(min) : min + "-" + max;
	}
}
