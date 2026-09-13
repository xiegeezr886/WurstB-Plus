package net.wurstclient.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.WurstClient;
import net.wurstclient.event.EventManager;
import net.wurstclient.events.CameraTransformViewBobbingListener.CameraTransformViewBobbingEvent;
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
	private boolean wurstIncludeFluids;

	@WrapOperation(at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/GameRenderer;bobView(Lcom/mojang/blaze3d/vertex/PoseStack;F)V",
		ordinal = 0),
		method = "renderLevel(Lnet/minecraft/client/DeltaTracker;)V")
	private void onBobView(GameRenderer instance, PoseStack matrices,
		float tickDelta, Operation<Void> original)
	{
		CameraTransformViewBobbingEvent event =
			new CameraTransformViewBobbingEvent();
		EventManager.fire(event);

		if(!event.isCancelled())
			original.call(instance, matrices, tickDelta);
	}

	@ModifyReturnValue(at = @At("RETURN"),
		method = "getFov(Lnet/minecraft/client/Camera;FZ)D")
	private double onGetFov(double original)
	{
		return WurstClient.INSTANCE.getOtfs().zoomOtf
			.changeFovBasedOnZoom(original);
	}

	@WrapOperation(at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/entity/Entity;pick(DFZ)Lnet/minecraft/world/phys/HitResult;",
		ordinal = 0),
		method = "pick(Lnet/minecraft/world/entity/Entity;DDF)Lnet/minecraft/world/phys/HitResult;")
	private HitResult liquidsRaycast(Entity instance, double maxDistance,
		float tickDelta, boolean includeFluids, Operation<HitResult> original)
	{
		if(!WurstClient.INSTANCE.getHax().liquidsHack.isEnabled())
			return original.call(instance, maxDistance, tickDelta,
				includeFluids);

		return original.call(instance, maxDistance, tickDelta, true);
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

	@WrapOperation(
		at = @At(value = "INVOKE",
			target = "Lnet/minecraft/util/Mth;lerp(FFF)F",
			ordinal = 0),
		method = "renderLevel(Lnet/minecraft/client/DeltaTracker;)V")
	private float onRenderWorldNauseaLerp(float delta, float start, float end,
		Operation<Float> original)
	{
		if(!WurstClient.INSTANCE.getHax().antiWobbleHack.isEnabled())
			return original.call(delta, start, end);

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
