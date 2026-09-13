/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.seed.structure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

/**
 * One chunk that a structure set can place its structure in, together with the
 * structures that set can choose from.
 */
public final class StructureHit
{
	public final ChunkPos chunk;
	
	/** e.g. {@code "minecraft:villages"}. */
	public final String structureSetId;
	
	/** e.g. {@code ["minecraft:village_plains", ...]}. */
	public final List<String> structureIds;
	
	/**
	 * Whether every loaded neighbouring chunk of this candidate had a biome
	 * that allows one of the structures. Only meaningful when
	 * {@link #biomeChecked} is true.
	 */
	public final boolean biomeAllowed;
	
	/**
	 * Whether the client had loaded the chunk, so that the biome could be
	 * checked at all.
	 */
	public final boolean biomeChecked;
	
	public StructureHit(ChunkPos chunk, String structureSetId,
		List<String> structureIds, boolean biomeChecked, boolean biomeAllowed)
	{
		this.chunk = chunk;
		this.structureSetId = structureSetId;
		this.structureIds = Collections
			.unmodifiableList(new ArrayList<>(structureIds));
		this.biomeChecked = biomeChecked;
		this.biomeAllowed = biomeAllowed;
	}
	
	public int chunkX()
	{
		return chunk.x;
	}
	
	public int chunkZ()
	{
		return chunk.z;
	}
	
	public int blockX()
	{
		return chunk.getMinBlockX() + 8;
	}
	
	public int blockZ()
	{
		return chunk.getMinBlockZ() + 8;
	}
	
	public double distanceSq(BlockPos reference)
	{
		if(reference == null)
			return 0;
		
		double dx = blockX() - reference.getX();
		double dz = blockZ() - reference.getZ();
		return dx * dx + dz * dz;
	}
	
	/**
	 * @return whether this candidate should still be shown, i.e. it is either
	 *         unchecked or its biome allows the structure.
	 */
	public boolean isPossible()
	{
		return !biomeChecked || biomeAllowed;
	}
	
	public String describe()
	{
		StringBuilder builder = new StringBuilder();
		builder.append(shortName(structureSetId));
		builder.append(" at chunk [").append(chunk.x).append(", ")
			.append(chunk.z).append("] block [").append(blockX()).append(", ")
			.append(blockZ()).append("]");
		
		if(!structureIds.isEmpty())
		{
			builder.append(" - ");
			for(int i = 0; i < structureIds.size(); i++)
			{
				if(i > 0)
					builder.append(" / ");
				builder.append(shortName(structureIds.get(i)));
			}
		}
		
		if(!biomeChecked)
			builder.append(" (biome unknown)");
		else if(!biomeAllowed)
			builder.append(" (wrong biome)");
		
		return builder.toString();
	}
	
	public static String shortName(String id)
	{
		if(id == null)
			return "?";
		
		int colon = id.indexOf(':');
		return colon < 0 ? id : id.substring(colon + 1);
	}
	
	@Override
	public String toString()
	{
		return describe();
	}
}
