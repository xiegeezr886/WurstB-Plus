/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

/**
 * 战斗目标加权打分，移植自 CakeSlayers/OpenEpsilon 的目标选择（其
 * {@code management/CombatManager.kt}，见 {@code _oe_ref/core-spec.md} §7）。
 *
 * <p>
 * 五个因子各带一个权重，默认各 <b>0.5</b>，总分越大越优先：
 *
 * <pre>
 * total = distF + healthF + armorF + holeF + crosshairF
 *                                     （按总分降序，即 sortedByDescending）
 * reverseAndClamp(x) = 1 - clamp(x, 0, 1)      // 越小越好 → 越大越好
 * distF      = reverseAndClamp(距离 / 8)   * w  // 8 格外饱和
 * healthF    = reverseAndClamp(血量 / 20)  * w  // 20 血外饱和
 * armorF     = (护甲减免后的爆炸伤害 / 20) * w  // 越脆分越高
 * holeF      = (4 个水平邻向中不抗爆的个数 / 4) * w
 * crosshairF = reverseAndClamp(|相对偏航| / 30) * w   // 30° 外饱和
 * </pre>
 *
 * <p>
 * 本工程原来只有按距离之类的单键比较；改成加权打分后，可以表达「近的、残血的、
 * 甲薄的、露在爆点外的、离准心近的」综合最优，而不用为每个 hack 各写一套排序。
 *
 * <p>
 * 纯函数，不含 Minecraft 依赖——权重与饱和点是抄错的常见位置，必须能单测。
 */
public final class TargetScore
{
	/** 参考项目的默认权重，五个因子各 0.5。 */
	public static final float DEFAULT_WEIGHT = 0.5F;
	
	/** 距离在 8 格外饱和。 */
	public static final float DISTANCE_SATURATION = 8F;
	/** 血量在 20 格外饱和。 */
	public static final float HEALTH_SATURATION = 20F;
	/** 相对偏航在 30° 外饱和。 */
	public static final float YAW_SATURATION = 30F;
	/** 护甲因子用的参考伤害（一次 20 点爆炸）。 */
	public static final float ARMOR_REFERENCE_DAMAGE = 20F;
	/** 洞因子看四个水平邻向。 */
	public static final int HOLE_SIDES = 4;
	
	private TargetScore()
	{}
	
	/**
	 * 五个权重。默认全 0.5；把某个权重设为 0 就等价于关掉那个因子。
	 */
	public record Weights(float distance, float health, float armor, float hole,
		float crosshair)
	{
		public static final Weights DEFAULT = new Weights(DEFAULT_WEIGHT,
			DEFAULT_WEIGHT, DEFAULT_WEIGHT, DEFAULT_WEIGHT, DEFAULT_WEIGHT);
		
		public float total()
		{
			return distance + health + armor + hole + crosshair;
		}
	}
	
	/**
	 * 五个因子的原始输入，便于一次性算总分。
	 *
	 * @param distance               与目标的距离（格）
	 * @param health                 目标当前血量
	 * @param explosionDamageAtFull  对目标来一次 20 点爆炸后实际吃到的伤害
	 * @param exposedSides           四个水平邻向里不抗爆的个数（0..4）
	 * @param relativeYawDegrees     目标相对准心的偏航角（度，取绝对值前）
	 */
	public record Inputs(float distance, float health,
		float explosionDamageAtFull, int exposedSides,
		float relativeYawDegrees)
	{
	}
	
	/** 越小越好的量转成越大越好，并把越界值夹到 [0,1]。 */
	public static float reverseAndClamp(float value)
	{
		return 1F - clamp01(value);
	}
	
	public static float distanceFactor(float distance, float weight)
	{
		return reverseAndClamp(distance / DISTANCE_SATURATION) * weight;
	}
	
	public static float healthFactor(float health, float weight)
	{
		return reverseAndClamp(health / HEALTH_SATURATION) * weight;
	}
	
	/**
	 * 护甲因子：一场 20 点爆炸实际能打进多少。越脆分越高。
	 * 减免后的伤害理应落在 [0,20]，但输入来自外部，仍然夹一次免得出界。
	 */
	public static float armorFactor(float explosionDamage, float weight)
	{
		return clamp01(explosionDamage / ARMOR_REFERENCE_DAMAGE) * weight;
	}
	
	/** 洞因子：四个水平邻向里不抗爆的占比。 */
	public static float holeFactor(int exposedSides, float weight)
	{
		int clamped = Math.max(0, Math.min(HOLE_SIDES, exposedSides));
		return clamped / (float)HOLE_SIDES * weight;
	}
	
	public static float crosshairFactor(float relativeYawDegrees, float weight)
	{
		return reverseAndClamp(Math.abs(relativeYawDegrees) / YAW_SATURATION)
			* weight;
	}
	
	public static float score(Inputs inputs, Weights weights)
	{
		return distanceFactor(inputs.distance(), weights.distance())
			+ healthFactor(inputs.health(), weights.health())
			+ armorFactor(inputs.explosionDamageAtFull(), weights.armor())
			+ holeFactor(inputs.exposedSides(), weights.hole())
			+ crosshairFactor(inputs.relativeYawDegrees(), weights.crosshair());
	}
	
	public static float score(Inputs inputs)
	{
		return score(inputs, Weights.DEFAULT);
	}
	
	/**
	 * 打分理论上的最大值：全部因子取最优。用于把总分归一化到 0..1，
	 * 便于和阈值比较（例如"分数低于 X 就不打"）。
	 */
	public static float maxScore(Weights weights)
	{
		return weights.total();
	}
	
	private static float clamp01(float value)
	{
		return Math.max(0F, Math.min(1F, value));
	}
}
