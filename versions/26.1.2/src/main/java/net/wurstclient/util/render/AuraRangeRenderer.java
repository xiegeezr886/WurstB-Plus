package net.wurstclient.util.render;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.WurstRenderLayers;
import net.wurstclient.util.EntityUtils;
import net.wurstclient.util.RenderUtils;

public final class AuraRangeRenderer
{
	private static final double MIN_BAND_WIDTH = 0.16;
	private static final double MAX_BAND_WIDTH = 0.32;
	private static final double GLOW_WIDTH = 0.12;

	private AuraRangeRenderer()
	{
	}

	public static void render(PoseStack PoseStack, Entity player,
		float partialTicks, double configuredRadius, int color, boolean active)
	{
		double radius = sanitizeRadius(configuredRadius);
		if(player == null || radius <= 0)
			return;

		Vec3 center = EntityUtils.getLerpedPos(player, partialTicks)
			.subtract(RenderUtils.getCameraPos()).add(0, 0.025, 0);
		int segments = segmentCount(radius);
		double innerRadius = innerRadius(radius);
		float red = (color >> 16 & 0xFF) / 255F;
		float green = (color >> 8 & 0xFF) / 255F;
		float blue = (color & 0xFF) / 255F;
		float pulse = (float)(Math.sin(System.nanoTime() * 2.4E-9) * 0.5
			+ 0.5);
		float edgeAlpha = (active ? 0.38F : 0.25F) + pulse * 0.05F;
		float outlineAlpha = active ? 0.9F : 0.68F;
		Matrix4f matrix = PoseStack.last().pose();
		MultiBufferSource.BufferSource buffers = RenderUtils.getVCP();
		RenderType quadLayer = WurstRenderLayers.ESP_QUADS_NO_CULLING;
		VertexConsumer quadBuffer = buffers.getBuffer(quadLayer);

		drawBand(quadBuffer, matrix, center, innerRadius, radius, segments, red, green,
			blue, 0.015F, edgeAlpha);
		drawBand(quadBuffer, matrix, center, radius, radius + GLOW_WIDTH, segments,
			red, green, blue, edgeAlpha * 0.72F, 0);
		buffers.endBatch(quadLayer);

		RenderType lineLayer = WurstRenderLayers.ESP_LINES;
		VertexConsumer lineBuffer = buffers.getBuffer(lineLayer);
		drawOutline(lineBuffer, matrix, center.add(0, 0.006, 0), radius, segments,
			red, green, blue, outlineAlpha);
		buffers.endBatch(lineLayer);
	}

	static double sanitizeRadius(double radius)
	{
		return Double.isFinite(radius) ? Math.max(0, radius) : 0;
	}

	static int segmentCount(double radius)
	{
		return Math.max(72, Math.min(192, (int)Math.ceil(radius * 24)));
	}

	static double innerRadius(double radius)
	{
		double width = Math.max(MIN_BAND_WIDTH,
			Math.min(MAX_BAND_WIDTH, radius * 0.06));
		return Math.max(0, radius - width);
	}

	private static void drawBand(VertexConsumer buffer, Matrix4f matrix,
		Vec3 center,
		double innerRadius, double outerRadius, int segments, float red,
		float green, float blue, float innerAlpha, float outerAlpha)
	{
		for(int i = 0; i < segments; i++)
		{
			double angle1 = Math.PI * 2 * i / segments;
			double angle2 = Math.PI * 2 * (i + 1) / segments;
			float cos1 = (float)Math.cos(angle1);
			float sin1 = (float)Math.sin(angle1);
			float cos2 = (float)Math.cos(angle2);
			float sin2 = (float)Math.sin(angle2);

			addVertex(buffer, matrix, center, cos1, sin1, innerRadius, red,
				green, blue, innerAlpha);
			addVertex(buffer, matrix, center, cos2, sin2, innerRadius, red,
				green, blue, innerAlpha);
			addVertex(buffer, matrix, center, cos2, sin2, outerRadius, red,
				green, blue, outerAlpha);
			addVertex(buffer, matrix, center, cos1, sin1, outerRadius, red,
				green, blue, outerAlpha);
		}
	}

	private static void drawOutline(VertexConsumer buffer, Matrix4f matrix,
		Vec3 center,
		double radius, int segments, float red, float green, float blue,
		float alpha)
	{
		for(int i = 0; i < segments; i++)
		{
			double angle1 = Math.PI * 2 * i / segments;
			double angle2 = Math.PI * 2 * (i + 1) / segments;
			float cos1 = (float)Math.cos(angle1);
			float sin1 = (float)Math.sin(angle1);
			float cos2 = (float)Math.cos(angle2);
			float sin2 = (float)Math.sin(angle2);
			buffer.addVertex(matrix,
				(float)center.x + cos1 * (float)radius, (float)center.y,
				(float)center.z + sin1 * (float)radius)
				.setColor(red, green, blue, alpha)
				.setNormal(sin1, 0, -cos1).setLineWidth(2);
			buffer.addVertex(matrix,
				(float)center.x + cos2 * (float)radius, (float)center.y,
				(float)center.z + sin2 * (float)radius)
				.setColor(red, green, blue, alpha)
				.setNormal(sin2, 0, -cos2).setLineWidth(2);
		}
	}

	private static void addVertex(VertexConsumer buffer, Matrix4f matrix,
		Vec3 center, float cos, float sin, double radius, float red,
		float green, float blue, float alpha)
	{
		buffer.addVertex(matrix, (float)center.x + cos * (float)radius,
			(float)center.y, (float)center.z + sin * (float)radius)
			.setColor(red, green, blue, alpha);
	}
}
