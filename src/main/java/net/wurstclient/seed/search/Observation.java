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
import java.util.List;

import net.minecraft.core.BlockPos;

/**
 * One structure the player actually saw, i.e. one constraint for the seed
 * search.
 *
 * <p>
 * Only the chunk matters: {@code getPotentialStructureChunk} always returns the
 * chunk a structure would be placed in, so any block inside that structure is
 * enough to derive it.
 */
public final class Observation
{
	public final String setId;
	public final int chunkX;
	public final int chunkZ;
	
	public Observation(String setId, int chunkX, int chunkZ)
	{
		this.setId = setId;
		this.chunkX = chunkX;
		this.chunkZ = chunkZ;
	}
	
	public static Observation ofBlock(String setId, int blockX, int blockZ)
	{
		return new Observation(setId, Math.floorDiv(blockX, 16),
			Math.floorDiv(blockZ, 16));
	}
	
	public int blockX()
	{
		return chunkX * 16 + 8;
	}
	
	public int blockZ()
	{
		return chunkZ * 16 + 8;
	}
	
	public double distanceSq(BlockPos reference)
	{
		if(reference == null)
			return 0;
		
		double dx = blockX() - reference.getX();
		double dz = blockZ() - reference.getZ();
		return dx * dx + dz * dz;
	}
	
	public String describe()
	{
		return net.wurstclient.seed.structure.StructureHit.shortName(setId)
			+ " at chunk [" + chunkX + ", " + chunkZ + "] block [" + blockX()
			+ ", " + blockZ() + "]";
	}
	
	/**
	 * How much a set of observations constrains a seed, as a rough number of
	 * bits: two offsets per observation, each worth {@code log2(range)} bits,
	 * plus {@code log2(1/frequency)} because the structure only generated there
	 * if it also passed the frequency gate.
	 *
	 * <p>
	 * This is an upper bound. Observations of the same structure set are
	 * correlated - their placements are seeded from the same level seed with a
	 * known offset - so the measured number of surviving candidates is higher
	 * than {@code 2^-bits} suggests. Use it to compare observations, not to
	 * promise that one seed remains.
	 */
	public static double bits(Collection<Observation> observations,
		List<StructureSpread> spreads)
	{
		double bits = 0;
		
		for(Observation observation : observations)
		{
			StructureSpread spread = find(spreads, observation.setId);
			
			if(spread == null || !spread.isUsable())
				continue;
			
			bits += 2 * (Math.log(spread.range()) / Math.log(2));
			
			if(spread.frequency < 1.0F)
				bits += Math.log(1.0D / spread.frequency) / Math.log(2);
		}
		
		return bits;
	}
	
	public static StructureSpread find(List<StructureSpread> spreads,
		String setId)
	{
		if(spreads == null || setId == null)
			return null;
		
		for(StructureSpread spread : spreads)
			if(setId.equals(spread.setId))
				return spread;
		
		return null;
	}
	
	public static List<String> setIds(Collection<Observation> observations)
	{
		ArrayList<String> ids = new ArrayList<>();
		
		for(Observation observation : observations)
			if(!ids.contains(observation.setId))
				ids.add(observation.setId);
		
		Collections.sort(ids);
		return ids;
	}
	
	@Override
	public String toString()
	{
		return describe();
	}
}
