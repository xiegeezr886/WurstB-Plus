/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixin;

import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogParameters;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.FogType;
import net.wurstclient.WurstClient;

@Mixin(FogRenderer.class)
public abstract class BackgroundRendererMixin
{
	/**
	 * Makes the distance fog 100% transparent when NoFog is enabled,
	 * effectively removing it.
	 *
	 * <p>
	 * 1.21.2 turned {@code setupFog()} into a static method that returns the
	 * finished {@link FogParameters} instead of writing them into
	 * {@code RenderSystem} and returning nothing, so the fog is now removed by
	 * cancelling the method and returning {@link FogParameters#NO_FOG}.
	 */
	@Inject(at = @At("HEAD"),
		method = "setupFog(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/FogRenderer$FogMode;Lorg/joml/Vector4f;FZF)Lnet/minecraft/client/renderer/FogParameters;",
		cancellable = true)
	private static void onApplyFog(Camera camera,
		FogRenderer.FogMode fogType, Vector4f fogColor, float viewDistance,
		boolean thickFog, float tickDelta,
		CallbackInfoReturnable<FogParameters> cir)
	{
		if(!WurstClient.INSTANCE.getHax().noFogHack.isEnabled()
			|| fogType != FogRenderer.FogMode.FOG_TERRAIN)
			return;
		
		FogType cameraSubmersionType = camera.getFluidInCamera();
		if(cameraSubmersionType != FogType.NONE)
			return;
		
		cir.setReturnValue(FogParameters.NO_FOG);
	}
	
	/**
	 * Makes AntiBlind remove the fog from blindness and darkness.
	 *
	 * <p>
	 * {@code getPriorityFogFunction()} is private and its return type is a
	 * package-private nested class, so this injection cannot reference either one
	 * from Java source and has to rely on the raw descriptor instead.
	 */
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
