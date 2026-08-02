/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import net.minecraft.client.DeltaTracker;
import net.wurstclient.WurstClient;
import net.wurstclient.hack.HackList;

@Mixin(DeltaTracker.Timer.class)
public abstract class RenderTickCounterMixin
{
	@Redirect(at = @At(value = "FIELD",
		target = "Lnet/minecraft/client/DeltaTracker$Timer;msPerTick:F"),
		method = "advanceGameTime(J)I")
	private float modifyMillisPerTick(DeltaTracker.Timer timer)
	{
		HackList hax = WurstClient.INSTANCE.getHax();
		if(hax == null)
			return 50F;
		return 50F / hax.timerHack.getTimerSpeed();
	}
}
