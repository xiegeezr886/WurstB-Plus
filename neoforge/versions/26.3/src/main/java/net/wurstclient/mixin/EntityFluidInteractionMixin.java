/*
 * Copyright (c) 2026 WurstB+ Plus contributors.
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package net.wurstclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityFluidInteraction;
import net.neoforged.neoforge.fluids.FluidType;
import net.wurstclient.event.EventManager;
import net.wurstclient.events.VelocityFromFluidListener.VelocityFromFluidEvent;

@Mixin(EntityFluidInteraction.class)
public abstract class EntityFluidInteractionMixin
{
	// NeoForge applies currents by fluid type instead of the vanilla Entity hook.
	@WrapOperation(method = {
		"applyCurrentTo(Lnet/neoforged/neoforge/fluids/FluidType;Lnet/minecraft/world/entity/Entity;D)V",
		"applyCurrentTo(Lnet/minecraft/world/entity/Entity;)V"},
		at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/Entity;isPushedByFluid(Lnet/neoforged/neoforge/fluids/FluidType;)Z"),
		require = 2)
	private boolean wurst$allowFluidPush(Entity entity, FluidType fluid,
		Operation<Boolean> original)
	{
		VelocityFromFluidEvent event = new VelocityFromFluidEvent(entity);
		EventManager.fire(event);
		return !event.isCancelled() && original.call(entity, fluid);
	}
}
