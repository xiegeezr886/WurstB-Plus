package net.wurstclient.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.Difficulty;
import org.junit.jupiter.api.Test;

final class DamageUtilsTest
{
	@Test
	void appliesVanillaDifficultyScaling()
	{
		assertEquals(0, DamageUtils.applyDifficulty(10, Difficulty.PEACEFUL));
		assertEquals(6, DamageUtils.applyDifficulty(10, Difficulty.EASY));
		assertEquals(10, DamageUtils.applyDifficulty(10, Difficulty.NORMAL));
		assertEquals(15, DamageUtils.applyDifficulty(10, Difficulty.HARD));
	}

	@Test
	void checksTargetAndSelfDamageTogether()
	{
		assertTrue(DamageUtils.isDamageWorthwhile(7, 5, 6, 6));
		assertFalse(DamageUtils.isDamageWorthwhile(5, 5, 6, 6));
		assertFalse(DamageUtils.isDamageWorthwhile(7, 7, 6, 6));
	}
}
