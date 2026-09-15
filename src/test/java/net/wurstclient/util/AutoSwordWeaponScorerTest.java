package net.wurstclient.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import net.minecraft.world.item.Tiers;

final class AutoSwordWeaponScorerTest
{
	private static final float MIN = AutoSwordWeaponScorer.NOT_A_WEAPON;

	@Test
	void picksHighestValueSlot()
	{
		assertEquals(2, AutoSwordWeaponScorer.selectBestHotbarSlot(values(MIN,
			1.6F, 7.0F, MIN, 1.0F, MIN, MIN, MIN, MIN)));
	}

	@Test
	void keepsEarliestSlotOnTie()
	{
		assertEquals(1, AutoSwordWeaponScorer.selectBestHotbarSlot(
			values(MIN, 6.0F, 6.0F, MIN, MIN, MIN, MIN, MIN, MIN)));
	}

	@Test
	void ignoresEmptySlots()
	{
		assertEquals(-1, AutoSwordWeaponScorer.selectBestHotbarSlot(values(MIN,
			MIN, MIN, MIN, MIN, MIN, MIN, MIN, MIN)));
	}

	@Test
	void neverScansPastTheHotbar()
	{
		// 下标 9 属于主背包（网络槽位还要 +36），AutoSword 只能改快捷栏
		assertEquals(0, AutoSwordWeaponScorer.selectBestHotbarSlot(values(1.0F,
			MIN, MIN, MIN, MIN, MIN, MIN, MIN, MIN)));
	}

	@Test
	void refusesToPickUpTheSentinelValue()
	{
		// 哨兵是 Integer.MIN_VALUE，和「没有武器」是同一个值；float 往返会
		// 让它变成一个大于哨兵的数，从而选中空槽——这里把这点钉死
		assertEquals(-1,
			AutoSwordWeaponScorer.selectBestSlot(3, i ->
			{
				return (float)Integer.MIN_VALUE;
			}));
		assertEquals(-1, AutoSwordWeaponScorer.selectBestSlot(3,
			i -> Integer.MIN_VALUE));
	}

	/**
	 * 锁定 DAMAGE 优先级在 1.20.1 原版数值下的实际评分。所有候选都缺了玩家
	 * 自身的 1.0 基础攻击力，但因为是**同一个常数**，相对排序不受影响
	 * （见 AutoSword.md 的对照证据）。
	 */
	@Test
	void damagePriorityOrderIsUnaffectedByTheSharedMissingBaseDamage()
	{
		float diamondSword = Tiers.DIAMOND.getAttackDamageBonus() + 3F;
		float netheriteAxe = Tiers.NETHERITE.getAttackDamageBonus() + 5F;
		assertArrayEquals(new float[]{6F, 9F, MIN},
			new float[]{diamondSword, netheriteAxe, MIN}, 0.0001F);

		assertEquals(1, AutoSwordWeaponScorer
			.selectBestSlot(3, values(diamondSword, netheriteAxe, MIN)));
	}

	/**
	 * SPEED 优先级按原版攻速属性取值排序：钻石剑 1.6、钻石斧 1.0（1.20.1 的
	 * {@code Item} 属性表）。这里刻意用字面量而不是 {@code Items.DIAMOND_SWORD}
	 * 或 {@code ItemUtils.getAttackSpeed}：那两个入口会触发
	 * {@code Registries.<clinit>}，在纯 JUnit 环境里必然抛
	 * {@code ExceptionInInitializerError}（本次实测就是这条，见 AutoSword.md）。
	 * 数值本身来自原版，这里锁的是「分高者胜、并列取小下标」的排序行为。
	 */
	@Test
	void speedPriorityPrefersTheFasterWeapon()
	{
		assertEquals(0, AutoSwordWeaponScorer.selectBestHotbarSlot(
			values(1.6F, 1.0F, MIN, MIN, MIN, MIN, MIN, MIN, MIN)));
		assertEquals(1, AutoSwordWeaponScorer.selectBestHotbarSlot(
			values(1.0F, 1.6F, MIN, MIN, MIN, MIN, MIN, MIN, MIN)));
	}

	private static AutoSwordWeaponScorer.SlotValue values(float... bySlot)
	{
		return i -> i < bySlot.length ? bySlot[i] : MIN;
	}
}
