/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import org.joml.Vector4f;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.FogType;
import net.wurstclient.WurstClient;

@Mixin(FogRenderer.class)
public abstract class BackgroundRendererMixin
{
	/**
	 * Makes the distance fog 100% transparent when NoFog is enabled,
	 * effectively removing it.
	 */
	@Inject(at = @At("HEAD"),
		method = "setupFog(Lnet/minecraft/client/Camera;ILnet/minecraft/client/DeltaTracker;FLnet/minecraft/client/multiplayer/ClientLevel;)Lnet/minecraft/client/renderer/fog/FogData;",
		cancellable = true)
	private static void onApplyFog(Camera camera, int mode,
		net.minecraft.client.DeltaTracker deltaTracker, float tickDelta,
		net.minecraft.client.multiplayer.ClientLevel level,
		CallbackInfoReturnable<FogData> cir)
	{
		if(!WurstClient.INSTANCE.getHax().noFogHack.isEnabled()
			|| mode != 0)
			return;
		
		FogType cameraSubmersionType = camera.getFluidInCamera();
		if(cameraSubmersionType != FogType.NONE)
			return;
		
		Entity entity = camera.entity();
		if(entity == null)
			return;

		FogData fogData = new FogData();
		fogData.color = new Vector4f(0, 0, 0, 0);
		fogData.environmentalStart = Float.MAX_VALUE;
		fogData.environmentalEnd = Float.MAX_VALUE;
		fogData.renderDistanceStart = Float.MAX_VALUE;
		fogData.renderDistanceEnd = Float.MAX_VALUE;
		fogData.skyEnd = Float.MAX_VALUE;
		fogData.cloudEnd = Float.MAX_VALUE;
		cir.setReturnValue(fogData);
	}
	
	@Inject(at = @At("HEAD"),
		method = "getPriorityFogFunction(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/FogRenderer$MobEffectFogFunction;",
		cancellable = true)
	private static void onGetFogModifier(Entity entity, float tickDelta,
		CallbackInfoReturnable<Object> ci)
	{
		if(WurstClient.INSTANCE.getHax().antiBlindHack.isEnabled())
			ci.setReturnValue(null);
	}
}
