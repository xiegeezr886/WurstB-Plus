/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixin;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.WurstClient;
import net.wurstclient.event.EventManager;
import net.wurstclient.events.CameraTransformViewBobbingListener.CameraTransformViewBobbingEvent;
import net.wurstclient.events.HitResultRayTraceListener.HitResultRayTraceEvent;
import net.wurstclient.hack.HackList;
import net.wurstclient.hacks.FullbrightHack;
import net.wurstclient.hacks.ReachHack;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin implements AutoCloseable
{
	@Shadow
	@Final
	private Minecraft minecraft;

	@Unique
	private boolean cancelNextBobView;
	
	/**
	 * Fires the CameraTransformViewBobbingEvent event and records whether the
	 * next view-bobbing call should be cancelled.
	 */
	@Inject(at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/GameRenderer;bobView(Lcom/mojang/blaze3d/vertex/PoseStack;F)V",
		ordinal = 0),
		method = "renderLevel(FJ)V")
	private void onRenderWorldViewBobbing(float tickDelta, long limitTime,
		CallbackInfo ci)
	{
		CameraTransformViewBobbingEvent event =
			new CameraTransformViewBobbingEvent();
		EventManager.fire(event);
		
		if(event.isCancelled())
			cancelNextBobView = true;
	}
	
	/**
	 * Cancels the view-bobbing call if requested by the last
	 * CameraTransformViewBobbingEvent.
	 */
	@Inject(at = @At("HEAD"),
		method = "bobView(Lcom/mojang/blaze3d/vertex/PoseStack;F)V",
		cancellable = true)
	private void onBobView(PoseStack matrices, float tickDelta,
		CallbackInfo ci)
	{
		if(!cancelNextBobView)
			return;
		
		ci.cancel();
		cancelNextBobView = false;
	}
	
	/**
	 * This mixin is injected into a random method call later in the
	 * renderWorld() method to ensure that cancelNextBobView is always reset
	 * after the view-bobbing call.
	 */
	@Inject(at = @At("HEAD"),
		method = "renderItemInHand(Lnet/minecraft/client/Camera;FLorg/joml/Matrix4f;)V")
	private void onRenderHand(Camera camera, float tickDelta,
		org.joml.Matrix4f matrix, CallbackInfo ci)
	{
		cancelNextBobView = false;
	}
	
	@ModifyReturnValue(at = @At("RETURN"),
		method = "getFov(Lnet/minecraft/client/Camera;FZ)D")
	private double onGetFov(double original)
	{
		return WurstClient.INSTANCE.getOtfs().zoomOtf
			.changeFovBasedOnZoom(original);
	}
	
	@Inject(at = @At("HEAD"),
		method = "pick(Lnet/minecraft/world/entity/Entity;DDF)Lnet/minecraft/world/phys/HitResult;")
	private void onHitResultRayTrace(net.minecraft.world.entity.Entity entity,
		double maxDistance, double entityDistance, float tickDelta,
		CallbackInfoReturnable<HitResult> cir)
	{
		HitResultRayTraceEvent event = new HitResultRayTraceEvent(tickDelta);
		EventManager.fire(event);
	}

	@Inject(at = @At("RETURN"), method = "pick(F)V")
	private void trimReachResult(float partialTicks, CallbackInfo ci)
	{
		HackList hax = WurstClient.INSTANCE.getHax();
		if(hax == null)
			return;
		ReachHack reach = hax.reachHack;
		if(!reach.isEnabled() || minecraft.getCameraEntity() == null
			|| minecraft.hitResult == null)
			return;

		HitResult result = minecraft.hitResult;
		float allowed = result instanceof EntityHitResult
			? reach.getEntityRange() : reach.getBlockRange();
		Vec3 eyes = minecraft.getCameraEntity().getEyePosition(partialTicks);
		if(eyes.distanceToSqr(result.getLocation()) <= allowed * allowed)
			return;

		minecraft.crosshairPickEntity = null;
		minecraft.hitResult = minecraft.getCameraEntity().pick(
			reach.getBlockRange(), partialTicks, false);
	}
	
	@Redirect(
		at = @At(value = "INVOKE",
			target = "Lnet/minecraft/util/Mth;lerp(FFF)F",
			ordinal = 0),
		method = "renderLevel(FJ)V")
	private float wurstNauseaLerp(float delta, float start, float end)
	{
		if(!WurstClient.INSTANCE.getHax().antiWobbleHack.isEnabled())
			return Mth.lerp(delta, start, end);
		
		return 0;
	}
	
	@Inject(at = @At("HEAD"),
		method = "getNightVisionScale(Lnet/minecraft/world/entity/LivingEntity;F)F",
		cancellable = true)
	private static void onGetNightVisionStrength(LivingEntity entity,
		float tickDelta, CallbackInfoReturnable<Float> cir)
	{
		FullbrightHack fullbright =
			WurstClient.INSTANCE.getHax().fullbrightHack;
		
		if(fullbright.isNightVisionActive())
			cir.setReturnValue(fullbright.getNightVisionStrength());
	}
	
	@Inject(at = @At("HEAD"),
		method = "bobHurt(Lcom/mojang/blaze3d/vertex/PoseStack;F)V",
		cancellable = true)
	private void onTiltViewWhenHurt(PoseStack matrices, float tickDelta,
		CallbackInfo ci)
	{
		if(WurstClient.INSTANCE.getHax().noHurtcamHack.isEnabled())
			ci.cancel();
	}
}
