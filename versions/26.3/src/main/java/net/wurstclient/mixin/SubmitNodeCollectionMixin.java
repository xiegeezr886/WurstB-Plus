/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixin;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.feature.TextFeatureRenderer;
import net.minecraft.client.renderer.feature.phase.TranslucentFeatureRenderPhase;
import net.minecraft.client.renderer.feature.submit.TranslucentSubmit;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.WurstClient;
import net.wurstclient.hacks.NameTagsHack;

/**
 * NameTags: label scaling, staying visible while sneaking, and moving tags
 * between the normal and see-through render phases.
 *
 * <p>This file merges two sources, because the fork and upstream both modified
 * it and 26.3 reshaped it:
 * <ul>
 * <li>{@code wrapLabelScale} and {@code forceNotSneaking} are this fork's own and
 * are carried over unchanged. Their anchors still exist: {@code submitNameTag}
 * still calls {@code PoseStack.scale(FFF)V} exactly once, and the boolean
 * argument is still the caller's {@code !state.isDiscrete} (verified in
 * {@code EntityRenderer}, identical in 26.2 and 26.3), so forcing it true still
 * means "treat as not sneaking".
 * <li>The two phase-swapping operations follow upstream Wurst's 26.3 port:
 * {@code NameTagFeatureRenderer.Submit} was renamed to
 * {@code TextFeatureRenderer.Submit} (which now implements {@code TranslucentSubmit}
 * itself), and the normal path no longer calls
 * {@code SimpleFeatureRenderPhase.submit} — {@code submitNameTag} routes it
 * through the private {@code SubmitNodeCollection.submitNameTagPart}, so that is
 * the new anchor. The two shadowed phase fields collapse into {@code seeThrough}.
 * </ul>
 */
@Mixin(SubmitNodeCollection.class)
public class SubmitNodeCollectionMixin
{
	private static final String SUBMIT_NAME_TAG =
		"submitNameTag(Lcom/mojang/blaze3d/vertex/PoseStack;"
			+ "Lnet/minecraft/world/phys/Vec3;ILnet/minecraft/network/chat/Component;ZI"
			+ "Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V";
	
	private static final String SUBMIT_NAME_TAG_PART =
		"Lnet/minecraft/client/renderer/SubmitNodeCollection;submitNameTagPart("
			+ "Lnet/minecraft/client/renderer/feature/TextFeatureRenderer$Submit;)V";
	
	@Shadow
	@Final
	private TranslucentFeatureRenderPhase seeThrough;
	
	@WrapOperation(method = SUBMIT_NAME_TAG,
		at = @At(value = "INVOKE",
			target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V"))
	private void wrapLabelScale(PoseStack matrices, float x, float y, float z,
		Operation<Void> original, PoseStack matrices2,
		@Nullable Vec3 nameTagAttachment, int offset, Component name,
		boolean seeThrough, int lightCoords, CameraRenderState camera)
	{
		NameTagsHack nameTagsHack = WurstClient.INSTANCE.getHax().nameTagsHack;
		if(!nameTagsHack.isEnabled())
		{
			original.call(matrices, x, y, z);
			return;
		}
		
		float scale = 0.025F * nameTagsHack.getScale();
		Matrix4f pose = new Matrix4f(matrices.last().pose());
		double distance =
			Math.sqrt(TranslucentSubmit.computeDistanceToCameraSq(pose));
		if(distance > 10)
			scale *= distance / 10;
		
		original.call(matrices, scale, -scale, scale);
	}
	
	/**
	 * Makes name tags remain visible while the player is sneaking when NameTags
	 * is enabled.
	 */
	@ModifyVariable(method = SUBMIT_NAME_TAG, at = @At("HEAD"),
		argsOnly = true)
	private boolean forceNotSneaking(boolean notSneaking)
	{
		return notSneaking
			|| WurstClient.INSTANCE.getHax().nameTagsHack.isEnabled();
	}
	
	@WrapOperation(method = SUBMIT_NAME_TAG,
		at = @At(value = "INVOKE", target = SUBMIT_NAME_TAG_PART))
	private void swapNormalNameTagSubmit(SubmitNodeCollection collection,
		TextFeatureRenderer.Submit submit, Operation<Void> original)
	{
		if(!WurstClient.INSTANCE.getHax().nameTagsHack.isSeeThrough())
		{
			original.call(collection, submit);
			return;
		}
		
		seeThrough.submit(copyWithDisplayMode(submit,
			Font.DisplayMode.SEE_THROUGH));
	}
	
	@WrapOperation(method = SUBMIT_NAME_TAG,
		at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/feature/phase/"
				+ "TranslucentFeatureRenderPhase;submit("
				+ "Lnet/minecraft/client/renderer/feature/submit/TranslucentSubmit;)V"))
	private void swapSeeThroughNameTagSubmit(TranslucentFeatureRenderPhase phase,
		TranslucentSubmit submit, Operation<Void> original)
	{
		// submitNameTag builds this submit itself, so it is always a
		// TextFeatureRenderer.Submit; the guard only avoids a hard crash in the
		// render path if that ever stops being true, and falls back to vanilla.
		if(!WurstClient.INSTANCE.getHax().nameTagsHack.isSeeThrough()
			|| !(submit instanceof TextFeatureRenderer.Submit nameTag))
		{
			original.call(phase, submit);
			return;
		}
		
		submitNameTagPart(
			copyWithDisplayMode(nameTag, Font.DisplayMode.NORMAL));
	}
	
	private TextFeatureRenderer.Submit copyWithDisplayMode(
		TextFeatureRenderer.Submit nameTag, Font.DisplayMode displayMode)
	{
		return new TextFeatureRenderer.Submit(nameTag.pose(), displayMode,
			nameTag.lightCoords(), nameTag.content());
	}
	
	@Shadow
	private void submitNameTagPart(TextFeatureRenderer.Submit nameTag)
	{
		
	}
}
