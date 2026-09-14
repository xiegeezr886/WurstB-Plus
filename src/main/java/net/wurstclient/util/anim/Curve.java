/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util.anim;

/**
 * 缓动曲线：把归一化的 {@code [0,1]} 进度映射成另一个进度。
 *
 * <p>
 * 单独成文件而不是嵌在 {@link Easings} 里，是因为 Java 不允许枚举的
 * {@code implements} 子句引用该枚举自己内嵌的类型（枚举头先于枚举体解析）。
 * 独立之后 {@link Easings} 才能直接实现本接口，枚举常量也就能当作曲线传递。
 *
 * <p>
 * 纯计算，不含 {@code net.minecraft.} 依赖。
 *
 * <p>
 * 注意曲线<b>不保证</b>输出落在 {@code [0,1]}：{@link Easings#EASE_IN_BACK} 会
 * 跌到 0 以下、{@link Easings#EASE_OUT_ELASTIC} 会冲出 1，这是回拉/弹性效果
 * 本身的要求。调用方要自己决定是否钳制。
 */
@FunctionalInterface
public interface Curve
{
	float apply(float x);
}
