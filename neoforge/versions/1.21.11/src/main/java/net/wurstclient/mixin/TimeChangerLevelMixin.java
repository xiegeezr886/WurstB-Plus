/* Adapted from Meteor Client. SPDX-License-Identifier: GPL-3.0 */
package net.wurstclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.wurstclient.WurstClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Level.class)
public abstract class TimeChangerLevelMixin
{
	@ModifyReturnValue(method = "getDayTime()J", at = @At("RETURN"))
	private long wurstDisplayTime(long original)
	{
		if((Object)this != Minecraft.getInstance().level)
			return original;
		var hax = WurstClient.INSTANCE.getHax();
		return hax == null ? original : hax.timeChangerHack.changeTime(original);
	}
}
