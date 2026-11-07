/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.wurstclient.WurstClient;
import net.wurstclient.event.EventManager;
import net.wurstclient.events.CameraTransformViewBobbingListener.CameraTransformViewBobbingEvent;
import net.wurstclient.hacks.FullbrightHack;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin implements AutoCloseable
{
	/**
	 * Prevents view bobbing when hacks disable it.
	 */
	@WrapOperation(method = "renderLevel(Lnet/minecraft/client/DeltaTracker;)V",
		at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/GameRenderer;bobView(Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V",
			ordinal = 0))
	private void onBobView(GameRenderer instance, CameraRenderState cameraState,
		PoseStack matrices, Operation<Void> original)
	{
		WurstClient.INSTANCE.getHax().freelookHack
			.applyCameraRotation(matrices);
		CameraTransformViewBobbingEvent event =
			new CameraTransformViewBobbingEvent();
		EventManager.fire(event);
		
		if(!event.isCancelled())
			original.call(instance, cameraState, matrices);
	}
	
	@WrapOperation(method = "renderLevel(Lnet/minecraft/client/DeltaTracker;)V",
		at = @At(value = "INVOKE",
			target = "Lnet/minecraft/util/Mth;lerp(FFF)F",
			ordinal = 0))
	private float onRenderWorldNauseaLerp(float delta, float start, float end,
		Operation<Float> original)
	{
		if(!WurstClient.INSTANCE.getHax().antiWobbleHack.isEnabled())
			return original.call(delta, start, end);
		
		return 0;
	}
	
	@Inject(
		method = "nightVisionScale(Lnet/minecraft/world/entity/LivingEntity;F)F",
		at = @At("HEAD"),
		cancellable = true)
	private static void onGetNightVisionStrength(LivingEntity entity,
		float tickDelta, CallbackInfoReturnable<Float> cir)
	{
		FullbrightHack fullbright =
			WurstClient.INSTANCE.getHax().fullbrightHack;
		
		if(fullbright.isNightVisionActive())
			cir.setReturnValue(fullbright.getNightVisionStrength());
	}
	
	/**
	 * Makes NoHurtcam work.
	 */
	@Inject(
		method = "bobHurt(Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V",
		at = @At("HEAD"),
		cancellable = true)
	private void onTiltViewWhenHurt(CameraRenderState cameraState,
		PoseStack matrices, CallbackInfo ci)
	{
		if(WurstClient.INSTANCE.getHax().noHurtcamHack.isEnabled())
			ci.cancel();
	}

	/**
	 * Draws the NanoVG HUD after Minecraft has finished the GUI pass.
	 * The HUD renders into its own framebuffer and is then composited onto
	 * the main target, so it cannot disturb the vanilla render pipeline.
	 */
	@Inject(at = @At("HEAD"),
		method = "render(Lnet/minecraft/client/DeltaTracker;Z)V")
	private void onPrepareHud(DeltaTracker deltaTracker,
		boolean renderLevel, CallbackInfo ci)
	{
		if(WurstClient.INSTANCE.getHudManager() != null)
			WurstClient.INSTANCE.getHudManager().prepareFrame(
				deltaTracker.getGameTimeDeltaPartialTick(false));
	}

}
