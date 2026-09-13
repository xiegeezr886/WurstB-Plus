package net.wurstclient.util.render;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
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

	public static void render(PoseStack poseStack, Entity player,
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
		Matrix4f matrix = poseStack.last().pose();

		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableCull();
		RenderSystem.disableDepthTest();
		RenderSystem.depthMask(false);
		RenderSystem.setShader(GameRenderer::getPositionColorShader);

		drawBand(matrix, center, innerRadius, radius, segments, red, green,
			blue, 0.015F, edgeAlpha);
		drawBand(matrix, center, radius, radius + GLOW_WIDTH, segments,
			red, green, blue, edgeAlpha * 0.72F, 0);
		drawOutline(matrix, center.add(0, 0.006, 0), radius, segments,
			red, green, blue, outlineAlpha);

		RenderSystem.depthMask(true);
		RenderSystem.enableDepthTest();
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

	private static void drawBand(Matrix4f matrix, Vec3 center,
		double innerRadius, double outerRadius, int segments, float red,
		float green, float blue, float innerAlpha, float outerAlpha)
	{
		BufferBuilder buffer = Tesselator.getInstance().getBuilder();
		buffer.begin(VertexFormat.Mode.TRIANGLE_STRIP,
			DefaultVertexFormat.POSITION_COLOR);

		for(int i = 0; i <= segments; i++)
		{
			double angle = Math.PI * 2 * i / segments;
			float cos = (float)Math.cos(angle);
			float sin = (float)Math.sin(angle);

			buffer.vertex(matrix,
				(float)center.x + cos * (float)outerRadius, (float)center.y,
				(float)center.z + sin * (float)outerRadius)
				.color(red, green, blue, outerAlpha).endVertex();
			buffer.vertex(matrix,
				(float)center.x + cos * (float)innerRadius, (float)center.y,
				(float)center.z + sin * (float)innerRadius)
				.color(red, green, blue, innerAlpha).endVertex();
		}

		draw(buffer);
	}

	private static void drawOutline(Matrix4f matrix, Vec3 center,
		double radius, int segments, float red, float green, float blue,
		float alpha)
	{
		RenderSystem.lineWidth(2);
		BufferBuilder buffer = Tesselator.getInstance().getBuilder();
		buffer.begin(VertexFormat.Mode.DEBUG_LINE_STRIP,
			DefaultVertexFormat.POSITION_COLOR);

		for(int i = 0; i <= segments; i++)
		{
			double angle = Math.PI * 2 * i / segments;
			float cos = (float)Math.cos(angle);
			float sin = (float)Math.sin(angle);

			buffer.vertex(matrix,
				(float)center.x + cos * (float)radius, (float)center.y,
				(float)center.z + sin * (float)radius)
				.color(red, green, blue, alpha).endVertex();
		}

		draw(buffer);
	}

	private static void draw(BufferBuilder buffer)
	{
		BufferBuilder.RenderedBuffer rendered = buffer.endOrDiscardIfEmpty();
		if(rendered != null)
			BufferUploader.drawWithShader(rendered);
	}
}
