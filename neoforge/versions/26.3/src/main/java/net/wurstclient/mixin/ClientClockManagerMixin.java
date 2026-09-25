/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixin;

import java.util.Optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import net.minecraft.client.ClientClockManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;
import net.wurstclient.WurstClient;
import net.wurstclient.hacks.NoWeatherHack;

/**
 * 26.3 把时钟的「总刻数」从 {@link ClientClockManager} 挪进了内嵌的
 * {@code ClientClockInstance}：原来的 {@code getTotalTicks(Holder)} 没有了，
 * 只剩实例上的 {@code totalTicks()}。因此这里改成在读取处改返回值。
 */
@Mixin(ClientClockManager.ClientClockInstance.class)
public abstract class ClientClockManagerMixin
{
	/**
	 * Modifies the total ticks returned for the overworld clock when NoWeather
	 * is changing the time. This affects all timeline-based environment
	 * attributes including sun/moon/star angles, sky colors, fog, etc.
	 */
	@ModifyReturnValue(method = "totalTicks()J", at = @At("RETURN"))
	private long onGetTotalTicks(long original)
	{
		NoWeatherHack noWeather = WurstClient.INSTANCE.getHax().noWeatherHack;

		if(!noWeather.isTimeChanged())
			return original;

		ClientLevel level = Minecraft.getInstance().level;
		if(level == null)
			return original;

		// Only modify the overworld clock. ClientClockInstance 身上没有指回自己
		// 所属时钟的引用，只能拿管理器里 Overworld 那一份来比对是不是自己。
		Optional<? extends Holder<WorldClock>> overworld =
			level.registryAccess().get(WorldClocks.OVERWORLD);
		if(overworld.isEmpty() || level.clockManager()
			.getInstance(overworld.get()) != (Object)this)
			return original;

		// Replace the time-of-day while keeping the day number
		long dayNumber = original / 24000;
		return dayNumber * 24000 + noWeather.getChangedTime();
	}
}
