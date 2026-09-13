/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

/**
 * 一个实体的「减伤画像」：把一次昂贵的护甲/附魔/抗性扫描压成几个系数，
 * 于是同一 tick 内对同一个实体评估 N 个爆炸候选点时只需付一次扫描成本。
 *
 * <p>
 * 设计参考 CakeSlayers/OpenEpsilon（1.12.2）的 {@code util/combat/DamageReduction.kt}
 * 与 {@code CombatUtils.kt} 的 {@code ArmorInfo} 缓存——它每 tick 为每个实体缓存一次
 * 护甲值与韧性，并把保护附魔预先算成系数。本工程原来的 {@link DamageUtils} 在每次
 * 调用里都跑一遍 {@code EnchantmentHelper.getDamageProtection}，在
 * {@code CrystalAura} / {@code AnchorAura} 这类「候选点 × 实体」的循环里是纯浪费。
 *
 * <p>
 * 与参考项目不同的是：**这里不重写附魔公式**。protection 的 EPF 仍然由原版的
 * {@code EnchantmentHelper.getDamageProtection} 算出来（1.20.1 的附魔是数据驱动的，
 * 照抄 1.12.2 的 {@code 2 * level} 约定会算错），本类只负责把算好的 EPF 与其它系数
 * 组合起来，因此是**纯优化、不改数值**。
 *
 * <p>
 * 另外补上参考项目有而本工程原来没有的**吸收（absorption）意识**：
 * {@code totalHealth} 与 {@code scaledHealth} 是判断「这一下能不能打死 / 会不会掉图腾」
 * 的基础，参考项目用 {@code health + absorption * (health / maxHealth)} 作为缩放后的
 * 有效血量。
 *
 * <p>
 * 故意不含 Minecraft 依赖，好让组合公式与有效血量可被单测覆盖。
 */
public record DamageProfile(float armorValue, float toughness, int epfGeneric,
	int epfBlast, float resistanceMultiplier)
{
	/** 原版把 EPF 上限压在 20，减伤系数为 1 - epf/25。 */
	public static final int EPF_CAP = 20;
	public static final float EPF_DIVISOR = 25.0F;
	
	/** 参考项目在「目标抗性未知」时假定的抗性倍率（抗性 I 的等效值）。 */
	public static final float ASSUMED_RESISTANCE = 0.8F;
	
	public static DamageProfile of(float armorValue, float toughness,
		int epfGeneric, int epfBlast, float resistanceMultiplier)
	{
		return new DamageProfile(Math.max(0F, armorValue),
			Math.max(0F, toughness), Math.max(0, epfGeneric),
			Math.max(0, epfBlast), clamp01(resistanceMultiplier));
	}
	
	/** 抗性药水等级换算成倍率；{@code amplifier} 是 0 基的等级。 */
	public static DamageProfile withResistance(DamageProfile profile,
		int amplifier)
	{
		return new DamageProfile(profile.armorValue(), profile.toughness(),
			profile.epfGeneric(), profile.epfBlast(),
			Math.max(0F, 1F - (amplifier + 1) * 0.2F));
	}
	
	/**
	 * 已扣过护甲的伤害再过抗性与保护附魔。护甲那一步仍然交给原版的
	 * {@code CombatRules.getDamageAfterAbsorb}，不在这里重算。
	 */
	public float apply(float afterAbsorb, boolean explosion)
	{
		return afterAbsorb * resistanceMultiplier * epfMultiplier(explosion);
	}
	
	public float epfMultiplier(boolean explosion)
	{
		int epf = epfGeneric + (explosion ? epfBlast : 0);
		return 1F - Math.min(epf, EPF_CAP) / EPF_DIVISOR;
	}
	
	/** 血量 + 吸收，用来判断「还剩多少能扛」。 */
	public static float totalHealth(float health, float maxHealth,
		float absorption)
	{
		return Math.max(0F, health) + Math.max(0F, absorption);
	}
	
	/**
	 * 参考项目的 scaledHealth：吸收按当前血量占比折算。
	 * 血量越低，吸收越不值钱——这正是「残血时那点吸收扛不住一下」的直觉。
	 */
	public static float scaledHealth(float health, float maxHealth,
		float absorption)
	{
		float capped = Math.max(0F, health);
		float ratio = maxHealth <= 0F ? 0F : Math.min(1F, capped / maxHealth);
		return capped + Math.max(0F, absorption) * ratio;
	}
	
	public static boolean isLethal(float damage, float health, float maxHealth,
		float absorption)
	{
		return damage >= totalHealth(health, maxHealth, absorption);
	}
	
	/** 这一下打完之后还剩多少「有效血量」，用来比较两次攻击的收益。 */
	public static float remainingHealth(float damage, float health,
		float maxHealth, float absorption)
	{
		return Math.max(0F, totalHealth(health, maxHealth, absorption) - damage);
	}
	
	private static float clamp01(float value)
	{
		return Math.max(0F, Math.min(1F, value));
	}
}
