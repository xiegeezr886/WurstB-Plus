package net.wurstclient.util.render;

import java.nio.IntBuffer;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.renderer.ShaderInstance;

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
	private final ShaderInstance shader;
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
		shader = RenderSystem.getShader();
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
		RenderSystem.viewport(viewportX, viewportY, viewportWidth,
			viewportHeight);

		if(blend)
			RenderSystem.enableBlend();
		else
			RenderSystem.disableBlend();
		GlStateManager._blendFuncSeparate(blendSrcRgb, blendDstRgb,
			blendSrcAlpha, blendDstAlpha);

		if(depthTest)
			RenderSystem.enableDepthTest();
		else
			RenderSystem.disableDepthTest();
		RenderSystem.depthMask(depthMask);
		RenderSystem.depthFunc(depthFunc);

		if(cull)
			RenderSystem.enableCull();
		else
			RenderSystem.disableCull();
		RenderSystem.lineWidth(lineWidth);
		RenderSystem.setShaderColor(shaderColor[0], shaderColor[1],
			shaderColor[2], shaderColor[3]);
		if(shader != null)
			RenderSystem.setShader(() -> shader);
	}
}
