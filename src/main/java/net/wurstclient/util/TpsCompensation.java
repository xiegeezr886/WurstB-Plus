/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

/**
 * 按服务器实际 tick 速率换算延迟，移植自 CakeSlayers/OpenEpsilon 的做法
 * （见 {@code _oe_ref/core-spec.md} §2、§7）。
 *
 * <p>
 * 问题：hack 里的冷却普遍写成"等 500 毫秒"，但服务端一卡到 10 TPS，它每秒只处理
 * 10 个 tick——同样的 500 毫秒在服务端看来只过了 5 个 tick 而不是 10 个，于是节奏
 * 整体偏快、动作被吞。参考项目在三处统一按 {@code 20 / tickRate} 缩放：
 * {@code MS delay*(20/tickRate)}、{@code atkSpeed=1000/(speed*(20/tickRate))}，
 * 以及 TimerManager 取 tick 长度时。
 *
 * <p>
 * 本工程其实**一直在测 TPS**（`ClientMetricsManager`，由服务端 tick 同步包驱动），
 * 但此前只有 TPS 的 HUD 显示在用它，**没有任何节奏逻辑消费它**——这个类补的就是
 * 这一环。
 *
 * <p>
 * 语义与参考一致：延迟表达的是"相当于 20 TPS 下多少时间"，所以
 * {@code tickRate < 20} 会把延迟**拉长**，{@code > 20}（服务端超频）会**缩短**。
 *
 * <p>
 * 纯函数，不含 Minecraft 依赖。
 */
public final class TpsCompensation
{
	/** 延迟是按这个 tick 速率写的。 */
	public static final double REFERENCE_TPS = 20.0D;
	
	/**
	 * 允许参与换算的最低 tick 速率。低于它（含 0 与 NaN）一律按
	 * {@link #REFERENCE_TPS} 处理，也就是**不缩放**——测量还没就绪时宁可保持原样，
	 * 也不要把延迟放大成一个荒唐的数字。
	 */
	public static final double MIN_TPS = 1.0D;
	
	private TpsCompensation()
	{}
	
	/**
	 * 把"按 20 TPS 写的毫秒延迟"换算成当前 tick 速率下应该等的毫秒。
	 * tick 速率不合法时原样返回，不做补偿。
	 */
	public static double scaleMillis(double millis, double tps)
	{
		if(!isMeasured(tps))
			return millis;
		
		return millis * (REFERENCE_TPS / tps);
	}
	
	/**
	 * 这个毫秒延迟在当前 tick 速率下相当于多少个服务端 tick。
	 * 用于把"等 N 毫秒"改写成"等 N 个 tick"的场合。
	 */
	public static double ticksFor(double millis, double tps)
	{
		double rate = isMeasured(tps) ? tps : REFERENCE_TPS;
		return millis * rate / 1000.0D;
	}
	
	/** 反过来：当前 tick 速率下 N 个 tick 是多少毫秒。 */
	public static double millisForTicks(double ticks, double tps)
	{
		double rate = isMeasured(tps) ? tps : REFERENCE_TPS;
		return ticks * 1000.0D / rate;
	}
	
	/**
	 * 测量是否可用。{@code ClientMetricsManager} 在换世界时会重置，刚进游戏还没
	 * 收到 tick 同步包时可能读到 0，这种值不能拿去除。
	 */
	public static boolean isMeasured(double tps)
	{
		return Double.isFinite(tps) && tps >= MIN_TPS;
	}
	
	/** 把测得的 tick 速率夹到可用范围，用于展示或记录。 */
	public static double sanitize(double tps)
	{
		if(!Double.isFinite(tps))
			return REFERENCE_TPS;
		
		return Math.max(MIN_TPS, Math.min(REFERENCE_TPS, tps));
	}
}
