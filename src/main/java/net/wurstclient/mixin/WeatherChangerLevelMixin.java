/* Adapted from Meteor Client. SPDX-License-Identifier: GPL-3.0 */
package net.wurstclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.wurstclient.WurstClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Level.class)
public abstract class WeatherChangerLevelMixin
{
	@ModifyReturnValue(method = "getRainLevel(F)F", at = @At("RETURN"))
	private float wurstRainLevel(float original)
	{
		if((Object)this != Minecraft.getInstance().level)
			return original;
		var hax = WurstClient.INSTANCE.getHax();
		if(hax == null)
			return original;
		return hax.noWeatherHack.isRainDisabled() ? 0
			: hax.weatherChangerHack.changeRain(original);
	}

	@ModifyReturnValue(method = "getThunderLevel(F)F", at = @At("RETURN"))
	private float wurstThunderLevel(float original)
	{
		if((Object)this != Minecraft.getInstance().level)
			return original;
		var hax = WurstClient.INSTANCE.getHax();
		if(hax == null)
			return original;
		return hax.noWeatherHack.isRainDisabled() ? 0
			: hax.weatherChangerHack.changeThunder(original);
	}
}
