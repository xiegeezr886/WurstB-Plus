/*
 * Copyright (c) 2026 WurstB+ Plus contributors.
 * GPL-3.0. Per-hand cooldown behavior reference: Meteor Client (Meteor Development).
 */
package net.wurstclient.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.ItemStack;
import net.wurstclient.WurstClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Minecraft.class)
public abstract class FastUseCooldownMixin
{
	@Shadow private int rightClickDelay;

	@WrapOperation(method = "startUseItem()V", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/item/ItemStack;isItemEnabled(Lnet/minecraft/world/flag/FeatureFlagSet;)Z"))
	private boolean wurstUseCooldown(ItemStack stack, FeatureFlagSet flags,
		Operation<Boolean> original)
	{
		if(WurstClient.INSTANCE.getHax() != null && WurstClient.INSTANCE.getHax().fastUseHack.isEnabled())
			rightClickDelay = WurstClient.INSTANCE.getHax().fastUseHack.getItemUseCooldown(stack);
		return original.call(stack, flags);
	}
}
