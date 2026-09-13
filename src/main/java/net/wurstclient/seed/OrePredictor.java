/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.seed;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.WorldgenRandom;

/**
 * Reproduces the ore part of vanilla chunk decoration for a known world seed.
 *
 * <p>
 * This is a direct port of meteor-rejects' {@code OreSim#doMathOnChunk},
 * {@code OreSim#generateNormal}, {@code OreSim#generateVeinPart},
 * {@code OreSim#generateHidden} and {@code OreSim#shouldPlace}. The random
 * number consumption order is kept identical to the reference on purpose, so
 * the comments below name the reference method each block was taken from.
 *
 * <p>
 * The class is deliberately free of {@code Minecraft} / {@code Level}
 * singletons: everything it needs about the world arrives through
 * {@link OreContext}, which lets it run in unit tests with a fake world.
 */
public final class OrePredictor
{
	/**
	 * The client integration layer implements this on top of the real level.
	 */
	public interface OreContext
	{
		/** Lowest buildable block of the dimension, e.g. {@code -64}. */
		int minY();
		
		/** Highest buildable block of the dimension, e.g. {@code 319}. */
		int maxY();
		

		/**
		 * @return whether the block at that position is currently air.
		 */
		boolean isAir(int x, int y, int z);
	}
	
	private final long seed;
	private final String dimensionId;
	private final OreContext context;
	private final List<OreRule> rules;
	
	public OrePredictor(long seed, String dimensionId, OreContext context)
	{
		this.seed = seed;
		this.dimensionId = normalize(dimensionId);
		this.context = Objects.requireNonNull(context, "context");
		this.rules = OreRules.forDimension(this.dimensionId);
	}
	
	/**
	 * @return the predicted ore positions of one chunk.
	 */
	public List<BlockPos> predictChunk(ChunkPos pos)
	{
		return new ArrayList<>(predictChunkWithIds(pos).keySet());
	}
	
	/**
	 * @return the predicted ore positions of one chunk, mapped to the block
	 *         id that vanilla would place there, e.g.
	 *         {@code "minecraft:diamond_ore"}. When two rules claim the same
	 *         position the first rule of {@link OreRules#forDimension} wins.
	 */
	public Map<BlockPos, String> predictChunkWithIds(ChunkPos pos)
	{
		Objects.requireNonNull(pos, "pos");
		
		Map<BlockPos, String> result = new LinkedHashMap<>();
		int chunkX = pos.getMinBlockX();
		int chunkZ = pos.getMinBlockZ();
		
		// OreSim#doMathOnChunk. Vanilla ChunkGenerator#applyBiomeDecoration
		// uses an Xoroshiro source, and WorldgenRandom#setDecorationSeed only
		// reseeds it, so the algorithm has to match or every position after the
		// reseed would differ.
		WorldgenRandom random =
			new WorldgenRandom(WorldgenRandom.Algorithm.XOROSHIRO.newInstance(0));
		long populationSeed =
			random.setDecorationSeed(seed, chunkX, chunkZ);
		
		for(OreRule rule : rules)
		{
			// Every rule reseeds the random, so the order in which the rules
			// are visited cannot influence the result.
			random.setFeatureSeed(populationSeed, rule.index, rule.step);
			int repeat = rule.count.sample(random);
			
			for(int i = 0; i < repeat; i++)
			{
				if(rule.rarity != 1F
					&& random.nextFloat() >= 1F / rule.rarity)
					continue;
				
				int x = random.nextInt(16) + chunkX;
				int z = random.nextInt(16) + chunkZ;
				int y = rule.heightProvider.sample(random, context.minY(),
					context.maxY());
				BlockPos origin = new BlockPos(x, y, z);
				
				// OreSim skips origins whose biome does not contain the
				// feature. OreContext has no biome source, so every rule of
				// the dimension is applied everywhere and the result is a
				// superset of the vanilla one.
				List<BlockPos> vein = rule.scattered
					? generateHidden(random, origin, rule.size)
					: generateNormal(random, origin, rule.size,
						rule.discardOnAirChance);
				
				for(BlockPos veinPos : vein)
					result.putIfAbsent(veinPos, rule.blockId);
			}
		}
		
		return result;
	}
	
	public Set<String> oreIds()
	{
		return OreRules.oreIds(dimensionId);
	}
	
	public long seed()
	{
		return seed;
	}
	
	public String dimensionId()
	{
		return dimensionId;
	}
	
	// ====================================
	// Mojang code, as ported by OreSim
	// ====================================
	
	/**
	 * OreSim#generateNormal, which is {@code OreFeature#place}.
	 */
	private List<BlockPos> generateNormal(WorldgenRandom random, BlockPos origin,
		int veinSize, float discardOnAir)
	{
		float angle = random.nextFloat() * (float)Math.PI;
		float halfLength = (float)veinSize / 8.0F;
		int radius =
			Mth.ceil(((float)veinSize / 16.0F * 2.0F + 1.0F) / 2.0F);
		double startX = (double)origin.getX() + Math.sin(angle) * (double)halfLength;
		double endX = (double)origin.getX() - Math.sin(angle) * (double)halfLength;
		double startZ = (double)origin.getZ() + Math.cos(angle) * (double)halfLength;
		double endZ = (double)origin.getZ() - Math.cos(angle) * (double)halfLength;
		double startY = origin.getY() + random.nextInt(3) - 2;
		double endY = origin.getY() + random.nextInt(3) - 2;
		int minX = origin.getX() - Mth.ceil(halfLength) - radius;
		int minY = origin.getY() - 2 - radius;
		int minZ = origin.getZ() - Mth.ceil(halfLength) - radius;
		int xSize = 2 * (Mth.ceil(halfLength) + radius);
		int ySize = 2 * (2 + radius);
		
		// Vanilla asks the OCEAN_FLOOR_WG heightmap, OreSim asks
		// MOTION_BLOCKING. OreContext has no heightmap, so the column height
		// is approximated with the highest non-air block, see
		// approximateHeight. This consumes no randomness, so the vein itself
		// is unaffected - only the "is this vein above the surface" decision
		// can differ.
		for(int x = minX; x <= minX + xSize; x++)
			for(int z = minZ; z <= minZ + xSize; z++)
				if(minY <= approximateHeight(x, z))
					return generateVeinPart(random, veinSize, startX, endX,
						startZ, endZ, startY, endY, minX, minY, minZ, xSize,
						ySize, discardOnAir);
		
		return new ArrayList<>();
	}
	
	/**
	 * OreSim#generateVeinPart, which is {@code OreFeature#doPlace}. The
	 * ellipsoid and the direction of the vein are computed exactly as in the
	 * reference.
	 */
	private List<BlockPos> generateVeinPart(WorldgenRandom random, int veinSize,
		double startX, double endX, double startZ, double endZ, double startY,
		double endY, int minX, int minY, int minZ, int xSize, int ySize,
		float discardOnAir)
	{
		BitSet bitSet = new BitSet(xSize * ySize * xSize);
		BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
		double[] points = new double[veinSize * 4];
		List<BlockPos> poses = new ArrayList<>();
		
		int index;
		double deltaX;
		double deltaY;
		double deltaZ;
		double distance;
		
		for(index = 0; index < veinSize; ++index)
		{
			float progress = (float)index / (float)veinSize;
			deltaX = Mth.lerp(progress, startX, endX);
			deltaY = Mth.lerp(progress, startY, endY);
			deltaZ = Mth.lerp(progress, startZ, endZ);
			distance = random.nextDouble() * (double)veinSize / 16.0D;
			double thickness = ((double)(Mth.sin((float)Math.PI * progress)
				+ 1.0F) * distance + 1.0D) / 2.0D;
			points[index * 4] = deltaX;
			points[index * 4 + 1] = deltaY;
			points[index * 4 + 2] = deltaZ;
			points[index * 4 + 3] = thickness;
		}
		
		for(index = 0; index < veinSize - 1; ++index)
			if(!(points[index * 4 + 3] <= 0.0D))
				for(int other = index + 1; other < veinSize; ++other)
					if(!(points[other * 4 + 3] <= 0.0D))
					{
						deltaX = points[index * 4] - points[other * 4];
						deltaY = points[index * 4 + 1] - points[other * 4 + 1];
						deltaZ = points[index * 4 + 2] - points[other * 4 + 2];
						distance = points[index * 4 + 3] - points[other * 4 + 3];
						
						if(distance * distance > deltaX * deltaX + deltaY * deltaY
							+ deltaZ * deltaZ)
						{
							if(distance > 0.0D)
								points[other * 4 + 3] = -1.0D;
							else
								points[index * 4 + 3] = -1.0D;
						}
					}
		
		for(index = 0; index < veinSize; ++index)
		{
			double radius = points[index * 4 + 3];
			
			if(radius < 0.0D)
				continue;
			
			double centerX = points[index * 4];
			double centerY = points[index * 4 + 1];
			double centerZ = points[index * 4 + 2];
			int x0 = Math.max(Mth.floor(centerX - radius), minX);
			int y0 = Math.max(Mth.floor(centerY - radius), minY);
			int z0 = Math.max(Mth.floor(centerZ - radius), minZ);
			int x1 = Math.max(Mth.floor(centerX + radius), x0);
			int y1 = Math.max(Mth.floor(centerY + radius), y0);
			int z1 = Math.max(Mth.floor(centerZ + radius), z0);
			
			for(int x = x0; x <= x1; ++x)
			{
				double dx = ((double)x + 0.5D - centerX) / radius;
				
				if(dx * dx >= 1.0D)
					continue;
				
				for(int y = y0; y <= y1; ++y)
				{
					double dy = ((double)y + 0.5D - centerY) / radius;
					
					if(dx * dx + dy * dy >= 1.0D)
						continue;
					
					for(int z = z0; z <= z1; ++z)
					{
						double dz = ((double)z + 0.5D - centerZ) / radius;
						
						if(dx * dx + dy * dy + dz * dz >= 1.0D)
							continue;
						
						int bit = x - minX + (y - minY) * xSize
							+ (z - minZ) * xSize * ySize;
						
						if(bitSet.get(bit))
							continue;
						
						bitSet.set(bit);
						mutable.set(x, y, z);
						
						// OreSim hardcodes the overworld bounds -64..319 here,
						// this uses the bounds the context reports.
						if(y < context.minY() || y > context.maxY())
							continue;
						
						if(context.isAir(x, y, z))
							continue;
						
						if(!shouldPlace(mutable, discardOnAir, random))
							continue;
						
						poses.add(mutable.immutable());
					}
				}
			}
		}
		
		return poses;
	}
	
	/**
	 * OreSim#generateHidden, which is
	 * {@code ScatteredOreFeature#place} - the ancient debris veins.
	 */
	private List<BlockPos> generateHidden(WorldgenRandom random, BlockPos origin,
		int veinSize)
	{
		List<BlockPos> poses = new ArrayList<>();
		int count = random.nextInt(veinSize + 1);
		
		for(int i = 0; i < count; ++i)
		{
			int spread = Math.min(i, 7);
			int x = randomCoord(random, spread) + origin.getX();
			int y = randomCoord(random, spread) + origin.getY();
			int z = randomCoord(random, spread) + origin.getZ();
			
			// OreSim only checks the block, the height bounds are added here
			// because the interface advertises a finite world height. The
			// check consumes no randomness.
			if(y < context.minY() || y > context.maxY())
				continue;
			
			if(context.isAir(x, y, z))
				continue;
			
			if(shouldPlace(new BlockPos(x, y, z), 1F, random))
				poses.add(new BlockPos(x, y, z));
		}
		
		return poses;
	}
	
	/**
	 * OreSim#shouldPlace, which is {@code OreFeature#shouldNotDiscard} plus
	 * the air exposure check. Exactly one {@code nextFloat()} is drawn for
	 * every candidate position whose discard chance is strictly between 0 and
	 * 1, which is what keeps the random in sync with the vanilla world.
	 */
	private boolean shouldPlace(BlockPos orePos, float discardOnAir,
		WorldgenRandom random)
	{
		if(discardOnAir == 0F || (discardOnAir != 1F
			&& random.nextFloat() >= discardOnAir))
			return true;
		
		for(Direction direction : Direction.values())
			if(discardOnAir != 1F && context.isAir(
				orePos.getX() + direction.getStepX(),
				orePos.getY() + direction.getStepY(),
				orePos.getZ() + direction.getStepZ()))
				return false;
		
		return true;
	}
	
	/**
	 * OreSim#randomCoord.
	 */
	private static int randomCoord(WorldgenRandom random, int size)
	{
		return Math.round(
			(random.nextFloat() - random.nextFloat()) * (float)size);
	}
	
	/**
	 * Stands in for
	 * {@code LevelReader#getHeight(Heightmap.Types.MOTION_BLOCKING, x, z)},
	 * which {@link OreContext} cannot provide. Scans down from the top of the
	 * world for the highest non-air block and returns the height above it. A
	 * column that is entirely air reports {@link OreContext#minY()}, so
	 * unloaded chunks never make a vein disappear.
	 */
	private int approximateHeight(int x, int z)
	{
		for(int y = context.maxY(); y >= context.minY(); y--)
			if(!context.isAir(x, y, z))
				return y + 1;
		
		return context.minY();
	}
	
	private static String normalize(String dimensionId)
	{
		if(dimensionId == null || dimensionId.isBlank())
			return "minecraft:overworld";
		
		String id = dimensionId.strip().toLowerCase(Locale.ROOT);
		
		if(!id.contains(":"))
			id = "minecraft:" + id;
		
		return id;
	}
}
