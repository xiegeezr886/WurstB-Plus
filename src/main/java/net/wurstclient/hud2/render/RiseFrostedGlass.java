package net.wurstclient.hud2.render;

import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.wurstclient.clickgui2.FlatRenderer;
import net.wurstclient.gui.visual.VisualTheme;

/** Frame capture and layered glass used by Rise-inspired HUD elements. */
public final class RiseFrostedGlass
{
	private static final int BLUR_RADIUS = 12;
	private static final float BLUR_COMPRESSION = 3F;
	private static final int CORNER_SEGMENTS = 10;
	private static final int[] SAMPLE_INDICES = {-12, -8, -4, 0, 4, 8, 12};
	private static final float[] SAMPLE_WEIGHTS = createSampleWeights();
	private static final float[] CONTOUR_X = createContour(true);
	private static final float[] CONTOUR_Y = createContour(false);
	private static TextureTarget capture;
	private static boolean frameAvailable;

	private RiseFrostedGlass()
	{}

	public static void captureFrame()
	{
		Minecraft minecraft = Minecraft.getInstance();
		RenderTarget main = minecraft.getMainRenderTarget();
		if(main == null || main.width <= 0 || main.height <= 0)
		{
			frameAvailable = false;
			return;
		}

		if(capture == null)
		{
			capture = new TextureTarget(main.width, main.height, false,
				Minecraft.ON_OSX);
			capture.setFilterMode(GL11.GL_LINEAR);
		}else if(capture.width != main.width || capture.height != main.height)
		{
			capture.resize(main.width, main.height, Minecraft.ON_OSX);
			capture.setFilterMode(GL11.GL_LINEAR);
		}

		GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,
			main.frameBufferId);
		GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,
			capture.frameBufferId);
		GlStateManager._glBlitFrameBuffer(0, 0, main.width, main.height, 0, 0,
			capture.width, capture.height, GL11.GL_COLOR_BUFFER_BIT,
			GL11.GL_LINEAR);
		main.bindWrite(true);
		frameAvailable = true;
	}

	public static void draw(GuiGraphics graphics, int left, int top, int right,
		int bottom, int radius, float opacity)
	{
		draw(graphics, left, top, right, bottom, radius, opacity, 0x78000000);
	}

	public static void draw(GuiGraphics graphics, int left, int top, int right,
		int bottom, int radius, float opacity, int backgroundColor)
	{
		if(right <= left || bottom <= top || opacity <= 0)
			return;

		drawSoftShadow(graphics, left, top, right, bottom, radius, opacity);
		if(frameAvailable && capture != null)
			drawBlurredCapture(graphics, left, top, right, bottom, radius,
				opacity);

		int backgroundAlpha = backgroundColor >>> 24;
		int glass = VisualTheme.withAlpha(backgroundColor,
			backgroundAlpha / 255F * opacity);
		FlatRenderer.fillRoundedRect(graphics, left, top, right, bottom, radius,
			glass);
	}

	public static void close()
	{
		if(capture != null)
			capture.destroyBuffers();
		capture = null;
		frameAvailable = false;
	}

	private static void drawBlurredCapture(GuiGraphics graphics, int left,
		int top, int right, int bottom, int radius, float opacity)
	{
		graphics.flush();
		boolean blendEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
		boolean depthEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
		boolean cullEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableDepthTest();
		RenderSystem.disableCull();
		RenderSystem.setShader(GameRenderer::getPositionColorTexShader);
		RenderSystem.setShaderTexture(0, capture.getColorTextureId());

		BufferBuilder buffer = Tesselator.getInstance().getBuilder();
		buffer.begin(VertexFormat.Mode.TRIANGLES,
			DefaultVertexFormat.POSITION_COLOR_TEX);
		Matrix4f pose = graphics.pose().last().pose();
		int safeRadius = Math.max(1,
			Math.min(radius, Math.min((right - left) / 2, (bottom - top) / 2)));
		double guiScale = Math.max(1,
			Minecraft.getInstance().getWindow().getGuiScale());

		for(int sampleY = 0; sampleY < SAMPLE_INDICES.length; sampleY++)
			for(int sampleX = 0; sampleX < SAMPLE_INDICES.length; sampleX++)
			{
				float weight = SAMPLE_WEIGHTS[sampleX]
					* SAMPLE_WEIGHTS[sampleY];
				int alpha = Math.max(1,
					Math.round(255 * opacity * weight * 1.65F));
				float offsetX = SAMPLE_INDICES[sampleX] * BLUR_COMPRESSION
					/ (float)guiScale;
				float offsetY = SAMPLE_INDICES[sampleY] * BLUR_COMPRESSION
					/ (float)guiScale;
				addRoundedSample(buffer, pose, left, top, right, bottom,
					safeRadius, offsetX, offsetY, alpha);
			}

		BufferUploader.drawWithShader(buffer.end());
		if(blendEnabled)
			RenderSystem.enableBlend();
		else
			RenderSystem.disableBlend();
		if(depthEnabled)
			RenderSystem.enableDepthTest();
		else
			RenderSystem.disableDepthTest();
		if(cullEnabled)
			RenderSystem.enableCull();
		else
			RenderSystem.disableCull();
	}

	private static void addRoundedSample(BufferBuilder buffer, Matrix4f pose,
		int left, int top, int right, int bottom, int radius, float offsetX,
		float offsetY, int alpha)
	{
		Minecraft minecraft = Minecraft.getInstance();
		float guiWidth = Math.max(1, minecraft.getWindow().getGuiScaledWidth());
		float guiHeight = Math.max(1,
			minecraft.getWindow().getGuiScaledHeight());
		float uScale = capture.viewWidth / (float)capture.width;
		float vScale = capture.viewHeight / (float)capture.height;
		float centerX = (left + right) / 2F;
		float centerY = (top + bottom) / 2F;
		int points = CORNER_SEGMENTS * 4;
		for(int point = 0; point < points; point++)
		{
			int nextPoint = (point + 1) % points;
			float currentX = contourX(point, left, right, radius);
			float currentY = contourY(point, top, bottom, radius);
			float nextX = contourX(nextPoint, left, right, radius);
			float nextY = contourY(nextPoint, top, bottom, radius);
			addVertex(buffer, pose, centerX, centerY, offsetX, offsetY, alpha,
				guiWidth, guiHeight, uScale, vScale);
			addVertex(buffer, pose, currentX, currentY, offsetX, offsetY,
				alpha, guiWidth, guiHeight, uScale, vScale);
			addVertex(buffer, pose, nextX, nextY, offsetX, offsetY, alpha,
				guiWidth, guiHeight, uScale, vScale);
		}
	}

	private static float contourX(int point, int left, int right, int radius)
	{
		int corner = point / CORNER_SEGMENTS;
		float center = corner < 2 ? right - radius : left + radius;
		return center + CONTOUR_X[point] * radius;
	}

	private static float contourY(int point, int top, int bottom, int radius)
	{
		int corner = point / CORNER_SEGMENTS;
		float center = corner == 0 || corner == 3 ? top + radius
			: bottom - radius;
		return center + CONTOUR_Y[point] * radius;
	}

	private static void addVertex(BufferBuilder buffer, Matrix4f pose, float x,
		float y, float offsetX, float offsetY, int alpha, float guiWidth,
		float guiHeight, float uScale, float vScale)
	{
		float screenX = pose.m00() * x + pose.m10() * y + pose.m30();
		float screenY = pose.m01() * x + pose.m11() * y + pose.m31();
		float u = clamp01((screenX + offsetX) / guiWidth) * uScale;
		float v = (1 - clamp01((screenY + offsetY) / guiHeight)) * vScale;
		buffer.vertex(pose, x, y, 0).color(255, 255, 255, alpha).uv(u, v)
			.endVertex();
	}

	private static void drawSoftShadow(GuiGraphics graphics, int left, int top,
		int right, int bottom, int radius, float opacity)
	{
		for(int spread = 7; spread >= 2; spread--)
		{
			float strength = (8 - spread) / 7F;
			int alpha = Math.round(20 * strength * strength * opacity);
			FlatRenderer.fillRoundedRect(graphics, left - spread,
				top - spread / 2, right + spread, bottom + spread,
				radius + spread, alpha << 24);
		}
	}

	private static float clamp01(float value)
	{
		return Math.max(0, Math.min(1, value));
	}

	private static float[] createSampleWeights()
	{
		float sigma = BLUR_RADIUS / 2F;
		float[] weights = new float[SAMPLE_INDICES.length];
		float sum = 0;
		for(int i = 0; i < SAMPLE_INDICES.length; i++)
		{
			float multiplier = SAMPLE_INDICES[i] / sigma;
			weights[i] = (float)Math.exp(-0.5F * multiplier * multiplier);
			sum += weights[i];
		}
		for(int i = 0; i < weights.length; i++)
			weights[i] /= sum;
		return weights;
	}

	private static float[] createContour(boolean xAxis)
	{
		float[] values = new float[CORNER_SEGMENTS * 4];
		for(int point = 0; point < values.length; point++)
		{
			int corner = point / CORNER_SEGMENTS;
			int step = point % CORNER_SEGMENTS;
			float angle = -90 + corner * 90
				+ step * 90F / CORNER_SEGMENTS;
			values[point] = xAxis ? (float)Math.cos(Math.toRadians(angle))
				: (float)Math.sin(Math.toRadians(angle));
		}
		return values;
	}
}
