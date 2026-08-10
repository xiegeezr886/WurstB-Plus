package net.wurstclient.util.render;

import java.util.List;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.WurstRenderLayers;
import net.wurstclient.util.EntityUtils;
import net.wurstclient.util.RenderUtils;

public final class PlayerHaloRenderer
{
	private static final int SEGMENTS = 48;
	private static final double MIN_RADIUS = 0.34;
	private static final double MAX_RADIUS = 0.48;
	private static final double RADIUS_SCALE = 0.72;
	private static final double INNER_GLOW = 0.055;
	private static final double OUTER_GLOW = 0.045;
	private static final double HEAD_OFFSET = 0.18;

	private PlayerHaloRenderer()
	{
	}

	public static void render(PoseStack PoseStack,
		List<AbstractClientPlayer> players, Player localPlayer,
		float partialTicks, int color, boolean renderLocalPlayer)
	{
		if(players.isEmpty())
			return;

		Vec3 camera = RenderUtils.getCameraPos();
		float red = (color >> 16 & 0xFF) / 255F;
		float green = (color >> 8 & 0xFF) / 255F;
		float blue = (color & 0xFF) / 255F;
		Matrix4f matrix = PoseStack.last().pose();

		RenderUtils.submit(PoseStack, WurstRenderLayers.getQuads(true),
			quads -> {
				for(AbstractClientPlayer player : players)
					if(shouldRender(player, localPlayer, renderLocalPlayer))
						addGlow(quads, matrix,
							getCenter(player, partialTicks, camera),
							radiusForWidth(player.getBbWidth()), red, green,
							blue);
			});
		RenderUtils.submit(PoseStack, WurstRenderLayers.getLines(true),
			lines -> {
				for(AbstractClientPlayer player : players)
					if(shouldRender(player, localPlayer, renderLocalPlayer))
						addOutline(lines, matrix,
							getCenter(player, partialTicks, camera),
							radiusForWidth(player.getBbWidth()), red, green,
							blue);
			});
	}

	static double radiusForWidth(double width)
	{
		return Mth.clamp(width * RADIUS_SCALE, MIN_RADIUS, MAX_RADIUS);
	}

	private static boolean shouldRender(AbstractClientPlayer player,
		Player localPlayer, boolean renderLocalPlayer)
	{
		return !player.isRemoved() && player.isAlive() && !player.isInvisible()
			&& (renderLocalPlayer || player != localPlayer);
	}

	private static Vec3 getCenter(AbstractClientPlayer player,
		float partialTicks, Vec3 camera)
	{
		return EntityUtils.getLerpedPos(player, partialTicks).subtract(camera)
			.add(0, player.getBbHeight() + HEAD_OFFSET, 0);
	}

	private static void addGlow(VertexConsumer buffer, Matrix4f matrix,
		Vec3 center, double radius, float red, float green, float blue)
	{
		for(int i = 0; i < SEGMENTS; i++)
		{
			double angle1 = Math.PI * 2 * i / SEGMENTS;
			double angle2 = Math.PI * 2 * (i + 1) / SEGMENTS;
			addBandSegment(buffer, matrix, center, radius - INNER_GLOW, radius,
				angle1, angle2, red, green, blue, 0, 0.32F);
			addBandSegment(buffer, matrix, center, radius, radius + OUTER_GLOW,
				angle1, angle2, red, green, blue, 0.32F, 0);
		}
	}

	private static void addBandSegment(VertexConsumer buffer, Matrix4f matrix,
		Vec3 center, double innerRadius, double outerRadius, double angle1,
		double angle2, float red, float green, float blue, float innerAlpha,
		float outerAlpha)
	{
		addVertex(buffer, matrix, center, innerRadius, angle1, red, green, blue,
			innerAlpha);
		addVertex(buffer, matrix, center, innerRadius, angle2, red, green, blue,
			innerAlpha);
		addVertex(buffer, matrix, center, outerRadius, angle2, red, green, blue,
			outerAlpha);
		addVertex(buffer, matrix, center, outerRadius, angle1, red, green, blue,
			outerAlpha);
	}

	private static void addOutline(VertexConsumer buffer, Matrix4f matrix,
		Vec3 center, double radius, float red, float green, float blue)
	{
		for(int i = 0; i < SEGMENTS; i++)
		{
			double angle1 = Math.PI * 2 * i / SEGMENTS;
			double angle2 = Math.PI * 2 * (i + 1) / SEGMENTS;
			addVertex(buffer, matrix, center, radius, angle1, red, green, blue,
				0.95F);
			addVertex(buffer, matrix, center, radius, angle2, red, green, blue,
				0.95F);
		}
	}

	private static void addVertex(VertexConsumer buffer, Matrix4f matrix,
		Vec3 center, double radius, double angle, float red, float green,
		float blue, float alpha)
	{
		buffer.addVertex(matrix,
			(float)(center.x + Math.cos(angle) * radius), (float)center.y,
			(float)(center.z + Math.sin(angle) * radius))
			.setColor(red, green, blue, alpha);
	}
}
