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
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 锁住减伤画像的组合公式与有效血量。这些数字直接决定「这一下值不值得打」，
 * 抄错了不会编译报错，只会在实战里表现成"伤害预估不对"。
 */
public final class DamageProfileTest
{
	private static final float DELTA = 0.0001F;
	
	@Test
	public void epfMultiplierFollowsTheVanillaCurve()
	{
		DamageProfile none = DamageProfile.of(0, 0, 0, 0, 1F);
		assertEquals(1.0F, none.epfMultiplier(false), DELTA);
		
		// 20 EPF 是原版上限，减伤 20/25
		DamageProfile capped = DamageProfile.of(0, 0, 20, 0, 1F);
		assertEquals(0.2F, capped.epfMultiplier(false), DELTA);
		
		// 超过上限不再继续减伤
		DamageProfile over = DamageProfile.of(0, 0, 25, 0, 1F);
		assertEquals(0.2F, over.epfMultiplier(false), DELTA);
		
		DamageProfile partial = DamageProfile.of(0, 0, 5, 0, 1F);
		assertEquals(0.8F, partial.epfMultiplier(false), DELTA);
	}
	
	@Test
	public void blastProtectionOnlyCountsForExplosions()
	{
		DamageProfile profile = DamageProfile.of(0, 0, 4, 8, 1F);
		
		// 非爆炸只看通用保护
		assertEquals(1F - 4F / 25F, profile.epfMultiplier(false), DELTA);
		// 爆炸时把爆炸保护一起算进去，并同样受 20 上限约束
		assertEquals(1F - 12F / 25F, profile.epfMultiplier(true), DELTA);
		assertEquals(0.2F, DamageProfile.of(0, 0, 20, 10, 1F)
			.epfMultiplier(true), DELTA);
	}
	
	@Test
	public void applyChainsResistanceAndProtection()
	{
		DamageProfile profile = DamageProfile.of(0, 0, 5, 0, 0.8F);
		
		// 100 扣护甲后 → 乘抗性 0.8 → 乘保护 0.8
		assertEquals(64.0F, profile.apply(100F, false), DELTA);
		// 顺序无关：乘法可交换
		assertEquals(100F * 0.8F * 0.8F, profile.apply(100F, false), DELTA);
	}
	
	@Test
	public void resistanceLevelsConvertToMultipliers()
	{
		DamageProfile base = DamageProfile.of(0, 0, 0, 0, 1F);
		
		// 抗性 I（amplifier 0）
		assertEquals(0.8F,
			DamageProfile.withResistance(base, 0).resistanceMultiplier(), DELTA);
		// 抗性 II
		assertEquals(0.6F,
			DamageProfile.withResistance(base, 1).resistanceMultiplier(), DELTA);
		// 抗性 V 正好归零
		assertEquals(0.0F,
			DamageProfile.withResistance(base, 4).resistanceMultiplier(), DELTA);
		// 更高等级不会变成负数
		assertEquals(0.0F,
			DamageProfile.withResistance(base, 9).resistanceMultiplier(), DELTA);
	}
	
	@Test
	public void totalHealthAddsAbsorption()
	{
		assertEquals(18.0F, DamageProfile.totalHealth(10F, 20F, 8F), DELTA);
		assertEquals(10.0F, DamageProfile.totalHealth(10F, 20F, 0F), DELTA);
		// 负血量不参与
		assertEquals(8.0F, DamageProfile.totalHealth(-5F, 20F, 8F), DELTA);
	}
	
	@Test
	public void scaledHealthWeightsAbsorptionByHealthRatio()
	{
		// 满血：吸收全额计入（20 血 + 8 吸收）
		assertEquals(28.0F, DamageProfile.scaledHealth(20F, 20F, 8F), DELTA);
		// 半血：吸收只算一半
		assertEquals(14.0F, DamageProfile.scaledHealth(10F, 20F, 8F), DELTA);
		// 空血：吸收完全不值钱
		assertEquals(0.0F, DamageProfile.scaledHealth(0F, 20F, 8F), DELTA);
	}
	
	@Test
	public void scaledHealthSurvivesAMissingMaxHealth()
	{
		// maxHealth 为 0 时不能出现 NaN 或除零
		assertEquals(7.0F, DamageProfile.scaledHealth(7F, 0F, 8F), DELTA);
	}
	
	@Test
	public void scaledHealthNeverExceedsTotalHealth()
	{
		for(float health = 0F; health <= 20F; health += 2.5F)
		{
			float total = DamageProfile.totalHealth(health, 20F, 8F);
			float scaled = DamageProfile.scaledHealth(health, 20F, 8F);
			assertTrue(scaled <= total + DELTA,
				"scaledHealth 不该超过 totalHealth");
		}
	}
	
	@Test
	public void lethalityCountsAbsorption()
	{
		// 10 血 + 8 吸收 = 18，17.9 打不死、18 正好打死
		assertFalse(DamageProfile.isLethal(17.9F, 10F, 20F, 8F));
		assertTrue(DamageProfile.isLethal(18F, 10F, 20F, 8F));
		assertTrue(DamageProfile.isLethal(30F, 10F, 20F, 8F));
		// 没有吸收时就是单纯比血量
		assertFalse(DamageProfile.isLethal(9.9F, 10F, 20F, 0F));
		assertTrue(DamageProfile.isLethal(10F, 10F, 20F, 0F));
	}
	
	@Test
	public void remainingHealthFloorsAtZero()
	{
		assertEquals(2.0F, DamageProfile.remainingHealth(16F, 10F, 20F, 8F),
			DELTA);
		assertEquals(0.0F, DamageProfile.remainingHealth(100F, 10F, 20F, 8F),
			DELTA);
	}
	
	@Test
	public void ofClampsNonsenseInput()
	{
		DamageProfile profile = DamageProfile.of(-4F, -1F, -3, -9, 4F);
		
		assertEquals(0F, profile.armorValue(), DELTA);
		assertEquals(0F, profile.toughness(), DELTA);
		assertEquals(0, profile.epfGeneric());
		assertEquals(0, profile.epfBlast());
		// 抗性倍率被夹到 0..1
		assertEquals(1F, profile.resistanceMultiplier(), DELTA);
	}
}
