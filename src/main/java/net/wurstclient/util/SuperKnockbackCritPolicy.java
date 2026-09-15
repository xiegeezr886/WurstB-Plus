/*
 * Copyright (c) 2025-2026 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

/**
 * SuperKnockback 的「这一次攻击在原版眼里是不是真正的暴击」判定。
 *
 * <p>
 * 判据逐项抄自原版 {@code Player#attack}（1.20.1 与 1.20.2 这段完全一致）：
 *
 * <pre>
 * flag  = f2 &gt; 0.9F;                     // f2 = getAttackStrengthScale(0.5F)
 * flag2 = flag &amp;&amp; fallDistance &gt; 0.0F &amp;&amp; !onGround &amp;&amp; !onClimbable
 *         &amp;&amp; !isInWater() &amp;&amp; !hasEffect(BLINDNESS) &amp;&amp; !isPassenger
 *         &amp;&amp; 目标是生物;
 * flag2 = flag2 &amp;&amp; !isSprinting();
 * </pre>
 *
 * <p>
 * 为什么要这么严：暴击与疾跑击退在原版里是**互斥**的——{@code isSprinting()} 时
 * {@code flag2} 恒为 false，而且疾跑攻击还会顺带把疾跑状态清掉。本 hack 的重置动作
 * 恰恰是在攻击前把服务端的疾跑状态立起来，所以只有「这次攻击真的会暴击」时才必须
 * 跳过，否则会把用户的暴击弄没。
 *
 * <p>
 * 本工程另一处早就把这条判据写全过（{@code KillauraHack.CriticalsSelectionMode
 * #allowsAttack}，见其 :1190-1199）；SuperKnockback 原来只抄了「离地在下降」那一半，
 * 少了 {@code !isSprinting()} 这条关键项，于是**疾跑跳跃下落时把疾跑攻击误判成暴击**，
 * {@code skipCriticals} 会一直跳过重置。原版的自动疾跑在空中要求按住疾跑键
 * （{@code LocalPlayer#aiStep}），所以被跳过的那一下正是本 hack 唯一有用的场景。
 *
 * <p>
 * 调用点在 {@code MultiPlayerGameMode#attack} 的 HEAD（本工程
 * {@code ClientPlayerInteractionManagerMixin:52-58}），也就是原版 {@code Player#attack}
 * 与 {@code resetAttackStrengthTicker()} 之前，因此这里读到的
 * {@code getAttackStrengthScale(0.5F)} 与原版随后自己算的 {@code f2} 是同一个值。
 *
 * <p>
 * 本类不引用任何 Minecraft 类型，便于单测。
 */
public enum SuperKnockbackCritPolicy
{
	;

	/**
	 * 状态取自攻击者（本地玩家）在攻击发出的那一刻。
	 *
	 * @param onGround
	 *            是否在地面
	 * @param onClimbable
	 *            是否在攀爬（梯子、藤蔓）
	 * @param inWater
	 *            是否在水中，即原版的 {@code isInWater()}
	 * @param sprinting
	 *            是否正在疾跑
	 * @param passenger
	 *            是否骑乘中
	 * @param blindness
	 *            是否有失明效果
	 * @param fallDistance
	 *            下落距离
	 * @param attackStrength
	 *            攻击充能进度，即原版的 {@code getAttackStrengthScale(0.5F)}
	 */
	public record State(boolean onGround, boolean onClimbable,
		boolean inWater, boolean sprinting, boolean passenger,
		boolean blindness, float fallDistance, float attackStrength)
	{
	}

	/**
	 * 这次攻击是否构成原版的「下落暴击」。
	 */
	public static boolean isNaturalCritical(State state)
	{
		return state != null && state.attackStrength() > 0.9F
			&& state.fallDistance() > 0.0F && !state.onGround()
			&& !state.onClimbable() && !state.inWater() && !state.sprinting()
			&& !state.passenger() && !state.blindness();
	}
}
