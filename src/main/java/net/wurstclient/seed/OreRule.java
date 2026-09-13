/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.seed;


/**
 * The vanilla data of a single ore placed feature, transcribed from
 * {@code data/minecraft/worldgen/placed_feature} and
 * {@code data/minecraft/worldgen/configured_feature} of 1.20.1.
 *
 * <p>
 * {@link #index} and {@link #step} are exactly the two values that
 * {@code WorldgenRandom#setFeatureSeed} needs, see
 * {@link OrePredictor#predictChunkWithIds} for how they are consumed.
 */
public final class OreRule
{
	/** e.g. {@code "minecraft:ore_diamond"}. */
	public final String featureId;
	
	/** The stone variant of the ore block, e.g. {@code "minecraft:diamond_ore"}. */
	public final String blockId;
	
	/**
	 * The deepslate variant, or {@code null} for ores that replace other
	 * blocks.
	 */
	public final String deepslateBlockId;
	
	/** Index of the placed feature inside its generation step. */
	public final int index;
	
	/** {@code GenerationStep.Decoration} ordinal. */
	public final int step;
	
	public final OreCount count;
	public final float rarity;
	public final int size;
	public final float discardOnAirChance;
	public final boolean scattered;
	public final OreHeight heightProvider;
	
	public OreRule(String featureId, String blockId, String deepslateBlockId,
		int index, int step, OreCount count, float rarity,
		OreHeight heightProvider, int size, float discardOnAirChance,
		boolean scattered)
	{
		this.featureId = featureId;
		this.blockId = blockId;
		this.deepslateBlockId = deepslateBlockId;
		this.index = index;
		this.step = step;
		this.count = count;
		this.rarity = rarity;
		this.heightProvider = heightProvider;
		this.size = size;
		this.discardOnAirChance = discardOnAirChance;
		this.scattered = scattered;
	}
	
	public String featureId()
	{
		return featureId;
	}
	
	public String blockId()
	{
		return blockId;
	}
	
	public String deepslateBlockId()
	{
		return deepslateBlockId;
	}
	
	public int index()
	{
		return index;
	}
	
	public int step()
	{
		return step;
	}
	
	public OreCount count()
	{
		return count;
	}
	
	public float rarity()
	{
		return rarity;
	}
	
	public int size()
	{
		return size;
	}
	
	public float discardOnAirChance()
	{
		return discardOnAirChance;
	}
	
	public boolean scattered()
	{
		return scattered;
	}
	
	public OreHeight heightProvider()
	{
		return heightProvider;
	}
	
	@Override
	public String toString()
	{
		return featureId + "[index=" + index + ", step=" + step + ", block="
			+ blockId + "]";
	}
}
