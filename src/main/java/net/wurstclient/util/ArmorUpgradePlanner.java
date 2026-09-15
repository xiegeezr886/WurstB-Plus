/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 * Copyright (c) 2015-2026 CCBlueX
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

import java.util.Arrays;
import java.util.List;

/**
 * AutoArmor 的换装决策：哪一件更值得穿、这一 tick 该动哪个护甲位。
 *
 * <p>
 * 原实现按「护甲值 ×5 + 保护等级 ×3 + 韧性 + 耐久分」给每件护甲**单独**打分，再比
 * 分数。那套权重是拍出来的，换算比例相当于「1 点护甲值 = 1.67 级保护」，只在总护甲
 * 约 15 点、挨 10 点伤害附近碰巧贴近原版曲线；总护甲一低（刚复活、只捡到一两件），
 * 原版会把护甲收益压在 {@code 0.2 × 护甲值} 这个下限上，那 1.67 的换算这时把保护
 * 附魔算得太便宜，于是会出现「用下界合金靴换掉保护 IV 皮革靴」这种越换越疼的结果。
 *
 * <p>
 * 这里改成参考项目 CakeSlayers/OpenEpsilon 的口径：{@code AutoArmour.kt} 只用
 * 「护甲值 + 保护等级」排序（所以它比本工程原来的加权和更粗糙），而
 * {@code util/combat/DamageReduction.kt} 是按**整支队伍的 armorInventoryList**
 * 求和后再走原版 {@code CombatRules.getDamageAfterAbsorb}，也就是「换上它以后这一套
 * 实际少吃多少伤害」。本类采用后者：调用方把「现在这一套挨多少」与「换上候选以后挨
 * 多少」交进来，于是「换上会让这一套更疼」的候选天然被拒；耐久只在减伤完全相同时才
 * 参与比较（原实现里耐久能顶掉约 1 点护甲值的减伤）。
 *
 * <p>
 * 刻意不含 Minecraft 依赖：伤害测量仍由原版的 {@code CombatRules} 和本包的
 * {@link DamageProfile} 在 hack 里完成（与 {@code DamageProfile} 的分工一致——它也不
 * 重算护甲那一步），本类只负责比较与选择，因此可以被单测覆盖。
 */
public final class ArmorUpgradePlanner
{
	/** 名义命中伤害：一次普通近战命中的量级，只影响排序刻度。 */
	public static final float NOMINAL_DAMAGE = 10F;
	
	/** 耐久分满分，与原实现的 durabilityScore 同一口径。 */
	private static final int DURABILITY_MAX = 5;
	
	/** 提升量的编码比例，见 {@code gain(Piece, Piece)}。 */
	private static final int GAIN_SCALE = 100;
	
	private ArmorUpgradePlanner()
	{}
	
	/**
	 * 一件候选换上之后的表现。
	 *
	 * @param gainMilli
	 *            这一套少挨的伤害（千分之一点），越高越好，负数表示换上它反而更疼
	 * @param durabilityScore
	 *            0..5 的耐久分，只在 {@code gainMilli} 相同时参与比较
	 */
	public record Piece(int gainMilli, int durabilityScore)
	{}
	
	/**
	 * 一个护甲位现在的状态。
	 *
	 * @param occupied
	 *            这一位有没有东西（有的话换下来需要一个空格子放它）
	 * @param keep
	 *            这一位不许动（开了 Keep elytra 的鞘翅、带绑定诅咒的护甲）
	 * @param worn
	 *            现在这一件的表现，用 {@link #worn(int)} 构造
	 */
	public record Slot(boolean occupied, boolean keep, Piece worn)
	{}
	
	/** 背包里的一件候选护甲：属于哪个护甲位、在第几格、换上以后如何。 */
	public record Candidate(int type, int inventorySlot, Piece piece)
	{}
	
	/** 这一 tick 应该换的那一件。 */
	public record Choice(int type, int inventorySlot)
	{}
	
	/** 现在已经穿在身上的那一件：没有提升，只有耐久参与同分比较。 */
	public static Piece worn(int durabilityScore)
	{
		return new Piece(0, clampDurability(durabilityScore));
	}
	
	/**
	 * 把「换之前这一套挨多少」和「换上它以后这一套挨多少」折成可比较的分数。
	 *
	 * @param wornDamage
	 *            换之前这一套挨的伤害
	 * @param pieceDamage
	 *            换上它以后这一套挨的伤害
	 */
	public static Piece piece(float wornDamage, float pieceDamage,
		int durabilityScore)
	{
		return new Piece(Math.round((wornDamage - pieceDamage) * 1000F),
			clampDurability(durabilityScore));
	}
	
	/**
	 * 剩余耐久折算成 0..5 分。不可损坏的物品按满分算，与原实现里
	 * {@code isDamageableItem()} 的分支结果一致。
	 */
	public static int durabilityScore(int maxDamage, int damageValue)
	{
		if(maxDamage <= 0)
			return DURABILITY_MAX;
		
		int remaining = maxDamage - Math.max(0, damageValue);
		return Math.max(0, remaining * DURABILITY_MAX / maxDamage);
	}
	
	/**
	 * 换上它是不是比现在更好。严格更疼的一件（{@code gainMilli} 更低）永远换不上，
	 * 无论它有多新；减伤完全相同时才看耐久。
	 */
	public static boolean isUpgrade(Piece candidate, Piece worn)
	{
		if(candidate.gainMilli() != worn.gainMilli())
			return candidate.gainMilli() > worn.gainMilli();
		
		return candidate.durabilityScore() > worn.durabilityScore();
	}
	
	/**
	 * 选出这一 tick 要换的那一件。
	 *
	 * <p>
	 * 先给每个护甲位挑出最好的一件（提升相同取耐久更高的，再相同取背包里靠前的），
	 * 然后在能动的护甲位里挑提升最大的一个；提升相同按 脚→腿→胸→头 的固定次序，
	 * 所以同一份背包状态永远给出同一个结果。背包满时换下旧护甲没地方放，这一 tick
	 * 直接跳过那一位，于是空位仍然补得上（补空位不需要空格子）。
	 *
	 * @param slots
	 *            四个护甲位的现状，下标与 {@link Candidate#type()} 一致
	 * @param candidates
	 *            背包里的候选护甲
	 * @param freeSlotAvailable
	 *            背包里还有没有空格子
	 * @return 该换的那一件；没有值得换的就返回 {@code null}
	 */
	public static Choice choose(Slot[] slots, List<Candidate> candidates,
		boolean freeSlotAvailable)
	{
		Piece[] bestPieces = new Piece[slots.length];
		int[] bestSlots = new int[slots.length];
		Arrays.fill(bestSlots, -1);
		
		for(Candidate candidate : candidates)
		{
			int type = candidate.type();
			if(type < 0 || type >= slots.length || slots[type].keep())
				continue;
			if(!isUpgrade(candidate.piece(), slots[type].worn()))
				continue;
			if(!isBetterCandidate(candidate.piece(),
				candidate.inventorySlot(), bestPieces[type], bestSlots[type]))
				continue;
			
			bestPieces[type] = candidate.piece();
			bestSlots[type] = candidate.inventorySlot();
		}
		
		Choice choice = null;
		int bestGain = 0;
		
		for(int type = 0; type < slots.length; type++)
		{
			if(bestSlots[type] == -1)
				continue;
			// 换下旧护甲要一个空格子放它，背包满了就跳过这一位
			if(slots[type].occupied() && !freeSlotAvailable)
				continue;
			
			int slotGain = gain(bestPieces[type], slots[type].worn());
			if(choice == null || slotGain > bestGain)
			{
				choice = new Choice(type, bestSlots[type]);
				bestGain = slotGain;
			}
		}
		
		return choice;
	}
	
	/** 同一护甲位的两件候选谁更值得换。 */
	private static boolean isBetterCandidate(Piece piece, int slot, Piece best,
		int bestSlot)
	{
		if(bestSlot == -1)
			return true;
		if(isUpgrade(piece, best))
			return true;
		
		// 完全同分的一对候选取背包里靠前的那一件，结果才可复现
		return piece.equals(best) && slot < bestSlot;
	}
	
	/**
	 * 提升量：减伤增量放高位、耐久增量放低位，于是可以直接比大小。耐久增量绝对值
	 * 不超过 {@link #DURABILITY_MAX}，远小于 {@link #GAIN_SCALE}，不会串位。
	 */
	private static int gain(Piece best, Piece worn)
	{
		return (best.gainMilli() - worn.gainMilli()) * GAIN_SCALE
			+ best.durabilityScore() - worn.durabilityScore();
	}
	
	private static int clampDurability(int durabilityScore)
	{
		return Math.max(0, Math.min(DURABILITY_MAX, durabilityScore));
	}
}
