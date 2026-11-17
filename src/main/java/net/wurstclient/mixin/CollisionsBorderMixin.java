/*
 * Adapted from Meteor Client. Copyright (c) Meteor Development.
 * SPDX-License-Identifier: GPL-3.0
 */
package net.wurstclient.mixin;

import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.wurstclient.WurstClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldBorder.class)
public abstract class CollisionsBorderMixin
{
	@Unique
	private boolean wurstIgnoresBorder()
	{
		var hax = WurstClient.INSTANCE.getHax();
		return hax != null && hax.collisionsHack.ignoresBorder((WorldBorder)(Object)this);
	}

	@Inject(method = "isInsideCloseToBorder(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Z",
		at = @At("HEAD"), cancellable = true)
	private void wurstCanCollide(CallbackInfoReturnable<Boolean> cir)
	{
		if(wurstIgnoresBorder())
			cir.setReturnValue(false);
	}

	@Inject(method = "isWithinBounds(Lnet/minecraft/core/BlockPos;)Z",
		at = @At("HEAD"), cancellable = true)
	private void wurstContains(CallbackInfoReturnable<Boolean> cir)
	{
		if(wurstIgnoresBorder())
			cir.setReturnValue(true);
	}

	@Inject(method = "getCollisionShape()Lnet/minecraft/world/phys/shapes/VoxelShape;",
		at = @At("HEAD"), cancellable = true)
	private void wurstBorderShape(CallbackInfoReturnable<VoxelShape> cir)
	{
		if(wurstIgnoresBorder())
			cir.setReturnValue(Shapes.empty());
	}
}
