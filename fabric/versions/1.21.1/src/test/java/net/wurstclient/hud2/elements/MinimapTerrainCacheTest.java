package net.wurstclient.hud2.elements;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class MinimapTerrainCacheTest
{
	@Test
	void mapsNegativeBlocksToCorrectChunkAndLocalCoordinate()
	{
		assertEquals(-1, MinimapTerrainCache.chunkCoordinate(-1));
		assertEquals(15, MinimapTerrainCache.localCoordinate(-1));
		assertEquals(-2, MinimapTerrainCache.chunkCoordinate(-17));
		assertEquals(15, MinimapTerrainCache.localCoordinate(-17));
	}

	@Test
	void packsSignedChunkCoordinatesWithoutCollisions()
	{
		assertNotEquals(MinimapTerrainCache.chunkKey(-1, 0),
			MinimapTerrainCache.chunkKey(0, -1));
		assertNotEquals(MinimapTerrainCache.chunkKey(-1, -1),
			MinimapTerrainCache.chunkKey(1, 1));
	}

	@Test
	void expiresTilesAtConfiguredLifetime()
	{
		long refreshedTick = 100;
		assertFalse(MinimapTerrainCache.isExpired(refreshedTick,
			refreshedTick + MinimapTerrainCache.TILE_TTL - 1));
		assertTrue(MinimapTerrainCache.isExpired(refreshedTick,
			refreshedTick + MinimapTerrainCache.TILE_TTL));
	}
}
