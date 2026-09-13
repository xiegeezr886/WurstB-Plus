/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.seed.search;

import net.wurstclient.seed.structure.PlacementFrequency;
import net.wurstclient.seed.structure.StructureHit;

/**
 * Everything the seed search needs to know about one structure placement, as
 * plain data: no level, no registry, no allocation per candidate.
 *
 * <p>
 * The class also re-implements
 * {@code RandomSpreadStructurePlacement#getPotentialStructureChunk} and the
 * frequency check on top of {@link PlacementFrequency.Rng}, because the seed
 * search evaluates billions of candidates - a {@code WorldgenRandom} per
 * candidate would be far too slow. {@code StructureFinder} cross-checks this
 * fast path against the real vanilla method before any search starts.
 */
public final class StructureSpread
{
	public final String setId;
	public final int spacing;
	public final int separation;
	public final int salt;
	public final boolean triangular;
	public final float frequency;
	public final int reducer;
	
	public StructureSpread(String setId, int spacing, int separation, int salt,
		boolean triangular, float frequency, int reducer)
	{
		this.setId = setId;
		this.spacing = spacing;
		this.separation = separation;
		this.salt = salt;
		this.triangular = triangular;
		this.frequency = frequency;
		this.reducer = reducer;
	}
	
	/** How many chunks the placement can be offset by. */
	public int range()
	{
		return spacing - separation;
	}
	
	/**
	 * A range of 0 or 1 always yields the same chunk, so an observed position
	 * of such a structure says nothing about the seed.
	 */
	public boolean isUsable()
	{
		return spacing > 0 && range() > 1;
	}
	
	/**
	 * @return whether the given level seed would place this structure exactly
	 *         in the given chunk.
	 */
	public boolean matches(long levelSeed, int chunkX, int chunkZ)
	{
		if(!isUsable())
			return false;
		
		int range = range();
		int regionX = Math.floorDiv(chunkX, spacing);
		int regionZ = Math.floorDiv(chunkZ, spacing);
		int wantX = chunkX - regionX * spacing;
		int wantZ = chunkZ - regionZ * spacing;
		
		if(wantX < 0 || wantX >= range || wantZ < 0 || wantZ >= range)
			return false;
		
		PlacementFrequency.Rng random = PlacementFrequency.rng();
		random.setLargeFeatureWithSalt(levelSeed, regionX, regionZ, salt);
		
		if(offset(random, range) != wantX)
			return false;
		
		if(offset(random, range) != wantZ)
			return false;
		
		return PlacementFrequency.passes(reducer, levelSeed, salt, chunkX,
			chunkZ, frequency);
	}
	
	private int offset(PlacementFrequency.Rng random, int range)
	{
		if(!triangular)
			return random.nextInt(range);
		
		// RandomSpreadType#evaluate: (nextInt + nextInt) / 2
		return (random.nextInt(range) + random.nextInt(range)) / 2;
	}
	
	/**
	 * The same computation as {@link #matches}, but returning the chunk instead
	 * of comparing it. Only used to cross-check this fast path against vanilla's
	 * own {@code getPotentialStructureChunk} before a search starts, so the
	 * allocation does not matter.
	 */
	public int[] potentialChunk(long levelSeed, int regionX, int regionZ)
	{
		PlacementFrequency.Rng random = PlacementFrequency.rng();
		random.setLargeFeatureWithSalt(levelSeed, regionX, regionZ, salt);
		
		int range = range();
		int offsetX = offset(random, range);
		int offsetZ = offset(random, range);
		
		return new int[]{regionX * spacing + offsetX,
			regionZ * spacing + offsetZ};
	}
	
	public String describe()
	{
		StringBuilder builder = new StringBuilder();
		builder.append(StructureHit.shortName(setId));
		builder.append(" (spacing ").append(spacing).append(", separation ")
			.append(separation).append(", salt ").append(salt)
			.append(triangular ? ", triangular" : ", linear");
		
		if(frequency < 1.0F)
			builder.append(", frequency ").append(frequency);
		
		builder.append(")");
		return builder.toString();
	}
	
	@Override
	public String toString()
	{
		return describe();
	}
}
