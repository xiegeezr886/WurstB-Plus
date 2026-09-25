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

	// 26.3 起 swingTime 换成了对象化的 swingState，ticks 见 SwingStateAccessor
	@Accessor("swingState")
	public LivingEntity.SwingState wurst_getSwingState();
}
