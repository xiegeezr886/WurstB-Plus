/* Adapted from Meteor Client. Copyright (c) Meteor Development. SPDX-License-Identifier: GPL-3.0 */
package net.wurstclient.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.wurstclient.WurstClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MultiPlayerGameMode.class, priority = 1100)
public abstract class NoInteractMixin
{
	@Inject(method = {"startDestroyBlock", "continueDestroyBlock"}, at = @At("HEAD"), cancellable = true)
	private void wurstBlockMining(BlockPos pos, Direction side, CallbackInfoReturnable<Boolean> ci)
	{
		var hax = WurstClient.INSTANCE.getHax();
		if(hax != null && !hax.noInteractHack.shouldAttackBlock(pos))
			ci.setReturnValue(false);
	}

	@Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
	private void wurstBlockUse(LocalPlayer player, InteractionHand hand, BlockHitResult hit,
		CallbackInfoReturnable<InteractionResult> ci)
	{
		var hax = WurstClient.INSTANCE.getHax();
		if(hax != null && !hax.noInteractHack.shouldInteractBlock(hit.getBlockPos(), hand))
			ci.setReturnValue(InteractionResult.FAIL);
	}

	@Inject(method = "attack", at = @At("HEAD"), cancellable = true)
	private void wurstEntityAttack(Player player, Entity target, CallbackInfo ci)
	{
		var hax = WurstClient.INSTANCE.getHax();
		if(hax != null && !hax.noInteractHack.shouldAttackEntity(target))
			ci.cancel();
	}

	@Inject(method = "interact", at = @At("HEAD"), cancellable = true)
	private void wurstEntityUse(Player player, Entity target, EntityHitResult hit, InteractionHand hand,
		CallbackInfoReturnable<InteractionResult> ci)
	{
		var hax = WurstClient.INSTANCE.getHax();
		if(hax != null && !hax.noInteractHack.shouldInteractEntity(target, hand))
			ci.setReturnValue(InteractionResult.FAIL);
	}

}
