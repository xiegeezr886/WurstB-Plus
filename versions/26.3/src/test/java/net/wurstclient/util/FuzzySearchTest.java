package net.wurstclient.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class FuzzySearchTest
{
	@Test
	void normalizesCamelCaseSeparatorsAndAccents()
	{
		assertEquals("kill aura deja vu",
			FuzzySearch.normalize("KillAura_deja-vu"));
	}

	@Test
	void shortQueriesDoNotReceiveTypoTolerance()
	{
		assertTrue(FuzzySearch.fuzzyWordMatch("flight", "fl"));
		assertFalse(FuzzySearch.fuzzyWordMatch("flight", "xy"));
	}

	@Test
	void requiresEveryQueryWordToMatch()
	{
		assertTrue(FuzzySearch.fuzzyMultiWordMatch("auto sprint movement",
			"auto sprint"));
		assertFalse(FuzzySearch.fuzzyMultiWordMatch("auto sprint movement",
			"auto flight"));
	}

	@Test
	void supportsCompactNamesAndAcronyms()
	{
		assertTrue(FuzzySearch.fuzzyWordMatch("Kill Aura", "killaura"));
		assertTrue(FuzzySearch.fuzzyWordMatch("Kill Aura", "ka"));
	}

	@Test
	void toleratesTransposedCharactersAndSubsequences()
	{
		assertTrue(FuzzySearch.fuzzyWordMatch("killaura", "killarua"));
		assertTrue(FuzzySearch.fuzzyWordMatch("velocity", "vlcty"));
	}

	@Test
	void matchesChineseNamesWithoutLatinTokenRules()
	{
		assertTrue(FuzzySearch.fuzzyWordMatch("自动疾跑", "疾跑"));
		assertFalse(FuzzySearch.fuzzyWordMatch("自动疾跑", "自动飞行"));
	}

	@Test
	void ranksExactMatchesAboveTypoMatches()
	{
		assertTrue(FuzzySearch.score("Killaura", "killaura")
			> FuzzySearch.score("Killaura", "killarua"));
	}
}
