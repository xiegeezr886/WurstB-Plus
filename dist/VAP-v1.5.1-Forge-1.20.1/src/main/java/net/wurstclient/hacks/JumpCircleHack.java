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
import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.WurstRenderLayers;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.events.WorldChangeListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.util.RenderUtils;

/**
 * Renders an expanding, fading ring under the player when they jump.
 *
 * <p>Simplification: Soar hooked a dedicated jump event. Wurst has no such
 * event, so jumps are detected from upward velocity while leaving the ground.
 */
@SearchTags({"jump circle", "jump", "circle", "landing", "jump ring"})
public final class JumpCircleHack extends Hack
	implements UpdateListener, RenderListener, WorldChangeListener
{
	private static final int DURATION = 20;
	private static final int SEGMENTS = 72;
	private static final double MAX_RADIUS = 0.7;

	private final ColorSetting color = new ColorSetting("Color",
		"Color of the jump circle.", new Color(0, 220, 255, 200));

	private final List<JumpCircle> circles = new ArrayList<>();
	private boolean jumped;

	public JumpCircleHack()
	{
		super("JumpCircle");
		setCategory(Category.RENDER);
		addSetting(color);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(RenderListener.class, this);
		EVENTS.add(WorldChangeListener.class, this);
		circles.clear();
		jumped = false;
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		EVENTS.remove(WorldChangeListener.class, this);
		circles.clear();
		jumped = false;
	}

	@Override
	public void onWorldChange(ClientLevel world)
	{
		circles.clear();
		jumped = false;
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null)
			return;

		boolean onGround = MC.player.onGround();
		if(onGround)
			jumped = false;
		else if(!jumped && !MC.player.isInWater() && !MC.player.isInLava()
			&& !MC.player.isPassenger()
			&& MC.player.getDeltaMovement().y > 0.15)
		{
			circles.add(new JumpCircle(MC.player.position()));
			jumped = true;
		}

		for(JumpCircle circle : circles)
			circle.age++;

		circles.removeIf(circle -> circle.age > DURATION);
	}

	@Override
	public void onRender(PoseStack matrixStack, float partialTicks)
	{
		if(circles.isEmpty())
			return;

		Vec3 cam = RenderUtils.getCameraPos();
		Color col = color.getColor();
		float red = col.getRed() / 255F;
		float green = col.getGreen() / 255F;
		float blue = col.getBlue() / 255F;
		float baseAlpha = col.getAlpha() / 255F;
		Matrix4f matrix = matrixStack.last().pose();

		MultiBufferSource.BufferSource vcp = RenderUtils.getVCP();
		RenderType layer = WurstRenderLayers.ESP_QUADS_NO_CULLING;
		VertexConsumer buffer = vcp.getBuffer(layer);
		for(JumpCircle circle : circles)
			drawCircle(buffer, matrix, cam, circle, red, green, blue,
				baseAlpha, partialTicks);
		vcp.endBatch(layer);
	}

	private void drawCircle(VertexConsumer buffer, Matrix4f matrix, Vec3 cam,
		JumpCircle circle, float red, float green, float blue,
		float baseAlpha, float partialTicks)
	{
		float progress = Math.min(1F,
			(circle.age + partialTicks) / (float)DURATION);
		double ease = Math.sqrt(1 - Math.pow(1 - progress, 2));
		double inner = MAX_RADIUS * ease;
		double outer = inner * 1.4;
		float fade = 1 - progress;
		float innerAlpha = baseAlpha * 0.6F * fade;
		float outerAlpha = baseAlpha * 0.2F * fade;

		double baseY = circle.pos.y + 0.03;
		for(int i = 0; i < SEGMENTS; i++)
		{
			double angle1 = Math.PI * 2 * i / SEGMENTS;
			double angle2 = Math.PI * 2 * (i + 1) / SEGMENTS;
			float cos1 = (float)Math.cos(angle1);
			float sin1 = (float)Math.sin(angle1);
			float cos2 = (float)Math.cos(angle2);
			float sin2 = (float)Math.sin(angle2);

			addVertex(buffer, matrix, circle.pos, cam, cos1, sin1, baseY,
				inner, red, green, blue, innerAlpha);
			addVertex(buffer, matrix, circle.pos, cam, cos2, sin2, baseY,
				inner, red, green, blue, innerAlpha);
			addVertex(buffer, matrix, circle.pos, cam, cos2, sin2, baseY,
				outer, red, green, blue, outerAlpha);
			addVertex(buffer, matrix, circle.pos, cam, cos1, sin1, baseY,
				outer, red, green, blue, outerAlpha);
		}
	}

	private void addVertex(VertexConsumer buffer, Matrix4f matrix, Vec3 center,
		Vec3 cam, float cos, float sin, double y, double radius, float red,
		float green, float blue, float alpha)
	{
		buffer
			.vertex(matrix, (float)(center.x + cos * radius - cam.x),
				(float)(y - cam.y), (float)(center.z + sin * radius - cam.z))
			.color(red, green, blue, alpha).endVertex();
	}

	private static final class JumpCircle
	{
		private final Vec3 pos;
		private int age;

		private JumpCircle(Vec3 pos)
		{
			this.pos = pos;
		}
	}
}
