package net.wurstclient.util.render;

import java.nio.IntBuffer;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;

/**
 * Captures and restores the raw OpenGL state around a block of custom
 * rendering.
 *
 * <p>
 * 1.21.5 removed the global render state methods from {@code RenderSystem}
 * (blend, depth test, cull, viewport) because pipelines now own that state, and
 * it removed {@code ShaderInstance} entirely. The raw {@link GlStateManager}
 * level still exists, so that is what gets saved here.
 */
public final class RenderScope implements AutoCloseable
{
	private final boolean blend;
	private final boolean depthTest;
	private final boolean cull;
	private final boolean depthMask;
	private final int depthFunc;
	private final int blendSrcRgb;
	private final int blendDstRgb;
	private final int blendSrcAlpha;
	private final int blendDstAlpha;
	private final float lineWidth;
	private final float[] shaderColor;
	private final int drawFramebuffer;
	private final int readFramebuffer;
	private final int viewportX;
	private final int viewportY;
	private final int viewportWidth;
	private final int viewportHeight;
	private boolean closed;

	private RenderScope()
	{
		RenderSystem.assertOnRenderThread();
		blend = GL11.glIsEnabled(GL11.GL_BLEND);
		depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
		cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
		depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
		depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
		blendSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
		blendDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
		blendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
		blendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
		lineWidth = GL11.glGetFloat(GL11.GL_LINE_WIDTH);
		shaderColor = RenderSystem.getShaderColor().clone();
		drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
		readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);

		try(MemoryStack stack = MemoryStack.stackPush())
		{
			IntBuffer viewport = stack.mallocInt(4);
			GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
			viewportX = viewport.get(0);
			viewportY = viewport.get(1);
			viewportWidth = viewport.get(2);
			viewportHeight = viewport.get(3);
		}

	}

	public static RenderScope capture()
	{
		return new RenderScope();
	}

	@Override
	public void close()
	{
		if(closed)
			return;
		closed = true;

		GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,
			drawFramebuffer);
		GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,
			readFramebuffer);
		GlStateManager._viewport(viewportX, viewportY, viewportWidth,
			viewportHeight);

		if(blend)
			GlStateManager._enableBlend();
		else
			GlStateManager._disableBlend();
		GlStateManager._blendFuncSeparate(blendSrcRgb, blendDstRgb,
			blendSrcAlpha, blendDstAlpha);

		if(depthTest)
			GlStateManager._enableDepthTest();
		else
			GlStateManager._disableDepthTest();
		GlStateManager._depthMask(depthMask);
		GlStateManager._depthFunc(depthFunc);

		if(cull)
			GlStateManager._enableCull();
		else
			GlStateManager._disableCull();
		GL11.glLineWidth(lineWidth);
		RenderSystem.setShaderColor(shaderColor[0], shaderColor[1],
			shaderColor[2], shaderColor[3]);
	}
}
