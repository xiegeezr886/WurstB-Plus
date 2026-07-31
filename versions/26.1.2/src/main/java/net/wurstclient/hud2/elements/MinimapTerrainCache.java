package net.wurstclient.hud2.elements;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

final class MinimapTerrainCache
{
	static final int CHUNK_SIZE = 16;
	static final int UNKNOWN_COLOR = 0xFF181B20;
	static final long TILE_TTL = 100;
	private static final int MAX_TILES = 256;

	private final LinkedHashMap<Long, TerrainTile> tiles =
		new LinkedHashMap<>(64, 0.75F, true)
		{
			@Override
			protected boolean removeEldestEntry(
				Map.Entry<Long, TerrainTile> eldest)
			{
				return size() > MAX_TILES;
			}
		};
	private ResourceKey<Level> dimension;

	public boolean refreshVisible(ClientLevel level, int centerX, int centerZ,
		int radius, long gameTime, int budget)
	{
		boolean changed = ensureDimension(level.dimension());
		int minChunkX = chunkCoordinate(centerX - radius);
		int maxChunkX = chunkCoordinate(centerX + radius);
		int minChunkZ = chunkCoordinate(centerZ - radius);
		int maxChunkZ = chunkCoordinate(centerZ + radius);
		int centerChunkX = chunkCoordinate(centerX);
		int centerChunkZ = chunkCoordinate(centerZ);
		int maxDistance = Math.max(
			Math.max(centerChunkX - minChunkX, maxChunkX - centerChunkX),
			Math.max(centerChunkZ - minChunkZ, maxChunkZ - centerChunkZ));

		int refreshed = 0;
		for(int pass = 0; pass < 2 && refreshed < budget; pass++)
			for(int distance = 0;
				distance <= maxDistance && refreshed < budget; distance++)
				for(int chunkZ = centerChunkZ - distance;
					chunkZ <= centerChunkZ + distance
						&& refreshed < budget;
					chunkZ++)
					for(int chunkX = centerChunkX - distance;
						chunkX <= centerChunkX + distance
							&& refreshed < budget;
						chunkX++)
					{
						if(Math.max(Math.abs(chunkX - centerChunkX),
							Math.abs(chunkZ - centerChunkZ)) != distance
							|| chunkX < minChunkX || chunkX > maxChunkX
							|| chunkZ < minChunkZ || chunkZ > maxChunkZ)
							continue;

						long key = chunkKey(chunkX, chunkZ);
						TerrainTile tile = tiles.get(key);
						boolean needsRefresh = pass == 0 ? tile == null
							: tile != null
								&& isExpired(tile.refreshedTick(), gameTime);
						if(!needsRefresh
							|| !isChunkLoaded(level, chunkX, chunkZ))
							continue;

						tiles.put(key,
							renderTile(level, chunkX, chunkZ, gameTime));
						refreshed++;
						changed = true;
					}
		return changed;
	}

	public int colorAt(int worldX, int worldZ)
	{
		TerrainTile tile = tiles
			.get(chunkKey(chunkCoordinate(worldX), chunkCoordinate(worldZ)));
		if(tile == null)
			return UNKNOWN_COLOR;
		return tile.colors()[localCoordinate(worldZ) * CHUNK_SIZE
			+ localCoordinate(worldX)];
	}

	public int size()
	{
		return tiles.size();
	}

	public void clear()
	{
		tiles.clear();
		dimension = null;
	}

	private boolean ensureDimension(ResourceKey<Level> currentDimension)
	{
		if(currentDimension.equals(dimension))
			return false;
		tiles.clear();
		dimension = currentDimension;
		return true;
	}

	private TerrainTile renderTile(ClientLevel level, int chunkX, int chunkZ,
		long gameTime)
	{
		int[] colors = new int[CHUNK_SIZE * CHUNK_SIZE];
		int[] heights = new int[colors.length];
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int minHeight = level.getMinY() * 16;
		int worldStartX = chunkX * CHUNK_SIZE;
		int worldStartZ = chunkZ * CHUNK_SIZE;

		for(int localZ = 0; localZ < CHUNK_SIZE; localZ++)
			for(int localX = 0; localX < CHUNK_SIZE; localX++)
			{
				int index = localZ * CHUNK_SIZE + localX;
				int worldX = worldStartX + localX;
				int worldZ = worldStartZ + localZ;
				int surface = Math.max(minHeight,
					level.getHeight(Heightmap.Types.WORLD_SURFACE, worldX,
						worldZ) - 1);
				heights[index] = surface;
				colors[index] = findSurfaceColor(level, pos, worldX, worldZ,
					surface, minHeight);
			}

		for(int localZ = 0; localZ < CHUNK_SIZE; localZ++)
			for(int localX = 0; localX < CHUNK_SIZE; localX++)
			{
				int index = localZ * CHUNK_SIZE + localX;
				int northHeight = localZ == 0 ? heights[index]
					: heights[index - CHUNK_SIZE];
				int argb = colors[index];
				if(argb != UNKNOWN_COLOR)
					colors[index] = shade(argb,
						brightnessFor(heights[index], northHeight));
			}
		return new TerrainTile(colors, gameTime);
	}

	private int findSurfaceColor(ClientLevel level,
		BlockPos.MutableBlockPos pos, int worldX, int worldZ, int surface,
		int minHeight)
	{
		for(int depth = 0; depth < 8 && surface - depth >= minHeight; depth++)
		{
			pos.set(worldX, surface - depth, worldZ);
			BlockState state = level.getBlockState(pos);
			MapColor color = state.getMapColor(level, pos);
			if(color != MapColor.NONE)
				return color.col | 0xFF000000;
		}
		return UNKNOWN_COLOR;
	}

	private static int shade(int argb, MapColor.Brightness brightness)
	{
		int multiplier = switch(brightness)
		{
			case LOWEST -> 135;
			case LOW -> 180;
			case NORMAL -> 220;
			case HIGH -> 255;
		};
		int red = (argb >> 16 & 0xFF) * multiplier / 255;
		int green = (argb >> 8 & 0xFF) * multiplier / 255;
		int blue = (argb & 0xFF) * multiplier / 255;
		return 0xFF000000 | red << 16 | green << 8 | blue;
	}

	private static boolean isChunkLoaded(ClientLevel level, int chunkX,
		int chunkZ)
	{
		return level.hasChunkAt(new BlockPos(chunkX * CHUNK_SIZE,
			level.getMinY() * 16, chunkZ * CHUNK_SIZE));
	}

	static int chunkCoordinate(int blockCoordinate)
	{
		return Math.floorDiv(blockCoordinate, CHUNK_SIZE);
	}

	static int localCoordinate(int blockCoordinate)
	{
		return Math.floorMod(blockCoordinate, CHUNK_SIZE);
	}

	static long chunkKey(int chunkX, int chunkZ)
	{
		return (long)chunkX & 0xFFFFFFFFL
			| ((long)chunkZ & 0xFFFFFFFFL) << 32;
	}

	static boolean isExpired(long refreshedTick, long gameTime)
	{
		return gameTime - refreshedTick >= TILE_TTL;
	}

	static MapColor.Brightness brightnessFor(int height, int northHeight)
	{
		if(height > northHeight)
			return MapColor.Brightness.HIGH;
		if(height < northHeight)
			return MapColor.Brightness.LOW;
		return MapColor.Brightness.NORMAL;
	}

	private record TerrainTile(int[] colors, long refreshedTick)
	{
	}
}
