/*
 * Adapted from Meteor Client. Copyright (c) Meteor Development.
 * SPDX-License-Identifier: GPL-3.0
 */
package net.wurstclient.mixin;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.wurstclient.WurstClient;
import net.wurstclient.hacks.NoRotateHack.SavedRotation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class NoRotatePacketMixin
{
	@Inject(method = {
		"handleMovePlayer(Lnet/minecraft/network/protocol/game/ClientboundPlayerPositionPacket;)V",
		"handleLookAt(Lnet/minecraft/network/protocol/game/ClientboundPlayerLookAtPacket;)V"
	}, at = @At("HEAD"))
	private void wurstCaptureView(CallbackInfo ci,
		@Share("wurstSavedView") LocalRef<SavedRotation> saved)
	{
		var hax = WurstClient.INSTANCE.getHax();
		saved.set(hax == null ? null : hax.noRotateHack.capture());
	}

	@Inject(method = {
		"handleMovePlayer(Lnet/minecraft/network/protocol/game/ClientboundPlayerPositionPacket;)V",
		"handleLookAt(Lnet/minecraft/network/protocol/game/ClientboundPlayerLookAtPacket;)V"
	}, at = @At("RETURN"))
	private void wurstRestoreView(CallbackInfo ci,
		@Share("wurstSavedView") LocalRef<SavedRotation> saved)
	{
		var hax = WurstClient.INSTANCE.getHax();
		if(hax != null)
			hax.noRotateHack.restore(saved.get());
	}
}
