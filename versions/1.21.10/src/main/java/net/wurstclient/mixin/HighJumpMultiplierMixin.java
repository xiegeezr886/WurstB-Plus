/*
 * Copyright (c) 2026 WurstB+ Plus contributors.
 * GPL-3.0. Behavior reference: Meteor Client, Copyright (c) Meteor Development.
 */
package net.wurstclient.mixin;

import net.minecraft.world.entity.Entity;
import net.wurstclient.WurstClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class HighJumpMultiplierMixin
{
	@Inject(method = "getBlockJumpFactor()F", at = @At("RETURN"), cancellable = true)
	private void wurstJumpMultiplier(CallbackInfoReturnable<Float> cir)
	{
		if((Object)this != WurstClient.MC.player || WurstClient.INSTANCE.getHax() == null)
			return;
		float multiplier = WurstClient.INSTANCE.getHax().highJumpHack.getJumpMultiplier();
		if(multiplier != 1)
			cir.setReturnValue(cir.getReturnValueF() * multiplier);
	}
}
