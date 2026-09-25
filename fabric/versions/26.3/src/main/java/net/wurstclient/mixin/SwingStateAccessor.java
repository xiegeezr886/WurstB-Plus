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

/**
 * 26.3 起 `LivingEntity.swingTime`（int）被换成了 `LivingEntity.swingState`
 * （{@code SwingState} 对象）。{@code SwingState.ticks} 才是旧 `swingTime` 的
 * 语义对应，故在这里单独开一个访问器把它取出来。
 */
@Mixin(LivingEntity.SwingState.class)
public interface SwingStateAccessor
{
	@Accessor("ticks")
	public int wurst_getTicks();
}
