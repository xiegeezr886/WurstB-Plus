/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
package net.wurstclient.hacks;

import java.awt.Color;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.WurstRenderLayers;
import net.wurstclient.events.RenderListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.EntityUtils;
import net.wurstclient.util.RenderUtils;

/**
 * Renders a pulsing translucent ring around the entity the player is currently
 * aiming at.
 *
 * <p>Simplification: Soar remembered the last target for 2.5 seconds and only
 * tracked other players. This port uses the current crosshair entity and
 * accepts any living entity.
 */
@SearchTags({"target indicator", "target", "indicator", "target ring",
	"crosshair target"})
public final class TargetIndicatorHack extends Hack implements RenderListener
{
	private static final int SEGMENTS = 64;

	private final ColorSetting color = new ColorSetting("Color",
		"Color of the target ring.", Color.RED);

	private final SliderSetting radius = new SliderSetting("Radius",
		"Radius of the target ring.", 0.67, 0.2, 3, 0.01,
		ValueDisplay.DECIMAL);

	public TargetIndicatorHack()
	{
		super("TargetIndicator");
		setCategory(Category.RENDER);
		addSetting(color);
		addSetting(radius);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(RenderListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(RenderListener.class, this);
	}

	@Override
	public void onRender(PoseStack poseStack, float partialTicks)
	{
		if(MC.level == null || MC.player == null)
			return;

		if(!(MC.hitResult instanceof EntityHitResult hitResult))
			return;

		Entity target = hitResult.getEntity();
		if(!(target instanceof LivingEntity living) || living == MC.player
			|| !living.isAlive())
			return;

		Vec3 center = EntityUtils.getLerpedPos(living, partialTicks)
			.add(0, living.getBbHeight() * 0.5, 0);
		Vec3 cam = RenderUtils.getCameraPos();

		double time = System.currentTimeMillis() / 200.0;
		double ringY = center.y + Math.sin(time) * 0.12;
		double bottom = ringY - 0.45;
		double top = ringY + 0.45;

		Color col = color.getColor();
		float red = col.getRed() / 255F;
		float green = col.getGreen() / 255F;
		float blue = col.getBlue() / 255F;
		float topAlpha = 0.4F * (col.getAlpha() / 255F);
		double ringRadius = radius.getValue();
		Matrix4f matrix = poseStack.last().pose();

		MultiBufferSource.BufferSource vcp = RenderUtils.getVCP();
		RenderType layer = WurstRenderLayers.ESP_QUADS_NO_CULLING;
		VertexConsumer buffer = vcp.getBuffer(layer);

		for(int i = 0; i < SEGMENTS; i++)
		{
			double angle1 = Math.PI * 2 * i / SEGMENTS;
			double angle2 = Math.PI * 2 * (i + 1) / SEGMENTS;
			float x1 =
				(float)(center.x + Math.cos(angle1) * ringRadius - cam.x);
			float z1 =
				(float)(center.z + Math.sin(angle1) * ringRadius - cam.z);
			float x2 =
				(float)(center.x + Math.cos(angle2) * ringRadius - cam.x);
			float z2 =
				(float)(center.z + Math.sin(angle2) * ringRadius - cam.z);
			float yBottom = (float)(bottom - cam.y);
			float yTop = (float)(top - cam.y);

			buffer.addVertex(matrix, x1, yBottom, z1)
				.setColor(red, green, blue, 0);
			buffer.addVertex(matrix, x2, yBottom, z2)
				.setColor(red, green, blue, 0);
			buffer.addVertex(matrix, x2, yTop, z2)
				.setColor(red, green, blue, topAlpha);
			buffer.addVertex(matrix, x1, yTop, z1)
				.setColor(red, green, blue, topAlpha);
		}

		vcp.endBatch(layer);
	}
}
