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

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.wurstclient.WurstClient;
import net.wurstclient.hacks.NameTagsHack;

/**
 * 1.21.8 port of the NameTags scale adjustment.
 *
 * <p>
 * On 1.21.9+ the label is built by {@code NameTagFeatureRenderer.Storage}, so
 * the scale could be wrapped there. 1.21.8 still renders the label directly
 * from {@link EntityRenderer#renderNameTag}, so the same adjustment is applied
 * to the {@code PoseStack.scale} call inside that method instead.
 */
@Mixin(EntityRenderer.class)
public class SubmitNodeCollectionMixin
{
	@WrapOperation(
		method = "renderNameTag(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
		at = @At(value = "INVOKE",
			target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V"))
	private void wrapLabelScale(PoseStack matrices, float x, float y, float z,
		Operation<Void> original, EntityRenderState state, Component name,
		PoseStack matrices2, MultiBufferSource vertexConsumers, int light)
	{
		NameTagsHack nameTagsHack = WurstClient.INSTANCE.getHax().nameTagsHack;
		if(!nameTagsHack.isEnabled())
		{
			original.call(matrices, x, y, z);
			return;
		}
		
		float scale = 0.025F * nameTagsHack.getScale();
		double distance = Math.sqrt(state.distanceToCameraSq);
		if(distance > 10)
			scale *= distance / 10;
		
		original.call(matrices, scale, -scale, scale);
	}
}
