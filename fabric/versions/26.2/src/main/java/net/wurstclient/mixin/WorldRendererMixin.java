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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.entity.Entity;
import net.wurstclient.WurstClient;

@Mixin(LevelRenderer.class)
public class WorldRendererMixin
{
	@Inject(method = "entityOutlineTarget()Lcom/mojang/blaze3d/pipeline/RenderTarget;",
		at = @At("HEAD"), cancellable = true)
	private void moon$outlineTarget(CallbackInfoReturnable<com.mojang.blaze3d.pipeline.RenderTarget> cir)
	{
		var target = net.wurstclient.util.render.MoonEntityGlow.INSTANCE.captureTarget();
		if(target != null) cir.setReturnValue(target);
	}
}
