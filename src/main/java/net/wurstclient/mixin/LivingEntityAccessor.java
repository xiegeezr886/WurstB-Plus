/*
 * Copyright (c) 2025-2026 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.entity.LivingEntity;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor
{
	@Accessor("attackStrengthTicker")
	public int wurst_getAttackStrengthTicker();

	@Accessor("swingTime")
	public int wurst_getSwingTime();

	/**
	 * 跳跃冷却。1.20 的跳跃间隔是 {@code LivingEntity.noJumpDelay}
	 * （1.20.2 反编译源 {@code LivingEntity.java:220}，由 {@code aiStep()} 在
	 * {@code :2565} 递减、{@code :2632} 判定、{@code :2644} 归零）。
	 * {@code NoJumpDelayHack} 用反射找过这个名字，但反射不查父类所以从未生效，
	 * 改走 accessor 后还能享受 mixin 的 refmap 重映射。
	 */
	@Accessor("noJumpDelay")
	public void wurst_setNoJumpDelay(int noJumpDelay);
}
