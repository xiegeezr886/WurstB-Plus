/*
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.WurstClient;

public enum DamageUtils
{
	;
	
	private static final Minecraft MC = WurstClient.MC;

	private static double getSeenPercent(Vec3 explosionPos,
		LivingEntity entity)
	{
		AABB box = entity.getBoundingBox();
		int hits = 0;
		int samples = 0;
		for(int x = 0; x <= 1; x++)
		for(int y = 0; y <= 1; y++)
		for(int z = 0; z <= 1; z++)
		{
			Vec3 target = new Vec3(x == 0 ? box.minX : box.maxX,
				y == 0 ? box.minY : box.maxY, z == 0 ? box.minZ : box.maxZ);
			ClipContext context = new ClipContext(explosionPos, target,
				ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity);
			BlockHitResult hit = MC.level.clip(context);
			samples++;
			if(hit.getType() != HitResult.Type.BLOCK)
				hits++;
		}
		return samples == 0 ? 1 : hits / (double)samples;
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
			// Explosion.getSeenPercent() was removed in 26.1.2; sample
			// the entity bounding box corners with block-collision raycasts
			// to approximate the original exposure calculation.
			double exposure = getSeenPercent(explosionPos, entity);
			double diameter = explosionPower * 2;
			double dist =
				Math.sqrt(entity.distanceToSqr(explosionPos)) / diameter;
			if(dist > 1.0)
				return 0;
			
			double impact = (1.0 - dist) * exposure;
			float damage = (float)((impact * impact + impact) * 0.5 * 7.0
				* diameter + 1.0);
			damage = applyDifficulty(damage, MC.level.getLevelData().getDifficulty());
			DamageSource source = MC.level.damageSources().explosion(null, null);
			damage = CombatRules.getDamageAfterAbsorb(entity, damage, source,
				entity.getArmorValue(), (float)entity
					.getAttributeValue(Attributes.ARMOR_TOUGHNESS));

			int protection = 0;
			for(EquipmentSlot slot : EquipmentSlot.values())
			{
				if(!slot.isArmor())
					continue;
				ItemStack armor = entity.getItemBySlot(slot);
				protection += EnchantmentUtils.getLevel(Enchantments.PROTECTION,
					armor) + EnchantmentUtils
						.getLevel(Enchantments.BLAST_PROTECTION, armor) * 2;
			}
			protection = Math.min(20, protection);
			damage = CombatRules.getDamageAfterMagicAbsorb(damage, protection);

			if(entity.hasEffect(MobEffects.RESISTANCE))
			{
				int amplifier = entity.getEffect(MobEffects.RESISTANCE)
					.getAmplifier();
				damage *= Math.max(0, 25 - (amplifier + 1) * 5) / 25F;
			}
			return Math.max(0, damage);
			
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
