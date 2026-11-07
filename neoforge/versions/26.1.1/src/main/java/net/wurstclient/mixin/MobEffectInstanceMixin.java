/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 * GPL-3.0. Selected-effect behavior reference: Meteor Client (Meteor Development).
 */
package net.wurstclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.world.effect.MobEffectInstance;
import net.wurstclient.WurstClient;

@Mixin(MobEffectInstance.class)
public abstract class MobEffectInstanceMixin implements Comparable<MobEffectInstance>
{

	@Inject(at = @At("HEAD"), method = "tickDownDuration()V", cancellable = true)
	private void onUpdateDuration(CallbackInfo ci)
	{
		if(WurstClient.INSTANCE.getHax() != null
			&& WurstClient.INSTANCE.getHax().potionSaverHack.shouldFreeze((MobEffectInstance)(Object)this))
			ci.cancel();
	}
}
