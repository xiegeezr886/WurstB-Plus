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
import java.util.Comparator;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.wurstclient.WurstClient;
import net.wurstclient.seed.SeedEntry;
import net.wurstclient.seed.SeedStore;
import net.wurstclient.seed.search.StructureSpread;

/**
 * Predicts which chunks a structure set places its structure in, using nothing
 * but the level seed and the structure sets of the loaded level.
 *
 * <p>
 * The placement math itself is <b>not</b> re-implemented: 1.20.1 exposes
 * {@code RandomSpreadStructurePlacement#getPotentialStructureChunk(long, int,
 * int)} publicly, so this class calls the very same method the server uses.
 * That keeps the prediction exact by construction and makes it work for
 * datapack and modded structure sets as well.
 *
 * <p>
 * The placement's {@code frequency} and {@code frequency_reducer} are protected
 * fields without accessors, so they are read back through the placement codec
 * as JSON - the same schema the datapack format uses - and the decision is then
 * made by {@link PlacementFrequency}.
 *
 * <p>
 * Biome rules cannot be evaluated without world generation, but for chunks the
 * client has already loaded the biome is known, so those candidates are checked
 * against the structure's biome set and marked accordingly.
 *
 * <p>
 * Two things are deliberately not handled: structure sets that use another
 * placement type - most notably the stronghold rings
 * ({@code ConcentricRingsStructurePlacement}) - are skipped, and exclusion
 * zones are ignored, so a structure that excludes another one can still show up
 * next to it.
 */
public final class StructureFinder
{
	/**
	 * Samples the potential structure chunk of one region. Exists so that the
	 * region scan can be tested without a Minecraft level.
	 */
	public interface RegionSampler
	{
		ChunkPos sample(int regionX, int regionZ);
	}
	
	private static final class Params
	{
		private final float frequency;
		private final int salt;
		private final int reducer;
		private final boolean exclusionZone;
		private final int width;
		private final int gap;
		private final boolean triangular;
		
		private Params(float frequency, int salt, int reducer,
			boolean exclusionZone, int width, int gap, boolean triangular)
		{
			this.frequency = frequency;
			this.salt = salt;
			this.reducer = reducer;
			this.exclusionZone = exclusionZone;
			this.width = width;
			this.gap = gap;
			this.triangular = triangular;
		}
	}
	
	private List<RandomSpreadStructurePlacement> placements;
	private List<List<Structure>> structures;
	private List<List<String>> structureIds;
	private List<String> setIds;
	private List<Params> params;
	private List<StructureSpread> spreads;
	private String spreadCheck = "not checked";
	private int reducedSets;
	private int exclusionSets;
	private Registry<StructureSet> cachedRegistry;
	private long cachedSeed;
	private boolean cached;
	
	/**
	 * @return the chunks of the given structure set inside the given radius,
	 *         nearest first.
	 */
	public List<StructureHit> find(ChunkPos center, int radiusChunks)
	{
		return find(center, radiusChunks, false);
	}
	
	public List<StructureHit> find(ChunkPos center, int radiusChunks,
		boolean skipRejectedBiomes)
	{
		if(center == null || radiusChunks < 0 || !refresh())
			return Collections.emptyList();
		
		long seed = cachedSeed;
		ArrayList<StructureHit> hits = new ArrayList<>();
		
		for(int i = 0; i < placements.size(); i++)
		{
			RandomSpreadStructurePlacement placement = placements.get(i);
			Params placementParams = params.get(i);
			
			for(ChunkPos chunk : scan(placement.spacing(), radiusChunks, center,
				(regionX, regionZ) -> placement.getPotentialStructureChunk(seed,
					regionX, regionZ)))
			{
				if(!PlacementFrequency.passes(placementParams.reducer, seed,
					placementParams.salt, chunk.x, chunk.z,
					placementParams.frequency))
					continue;
				
				StructureHit hit = checkBiomes(chunk, setIds.get(i),
					structures.get(i), structureIds.get(i));
				
				if(skipRejectedBiomes && !hit.isPossible())
					continue;
				
				hits.add(hit);
			}
		}
		
		BlockPos reference = new BlockPos(center.getMinBlockX() + 8, 0,
			center.getMinBlockZ() + 8);
		hits.sort(Comparator.comparingDouble(hit -> hit.distanceSq(reference)));
		
		return hits;
	}
	
	/**
	 * The region scan: a structure set can place at most one structure per
	 * {@code spacing * spacing} region, and the offset is always inside that
	 * region, so walking the regions that overlap the radius finds every
	 * candidate without checking chunk by chunk.
	 */
	public static List<ChunkPos> scan(int spacing, int radiusChunks,
		ChunkPos center, RegionSampler sampler)
	{
		ArrayList<ChunkPos> found = new ArrayList<>();
		
		if(spacing <= 0 || radiusChunks < 0 || center == null || sampler == null)
			return found;
		
		int minRegionX = Math.floorDiv(center.x - radiusChunks, spacing);
		int maxRegionX = Math.floorDiv(center.x + radiusChunks, spacing);
		int minRegionZ = Math.floorDiv(center.z - radiusChunks, spacing);
		int maxRegionZ = Math.floorDiv(center.z + radiusChunks, spacing);
		
		for(int regionX = minRegionX; regionX <= maxRegionX; regionX++)
			for(int regionZ = minRegionZ; regionZ <= maxRegionZ; regionZ++)
			{
				ChunkPos potential = sampler.sample(regionX, regionZ);
				
				if(potential == null)
					continue;
				
				if(Math.abs(potential.x - center.x) > radiusChunks
					|| Math.abs(potential.z - center.z) > radiusChunks)
					continue;
				
				if(!found.contains(potential))
					found.add(potential);
			}
		
		found.sort(Comparator.comparingInt((ChunkPos pos) -> Math
			.max(Math.abs(pos.x - center.x), Math.abs(pos.z - center.z))));
		return found;
	}
	
	/**
	 * Reads the structure sets of the loaded level, if that has not happened for
	 * the current level and seed yet.
	 *
	 * @return whether predictions are possible.
	 */
	public boolean refresh()
	{
		try
		{
			if(WurstClient.MC.level == null)
			{
				cached = false;
				return false;
			}
			
			Registry<StructureSet> registry = WurstClient.MC.level
				.registryAccess().registryOrThrow(Registries.STRUCTURE_SET);
			Registry<Structure> structureRegistry = WurstClient.MC.level
				.registryAccess().registryOrThrow(Registries.STRUCTURE);
			long seed = currentSeed();
			
			if(cached && registry == cachedRegistry && seed == cachedSeed)
				return true;
			
			placements = new ArrayList<>();
			structures = new ArrayList<>();
			structureIds = new ArrayList<>();
			setIds = new ArrayList<>();
			params = new ArrayList<>();
			reducedSets = 0;
			exclusionSets = 0;
			
			for(StructureSet set : registry)
			{
				StructurePlacement placement = set.placement();
				
				if(!(placement instanceof RandomSpreadStructurePlacement))
					continue;
				
				ArrayList<Structure> setStructures = new ArrayList<>();
				ArrayList<String> ids = new ArrayList<>();
				
				for(StructureSet.StructureSelectionEntry entry : set
					.structures())
				{
					Structure structure = entry.structure().value();
					setStructures.add(structure);
					ids.add(keyOf(structureRegistry, structure));
				}
				
				Params placementParams = readParams(placement);
				
				if(placementParams.frequency < 1.0F)
					reducedSets++;
				
				if(placementParams.exclusionZone)
					exclusionSets++;
				
				placements.add((RandomSpreadStructurePlacement)placement);
				structures.add(setStructures);
				structureIds.add(ids);
				setIds.add(registry.getResourceKey(set)
					.map(key -> key.location().toString()).orElse("?"));
				params.add(placementParams);
			}
			
			cachedRegistry = registry;
			cachedSeed = seed;
			cached = true;
			buildSpreads();
			return true;
		}catch(Exception e)
		{
			e.printStackTrace();
			cached = false;
			return false;
		}
	}
	
	public void clear()
	{
		placements = null;
		structures = null;
		structureIds = null;
		setIds = null;
		params = null;
		spreads = null;
		spreadCheck = "not checked";
		cachedRegistry = null;
		cached = false;
	}
	
	public boolean isAvailable()
	{
		return cached && placements != null && !placements.isEmpty();
	}
	
	/**
	 * @return the ids of the handled structure sets, sorted.
	 */
	public List<String> structureSetIds()
	{
		if(!refresh())
			return Collections.emptyList();
		
		ArrayList<String> sorted = new ArrayList<>(setIds);
		Collections.sort(sorted);
		return Collections.unmodifiableList(sorted);
	}
	
	/**
	 * @return the placement data of every handled structure set, as plain data
	 *         for the seed search.
	 */
	public List<StructureSpread> spreads()
	{
		if(!refresh())
			return Collections.emptyList();
		
		return spreads == null ? Collections.emptyList() : spreads;
	}
	
	/**
	 * @return the result of comparing {@link StructureSpread}'s fast path with
	 *         vanilla's own placement method.
	 */
	public String describeSpreadCheck()
	{
		return spreadCheck;
	}
	
	/**
	 * @return how many structure sets are handled and what was skipped or
	 *         ignored.
	 */
	public String describeSources()
	{
		if(!refresh())
			return "no level loaded";
		
		int skipped = 0;
		
		try
		{
			Registry<StructureSet> registry = WurstClient.MC.level
				.registryAccess().registryOrThrow(Registries.STRUCTURE_SET);
			skipped = registry.size() - placements.size();
		}catch(Exception e)
		{
			// the count is cosmetic, the placements themselves are fine
		}
		
		StringBuilder builder = new StringBuilder();
		builder.append(placements.size()).append(" spread structure sets");
		
		if(skipped > 0)
			builder.append(", ").append(skipped)
				.append(" skipped (other placement type)");
		
		if(reducedSets > 0)
			builder.append(", ").append(reducedSets)
				.append(" with frequency reduction");
		
		if(exclusionSets > 0)
			builder.append(", ").append(exclusionSets)
				.append(" with ignored exclusion zone");
		
		return builder.toString();
	}
	
	private StructureHit checkBiomes(ChunkPos chunk, String setId,
		List<Structure> setStructures, List<String> ids)
	{
		if(WurstClient.MC.level == null
			|| !WurstClient.MC.level.hasChunk(chunk.x, chunk.z))
			return new StructureHit(chunk, setId, ids, false, true);
		
		try
		{
			Holder<Biome> biome = WurstClient.MC.level
				.getBiome(new BlockPos(chunk.getMinBlockX() + 8, 64,
					chunk.getMinBlockZ() + 8));
			
			for(Structure structure : setStructures)
			{
				HolderSet<Biome> allowed = structure.biomes();
				
				if(allowed != null && allowed.contains(biome))
					return new StructureHit(chunk, setId, ids, true, true);
			}
			
			return new StructureHit(chunk, setId, ids, true, false);
		}catch(Exception e)
		{
			return new StructureHit(chunk, setId, ids, false, true);
		}
	}
	
	/**
	 * Reads the fields that have no public accessor back out of the placement
	 * codec. The JSON schema is the datapack format, so this does not depend on
	 * field names.
	 */
	private Params readParams(StructurePlacement placement)
	{
		float frequency = 1.0F;
		int salt = 0;
		int reducer = PlacementFrequency.DEFAULT;
		boolean exclusionZone = false;
		int width = 0;
		int gap = 0;
		boolean triangular = false;
		
		try
		{
			DataResult<JsonElement> result = StructurePlacement.CODEC
				.encodeStart(JsonOps.INSTANCE, placement);
			JsonElement json = result.result().orElse(null);
			
			if(json != null && json.isJsonObject())
			{
				JsonObject object = json.getAsJsonObject();
				
				if(object.has("frequency"))
					frequency = object.get("frequency").getAsFloat();
				
				if(object.has("salt"))
					salt = object.get("salt").getAsInt();
				
				if(object.has("frequency_reducer"))
					reducer = PlacementFrequency
						.fromId(object.get("frequency_reducer").getAsString());
				
				exclusionZone = object.has("exclusion_zone");
				
				if(object.has("spacing"))
					width = object.get("spacing").getAsInt();
				
				if(object.has("separation"))
					gap = object.get("separation").getAsInt();
				
				if(object.has("spread_type"))
					triangular = object.get("spread_type").getAsString()
						.contains("triangular");
			}
		}catch(Exception e)
		{
			// keep the defaults, which make the prediction a superset
		}
		
		return new Params(frequency, salt, reducer, exclusionZone, width, gap,
			triangular);
	}
	
	private void buildSpreads()
	{
		ArrayList<StructureSpread> built = new ArrayList<>();
		
		for(int i = 0; i < placements.size(); i++)
		{
			Params placementParams = params.get(i);
			
			if(placementParams.width <= 0)
				continue;
			
			built.add(new StructureSpread(setIds.get(i), placementParams.width,
				placementParams.gap, placementParams.salt,
				placementParams.triangular, placementParams.frequency,
				placementParams.reducer));
		}
		
		spreads = built;
		spreadCheck = verifySpreads();
	}
	
	/**
	 * Compares the allocation-free re-implementation with the vanilla method for
	 * a sample of chunks, which is cheap and catches any transcription error in
	 * the offsets or the seeding.
	 */
	private String verifySpreads()
	{
		int checked = 0;
		int usable = 0;
		
		for(int i = 0; i < placements.size() && i < spreads.size(); i++)
		{
			RandomSpreadStructurePlacement placement = placements.get(i);
			StructureSpread spread = findSpread(setIds.get(i));
			
			if(spread == null || !spread.isUsable())
				continue;
			
			usable++;
			
			for(int sample = 0; sample < 32; sample++)
			{
				int chunkX = sample * 53 - 700;
				int chunkZ = sample * 97 - 1400;
				ChunkPos vanilla = placement.getPotentialStructureChunk(
					cachedSeed, chunkX, chunkZ);
				int regionX = Math.floorDiv(chunkX, spread.spacing);
				int regionZ = Math.floorDiv(chunkZ, spread.spacing);
				int[] mine =
					spread.potentialChunk(cachedSeed, regionX, regionZ);
				
				if(vanilla.x != mine[0] || vanilla.z != mine[1])
					return "MISMATCH in " + spread.setId + ": vanilla ["
						+ vanilla.x + ", " + vanilla.z + "] vs fast [" + mine[0]
						+ ", " + mine[1] + "]";
				
				checked++;
			}
		}
		
		return checked + " samples of " + usable
			+ " usable structure sets match vanilla";
	}
	
	private StructureSpread findSpread(String setId)
	{
		if(spreads == null)
			return null;
		
		for(StructureSpread spread : spreads)
			if(spread.setId.equals(setId))
				return spread;
		
		return null;
	}
	
	private String keyOf(Registry<Structure> structureRegistry,
		Structure structure)
	{
		try
		{
			return structureRegistry.getKey(structure).toString();
		}catch(Exception e)
		{
			return "?";
		}
	}
	
	private long currentSeed()
	{
		try
		{
			SeedEntry entry = SeedStore.get().current();
			return entry == null ? 0L : entry.seed;
		}catch(Exception e)
		{
			return 0L;
		}
	}
}
