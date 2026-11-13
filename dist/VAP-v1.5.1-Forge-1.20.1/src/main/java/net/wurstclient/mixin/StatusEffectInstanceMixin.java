/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 * GPL-3.0. Selected-effect behavior reference: Meteor Client (Meteor Development).
 */
package net.wurstclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.world.effect.MobEffectInstance;
import net.wurstclient.WurstClient;

@Mixin(MobEffectInstance.class)
public abstract class StatusEffectInstanceMixin implements Comparable<MobEffectInstance>
{
	@org.spongepowered.asm.mixin.Shadow
	private int duration;

	@Inject(at = @At("HEAD"), method = "tickDownDuration()I", cancellable = true)
	private void onUpdateDuration(CallbackInfoReturnable<Integer> ci)
	{
		if(WurstClient.INSTANCE.getHax() != null
			&& WurstClient.INSTANCE.getHax().potionSaverHack.shouldFreeze((MobEffectInstance)(Object)this))
			ci.setReturnValue(duration);
	}
}
