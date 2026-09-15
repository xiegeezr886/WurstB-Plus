package net.wurstclient.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.wurstclient.util.AntiBotPredicate.Snapshot;
import net.wurstclient.util.AntiBotPredicate.Verdict;

final class AntiBotPredicateTest
{
	@Test
	void acceptsOrdinaryPlayer()
	{
		assertEquals(Verdict.HUMAN, AntiBotPredicate.classify(ordinary(),
			Map.of("wurst", 1), defaults()));
	}

	@Test
	void flagsMissingTabEntry()
	{
		assertEquals(Verdict.BOT, AntiBotPredicate.classify(
			snapshot("wurst", false, true, 50, false, false, 0F, 20F, 20F, 1234,
				400),
			Map.of("wurst", 1), defaults()));
	}

	@Test
	void flagsTabEntryWithoutGameMode()
	{
		assertEquals(Verdict.BOT, AntiBotPredicate.classify(
			snapshot("wurst", true, false, 50, false, false, 0F, 20F, 20F, 1234,
				400),
			Map.of("wurst", 1), defaults()));
	}

	@Test
	void ignoresPingUnlessEnabled()
	{
		assertEquals(Verdict.HUMAN, AntiBotPredicate.classify(
			snapshot("wurst", true, true, 0, false, false, 0F, 20F, 20F, 1234,
				400),
			Map.of("wurst", 1), defaults()));

		AntiBotSettings withPing = new AntiBotSettings(true, true, true, false,
			true, true, true, true, true, true, 5);
		assertEquals(Verdict.BOT, AntiBotPredicate.classify(
			snapshot("wurst", true, true, 0, false, false, 0F, 20F, 20F, 1234,
				400),
			Map.of("wurst", 1), withPing));
		assertEquals(Verdict.HUMAN, AntiBotPredicate.classify(
			snapshot("wurst", true, true, 30, false, false, 0F, 20F, 20F, 1234,
				400),
			Map.of("wurst", 1), withPing));
	}

	/**
	 * 单 tick 的「贴地但竖直速度非零」只有升格成连续成立才会被判据消费，
	 * 这是把真人从该判据里救出来的关键。
	 */
	@Test
	void impossibleGroundIsFirstDerived()
	{
		assertTrue(AntiBotPredicate.isImpossibleGroundState(true, 0.42));
		assertTrue(AntiBotPredicate.isImpossibleGroundState(true, -0.42));
		assertFalse(AntiBotPredicate.isImpossibleGroundState(true, 0.0));
		assertFalse(AntiBotPredicate.isImpossibleGroundState(false, 0.42));
		assertFalse(AntiBotPredicate.isImpossibleGroundState(true, 0.1));
	}

	@Test
	void flagsInvisibleOnlyWhenEnabled()
	{
		assertEquals(Verdict.BOT, AntiBotPredicate.classify(
			snapshot("wurst", true, true, 50, false, true, 0F, 20F, 20F, 1234,
				400),
			Map.of("wurst", 1), defaults()));

		AntiBotSettings withoutInvisible = new AntiBotSettings(true, true,
			false, false, false, true, true, true, true, true, 5);
		assertEquals(Verdict.HUMAN, AntiBotPredicate.classify(
			snapshot("wurst", true, true, 50, false, true, 0F, 20F, 20F, 1234,
				400),
			Map.of("wurst", 1), withoutInvisible));
	}

	@Test
	void flagsIllegalPitchAndHealthAndEntityId()
	{
		assertEquals(Verdict.BOT, AntiBotPredicate.classify(
			snapshot("wurst", true, true, 50, false, false, -95F, 20F, 20F,
				1234, 400),
			Map.of("wurst", 1), defaults()));
		assertEquals(Verdict.BOT, AntiBotPredicate.classify(
			snapshot("wurst", true, true, 50, false, false, 0F, 21F, 20F, 1234,
				400),
			Map.of("wurst", 1), defaults()));
		assertEquals(Verdict.BOT, AntiBotPredicate.classify(
			snapshot("wurst", true, true, 50, false, false, 0F, Float.NaN, 20F,
				1234, 400),
			Map.of("wurst", 1), defaults()));
		assertEquals(Verdict.BOT, AntiBotPredicate.classify(
			snapshot("wurst", true, true, 50, false, false, 0F, 20F, 20F,
				1_000_000_001, 400),
			Map.of("wurst", 1), defaults()));
	}

	@Test
	void flagsDuplicateProfileName()
	{
		assertEquals(Verdict.BOT, AntiBotPredicate.classify(ordinary(),
			Map.of("wurst", 2), defaults()));
	}

	@Test
	void flagsSuspiciousUuidPatterns()
	{
		assertTrue(AntiBotPredicate
			.isSuspiciousUuid("00000000-0000-0000-0000-000000000000"));
		assertTrue(AntiBotPredicate
			.isSuspiciousUuid("12345678-1234-1234-1234-000000000000"));
		assertFalse(AntiBotPredicate
			.isSuspiciousUuid("01234567-89ab-cdef-0123-456789abcdef"));
	}

	/**
	 * 最小年龄必须仍然返回 {@link Verdict#BOT}：`AntiBotHack.isBot()` 只读
	 * `detectedBots`，而 `EntityUtils.IS_ATTACKABLE` 只问 `isBot()`，所以
	 * 「不返回 BOT」等于让刚进服的真人立刻可被攻击——与设置项「暂时忽略」
	 * 的说明相反。
	 */
	@Test
	void treatsTooYoungAsBotSoCombatStillIgnoresThem()
	{
		assertEquals(Verdict.BOT, AntiBotPredicate.classify(
			snapshot("wurst", true, true, 50, false, false, 0F, 20F, 20F, 1234,
				0),
			Map.of("wurst", 1), defaults()));
		assertEquals(Verdict.BOT, AntiBotPredicate.classify(
			snapshot("wurst", true, true, 50, false, false, 0F, 20F, 20F, 1234,
				4),
			Map.of("wurst", 1), defaults()));
		assertEquals(Verdict.HUMAN, AntiBotPredicate.classify(
			snapshot("wurst", true, true, 50, false, false, 0F, 20F, 20F, 1234,
				5),
			Map.of("wurst", 1), defaults()));
	}

	@Test
	void normalizeNameIsLocaleIndependentLowerCase()
	{
		assertEquals("wurst", AntiBotPredicate.normalizeName("Wurst"));
		assertEquals("wurst", AntiBotPredicate.normalizeName("WURST"));
	}

	private static AntiBotSettings defaults()
	{
		return new AntiBotSettings(true, true, false, false, true, true, true,
			true, true, true, 5);
	}

	private static Snapshot ordinary()
	{
		return snapshot("wurst", true, true, 50, false, false, 0F, 20F, 20F,
			1234, 400);
	}

	private static Snapshot snapshot(String name, boolean hasPlayerInfo,
		boolean hasGameMode, int latency, boolean impossibleGround,
		boolean invisible, float pitch, float health, float maxHealth,
		int entityId, int tickCount)
	{
		return new Snapshot(name, hasPlayerInfo, hasGameMode, latency,
			impossibleGround, invisible, pitch, health, maxHealth, entityId,
			UUID.randomUUID().toString(), tickCount);
	}
}
