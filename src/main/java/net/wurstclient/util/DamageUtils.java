/*
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.WurstClient;

public enum DamageUtils
{
	;
	
	private static final Minecraft MC = WurstClient.MC;
	
	/** 护甲/附魔只在装备变化时改变，所以短 TTL 缓存足够且不怕漏刷新。 */
	private static final long PROFILE_TTL_MS = 250L;
	private static final Map<LivingEntity, CachedProfile> PROFILES =
		Collections.synchronizedMap(new WeakHashMap<>());
	
	/**
	 * 每实体的减伤画像，参考 CakeSlayers/OpenEpsilon 的
	 * {@code CombatUtils.ArmorInfo} 缓存思路。{@code CrystalAura} /
	 * {@code AnchorAura} 会为每个候选点、每个实体调用一次伤害计算，原来每次都要
	 * 重新扫一遍护甲附魔；缓存后同一 tick 内每实体只扫一次。
	 *
	 * <p>
	 * 换世界（level 实例变化）立刻失效，所以不需要外部重置钩子；护甲值本身很少
	 * 变动，250ms 的陈旧窗口最多让一次放置用到换装前的数值。
	 */
	public static DamageProfile profileOf(LivingEntity entity)
	{
		if(entity == null || MC.level == null)
			return DamageProfile.of(0, 0, 0, 0, 1);
		
		long now = System.currentTimeMillis();
		Object level = MC.level;
		CachedProfile cached = PROFILES.get(entity);
		
		if(cached != null && cached.level() == level
			&& now - cached.time() < PROFILE_TTL_MS)
			return cached.profile();
		
		DamageSource source = MC.level.damageSources().explosion(null, null);
		int epf = EnchantmentHelper.getDamageProtection(entity.getArmorSlots(),
			source);
		float resistance = 1F;
		
		if(entity.hasEffect(MobEffects.DAMAGE_RESISTANCE))
		{
			int amplifier =
				entity.getEffect(MobEffects.DAMAGE_RESISTANCE).getAmplifier();
			resistance = Math.max(0F, 1F - (amplifier + 1) * 0.2F);
		}
		
		DamageProfile profile = DamageProfile.of(entity.getArmorValue(),
			(float)entity.getAttributeValue(Attributes.ARMOR_TOUGHNESS), epf,
			0, resistance);
		PROFILES.put(entity, new CachedProfile(profile, now, level));
		return profile;
	}
	
	public static void clearProfileCache()
	{
		PROFILES.clear();
	}
	
	private record CachedProfile(DamageProfile profile, long time,
		Object level)
	{}
	
	/** 血量 + 吸收。 */
	public static float totalHealth(LivingEntity entity)
	{
		return entity == null ? 0F
			: DamageProfile.totalHealth(entity.getHealth(),
				entity.getMaxHealth(), entity.getAbsorptionAmount());
	}
	
	/** 吸收按血量占比折算后的有效血量：越低血，那点吸收越不值钱。 */
	public static float scaledHealth(LivingEntity entity)
	{
		return entity == null ? 0F
			: DamageProfile.scaledHealth(entity.getHealth(),
				entity.getMaxHealth(), entity.getAbsorptionAmount());
	}
	
	/** 这一下能不能打死（把吸收算进去）。 */
	public static boolean isLethal(float damage, LivingEntity entity)
	{
		return entity != null
			&& DamageProfile.isLethal(damage, entity.getHealth(),
				entity.getMaxHealth(), entity.getAbsorptionAmount());
	}
	
	/**
	 * 对目标来一次「标称」爆炸后实际吃到的伤害：不跑暴露度光线投射，只算护甲、
	 * 保护附魔与抗性。用来给目标打分（甲越薄数值越高）。
	 *
	 * <p>
	 * 走 {@link #profileOf} 的每实体缓存，所以可以在打分时对每个候选算一次，
	 * 而不是放进排序比较器里反复算。
	 */
	public static float nominalExplosionDamage(LivingEntity entity,
		float rawDamage)
	{
		if(entity == null || rawDamage <= 0F)
			return 0F;
		
		DamageProfile profile = profileOf(entity);
		float afterAbsorb = CombatRules.getDamageAfterAbsorb(rawDamage,
			profile.armorValue(), profile.toughness());
		return Math.max(0F, profile.apply(afterAbsorb, true));
	}
	
	public static float calculateDamage(Vec3 explosionPos, LivingEntity entity)
	{
		return calculateDamage(explosionPos, entity, 6);
	}

	public static float calculateDamage(Vec3 explosionPos, LivingEntity entity,
		float explosionPower)
	{
		if(entity == null || MC.level == null || explosionPower <= 0)
			return 0;
		
		try
		{
			double exposure = Explosion.getSeenPercent(explosionPos, entity);
			double diameter = explosionPower * 2;
			double dist =
				Math.sqrt(entity.distanceToSqr(explosionPos)) / diameter;
			if(dist > 1.0)
				return 0;
			
			double impact = (1.0 - dist) * exposure;
			float damage = (float)((impact * impact + impact) * 0.5 * 7.0
				* diameter + 1.0);
			damage = applyDifficulty(damage, MC.level.getDifficulty());
			
			/*
			 * 护甲吸收仍走原版；保护附魔与抗性改用缓存的画像。数值与逐次调用
			 * EnchantmentHelper.getDamageProtection 一致（EPF 仍由原版算，
			 * 因为 1.20.1 的附魔是数据驱动的，照抄 1.12.2 的 2*level 会算错）。
			 */
			DamageProfile profile = profileOf(entity);
			damage = CombatRules.getDamageAfterAbsorb(damage,
				profile.armorValue(), profile.toughness());
			return Math.max(0, profile.apply(damage, true));
			
		}catch(Exception e)
		{
			return 0;
		}
	}
	
	public static float applyDifficulty(float damage, Difficulty difficulty)
	{
		return switch(difficulty)
		{
			case PEACEFUL -> 0;
			case EASY -> Math.min(damage * 0.5F + 1, damage);
			case NORMAL -> damage;
			case HARD -> damage * 1.5F;
		};
	}
	
	public static float getTotalDamage(Vec3 explosionPos,
		Iterable<? extends LivingEntity> targets)
	{
		float total = 0;
		for(LivingEntity target : targets)
			total += calculateDamage(explosionPos, target);
		return total;
	}
	
	public static float calculateSelfDamage(Vec3 explosionPos)
	{
		return calculateDamage(explosionPos, MC.player);
	}
	
	public static boolean isDamageWorthwhile(float targetDamage, float selfDamage,
		float minDamage, float maxSelfDamage)
	{
		return targetDamage >= minDamage && selfDamage <= maxSelfDamage;
	}
}
