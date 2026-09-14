package net.wurstclient.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

final class HoleFillPolicyTest
{
	@Test
	void acceptsTwoHighGapAboveNonAirFloor()
	{
		assertTrue(HoleFillPolicy.isHole(world(Set.of("0:-1:0")), 0, 0, 0));
	}

	@Test
	void acceptsReplaceableFloorThatIsNotAir()
	{
		assertTrue(HoleFillPolicy.isHole(
			world(Set.of(), Set.of("0:-1:0")), 0, 0, 0));
	}

	@Test
	void rejectsGapWithAirFloorSolidFeetOrSolidHead()
	{
		assertFalse(HoleFillPolicy.isHole(world(Set.of()), 0, 0, 0));
		assertFalse(HoleFillPolicy.isHole(
			world(Set.of("0:-1:0", "0:0:0")), 0, 0, 0));
		assertFalse(HoleFillPolicy.isHole(
			world(Set.of("0:-1:0", "0:1:0")), 0, 0, 0));
	}

	@Test
	void countsBoxesInsideTheHoleAsOccupied()
	{
		assertTrue(HoleFillPolicy.isOccupiedBy(0.2, 0, 0.2, 0.8, 1.8, 0.8,
			0, 0, 0));
		assertTrue(HoleFillPolicy.isOccupiedBy(-0.5, 0, 0.2, 0.5, 1.8, 0.8,
			0, 0, 0));
	}

	@Test
	void ignoresBoxesThatOnlyTouchTheHole()
	{
		assertFalse(HoleFillPolicy.isOccupiedBy(0.2, 2, 0.2, 0.8, 3.8, 0.8,
			0, 0, 0));
		assertFalse(HoleFillPolicy.isOccupiedBy(1, 0, 0.2, 1.6, 1.8, 0.8,
			0, 0, 0));
		assertFalse(HoleFillPolicy.isOccupiedBy(1.2, 0, 0.2, 1.8, 1.8, 0.8,
			0, 0, 0));
	}

	/**
	 * 一个只认识实心方块坐标的假世界，其余位置按空气处理。
	 */
	private static HoleFillPolicy.BlockLookup world(Set<String> solid)
	{
		return world(solid, Set.of());
	}

	private static HoleFillPolicy.BlockLookup world(Set<String> solid,
		Set<String> replaceable)
	{
		return (x, y, z) ->
		{
			String key = x + ":" + y + ":" + z;
			if(solid.contains(key))
				return HoleFillPolicy.BlockKind.SOLID;
			if(replaceable.contains(key))
				return HoleFillPolicy.BlockKind.REPLACEABLE;
			return HoleFillPolicy.BlockKind.AIR;
		};
	}
}
