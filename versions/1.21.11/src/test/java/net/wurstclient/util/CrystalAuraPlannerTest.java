package net.wurstclient.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

final class CrystalAuraPlannerTest
{
	@Test
	void selectsHighestSafeDamageScore()
	{
		List<CrystalAuraPlanner.Candidate<String>> candidates = List.of(
			new CrystalAuraPlanner.Candidate<>("unsafe", 14, 9, 1, 10),
			new CrystalAuraPlanner.Candidate<>("balanced", 10, 2, 4, 10),
			new CrystalAuraPlanner.Candidate<>("strong", 12, 5, 9, 10));

		CrystalAuraPlanner.Candidate<String> best =
			CrystalAuraPlanner.selectBest(candidates, 6, 8, 0, 20, true, 0);

		assertEquals("strong", best.value());
	}

	@Test
	void rejectsYoungAndSuicidalCrystals()
	{
		List<CrystalAuraPlanner.Candidate<String>> candidates = List.of(
			new CrystalAuraPlanner.Candidate<>("young", 12, 2, 1, 1),
			new CrystalAuraPlanner.Candidate<>("lethal", 12, 10, 1, 10));

		assertNull(CrystalAuraPlanner.selectBest(candidates, 6, 12, 0, 10,
			true, 2));
	}

	@Test
	void enforcesDamageAdvantage()
	{
		CrystalAuraPlanner.Candidate<String> trade =
			new CrystalAuraPlanner.Candidate<>("trade", 8, 7, 1, 10);

		assertFalse(CrystalAuraPlanner.isAllowed(trade, 6, 8, 2, 20, true,
			0));
	}
}
