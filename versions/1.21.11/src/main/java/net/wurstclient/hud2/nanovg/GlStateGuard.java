/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
package net.wurstclient.hud2.nanovg;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.FloatBuffer;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;
import org.lwjgl.system.MemoryStack;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;

/**
 * Captures and restores every piece of OpenGL state that NanoVG can touch.
 *
 * <p>The existing {@code RenderScope} covers blend/depth/cull and the
 * framebuffer, but NanoVG also changes the program, VAO, array buffer,
 * texture unit and stencil state. Restoring all of them keeps Minecraft's
 * {@code GlStateManager} cache valid without extra resynchronisation.
 */
public final class GlStateGuard implements AutoCloseable
{
	private final int drawFramebuffer;
	private final int readFramebuffer;
	private final int viewportX;
	private final int viewportY;
	private final int viewportWidth;
	private final int viewportHeight;

	private final boolean blend;
	private final int blendSrcRgb;
	private final int blendDstRgb;
	private final int blendSrcAlpha;
	private final int blendDstAlpha;
	private final int blendEquationRgb;
	private final int blendEquationAlpha;

	private final boolean depthTest;
	private final boolean depthMask;
	private final int depthFunc;

	private final boolean cull;
	private final int cullFace;

	private final boolean scissor;
	private final int scissorX;
	private final int scissorY;
	private final int scissorWidth;
	private final int scissorHeight;

	private final boolean stencil;
	private final int stencilFunc;
	private final int stencilRef;
	private final int stencilValueMask;
	private final int stencilWriteMask;
	private final int stencilFail;
	private final int stencilDepthFail;
	private final int stencilDepthPass;
	private final int stencilBackFunc;
	private final int stencilBackRef;
	private final int stencilBackValueMask;
	private final int stencilBackWriteMask;
	private final int stencilBackFail;
	private final int stencilBackDepthFail;
	private final int stencilBackDepthPass;
	private final int clearStencil;

	private final int program;
	private final int vertexArray;
	private final int arrayBuffer;
	private final int elementArrayBuffer;
	private final int activeTexture;
	private final int texture0;
	private final int sampler0;
	private final int activeTextureBinding;
	private final int unpackAlignment;
	private final int unpackRowLength;
	private final int unpackSkipPixels;
	private final int unpackSkipRows;
	private final int unpackBuffer;
	private final float clearR;
	private final float clearG;
	private final float clearB;
	private final float clearA;
	private final boolean srgb;

	private final boolean colorMaskR;
	private final boolean colorMaskG;
	private final boolean colorMaskB;
	private final boolean colorMaskA;

	private boolean closed;

	private GlStateGuard()
	{
		RenderSystem.assertOnRenderThread();

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

			IntBuffer box = stack.mallocInt(4);
			GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, box);
			scissorX = box.get(0);
			scissorY = box.get(1);
			scissorWidth = box.get(2);
			scissorHeight = box.get(3);

			ByteBuffer mask = stack.malloc(4);
			GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, mask);
			colorMaskR = mask.get(0) != 0;
			colorMaskG = mask.get(1) != 0;
			colorMaskB = mask.get(2) != 0;
			colorMaskA = mask.get(3) != 0;

			FloatBuffer clear = stack.mallocFloat(4);
			GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, clear);
			clearR = clear.get(0);
			clearG = clear.get(1);
			clearB = clear.get(2);
			clearA = clear.get(3);
		}

		blend = GL11.glIsEnabled(GL11.GL_BLEND);
		blendSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
		blendDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
		blendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
		blendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
		blendEquationRgb = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB);
		blendEquationAlpha = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);

		depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
		depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
		depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);

		cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
		cullFace = GL11.glGetInteger(GL11.GL_CULL_FACE_MODE);

		scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);

		stencil = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
		stencilFunc = GL11.glGetInteger(GL11.GL_STENCIL_FUNC);
		stencilRef = GL11.glGetInteger(GL11.GL_STENCIL_REF);
		stencilValueMask = GL11.glGetInteger(GL11.GL_STENCIL_VALUE_MASK);
		stencilWriteMask = GL11.glGetInteger(GL11.GL_STENCIL_WRITEMASK);
		stencilFail = GL11.glGetInteger(GL11.GL_STENCIL_FAIL);
		stencilDepthFail =
			GL11.glGetInteger(GL11.GL_STENCIL_PASS_DEPTH_FAIL);
		stencilDepthPass =
			GL11.glGetInteger(GL11.GL_STENCIL_PASS_DEPTH_PASS);
		stencilBackFunc = GL11.glGetInteger(GL20.GL_STENCIL_BACK_FUNC);
		stencilBackRef = GL11.glGetInteger(GL20.GL_STENCIL_BACK_REF);
		stencilBackValueMask = GL11.glGetInteger(GL20.GL_STENCIL_BACK_VALUE_MASK);
		stencilBackWriteMask = GL11.glGetInteger(GL20.GL_STENCIL_BACK_WRITEMASK);
		stencilBackFail = GL11.glGetInteger(GL20.GL_STENCIL_BACK_FAIL);
		stencilBackDepthFail = GL11.glGetInteger(GL20.GL_STENCIL_BACK_PASS_DEPTH_FAIL);
		stencilBackDepthPass = GL11.glGetInteger(GL20.GL_STENCIL_BACK_PASS_DEPTH_PASS);
		clearStencil = GL11.glGetInteger(GL11.GL_STENCIL_CLEAR_VALUE);

		program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
		vertexArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
		arrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
		elementArrayBuffer =
			GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING);
		activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
		GL13.glActiveTexture(GL13.GL_TEXTURE0);
		texture0 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
		sampler0 = GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
		// NanoVG binds its own texture on whatever unit happens to be active.
		// Restoring only unit 0 leaves the active unit pointing at NanoVG's
		// image, which makes later draws sample black/garbage on some drivers.
		GL13.glActiveTexture(activeTexture);
		activeTextureBinding =
			GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
		srgb = GL11.glIsEnabled(GL30.GL_FRAMEBUFFER_SRGB);
		unpackAlignment = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
		unpackRowLength = GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH);
		unpackSkipPixels = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_PIXELS);
		unpackSkipRows = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_ROWS);
		unpackBuffer = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
	}

	public static GlStateGuard capture()
	{
		return new GlStateGuard();
	}

	@Override
	public void close()
	{
		if(closed)
			return;
		closed = true;

		GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
		GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
		GL11.glViewport(viewportX, viewportY, viewportWidth, viewportHeight);

		GL20.glUseProgram(program);
		GL30.glBindVertexArray(vertexArray);
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, arrayBuffer);
		GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, elementArrayBuffer);
		// Raw binds restore native changes; cached binds also synchronize
		// Minecraft after GPU texture allocation updated GlStateManager.
		GL13.glActiveTexture(GL13.GL_TEXTURE0);
		GlStateManager._activeTexture(GL13.GL_TEXTURE0);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture0);
		GlStateManager._bindTexture(texture0);
		GL33.glBindSampler(0, sampler0);
		GL13.glActiveTexture(activeTexture);
		GlStateManager._activeTexture(activeTexture);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, activeTextureBinding);
		GlStateManager._bindTexture(activeTextureBinding);
		GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, unpackBuffer);
		GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, unpackAlignment);
		GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, unpackRowLength);
		GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, unpackSkipPixels);
		GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, unpackSkipRows);
		GL11.glClearColor(clearR, clearG, clearB, clearA);
		GL11.glClearStencil(clearStencil);

		if(srgb)
			GL11.glEnable(GL30.GL_FRAMEBUFFER_SRGB);
		else
			GL11.glDisable(GL30.GL_FRAMEBUFFER_SRGB);

		if(blend)
			GL11.glEnable(GL11.GL_BLEND);
		else
			GL11.glDisable(GL11.GL_BLEND);
		GL14.glBlendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha,
			blendDstAlpha);
		GL20.glBlendEquationSeparate(blendEquationRgb, blendEquationAlpha);

		if(depthTest)
			GL11.glEnable(GL11.GL_DEPTH_TEST);
		else
			GL11.glDisable(GL11.GL_DEPTH_TEST);
		GL11.glDepthMask(depthMask);
		GL11.glDepthFunc(depthFunc);

		if(cull)
			GL11.glEnable(GL11.GL_CULL_FACE);
		else
			GL11.glDisable(GL11.GL_CULL_FACE);
		GL11.glCullFace(cullFace);

		if(stencil)
			GL11.glEnable(GL11.GL_STENCIL_TEST);
		else
			GL11.glDisable(GL11.GL_STENCIL_TEST);
		GL20.glStencilFuncSeparate(GL11.GL_FRONT, stencilFunc, stencilRef,
			stencilValueMask);
		GL20.glStencilMaskSeparate(GL11.GL_FRONT, stencilWriteMask);
		GL20.glStencilOpSeparate(GL11.GL_FRONT, stencilFail, stencilDepthFail,
			stencilDepthPass);
		GL20.glStencilFuncSeparate(GL11.GL_BACK, stencilBackFunc, stencilBackRef,
			stencilBackValueMask);
		GL20.glStencilMaskSeparate(GL11.GL_BACK, stencilBackWriteMask);
		GL20.glStencilOpSeparate(GL11.GL_BACK, stencilBackFail,
			stencilBackDepthFail, stencilBackDepthPass);

		GL11.glColorMask(colorMaskR, colorMaskG, colorMaskB, colorMaskA);

		GL11.glScissor(scissorX, scissorY, scissorWidth, scissorHeight);
		if(scissor)
			GL11.glEnable(GL11.GL_SCISSOR_TEST);
		else
			GL11.glDisable(GL11.GL_SCISSOR_TEST);
	}
}
