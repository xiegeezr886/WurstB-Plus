/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixin;

import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.wurstclient.WurstClient;
import net.wurstclient.hacks.NameTagsHack;
import net.wurstclient.util.NameTagRenderState;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin<T extends Entity>
{
	@Shadow
	@Final
	protected EntityRenderDispatcher entityRenderDispatcher;
	
	@Inject(at = @At("HEAD"),
		method = "renderNameTag(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
		cancellable = true)
	private void onRenderLabelIfPresent(T entity, Component text,
		PoseStack matrixStack, MultiBufferSource vertexConsumerProvider,
		int i, CallbackInfo ci)
	{
		NameTagsHack nameTags = WurstClient.INSTANCE.getHax().nameTagsHack;
		NameTagRenderState renderState = nameTags.getRenderState(entity);
		if(renderState != null)
			text = renderState.label();

		// add HealthTags info
		if(entity instanceof LivingEntity
			&& (renderState == null || !nameTags.isHealthShown()))
			text = WurstClient.INSTANCE.getHax().healthTagsHack
				.addHealth((LivingEntity)entity, text);
		
		// do NameTags adjustments
		wurstRenderLabelIfPresent(entity, text, matrixStack,
			vertexConsumerProvider, i);
		ci.cancel();
	}
	
	/**
	 * Copy of renderLabelIfPresent() since calling the original would result in
	 * an infinite loop. Also makes it easier to modify.
	 */
	protected void wurstRenderLabelIfPresent(T entity, Component text,
		PoseStack matrices, MultiBufferSource vertexConsumers, int light)
	{
		NameTagsHack nameTags = WurstClient.INSTANCE.getHax().nameTagsHack;
		
		// disable distance limit if configured in NameTags
		double distanceSq = entityRenderDispatcher.distanceToSqr(entity);
		if(distanceSq > 4096 && !nameTags.isUnlimitedRange())
			return;
		
		// disable sneaking changes if NameTags is enabled
		boolean notSneaky = !entity.isDiscrete() || nameTags.isEnabled();
		
		float matrixY = entity.getBbHeight() + 0.5F;
		int labelY = "deadmau5".equals(text.getString()) ? -10 : 0;
		
		matrices.pushPose();
		matrices.translate(0, matrixY, 0);
		matrices.mulPose(entityRenderDispatcher.cameraOrientation());
		
		// adjust scale if NameTags is enabled
		float scale = 0.025F * nameTags.getScale();
		if(nameTags.isEnabled())
		{
			double distance = WurstClient.MC.player.distanceTo(entity);
			if(distance > 10)
				scale *= distance / 10;
		}
		matrices.scale(-scale, -scale, scale);
		
		Matrix4f matrix = matrices.last().pose();
		float bgOpacity =
			WurstClient.MC.options.getBackgroundOpacity(0.25F);
		int bgColor = (int)(bgOpacity * 255F) << 24;
		Font tr = getFont();
		float labelX = -tr.width(text) / 2;
		
		// adjust layers if using NameTags in see-through mode
		DisplayMode bgLayer = notSneaky && !nameTags.isSeeThrough()
			? DisplayMode.SEE_THROUGH : DisplayMode.NORMAL;
		DisplayMode textLayer = nameTags.isSeeThrough()
			? DisplayMode.SEE_THROUGH : DisplayMode.NORMAL;
		
		// draw background
		tr.drawInBatch(text, labelX, labelY, 0x20FFFFFF, false, matrix,
			vertexConsumers, bgLayer, bgColor, light);
		
		// draw text
		if(notSneaky)
			tr.drawInBatch(text, labelX, labelY, 0xFFFFFFFF, false, matrix,
				vertexConsumers, textLayer, 0, light);

		NameTagRenderState renderState = nameTags.getRenderState(entity);
		if(renderState != null && nameTags.shouldShowEquipment())
			renderEquipment(entity, renderState, matrices, vertexConsumers,
				light, labelY);
		
		matrices.popPose();
	}

	private void renderEquipment(T entity, NameTagRenderState state,
		PoseStack matrices, MultiBufferSource vertexConsumers, int light,
		int labelY)
	{
		int count = state.equipment().size();
		float startX = count * -8F;
		for(int index = 0; index < count; index++)
		{
			matrices.pushPose();
			matrices.translate(startX + index * 16 + 8, labelY - 11, 0.02);
			matrices.scale(16, -16, 0.01F);
			WurstClient.MC.getItemRenderer().renderStatic(
				state.equipment().get(index), ItemDisplayContext.GUI, light,
				OverlayTexture.NO_OVERLAY, matrices, vertexConsumers,
				entity.level(), entity.getId() + index);
			matrices.popPose();

			int durability = state.durability().get(index);
			if(!WurstClient.INSTANCE.getHax().nameTagsHack
				.shouldShowDurability() || durability < 0)
				continue;

			String value = durability + "%";
			int color = durability > 60 ? 0xFF55FF55
				: durability > 25 ? 0xFFFFFF55 : 0xFFFF5555;
			float textX = startX + index * 16 + 8 - getFont().width(value) / 2F;
			getFont().drawInBatch(value, textX, labelY - 23, color, true,
				matrices.last().pose(), vertexConsumers, DisplayMode.NORMAL, 0,
				light);
		}
	}
	
	@Shadow
	public abstract Font getFont();
}
