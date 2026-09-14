/*
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

import java.util.Arrays;

/**
 * AutoCity 拆墙候选（目标玩家脚边的四个水平邻块）的纯排序逻辑：这一 tick 该先挖
 * 哪一面墙。
 *
 * <p>
 * 参考项目 OpenEpsilon 的 AutoCity 不是"看到黑曜石就挖"：它的
 * {@code findHoleBlock} 只把通过 {@code checkPos} 的墙收进候选，而
 * {@code checkPos} 判的是「挖掉这一面之后，目标身边是否真的出现一个可用的水晶位」
 * （下方是黑曜石/基岩、上方留得出水晶的高度）。能换来水晶位的墙才值得挖，否则
 * 只是白送对方一条出路——这就是那边「拆掉保护但把人留在坑里」的做法。
 *
 * <p>
 * 本项目把它抽成**优先级**而不是硬过滤，是有意的：参考的 {@code findHoleBlock}
 * 先要求目标处在真正的坑里（墙下必须有抗爆基底），而本 hack 一直对任意黑曜石环绕
 * 生效（包括建在泥土/石头地面上的环绕）。照搬硬过滤会让它在这种场景下彻底不动，
 * 等于删功能。所以这里只把"能换来水晶位的墙"排到前面；一面都没有时，保持原来的
 * "离玩家最近的墙"行为。
 *
 * <p>
 * 这个类不引用任何 Minecraft 类型，方便单元测试。
 */
public enum CityBlockPlanner
{
	;

	public static final int COUNT = 4;

	// 与旧版 AutoCityHack.cityOffsets 一一对应：东、西、南、北
	private static final int[] OFFSET_X = {1, -1, 0, 0};
	private static final int[] OFFSET_Z = {0, 0, 1, -1};

	public static int offsetX(int index)
	{
		return OFFSET_X[index];
	}

	public static int offsetZ(int index)
	{
		return OFFSET_Z[index];
	}

	/**
	 * 返回真正该挖的候选下标，排在第一个的就是本 tick 要挖的方块。
	 *
	 * @param distanceSq
	 *            每个候选到玩家的距离平方（越小越优先）
	 * @param usable
	 *            每个候选能不能挖（黑曜石、可破坏、不在自己的环绕里）
	 * @param punishable
	 *            挖掉每个候选之后，目标脚边是否出现可用的水晶位
	 * @return 先排"能换来水晶位"的，再按距离从近到远；并列时保持原下标顺序
	 */
	public static int[] plan(double[] distanceSq, boolean[] usable,
		boolean[] punishable)
	{
		int count = Math.min(distanceSq.length,
			Math.min(usable.length, punishable.length));

		int[] order = new int[count];
		int size = 0;
		for(int i = 0; i < count; i++)
			if(usable[i])
				order[size++] = i;

		// 插入排序：比较相等时不交换，于是距离并列的候选取原来的
		// 东→西→南→北 顺序，结果才是确定的。距离是 NaN 时比较全为 false，
		// 同样直接保持原顺序。
		for(int i = 1; i < size; i++)
		{
			int current = order[i];
			int j = i - 1;
			while(j >= 0 && isBetter(current, order[j], punishable,
				distanceSq))
			{
				order[j + 1] = order[j];
				j--;
			}
			order[j + 1] = current;
		}

		return Arrays.copyOf(order, size);
	}

	private static boolean isBetter(int candidate, int current,
		boolean[] punishable, double[] distanceSq)
	{
		if(punishable[candidate] != punishable[current])
			return punishable[candidate];

		return distanceSq[candidate] < distanceSq[current];
	}
}
