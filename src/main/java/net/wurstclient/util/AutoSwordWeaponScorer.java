/*
 * Copyright (c) 2025-2026 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

/**
 * AutoSword 的纯决策逻辑：给定 9 个快捷栏槽位的评分，选出要切换到的槽位。
 *
 * <p>
 * 这里刻意只处理「快捷栏下标 0..8」。原版 {@code Inventory#getItem(int)} 的
 * 0..8 就是快捷栏本身（9..35 才是主背包，网络槽位还要再 +36），原实现直接拿
 * 这个下标写进 {@code inventory.selected}，是自洽的；把网络槽位混进来才是
 * 经典的 +36 错位，本类通过不接受任何网络槽位来把这层含义固化下来。
 *
 * <p>
 * 评分由调用方给出。评分类型是 {@code float} 而不是 {@code double}：调用方用
 * {@link #NOT_A_WEAPON} 当哨兵，一旦经过 double 往返，整数下限
 * -2147483648 会被舍入成 -2147483648.0f 之外的值，比较结果就不再等于原实现。
 * 本类不引用任何 Minecraft 类型，便于单测。
 */
public enum AutoSwordWeaponScorer
{
	;

	/**
	 * 找不到武器时使用的哨兵值，与 {@code AutoSwordHack} 原实现一致。
	 */
	public static final float NOT_A_WEAPON = Integer.MIN_VALUE;

	/**
	 * 按快捷栏下标取评分。
	 */
	@FunctionalInterface
	public interface SlotValue
	{
		float valueOf(int hotbarSlot);
	}

	/**
	 * 选槽规则与原实现逐条一致：严格大于才替换，因此并列时保留下标更小的
	 * 槽位，且空槽（{@link #NOT_A_WEAPON}）永远不会被选中。
	 *
	 * @param hotbarSize
	 *            快捷栏槽位数，原版为 9
	 * @return 要切换到的槽位，没有可用武器时返回 -1
	 */
	public static int selectBestSlot(int hotbarSize, SlotValue valueOf)
	{
		float bestValue = Integer.MIN_VALUE;
		int bestSlot = -1;
		for(int i = 0; i < hotbarSize; i++)
		{
			float value = valueOf.valueOf(i);
			if(value > bestValue)
			{
				bestValue = value;
				bestSlot = i;
			}
		}
		return bestSlot;
	}

	/**
	 * 只扫描快捷栏，这是 {@code AutoSwordHack} 唯一允许改动的槽位范围。
	 */
	public static int selectBestHotbarSlot(SlotValue valueOf)
	{
		return selectBestSlot(9, valueOf);
	}
}
