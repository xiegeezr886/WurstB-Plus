package net.wurstclient.clickgui2;

import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;

final class RoundedRectRenderer
{
	private static final int MAX_SEGMENTS = 16;
	private static final int MAX_POINTS = MAX_SEGMENTS * 4;
	private static final float[][] POINT_X = new float[4][MAX_POINTS];
	private static final float[][] POINT_Y = new float[4][MAX_POINTS];

	private RoundedRectRenderer()
	{
	}

	public static void fill(GuiGraphics graphics, float x1, float y1, float x2,
		float y2, float radius, int color)
	{
		if(x2 <= x1 || y2 <= y1 || color >>> 24 == 0)
			return;

		float safeRadius = clampRadius(x1, y1, x2, y2, radius);
		if(safeRadius < 0.5F)
		{
			graphics.fill((int)x1, (int)y1, (int)x2, (int)y2, color);
			return;
		}

		int segments = segmentsFor(safeRadius);
		prepareContour(0, x1 + 0.5F, y1 + 0.5F, x2 - 0.5F,
			y2 - 0.5F, Math.max(0, safeRadius - 0.5F), segments);
		prepareContour(1, x1 - 0.5F, y1 - 0.5F, x2 + 0.5F,
			y2 + 0.5F, safeRadius + 0.5F, segments);

		RenderState state = begin(graphics);
		RenderSystem.setShader(GameRenderer::getPositionColorShader);
		BufferBuilder buffer = Tesselator.getInstance().getBuilder();
		buffer.begin(VertexFormat.Mode.TRIANGLES,
			DefaultVertexFormat.POSITION_COLOR);
		Matrix4f pose = graphics.pose().last().pose();
		addSolidFan(buffer, pose, 0, segments, color);
		addColorStrip(buffer, pose, 0, 1, segments, color,
			color & 0xFFFFFF);
		Tesselator.getInstance().end();
		state.restore();
	}

	public static void outline(GuiGraphics graphics, float x1, float y1,
		float x2, float y2, float radius, int color)
	{
		if(x2 <= x1 || y2 <= y1 || color >>> 24 == 0)
			return;

		float safeRadius = clampRadius(x1, y1, x2, y2, radius);
		int segments = segmentsFor(safeRadius);
		prepareContour(0, x1 - 0.5F, y1 - 0.5F, x2 + 0.5F,
			y2 + 0.5F, safeRadius + 0.5F, segments);
		prepareContour(1, x1 + 0.3F, y1 + 0.3F, x2 - 0.3F,
			y2 - 0.3F, Math.max(0, safeRadius - 0.3F), segments);
		prepareContour(2, x1 + 1.35F, y1 + 1.35F, x2 - 1.35F,
			y2 - 1.35F, Math.max(0, safeRadius - 1.35F), segments);

		RenderState state = begin(graphics);
		RenderSystem.setShader(GameRenderer::getPositionColorShader);
		BufferBuilder buffer = Tesselator.getInstance().getBuilder();
		buffer.begin(VertexFormat.Mode.TRIANGLES,
			DefaultVertexFormat.POSITION_COLOR);
		Matrix4f pose = graphics.pose().last().pose();
		int transparent = color & 0xFFFFFF;
		addColorStrip(buffer, pose, 0, 1, segments, transparent, color);
		addColorStrip(buffer, pose, 1, 2, segments, color, transparent);
		Tesselator.getInstance().end();
		state.restore();
	}

	public static void outlineGradient(GuiGraphics graphics, float x1,
		float y1, float x2, float y2, float radius,
		FlatRenderer.GradientColorFn colorFn)
	{
		if(x2 <= x1 || y2 <= y1)
			return;

		float safeRadius = clampRadius(x1, y1, x2, y2, radius);
		int segments = segmentsFor(safeRadius);
		prepareContour(0, x1 - 0.5F, y1 - 0.5F, x2 + 0.5F,
			y2 + 0.5F, safeRadius + 0.5F, segments);
		prepareContour(1, x1 + 0.3F, y1 + 0.3F, x2 - 0.3F,
			y2 - 0.3F, Math.max(0, safeRadius - 0.3F), segments);
		prepareContour(2, x1 + 1.35F, y1 + 1.35F, x2 - 1.35F,
			y2 - 1.35F, Math.max(0, safeRadius - 1.35F), segments);

		RenderState state = begin(graphics);
		RenderSystem.setShader(GameRenderer::getPositionColorShader);
		BufferBuilder buffer = Tesselator.getInstance().getBuilder();
		buffer.begin(VertexFormat.Mode.TRIANGLES,
			DefaultVertexFormat.POSITION_COLOR);
		Matrix4f pose = graphics.pose().last().pose();
		addGradientColorStrip(buffer, pose, 0, 1, segments, colorFn, false);
		addGradientColorStrip(buffer, pose, 1, 2, segments, colorFn, true);
		Tesselator.getInstance().end();
		state.restore();
	}

	private static RenderState begin(GuiGraphics graphics)
	{
		graphics.flush();
		RenderState state = new RenderState(GL11.glIsEnabled(GL11.GL_BLEND),
			GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
			GL11.glIsEnabled(GL11.GL_CULL_FACE));
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableDepthTest();
		RenderSystem.disableCull();
		return state;
	}

	private static void addSolidFan(BufferBuilder buffer, Matrix4f pose,
		int contour, int segments, int color)
	{
		int points = segments * 4;
		float centerX = 0;
		float centerY = 0;
		for(int i = 0; i < points; i++)
		{
			centerX += POINT_X[contour][i];
			centerY += POINT_Y[contour][i];
		}
		centerX /= points;
		centerY /= points;

		for(int i = 0; i < points; i++)
		{
			int next = (i + 1) % points;
			addColorVertex(buffer, pose, centerX, centerY, color);
			addColorVertex(buffer, pose, POINT_X[contour][i],
				POINT_Y[contour][i], color);
			addColorVertex(buffer, pose, POINT_X[contour][next],
				POINT_Y[contour][next], color);
		}
	}

	private static void addColorStrip(BufferBuilder buffer, Matrix4f pose,
		int inner, int outer, int segments, int innerColor, int outerColor)
	{
		int points = segments * 4;
		for(int i = 0; i < points; i++)
		{
			int next = (i + 1) % points;
			addColorVertex(buffer, pose, POINT_X[inner][i], POINT_Y[inner][i],
				innerColor);
			addColorVertex(buffer, pose, POINT_X[outer][i], POINT_Y[outer][i],
				outerColor);
			addColorVertex(buffer, pose, POINT_X[outer][next],
				POINT_Y[outer][next], outerColor);
			addColorVertex(buffer, pose, POINT_X[inner][i], POINT_Y[inner][i],
				innerColor);
			addColorVertex(buffer, pose, POINT_X[outer][next],
				POINT_Y[outer][next], outerColor);
			addColorVertex(buffer, pose, POINT_X[inner][next],
				POINT_Y[inner][next], innerColor);
		}
	}

	private static void addGradientColorStrip(BufferBuilder buffer,
		Matrix4f pose, int inner, int outer, int segments,
		FlatRenderer.GradientColorFn colorFn, boolean innerIsColor)
	{
		int points = segments * 4;
		for(int i = 0; i < points; i++)
		{
			int next = (i + 1) % points;
			int ic = colorFor(colorFn, POINT_X[inner][i], innerIsColor);
			int oc = colorFor(colorFn, POINT_X[outer][i], !innerIsColor);
			int inc = colorFor(colorFn, POINT_X[inner][next], innerIsColor);
			int onc = colorFor(colorFn, POINT_X[outer][next], !innerIsColor);
			addColorVertex(buffer, pose, POINT_X[inner][i], POINT_Y[inner][i],
				ic);
			addColorVertex(buffer, pose, POINT_X[outer][i], POINT_Y[outer][i],
				oc);
			addColorVertex(buffer, pose, POINT_X[outer][next],
				POINT_Y[outer][next], onc);
			addColorVertex(buffer, pose, POINT_X[inner][i], POINT_Y[inner][i],
				ic);
			addColorVertex(buffer, pose, POINT_X[outer][next],
				POINT_Y[outer][next], onc);
			addColorVertex(buffer, pose, POINT_X[inner][next],
				POINT_Y[inner][next], inc);
		}
	}

	private static int colorFor(FlatRenderer.GradientColorFn colorFn, float x,
		boolean isColor)
	{
		int color = colorFn.colorAt(x);
		return isColor ? color : color & 0xFFFFFF;
	}

	private static void addColorVertex(BufferBuilder buffer, Matrix4f pose,
		float x, float y, int color)
	{
		buffer.vertex(pose, x, y, 0).color(color).endVertex();
	}

	private static void prepareContour(int contour, float x1, float y1,
		float x2, float y2, float radius, int segments)
	{
		float safeX2 = Math.max(x1, x2);
		float safeY2 = Math.max(y1, y2);
		float safeRadius = clampRadius(x1, y1, safeX2, safeY2, radius);
		float[] centersX = {safeX2 - safeRadius, safeX2 - safeRadius,
			x1 + safeRadius, x1 + safeRadius};
		float[] centersY = {y1 + safeRadius, safeY2 - safeRadius,
			safeY2 - safeRadius, y1 + safeRadius};
		float[] starts = {-90, 0, 90, 180};
		int index = 0;
		for(int corner = 0; corner < 4; corner++)
			for(int step = 0; step < segments; step++)
			{
				double angle = Math.toRadians(starts[corner]
					+ step * 90F / segments);
				POINT_X[contour][index] =
					centersX[corner] + (float)Math.cos(angle) * safeRadius;
				POINT_Y[contour][index] =
					centersY[corner] + (float)Math.sin(angle) * safeRadius;
				index++;
			}
	}

	private static int segmentsFor(float radius)
	{
		return Math.max(8,
			Math.min(MAX_SEGMENTS, (int)Math.ceil(radius * 2)));
	}

	private static float clampRadius(float x1, float y1, float x2, float y2,
		float radius)
	{
		return Math.max(0,
			Math.min(radius, Math.min((x2 - x1) / 2, (y2 - y1) / 2)));
	}

	private record RenderState(boolean blend, boolean depth, boolean cull)
	{
		private void restore()
		{
			if(blend)
				RenderSystem.enableBlend();
			else
				RenderSystem.disableBlend();
			if(depth)
				RenderSystem.enableDepthTest();
			else
				RenderSystem.disableDepthTest();
			if(cull)
				RenderSystem.enableCull();
			else
				RenderSystem.disableCull();
		}
	}
}
