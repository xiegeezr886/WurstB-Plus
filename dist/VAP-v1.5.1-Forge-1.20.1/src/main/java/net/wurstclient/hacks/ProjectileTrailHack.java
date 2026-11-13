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
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import org.joml.Matrix4f;

import com.mojang.blaze3d.platform.GlConst;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.WurstRenderLayers;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.events.WorldChangeListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.RenderUtils;

/**
 * Renders a fading line trail behind arrows and other projectiles.
 *
 * <p>Simplification: Soar spawned particle trails from a per-projectile type
 * list. This port records client-side positions each tick and draws a fading
 * line instead, which needs no particle registry and works for every
 * {@link Projectile}.
 */
@SearchTags({"projectile trail", "arrow trail", "projectile", "trail",
	"particles"})
public final class ProjectileTrailHack extends Hack
	implements UpdateListener, RenderListener, WorldChangeListener
{
	private final Map<Entity, Deque<Vec3>> trails = new IdentityHashMap<>();

	private final SliderSetting trailLength = new SliderSetting(
		"Trail length", "How many recorded points each trail keeps.", 30, 5,
		100, 1, ValueDisplay.INTEGER);

	private final ColorSetting color = new ColorSetting("Color",
		"Color of the projectile trails.", new Color(160, 220, 255, 200));

	private final CheckboxSetting throughWalls = new CheckboxSetting(
		"Through walls", "Renders the trails through solid blocks.", false);

	public ProjectileTrailHack()
	{
		super("ProjectileTrail");
		setCategory(Category.RENDER);
		addSetting(trailLength);
		addSetting(color);
		addSetting(throughWalls);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(RenderListener.class, this);
		EVENTS.add(WorldChangeListener.class, this);
		trails.clear();
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		EVENTS.remove(WorldChangeListener.class, this);
		trails.clear();
	}

	@Override
	public void onWorldChange(ClientLevel world)
	{
		trails.clear();
	}

	@Override
	public void onUpdate()
	{
		if(MC.level == null || MC.player == null)
		{
			trails.clear();
			return;
		}

		Set<Entity> seen =
			Collections.newSetFromMap(new IdentityHashMap<Entity, Boolean>());
		int maxPoints = trailLength.getValueI();

		for(Entity entity : MC.level.entitiesForRendering())
		{
			if(!(entity instanceof Projectile) || entity.isRemoved())
				continue;

			seen.add(entity);
			Deque<Vec3> trail = trails.computeIfAbsent(entity,
				key -> new ArrayDeque<>());
			Vec3 pos = entity.position();

			if(trail.isEmpty()
				|| trail.peekLast().distanceToSqr(pos) > 0.0025)
				trail.addLast(pos);

			while(trail.size() > maxPoints)
				trail.removeFirst();
		}

		trails.keySet()
			.removeIf(entity -> entity.isRemoved() || !seen.contains(entity));
	}

	@Override
	public void onRender(PoseStack matrixStack, float partialTicks)
	{
		if(trails.isEmpty())
			return;

		Vec3 cam = RenderUtils.getCameraPos();
		Color col = color.getColor();
		float red = col.getRed() / 255F;
		float green = col.getGreen() / 255F;
		float blue = col.getBlue() / 255F;
		float baseAlpha = col.getAlpha() / 255F;
		Matrix4f matrix = matrixStack.last().pose();
		boolean depthTest = !throughWalls.isChecked();
		int depthFunc = depthTest ? GlConst.GL_LEQUAL : GlConst.GL_ALWAYS;
		RenderSystem.enableDepthTest();
		RenderSystem.depthFunc(depthFunc);
		RenderType layer = WurstRenderLayers.getLines(depthTest);

		MultiBufferSource.BufferSource vcp = RenderUtils.getVCP();
		VertexConsumer buffer = vcp.getBuffer(layer);
		for(Deque<Vec3> trail : trails.values())
			drawTrail(buffer, matrix, cam, trail, red, green, blue, baseAlpha);
		vcp.endBatch(layer);
	}

	private void drawTrail(VertexConsumer buffer, Matrix4f matrix, Vec3 cam,
		Deque<Vec3> trail, float red, float green, float blue,
		float baseAlpha)
	{
		if(trail.size() < 2)
			return;

		Vec3[] points = trail.toArray(new Vec3[0]);
		int count = points.length;

		for(int i = 1; i < count; i++)
		{
			Vec3 start = points[i - 1];
			Vec3 end = points[i];
			float fade = i / (float)count;
			float alpha = baseAlpha * fade;

			double dx = end.x - start.x;
			double dy = end.y - start.y;
			double dz = end.z - start.z;
			double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
			if(length < 1.0E-4)
				continue;

			float nx = (float)(dx / length);
			float ny = (float)(dy / length);
			float nz = (float)(dz / length);

			buffer
				.vertex(matrix, (float)(start.x - cam.x),
					(float)(start.y - cam.y), (float)(start.z - cam.z))
				.color(red, green, blue, alpha * 0.35F)
				.normal(nx, ny, nz).endVertex();
			buffer
				.vertex(matrix, (float)(end.x - cam.x),
					(float)(end.y - cam.y), (float)(end.z - cam.z))
				.color(red, green, blue, alpha).normal(nx, ny, nz)
				.endVertex();
		}
	}
}
