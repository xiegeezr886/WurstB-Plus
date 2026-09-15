/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

import net.wurstclient.util.ArmorUpgradePlanner.Candidate;
import net.wurstclient.util.ArmorUpgradePlanner.Choice;
import net.wurstclient.util.ArmorUpgradePlanner.Piece;
import net.wurstclient.util.ArmorUpgradePlanner.Slot;

/**
 * 锁住 AutoArmor 的换装决策。这些规则写错不会编译报错，只会表现成「换上了更疼的
 * 一件」、「背包满时反复空转」或者「结果随机」，所以逐条钉住。
 */
public final class ArmorUpgradePlannerTest
{
	@Test
	public void scoresAPieceByHowMuchLessTheSetTakes()
	{
		// 这一套从挨 8.4 变成挨 7.6，即少挨 0.8 点 = 800‰
		assertEquals(800,
			ArmorUpgradePlanner.piece(8.4F, 7.6F, 5).gainMilli());
		// 一点提升都没有
		assertEquals(0, ArmorUpgradePlanner.worn(5).gainMilli());
		// 换上更疼的候选，提升为负
		assertEquals(-1000, ArmorUpgradePlanner.piece(10F, 11F, 5).gainMilli());
	}
	
	@Test
	public void durabilityScoreKeepsTheOldCurve()
	{
		assertEquals(5, ArmorUpgradePlanner.durabilityScore(100, 0));
		assertEquals(2, ArmorUpgradePlanner.durabilityScore(100, 50));
		assertEquals(0, ArmorUpgradePlanner.durabilityScore(100, 100));
		// 不可损坏的物品按满分算，与原来的 isDamageableItem() 分支一致
		assertEquals(5, ArmorUpgradePlanner.durabilityScore(0, 0));
		// 耐久值超出上限也不该变成负数
		assertEquals(0, ArmorUpgradePlanner.durabilityScore(100, 150));
	}
	
	/**
	 * 这是原加权和最常见的翻车方式：裸装时皮革靴 + 保护 IV 让这一套挨 8.333，而白板
	 * 下界合金靴是 9.760，但原加权和只看「护甲值 ×5」，会把后者换上去。
	 */
	@Test
	public void rejectsAPieceThatMakesTheSetTakeMoreDamage()
	{
		Piece candidate = ArmorUpgradePlanner.piece(8.333F, 9.76F, 5);
		
		assertEquals(-1427, candidate.gainMilli());
		assertFalse(ArmorUpgradePlanner.isUpgrade(candidate,
			ArmorUpgradePlanner.worn(5)));
		assertNull(choose(slotSet(empty(), worn(5), empty(), empty()),
			List.of(new Candidate(2, 17, candidate)), true));
	}
	
	/** 反过来，换上以后真的少吃伤害就必须换。 */
	@Test
	public void takesAnUpgradeTheOldWeightingMissed()
	{
		Piece candidate = ArmorUpgradePlanner.piece(9.76F, 8.333F, 5);
		
		assertEquals(1427, candidate.gainMilli());
		assertTrue(ArmorUpgradePlanner.isUpgrade(candidate,
			ArmorUpgradePlanner.worn(5)));
		assertEquals(new Choice(2, 17),
			choose(slotSet(empty(), worn(5), empty(), empty()),
				List.of(new Candidate(2, 17, candidate)), true));
	}
	
	/** 减伤完全一样时，耐久更高的那件才值得换（磨坏的白板换成新的）。 */
	@Test
	public void durabilityOnlyBreaksExactTies()
	{
		assertTrue(ArmorUpgradePlanner.isUpgrade(
			ArmorUpgradePlanner.piece(10F, 10F, 5),
			ArmorUpgradePlanner.worn(2)));
		// 同分但更旧：不换
		assertFalse(ArmorUpgradePlanner.isUpgrade(
			ArmorUpgradePlanner.piece(10F, 10F, 1),
			ArmorUpgradePlanner.worn(2)));
		// 只要减伤差一点，再新也不换
		assertFalse(ArmorUpgradePlanner.isUpgrade(
			ArmorUpgradePlanner.piece(10F, 10.1F, 5),
			ArmorUpgradePlanner.worn(0)));
	}
	
	/**
	 * 背包满时，换下旧护甲没有格子放，这一位这一 tick 必须让开；空位不需要格子，
	 * 所以照样补得上，而不是整 tick 空转。
	 */
	@Test
	public void fillsAnEmptySlotEvenWhenTheInventoryIsFull()
	{
		Slot[] slots = slotSet(empty(), worn(5), empty(), empty());
		List<Candidate> candidates = List.of(
			new Candidate(0, 12, ArmorUpgradePlanner.piece(10F, 7F, 5)),
			new Candidate(1, 20, ArmorUpgradePlanner.piece(10F, 6F, 5)));
		
		// 胸位提升更大（4000‰ > 3000‰），但背包满了换不下来，退而补脚位
		assertEquals(new Choice(0, 12), choose(slots, candidates, false));
		// 有空格子时就能选提升最大的那一个
		assertEquals(new Choice(1, 20), choose(slots, candidates, true));
	}
	
	@Test
	public void prefersTheBiggestUpgrade()
	{
		List<Candidate> candidates = List.of(
			new Candidate(0, 12, ArmorUpgradePlanner.piece(10F, 8F, 5)),
			new Candidate(3, 30, ArmorUpgradePlanner.piece(10F, 6F, 5)));
		
		assertEquals(new Choice(3, 30), choose(
			slotSet(empty(), empty(), empty(), empty()), candidates, true));
	}
	
	/** 同一护甲位有两件完全相同的候选时，取背包里靠前的那一件，且与传入顺序无关。 */
	@Test
	public void picksTheLowestSlotAmongIdenticalCandidates()
	{
		Piece piece = ArmorUpgradePlanner.piece(10F, 7F, 5);
		Slot[] slots = slotSet(empty(), empty(), empty(), empty());
		
		assertEquals(new Choice(0, 12), choose(slots,
			List.of(new Candidate(0, 30, piece), new Candidate(0, 12, piece)),
			true));
		assertEquals(new Choice(0, 12), choose(slots,
			List.of(new Candidate(0, 12, piece), new Candidate(0, 30, piece)),
			true));
	}
	
	/** 两个护甲位提升相同时按 脚→腿→胸→头 的固定次序，结果必须可复现。 */
	@Test
	public void breaksEqualGainsBySlotOrder()
	{
		Piece piece = ArmorUpgradePlanner.piece(10F, 6F, 5);
		Slot[] slots = slotSet(empty(), empty(), empty(), empty());
		
		assertEquals(new Choice(0, 12), choose(slots,
			List.of(new Candidate(0, 12, piece), new Candidate(1, 20, piece)),
			true));
		assertEquals(new Choice(0, 12), choose(slots,
			List.of(new Candidate(1, 20, piece), new Candidate(0, 12, piece)),
			true));
	}
	
	/** 开了 Keep elytra 的鞘翅位、带绑定诅咒的护甲位一律不许动。 */
	@Test
	public void neverTouchesAKeptSlot()
	{
		Piece huge = ArmorUpgradePlanner.piece(10F, 2F, 5);
		
		assertNull(choose(slotSet(empty(), empty(), kept(5), empty()),
			List.of(new Candidate(2, 17, huge)), true));
		// 越界的候选不能把整个决策搞崩
		assertNull(choose(slotSet(empty(), empty(), kept(5), empty()),
			List.of(new Candidate(9, 17, huge)), true));
	}
	
	private static Slot[] slotSet(Slot feet, Slot legs, Slot chest, Slot head)
	{
		return new Slot[]{feet, legs, chest, head};
	}
	
	private static Slot empty()
	{
		return new Slot(false, false, ArmorUpgradePlanner.worn(0));
	}
	
	private static Slot worn(int durabilityScore)
	{
		return new Slot(true, false, ArmorUpgradePlanner.worn(durabilityScore));
	}
	
	private static Slot kept(int durabilityScore)
	{
		return new Slot(true, true, ArmorUpgradePlanner.worn(durabilityScore));
	}
	
	private static Choice choose(Slot[] slots, List<Candidate> candidates,
		boolean freeSlotAvailable)
	{
		return ArmorUpgradePlanner.choose(slots, candidates, freeSlotAvailable);
	}
}
