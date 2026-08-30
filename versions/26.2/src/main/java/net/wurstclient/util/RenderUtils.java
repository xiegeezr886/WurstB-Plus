/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

import java.util.List;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.opengl.GlConst;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.joml.Matrix3x2fStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.WurstClient;
import net.wurstclient.WurstRenderLayers;

public enum RenderUtils
{
	;
	
	private static SubmitNodeStorage submitNodeStorage;
	
	public static void setSubmitNodeStorage(SubmitNodeStorage storage)
	{
		submitNodeStorage = storage;
	}
	
	/**
	 * Submits custom geometry to the current render frame. The renderer
	 * callback receives a VertexConsumer that writes into the given RenderType.
	 */
	public static void submit(PoseStack matrices, RenderType layer,
		java.util.function.Consumer<VertexConsumer> renderer)
	{
		if(submitNodeStorage == null)
			return;
		submitNodeStorage.submitCustomGeometry(matrices, layer,
			(pose, buffer) -> renderer.accept(buffer));
	}
	
	/**
	 * Submits text to the current render frame, transformed by the given
	 * PoseStack (world-space text).
	 */
	public static void submitText(PoseStack matrices, float x, float y,
		FormattedCharSequence text, boolean dropShadow,
		Font.DisplayMode displayMode, int lightCoords, int color,
		int backgroundColor, int outlineColor)
	{
		if(submitNodeStorage == null)
			return;
		submitNodeStorage.submitText(matrices, x, y, text, dropShadow,
			displayMode, lightCoords, color, backgroundColor, outlineColor);
	}
	
	public static void applyRegionalRenderOffset(PoseStack matrixStack)
	{
		applyRegionalRenderOffset(matrixStack, getCameraRegion());
	}
	
	public static void applyRegionalRenderOffset(PoseStack matrixStack,
		ChunkAccess chunk)
	{
		applyRegionalRenderOffset(matrixStack, RegionPos.of(chunk.getPos()));
	}
	
	public static void applyRegionalRenderOffset(PoseStack matrixStack,
		RegionPos region)
	{
		Vec3 offset = region.toVec3d().subtract(getCameraPos());
		matrixStack.translate(offset.x, offset.y, offset.z);
	}
	
	public static void applyRenderOffset(PoseStack matrixStack)
	{
		Vec3 camPos = getCameraPos();
		matrixStack.translate(-camPos.x, -camPos.y, -camPos.z);
	}
	
	public static Vec3 getCameraPos()
	{
		return WurstClient.MC.gameRenderer.mainCamera().position();
	}
	
	public static BlockPos getCameraBlockPos()
	{
		return BlockPos.containing(getCameraPos());
	}
	
	public static RegionPos getCameraRegion()
	{
		return RegionPos.of(getCameraBlockPos());
	}
	
	public static float[] getRainbowColor()
	{
		float x = System.currentTimeMillis() % 2000 / 1000F;
		float pi = (float)Math.PI;
		
		float[] rainbow = new float[3];
		rainbow[0] = 0.5F + 0.5F * Mth.sin(x * pi);
		rainbow[1] = 0.5F + 0.5F * Mth.sin((x + 4F / 3F) * pi);
		rainbow[2] = 0.5F + 0.5F * Mth.sin((x + 8F / 3F) * pi);
		return rainbow;
	}
	
	public static void setShaderColor(float[] rgb, float opacity)
	{
		// Shader color managed by render pipeline
	}
	
	public static int toIntColor(float[] rgb, float opacity)
	{
		return (int)(Mth.clamp(opacity, 0, 1) * 255) << 24
			| (int)(Mth.clamp(rgb[0], 0, 1) * 255) << 16
			| (int)(Mth.clamp(rgb[1], 0, 1) * 255) << 8
			| (int)(Mth.clamp(rgb[2], 0, 1) * 255);
	}
	
	public static void drawLine(PoseStack matrices, Vec3 start, Vec3 end,
		int color, boolean depthTest)
	{
		RenderType layer = WurstRenderLayers.getLines(depthTest);
		Vec3 offset = getCameraPos().reverse();
		submit(matrices, layer, buffer -> drawLine(matrices, buffer,
			start.add(offset), end.add(offset), color));
	}
	
	private static Vec3 getTracerOrigin(float partialTicks)
	{
		Vec3 start = RotationUtils.getClientLookVec(partialTicks).scale(10);
		if(WurstClient.MC.options
			.getCameraType() == CameraType.THIRD_PERSON_FRONT)
			start = start.reverse();
		
		return start;
	}
	
	public static void drawTracer(PoseStack matrices, float partialTicks,
		Vec3 end, int color, boolean depthTest)
	{
		RenderType layer = WurstRenderLayers.getLines(depthTest);
		Vec3 start = getTracerOrigin(partialTicks);
		Vec3 offset = getCameraPos().reverse();
		submit(matrices, layer, buffer -> drawLine(matrices, buffer, start,
			end.add(offset), color));
	}
	
	public static void drawTracers(PoseStack matrices, float partialTicks,
		List<Vec3> ends, int color, boolean depthTest)
	{
		RenderType layer = WurstRenderLayers.getLines(depthTest);
		Vec3 start = getTracerOrigin(partialTicks);
		Vec3 offset = getCameraPos().reverse();
		submit(matrices, layer, buffer -> {
			for(Vec3 end : ends)
				drawLine(matrices, buffer, start, end.add(offset), color);
		});
	}
	
	public static void drawTracers(PoseStack matrices, float partialTicks,
		List<ColoredPoint> ends, boolean depthTest)
	{
		RenderType layer = WurstRenderLayers.getLines(depthTest);
		Vec3 start = getTracerOrigin(partialTicks);
		Vec3 offset = getCameraPos().reverse();
		submit(matrices, layer, buffer -> {
			for(ColoredPoint end : ends)
				drawLine(matrices, buffer, start, end.point().add(offset),
					end.color());
		});
	}
	
	public static void drawLine(PoseStack matrices, VertexConsumer buffer,
		Vec3 start, Vec3 end, int color)
	{
		PoseStack.Pose entry = matrices.last();
		float x1 = (float)start.x;
		float y1 = (float)start.y;
		float z1 = (float)start.z;
		float x2 = (float)end.x;
		float y2 = (float)end.y;
		float z2 = (float)end.z;
		drawLine(entry, buffer, x1, y1, z1, x2, y2, z2, color);
	}
	
	public static void drawLine(PoseStack.Pose entry, VertexConsumer buffer,
		float x1, float y1, float z1, float x2, float y2, float z2, int color)
	{
		Vector3f normal = new Vector3f(x2, y2, z2).sub(x1, y1, z1).normalize();
		buffer.addVertex(entry.pose(), x1, y1, z1).setColor(color)
			.setNormal( normal.x, normal.y, normal.z).setLineWidth(2)
			;
		
		// If the line goes through the screen, add another vertex there. This
		// works around a bug in Minecraft's line shader.
		float t = new Vector3f(x1, y1, z1).negate().dot(normal);
		float length = new Vector3f(x2, y2, z2).sub(x1, y1, z1).length();
		if(t > 0 && t < length)
		{
			Vector3f closeToCam = new Vector3f(normal).mul(t).add(x1, y1, z1);
			buffer
				.addVertex(entry.pose(), closeToCam.x, closeToCam.y,
					closeToCam.z)
				.setColor(color)
				.setNormal( normal.x, normal.y, normal.z).setLineWidth(2)
				;
			buffer
				.addVertex(entry.pose(), closeToCam.x, closeToCam.y,
					closeToCam.z)
				.setColor(color)
				.setNormal( normal.x, normal.y, normal.z).setLineWidth(2)
				;
		}
		
		buffer.addVertex(entry.pose(), x2, y2, z2).setColor(color)
			.setNormal( normal.x, normal.y, normal.z).setLineWidth(2)
			;
	}
	
	public static void drawLine(VertexConsumer buffer, float x1, float y1,
		float z1, float x2, float y2, float z2, int color)
	{
		Vector3f n = new Vector3f(x2, y2, z2).sub(x1, y1, z1).normalize();
		buffer.addVertex(x1, y1, z1).setColor(color).setNormal(n.x, n.y, n.z).setLineWidth(2);
		buffer.addVertex(x2, y2, z2).setColor(color).setNormal(n.x, n.y, n.z).setLineWidth(2);
	}
	
	public static void drawCurvedLine(PoseStack matrices, List<Vec3> points,
		int color, boolean depthTest)
	{
		RenderType layer = WurstRenderLayers.getLineStrip(depthTest);
		Vec3 offset = getCameraPos().reverse();
		List<Vec3> points2 = points.stream().map(v -> v.add(offset)).toList();
		submit(matrices, layer, buffer -> drawCurvedLine(matrices, buffer,
			points2, color));
	}
	
	public static void drawCurvedLine(PoseStack matrices,
		VertexConsumer buffer, List<Vec3> points, int color)
	{
		if(points.size() < 2)
			return;
		
		PoseStack.Pose entry = matrices.last();
		Vector3f first = points.get(0).toVector3f();
		Vector3f second = points.get(1).toVector3f();
		Vector3f normal = new Vector3f(first).sub(second).normalize();
		buffer.addVertex(entry.pose(), first.x, first.y, first.z)
			.setColor(color)
			.setNormal( normal.x, normal.y, normal.z).setLineWidth(2)
			;
		
		for(int i = 1; i < points.size(); i++)
		{
			Vector3f prev = points.get(i - 1).toVector3f();
			Vector3f current = points.get(i).toVector3f();
			normal = new Vector3f(current).sub(prev).normalize();
			buffer
				.addVertex(entry.pose(), current.x, current.y,
					current.z)
				.setColor(color)
				.setNormal( normal.x, normal.y, normal.z).setLineWidth(2)
				;
		}
	}
	
	public static void drawSolidBox(PoseStack matrices, AABB box, int color,
		boolean depthTest)
	{
		RenderType layer = WurstRenderLayers.getQuads(depthTest);
		submit(matrices, layer, buffer -> drawSolidBox(matrices, buffer,
			box.move(getCameraPos().reverse()), color));
	}
	
	public static void drawSolidBoxes(PoseStack matrices, List<AABB> boxes,
		int color, boolean depthTest)
	{
		RenderType layer = WurstRenderLayers.getQuads(depthTest);
		Vec3 camOffset = getCameraPos().reverse();
		submit(matrices, layer, buffer -> {
			for(AABB box : boxes)
				drawSolidBox(matrices, buffer, box.move(camOffset), color);
		});
	}
	
	public static void drawSolidBoxes(PoseStack matrices,
		List<ColoredBox> boxes, boolean depthTest)
	{
		RenderType layer = WurstRenderLayers.getQuads(depthTest);
		Vec3 camOffset = getCameraPos().reverse();
		submit(matrices, layer, buffer -> {
			for(ColoredBox box : boxes)
				drawSolidBox(matrices, buffer, box.box().move(camOffset),
					box.color());
		});
	}
	
	public static void drawSolidBox(VertexConsumer buffer, AABB box, int color)
	{
		drawSolidBox(new PoseStack(), buffer, box, color);
	}
	
	public static void drawSolidBox(PoseStack matrices, VertexConsumer buffer,
		AABB box, int color)
	{
		PoseStack.Pose entry = matrices.last();
		float x1 = (float)box.minX;
		float y1 = (float)box.minY;
		float z1 = (float)box.minZ;
		float x2 = (float)box.maxX;
		float y2 = (float)box.maxY;
		float z2 = (float)box.maxZ;
		
		buffer.addVertex(entry.pose(), x1, y1, z1).setColor(color)
			;
		buffer.addVertex(entry.pose(), x2, y1, z1).setColor(color)
			;
		buffer.addVertex(entry.pose(), x2, y1, z2).setColor(color)
			;
		buffer.addVertex(entry.pose(), x1, y1, z2).setColor(color)
			;
		
		buffer.addVertex(entry.pose(), x1, y2, z1).setColor(color)
			;
		buffer.addVertex(entry.pose(), x1, y2, z2).setColor(color)
			;
		buffer.addVertex(entry.pose(), x2, y2, z2).setColor(color)
			;
		buffer.addVertex(entry.pose(), x2, y2, z1).setColor(color)
			;
		
		buffer.addVertex(entry.pose(), x1, y1, z1).setColor(color)
			;
		buffer.addVertex(entry.pose(), x1, y2, z1).setColor(color)
			;
		buffer.addVertex(entry.pose(), x2, y2, z1).setColor(color)
			;
		buffer.addVertex(entry.pose(), x2, y1, z1).setColor(color)
			;
		
		buffer.addVertex(entry.pose(), x2, y1, z1).setColor(color)
			;
		buffer.addVertex(entry.pose(), x2, y2, z1).setColor(color)
			;
		buffer.addVertex(entry.pose(), x2, y2, z2).setColor(color)
			;
		buffer.addVertex(entry.pose(), x2, y1, z2).setColor(color)
			;
		
		buffer.addVertex(entry.pose(), x1, y1, z2).setColor(color)
			;
		buffer.addVertex(entry.pose(), x2, y1, z2).setColor(color)
			;
		buffer.addVertex(entry.pose(), x2, y2, z2).setColor(color)
			;
		buffer.addVertex(entry.pose(), x1, y2, z2).setColor(color)
			;
		
		buffer.addVertex(entry.pose(), x1, y1, z1).setColor(color)
			;
		buffer.addVertex(entry.pose(), x1, y1, z2).setColor(color)
			;
		buffer.addVertex(entry.pose(), x1, y2, z2).setColor(color)
			;
		buffer.addVertex(entry.pose(), x1, y2, z1).setColor(color)
			;
	}
	
	public static void drawOutlinedBox(PoseStack matrices, AABB box, int color,
		boolean depthTest)
	{
		RenderType layer = WurstRenderLayers.getLines(depthTest);
		submit(matrices, layer, buffer -> drawOutlinedBox(matrices, buffer,
			box.move(getCameraPos().reverse()), color));
	}
	
	public static void drawOutlinedBoxes(PoseStack matrices, List<AABB> boxes,
		int color, boolean depthTest)
	{
		RenderType layer = WurstRenderLayers.getLines(depthTest);
		Vec3 camOffset = getCameraPos().reverse();
		submit(matrices, layer, buffer -> {
			for(AABB box : boxes)
				drawOutlinedBox(matrices, buffer, box.move(camOffset), color);
		});
	}
	
	public static void drawOutlinedBoxes(PoseStack matrices,
		List<ColoredBox> boxes, boolean depthTest)
	{
		RenderType layer = WurstRenderLayers.getLines(depthTest);
		Vec3 camOffset = getCameraPos().reverse();
		submit(matrices, layer, buffer -> {
			for(ColoredBox box : boxes)
				drawOutlinedBox(matrices, buffer, box.box().move(camOffset),
					box.color());
		});
	}
	
	public static void drawOutlinedBox(VertexConsumer buffer, AABB box,
		int color)
	{
		drawOutlinedBox(new PoseStack(), buffer, box, color);
	}
	
	public static void drawOutlinedBox(PoseStack matrices,
		VertexConsumer buffer, AABB box, int color)
	{
		PoseStack.Pose entry = matrices.last();
		float x1 = (float)box.minX;
		float y1 = (float)box.minY;
		float z1 = (float)box.minZ;
		float x2 = (float)box.maxX;
		float y2 = (float)box.maxY;
		float z2 = (float)box.maxZ;
		
		// bottom lines
		buffer.addVertex(entry.pose(), x1, y1, z1).setColor(color)
			.setNormal( 1, 0, 0).setLineWidth(2);
		buffer.addVertex(entry.pose(), x2, y1, z1).setColor(color)
			.setNormal( 1, 0, 0).setLineWidth(2);
		buffer.addVertex(entry.pose(), x1, y1, z1).setColor(color)
			.setNormal( 0, 0, 1).setLineWidth(2);
		buffer.addVertex(entry.pose(), x1, y1, z2).setColor(color)
			.setNormal( 0, 0, 1).setLineWidth(2);
		buffer.addVertex(entry.pose(), x2, y1, z1).setColor(color)
			.setNormal( 0, 0, 1).setLineWidth(2);
		buffer.addVertex(entry.pose(), x2, y1, z2).setColor(color)
			.setNormal( 0, 0, 1).setLineWidth(2);
		buffer.addVertex(entry.pose(), x1, y1, z2).setColor(color)
			.setNormal( 1, 0, 0).setLineWidth(2);
		buffer.addVertex(entry.pose(), x2, y1, z2).setColor(color)
			.setNormal( 1, 0, 0).setLineWidth(2);
		
		// top lines
		buffer.addVertex(entry.pose(), x1, y2, z1).setColor(color)
			.setNormal( 1, 0, 0).setLineWidth(2);
		buffer.addVertex(entry.pose(), x2, y2, z1).setColor(color)
			.setNormal( 1, 0, 0).setLineWidth(2);
		buffer.addVertex(entry.pose(), x1, y2, z1).setColor(color)
			.setNormal( 0, 0, 1).setLineWidth(2);
		buffer.addVertex(entry.pose(), x1, y2, z2).setColor(color)
			.setNormal( 0, 0, 1).setLineWidth(2);
		buffer.addVertex(entry.pose(), x2, y2, z1).setColor(color)
			.setNormal( 0, 0, 1).setLineWidth(2);
		buffer.addVertex(entry.pose(), x2, y2, z2).setColor(color)
			.setNormal( 0, 0, 1).setLineWidth(2);
		buffer.addVertex(entry.pose(), x1, y2, z2).setColor(color)
			.setNormal( 1, 0, 0).setLineWidth(2);
		buffer.addVertex(entry.pose(), x2, y2, z2).setColor(color)
			.setNormal( 1, 0, 0).setLineWidth(2);
		
		// side lines
		buffer.addVertex(entry.pose(), x1, y1, z1).setColor(color)
			.setNormal( 0, 1, 0).setLineWidth(2);
		buffer.addVertex(entry.pose(), x1, y2, z1).setColor(color)
			.setNormal( 0, 1, 0).setLineWidth(2);
		buffer.addVertex(entry.pose(), x2, y1, z1).setColor(color)
			.setNormal( 0, 1, 0).setLineWidth(2);
		buffer.addVertex(entry.pose(), x2, y2, z1).setColor(color)
			.setNormal( 0, 1, 0).setLineWidth(2);
		buffer.addVertex(entry.pose(), x1, y1, z2).setColor(color)
			.setNormal( 0, 1, 0).setLineWidth(2);
		buffer.addVertex(entry.pose(), x1, y2, z2).setColor(color)
			.setNormal( 0, 1, 0).setLineWidth(2);
		buffer.addVertex(entry.pose(), x2, y1, z2).setColor(color)
			.setNormal( 0, 1, 0).setLineWidth(2);
		buffer.addVertex(entry.pose(), x2, y2, z2).setColor(color)
			.setNormal( 0, 1, 0).setLineWidth(2);
	}
	
	public static void drawCrossBox(PoseStack matrices, AABB box, int color,
		boolean depthTest)
	{
		RenderType layer = WurstRenderLayers.getLines(depthTest);
		submit(matrices, layer, buffer -> drawCrossBox(matrices, buffer,
			box.move(getCameraPos().reverse()), color));
	}
	
	public static void drawCrossBoxes(PoseStack matrices, List<AABB> boxes,
		int color, boolean depthTest)
	{
		RenderType layer = WurstRenderLayers.getLines(depthTest);
		Vec3 camOffset = getCameraPos().reverse();
		submit(matrices, layer, buffer -> {
			for(AABB box : boxes)
				drawCrossBox(matrices, buffer, box.move(camOffset), color);
		});
	}
	
	public static void drawCrossBoxes(PoseStack matrices,
		List<ColoredBox> boxes, boolean depthTest)
	{
		RenderType layer = WurstRenderLayers.getLines(depthTest);
		Vec3 camOffset = getCameraPos().reverse();
		submit(matrices, layer, buffer -> {
			for(ColoredBox box : boxes)
				drawCrossBox(matrices, buffer, box.box().move(camOffset),
					box.color());
		});
	}
	
	public static void drawCrossBox(VertexConsumer buffer, AABB box, int color)
	{
		drawCrossBox(new PoseStack(), buffer, box, color);
	}
	
	public static void drawCrossBox(PoseStack matrices, VertexConsumer buffer,
		AABB box, int color)
	{
		PoseStack.Pose entry = matrices.last();
		float x1 = (float)box.minX;
		float y1 = (float)box.minY;
		float z1 = (float)box.minZ;
		float x2 = (float)box.maxX;
		float y2 = (float)box.maxY;
		float z2 = (float)box.maxZ;
		
		// back
		buffer.addVertex(entry.pose(), x1, y1, z1).setColor(color)
			.setNormal( 1, 1, 0).setLineWidth(2);
		buffer.addVertex(entry.pose(), x2, y2, z1).setColor(color)
			.setNormal( 1, 1, 0).setLineWidth(2);
		buffer.addVertex(entry.pose(), x2, y1, z1).setColor(color)
			.setNormal( -1, 1, 0).setLineWidth(2);
		buffer.addVertex(entry.pose(), x1, y2, z1).setColor(color)
			.setNormal( -1, 1, 0).setLineWidth(2);
		
		// left
		buffer.addVertex(entry.pose(), x2, y1, z1).setColor(color)
			.setNormal( 0, 1, 1).setLineWidth(2);
		buffer.addVertex(entry.pose(), x2, y2, z2).setColor(color)
			.setNormal( 0, 1, 1).setLineWidth(2);
		buffer.addVertex(entry.pose(), x2, y1, z2).setColor(color)
			.setNormal( 0, 1, -1).setLineWidth(2);
		buffer.addVertex(entry.pose(), x2, y2, z1).setColor(color)
			.setNormal( 0, 1, -1).setLineWidth(2);
		
		// front
		buffer.addVertex(entry.pose(), x2, y1, z2).setColor(color)
			.setNormal( -1, 1, 0).setLineWidth(2);
		buffer.addVertex(entry.pose(), x1, y2, z2).setColor(color)
			.setNormal( -1, 1, 0).setLineWidth(2);
		buffer.addVertex(entry.pose(), x1, y1, z2).setColor(color)
			.setNormal( 1, 1, 0).setLineWidth(2);
		buffer.addVertex(entry.pose(), x2, y2, z2).setColor(color)
			.setNormal( 1, 1, 0).setLineWidth(2);
		
		// right
		buffer.addVertex(entry.pose(), x1, y1, z2).setColor(color)
			.setNormal( 0, 1, -1).setLineWidth(2);
		buffer.addVertex(entry.pose(), x1, y2, z1).setColor(color)
			.setNormal( 0, 1, -1).setLineWidth(2);
		buffer.addVertex(entry.pose(), x1, y1, z1).setColor(color)
			.setNormal( 0, 1, 1).setLineWidth(2);
		buffer.addVertex(entry.pose(), x1, y2, z2).setColor(color)
			.setNormal( 0, 1, 1).setLineWidth(2);
		
		// top
		buffer.addVertex(entry.pose(), x1, y2, z2).setColor(color)
			.setNormal( 1, 0, -1).setLineWidth(2);
		buffer.addVertex(entry.pose(), x2, y2, z1).setColor(color)
			.setNormal( 1, 0, -1).setLineWidth(2);
		buffer.addVertex(entry.pose(), x1, y2, z1).setColor(color)
			.setNormal( 1, 0, 1).setLineWidth(2);
		buffer.addVertex(entry.pose(), x2, y2, z2).setColor(color)
			.setNormal( 1, 0, 1).setLineWidth(2);
		
		// bottom
		buffer.addVertex(entry.pose(), x2, y1, z1).setColor(color)
			.setNormal( -1, 0, 1).setLineWidth(2);
		buffer.addVertex(entry.pose(), x1, y1, z2).setColor(color)
			.setNormal( -1, 0, 1).setLineWidth(2);
		buffer.addVertex(entry.pose(), x1, y1, z1).setColor(color)
			.setNormal( 1, 0, 1).setLineWidth(2);
		buffer.addVertex(entry.pose(), x2, y1, z2).setColor(color)
			.setNormal( 1, 0, 1).setLineWidth(2);
	}
	
	public static void drawNode(PoseStack matrices, AABB box, int color,
		boolean depthTest)
	{
		RenderType layer = WurstRenderLayers.getLines(depthTest);
		submit(matrices, layer, buffer -> drawNode(matrices, buffer,
			box.move(getCameraPos().reverse()), color));
	}
	
	public static void drawNode(VertexConsumer buffer, AABB box, int color)
	{
		drawNode(new PoseStack(), buffer, box, color);
	}
	
	public static void drawNode(PoseStack matrices, VertexConsumer buffer,
		AABB box, int color)
	{
		PoseStack.Pose entry = matrices.last();
		float x1 = (float)box.minX;
		float y1 = (float)box.minY;
		float z1 = (float)box.minZ;
		float x2 = (float)box.maxX;
		float y2 = (float)box.maxY;
		float z2 = (float)box.maxZ;
		float x3 = (x1 + x2) / 2F;
		float y3 = (y1 + y2) / 2F;
		float z3 = (z1 + z2) / 2F;
		
		// middle part
		drawLine(entry, buffer, x3, y3, z2, x1, y3, z3, color);
		drawLine(entry, buffer, x1, y3, z3, x3, y3, z1, color);
		drawLine(entry, buffer, x3, y3, z1, x2, y3, z3, color);
		drawLine(entry, buffer, x2, y3, z3, x3, y3, z2, color);
		
		// top part
		drawLine(entry, buffer, x3, y2, z3, x2, y3, z3, color);
		drawLine(entry, buffer, x3, y2, z3, x1, y3, z3, color);
		drawLine(entry, buffer, x3, y2, z3, x3, y3, z1, color);
		drawLine(entry, buffer, x3, y2, z3, x3, y3, z2, color);
		
		// bottom part
		drawLine(entry, buffer, x3, y1, z3, x2, y3, z3, color);
		drawLine(entry, buffer, x3, y1, z3, x1, y3, z3, color);
		drawLine(entry, buffer, x3, y1, z3, x3, y3, z1, color);
		drawLine(entry, buffer, x3, y1, z3, x3, y3, z2, color);
	}
	
	public static void drawArrow(PoseStack matrices, VertexConsumer buffer,
		BlockPos from, BlockPos to, RegionPos region, int color)
	{
		Vec3 fromVec = Vec3.atCenterOf(from).subtract(region.x(), 0, region.z());
		Vec3 toVec = Vec3.atCenterOf(to).subtract(region.x(), 0, region.z());
		drawArrow(matrices, buffer, fromVec, toVec, color, 1 / 16F);
	}
	
	public static void drawArrow(VertexConsumer buffer, Vec3 from, Vec3 to,
		int color, float headSize)
	{
		drawArrow(new PoseStack(), buffer, from, to, color, headSize);
	}
	
	public static void drawArrow(PoseStack matrices, VertexConsumer buffer,
		Vec3 from, Vec3 to, int color, float headSize)
	{
		matrices.pushPose();
		PoseStack.Pose entry = matrices.last();
		Matrix4f matrix = entry.pose();
		
		// main line
		drawLine(matrices, buffer, from, to, color);
		
		matrices.translate(to.x, to.y, to.z);
		matrices.scale(headSize, headSize, headSize);
		
		double xDiff = to.x - from.x;
		double yDiff = to.y - from.y;
		double zDiff = to.z - from.z;
		
		float xAngle = (float)(Math.atan2(yDiff, -zDiff) + Math.toRadians(90));
		matrix.rotate(xAngle, new Vector3f(1, 0, 0));
		
		double yzDiff = Math.sqrt(yDiff * yDiff + zDiff * zDiff);
		float zAngle = (float)Math.atan2(xDiff, yzDiff);
		matrix.rotate(zAngle, new Vector3f(0, 0, 1));
		
		// arrow head
		drawLine(entry, buffer, 0, 2, 1, -1, 2, 0, color);
		drawLine(entry, buffer, -1, 2, 0, 0, 2, -1, color);
		drawLine(entry, buffer, 0, 2, -1, 1, 2, 0, color);
		drawLine(entry, buffer, 1, 2, 0, 0, 2, 1, color);
		drawLine(entry, buffer, 1, 2, 0, -1, 2, 0, color);
		drawLine(entry, buffer, 0, 2, 1, 0, 2, -1, color);
		drawLine(entry, buffer, 0, 0, 0, 1, 2, 0, color);
		drawLine(entry, buffer, 0, 0, 0, -1, 2, 0, color);
		drawLine(entry, buffer, 0, 0, 0, 0, 2, -1, color);
		drawLine(entry, buffer, 0, 0, 0, 0, 2, 1, color);
		
		matrices.popPose();
	}
	
	public static void drawItem(GuiGraphicsExtractor context, ItemStack stack, int x,
		int y, boolean large)
	{
		Matrix3x2fStack matrixStack = context.pose();
		
		matrixStack.pushMatrix();
		matrixStack.translate(x, y);
		if(large)
			matrixStack.scale(1.5F, 1.5F);
		else
			matrixStack.scale(0.75F, 0.75F);
		
		ItemStack renderStack = stack.isEmpty() || stack.getItem() == null
			? new ItemStack(Blocks.GRASS_BLOCK) : stack;
		// Lighting managed by render pipeline
		context.item(renderStack, 0, 0);
		// Lighting managed by render pipeline
		matrixStack.popMatrix();
		
		if(stack.isEmpty())
		{
			matrixStack.pushMatrix();
			matrixStack.translate(x, y);
			if(large)
				matrixStack.scale(2, 2);
			
			Font tr = WurstClient.MC.font;
			context.text(tr, "?", 3, 2, 0xFFf0f0f0, true);
			
			matrixStack.popMatrix();
		}
		// Shader color managed by render pipeline
	}
	
	/**
	 * Similar to {@link GuiGraphicsExtractor#fill(int, int, int, int, int)}, but uses
	 * floating-point coordinates instead of integers.
	 */
	public static void fill2D(GuiGraphicsExtractor context, float x1, float y1, float x2,
		float y2, int color)
	{
		// TODO: 26.1.2 - new GUI rendering pipeline
		// Scale to pixel coordinates and use context.fill()
		int scale = WurstClient.MC.getWindow().getGuiScale();
		int xs1 = (int)(x1 * scale);
		int ys1 = (int)(y1 * scale);
		int xs2 = (int)(x2 * scale);
		int ys2 = (int)(y2 * scale);
		
		context.pose().pushMatrix();
		context.pose().scale(1F / scale);
		context.fill(xs1, ys1, xs2, ys2, color);
		context.pose().popMatrix();
	}
	
	/**
	 * Renders the given vertices in QUADS draw mode.
	 *
	 * @apiNote Due to back-face culling, quads will be invisible if their
	 *          vertices are not supplied in counter-clockwise order.
	 */
	public static void fillQuads2D(GuiGraphicsExtractor context, float[][] vertices,
		int color)
	{
		if(vertices == null || vertices.length < 4 || color >>> 24 == 0)
			return;
		context.getRenderState().addGuiElement(new PolygonRenderState(
			context.pose(), vertices, color));
	}
	
	/**
	 * Renders the given vertices in TRIANGLE_STRIP draw mode.
	 *
	 * @apiNote Due to back-face culling, triangles will be invisible if their
	 *          vertices are not supplied in counter-clockwise order.
	 */
	public static void fillTriangle2D(GuiGraphicsExtractor context, float[][] vertices,
		int color)
	{
		if(vertices == null || vertices.length < 3 || color >>> 24 == 0)
			return;
		float[][] quad = {vertices[0], vertices[1], vertices[2], vertices[2]};
		fillQuads2D(context, quad, color);
	}
	
	/**
	 * Similar to {@link GuiGraphicsExtractor#hLine(int, int, int, int)} and
	 * {@link GuiGraphicsExtractor#vLine(int, int, int, int)}, but supports
	 * diagonal lines, uses floating-point coordinates instead of integers, is
	 * one actual pixel wide instead of one scaled pixel, uses fewer draw calls
	 * than the vanilla method, and uses a z value of 1 to ensure that lines
	 * show up above fills.
	 */
	public static void drawLine2D(GuiGraphicsExtractor context, float x1, float y1,
		float x2, float y2, int color)
	{
		// TODO: 26.1.2 - needs guiRenderState.addGuiElement() approach
	}
	
	/**
	 * Similar to {@link GuiGraphicsExtractor#renderOutline(int, int, int, int, int)}, but
	 * uses floating-point coordinates instead of integers, is one actual pixel
	 * wide instead of one scaled pixel, uses fewer draw calls than the vanilla
	 * method, and uses a z value of 1 to ensure that lines show up above fills.
	 */
	public static void drawBorder2D(GuiGraphicsExtractor context, float x1, float y1,
		float x2, float y2, int color)
	{
		// TODO: 26.1.2 - needs guiRenderState.addGuiElement() approach
	}
	
	/**
	 * Draws a 1px border around the given polygon.
	 */
	public static void drawLineStrip2D(GuiGraphicsExtractor context, float[][] vertices,
		int color)
	{
		// TODO: 26.1.2 - needs guiRenderState.addGuiElement() approach
	}
	
	/**
	 * Draws a box shadow around the given rectangle.
	 */
	public static void drawBoxShadow2D(GuiGraphicsExtractor context, int x1, int y1,
		int x2, int y2)
	{
		// TODO: 26.1.2 - needs guiRenderState.addGuiElement() approach
		float[] acColor = WurstClient.INSTANCE.getGui().getAcColor();
		
		// outline
		float xo1 = x1 - 0.1F;
		float xo2 = x2 + 0.1F;
		float yo1 = y1 - 0.1F;
		float yo2 = y2 + 0.1F;
		
		int outlineColor = toIntColor(acColor, 0.5F);
		drawBorder2D(context, xo1, yo1, xo2, yo2, outlineColor);
	}

	private static final class PolygonRenderState implements GuiElementRenderState
	{
		private final Matrix3x2fc pose;
		private final float[][] vertices;
		private final int color;
		private final ScreenRectangle bounds;

		private PolygonRenderState(Matrix3x2fc pose, float[][] vertices,
			int color)
		{
			this.pose = new Matrix3x2f(pose);
			this.vertices = vertices;
			this.color = color;
			float[] tx = new float[4];
			float[] ty = new float[4];
			Matrix3x2f poseCopy = new Matrix3x2f(pose);
			for(int i = 0; i < 4; i++)
			{
				tx[i] = poseCopy.m00 * vertices[i][0]
					+ poseCopy.m01 * vertices[i][1] + poseCopy.m20;
				ty[i] = poseCopy.m10 * vertices[i][0]
					+ poseCopy.m11 * vertices[i][1] + poseCopy.m21;
			}
			float minX = tx[0];
			float minY = ty[0];
			float maxX = minX;
			float maxY = minY;
			for(int i = 1; i < 4; i++)
			{
				minX = Math.min(minX, tx[i]);
				minY = Math.min(minY, ty[i]);
				maxX = Math.max(maxX, tx[i]);
				maxY = Math.max(maxY, ty[i]);
			}
			bounds = new ScreenRectangle((int)Math.floor(minX),
				(int)Math.floor(minY), (int)Math.ceil(maxX - minX + 1),
				(int)Math.ceil(maxY - minY + 1));
		}

		@Override
		public void buildVertices(VertexConsumer consumer)
		{
			for(float[] vertex : vertices)
				consumer.addVertexWith2DPose(pose, vertex[0], vertex[1])
					.setColor(color);
		}

		@Override
		public com.mojang.blaze3d.pipeline.RenderPipeline pipeline()
		{
			return RenderPipelines.GUI;
		}

		@Override
		public TextureSetup textureSetup()
		{
			return TextureSetup.noTexture();
		}

		@Override
		public ScreenRectangle scissorArea()
		{
			return null;
		}

		@Override
		public ScreenRectangle bounds()
		{
			return bounds;
		}
	}
	
	public record ColoredPoint(Vec3 point, int color)
	{}
	
	public record ColoredBox(AABB box, int color)
	{}
}
