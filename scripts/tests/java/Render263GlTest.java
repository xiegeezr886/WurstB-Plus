import java.nio.ByteBuffer;
import java.util.List;
import java.util.stream.IntStream;

import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;

import com.mojang.renderpearl.backend.opengl.GlTexture;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.wurstclient.WurstClient;
import net.wurstclient.hud2.nanovg.*;

/** Real GL/NanoVG regression checks; Minecraft GPU/GUI boundaries are fixtures. */
public class Render263GlTest
{
	private static int assertions;

	private static void check(boolean condition, String message)
	{
		assertions++;
		if(!condition)
			throw new AssertionError(message);
	}

	private static int pixel(BlitRenderState state)
	{
		GlTexture texture = (GlTexture)state.texture().view().texture;
		try(GlStateGuard ignored = GlStateGuard.capture())
		{
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture.glId());
			ByteBuffer pixels = BufferUtils.createByteBuffer(texture.width * texture.height * 4);
			GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA,
				GL11.GL_UNSIGNED_BYTE, pixels);
			return pixels.get((texture.width * (texture.height / 2) + texture.width / 2) * 4) & 255;
		}
	}

	private static void pass(NanoVGContext ctx, GuiGraphicsExtractor graphics, int marker)
	{
		try(GlStateGuard ignored = GlStateGuard.capture())
		{
			check(ctx.beginFrame(), "begin frame");
			ctx.drawRect(0, 0, ctx.getFramebufferWidth(), ctx.getFramebufferHeight(),
				0xFF000000 | marker << 16);
			ctx.endFrame();
		}
		NanoVgBlitter.queueBlit(graphics, ctx);
	}

	private static void testGuard()
	{
		int sampler = GL33.glGenSamplers();
		int otherSampler = GL33.glGenSamplers();
		int callerTexture0 = GL11.glGenTextures();
		int callerTexture3 = GL11.glGenTextures();
		int allocatedTexture = GL11.glGenTextures();
		GlStateManager._activeTexture(GL13.GL_TEXTURE0);
		GlStateManager._bindTexture(callerTexture0);
		GlStateManager._activeTexture(GL13.GL_TEXTURE3);
		GlStateManager._bindTexture(callerTexture3);
		GL33.glBindSampler(0, sampler);
		GL33.glBindSampler(3, otherSampler);
		GL13.glActiveTexture(GL13.GL_TEXTURE3);
		GL20.glStencilFuncSeparate(GL11.GL_FRONT, GL11.GL_ALWAYS, 2, 255);
		GL20.glStencilFuncSeparate(GL11.GL_BACK, GL11.GL_EQUAL, 3, 127);
		GL20.glStencilMaskSeparate(GL11.GL_FRONT, 15);
		GL20.glStencilMaskSeparate(GL11.GL_BACK, 31);
		GL20.glStencilOpSeparate(GL11.GL_BACK, GL11.GL_REPLACE, GL11.GL_INCR, GL11.GL_DECR);
		GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 8);
		GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 11);
		GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 2);
		GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 3);
		GL11.glClearColor(0.25F, 0.5F, 0.75F, 1);
		GL11.glClearStencil(6);
		try(GlStateGuard guard = GlStateGuard.capture())
		{
			// Real GlDevice allocation changes both the driver and its cache.
			GlStateManager._activeTexture(GL13.GL_TEXTURE0);
			GlStateManager._bindTexture(allocatedTexture);
			GL33.glBindSampler(0, 0);
			GL13.glActiveTexture(GL13.GL_TEXTURE0);
			GL11.glStencilFunc(GL11.GL_NEVER, 4, 0);
			GL11.glStencilMask(0);
			GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
			GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
			GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
			GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
			GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
			GL11.glClearColor(0, 0, 0, 0);
			GL11.glClearStencil(0);
		}
		check(GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING, 0) == sampler, "sampler 0 restored");
		check(GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING, 3) == otherSampler, "sampler 3 preserved");
		check(GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE) == GL13.GL_TEXTURE3, "active texture restored");
		check(GlStateManager.boundTexture(0) == callerTexture0, "Minecraft texture 0 cache restored");
		check(GlStateManager.boundTexture(3) == callerTexture3, "Minecraft texture 3 cache restored");
		GlStateManager._activeTexture(GL13.GL_TEXTURE0);
		GlStateManager._bindTexture(callerTexture0);
		check(GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D) == callerTexture0, "cached bind preserves actual texture 0");
		GlStateManager._activeTexture(GL13.GL_TEXTURE3);
		GlStateManager._bindTexture(callerTexture3);
		check(GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D) == callerTexture3, "cached bind preserves actual texture 3");
		check(GL11.glGetInteger(GL11.GL_STENCIL_FUNC) == GL11.GL_ALWAYS, "front stencil function");
		check(GL11.glGetInteger(GL20.GL_STENCIL_BACK_FUNC) == GL11.GL_EQUAL, "back stencil function");
		check(GL11.glGetInteger(GL20.GL_STENCIL_BACK_REF) == 3, "back stencil ref");
		check(GL11.glGetInteger(GL20.GL_STENCIL_BACK_VALUE_MASK) == 127, "back stencil value mask");
		check(GL11.glGetInteger(GL11.GL_STENCIL_WRITEMASK) == 15, "front stencil write mask");
		check(GL11.glGetInteger(GL20.GL_STENCIL_BACK_WRITEMASK) == 31, "back stencil write mask");
		check(GL11.glGetInteger(GL20.GL_STENCIL_BACK_FAIL) == GL11.GL_REPLACE, "back stencil fail");
		check(GL11.glGetInteger(GL20.GL_STENCIL_BACK_PASS_DEPTH_FAIL) == GL11.GL_INCR, "back stencil depth fail");
		check(GL11.glGetInteger(GL20.GL_STENCIL_BACK_PASS_DEPTH_PASS) == GL11.GL_DECR, "back stencil depth pass");
		check(GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT) == 8, "unpack alignment");
		check(GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH) == 11, "unpack row length");
		check(GL11.glGetInteger(GL11.GL_UNPACK_SKIP_ROWS) == 2, "unpack rows");
		check(GL11.glGetInteger(GL11.GL_UNPACK_SKIP_PIXELS) == 3, "unpack pixels");
		check(GL11.glGetInteger(GL11.GL_STENCIL_CLEAR_VALUE) == 6, "clear stencil");
		var color = BufferUtils.createFloatBuffer(4);
		GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, color);
		check(color.get(0) == 0.25F && color.get(1) == 0.5F && color.get(2) == 0.75F, "clear color");
		GlStateManager._bindTexture(0);
		GlStateManager._activeTexture(GL13.GL_TEXTURE0);
		GlStateManager._bindTexture(0);
		GL11.glDeleteTextures(callerTexture0);
		GL11.glDeleteTextures(callerTexture3);
		GL11.glDeleteTextures(allocatedTexture);
		GL33.glBindSampler(0, 0);
		GL33.glBindSampler(3, 0);
		GL33.glDeleteSamplers(sampler);
		GL33.glDeleteSamplers(otherSampler);
	}

	private static void testContextAndSnapshots()
	{
		int callerDraw = GL30.glGenFramebuffers();
		int callerRead = GL30.glGenFramebuffers();
		GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, callerDraw);
		GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, callerRead);
		int pbo = GL15.glGenBuffers();
		GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, pbo);
		GL15.glBufferData(GL21.GL_PIXEL_UNPACK_BUFFER, 16, GL15.GL_STATIC_DRAW);
		NanoVGContext ctx = NanoVGContext.get();
		check(ctx.create(), "actual NanoVG context created");
		check(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING) == callerDraw, "initial draw FBO restored");
		check(GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING) == callerRead, "initial read FBO restored");
		check(GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING) == pbo, "initial unpack buffer restored");
		check(GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH) == 11, "initial unpack stride restored");
		check(HudFonts.REGULAR.isLoaded() && HudFonts.MEDIUM.isLoaded()
			&& HudFonts.DEMIBOLD.isLoaded() && HudFonts.ICON.isLoaded(), "four bundled fonts loaded");

		try(GlStateGuard ignored = GlStateGuard.capture())
		{
			check(ctx.beginFrame(), "text frame");
			check(GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING) == 0, "font upload has no PBO");
			check(GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH) == 0, "font upload has no stride");
			ctx.drawText("Wurst 26.3 中文", 12, 35, 0xFFFFFFFF, 22, HudFonts.REGULAR);
			ctx.endFrame();
			GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, ctx.getFramebufferFbo());
			ByteBuffer pixels = BufferUtils.createByteBuffer(320 * 160 * 4);
			GL11.glReadPixels(0, 0, 320, 160, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
			int visible = 0;
			for(int i = 3; i < pixels.capacity(); i += 4)
				if(pixels.get(i) != 0)
					visible++;
			check(visible > 100, "actual text produces visible pixels");
			System.out.println("textPixels=" + visible);
		}
		check(GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING) == pbo, "draw unpack buffer restored");
		GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
		GL15.glDeleteBuffers(pbo);
		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);

		GuiRenderState state = new GuiRenderState();
		GuiGraphicsExtractor graphics = new GuiGraphicsExtractor(state);
		for(int i = 1; i <= 12; i++)
			pass(ctx, graphics, i);
		List<Integer> pixels = state.elements.stream().map(Render263GlTest::pixel).toList();
		check(pixels.equals(IntStream.rangeClosed(1, 12).boxed().toList()), "12 snapshots are independent: " + pixels);
		System.out.println("twelvePasses=" + pixels);

		GuiRenderState other = new GuiRenderState();
		pass(ctx, new GuiGraphicsExtractor(other), 77);
		check(pixel(state.elements.get(0)) == 1, "other state does not overwrite pending snapshot");
		Minecraft.window.width = 640;
		pass(ctx, new GuiGraphicsExtractor(other), 88);
		check(pixel(state.elements.get(0)) == 1, "resize preserves pending old snapshot");
		check(pixel(other.elements.get(0)) == 77 && pixel(other.elements.get(1)) == 88, "resize preserves both snapshots");

		state.reset(); // Fixture dispatches the actual production mixin callback.
		other.reset();
		Minecraft.window.width = 320;
		pass(ctx, graphics, 42);
		check(pixel(state.elements.get(0)) == 42, "reused state renders next frame");
		state.reset();
		int before = GlTexture.created;
		pass(ctx, graphics, 43);
		check(GlTexture.created == before, "consumed slot reused without allocation");
		check(pixel(state.elements.get(0)) == 43, "reused slot updated");
		state.reset();
		state.reset();
		pass(ctx, graphics, 44);
		pass(ctx, graphics, 45);
		check(pixel(state.elements.get(0)) == 44, "duplicate reset cannot lease one slot twice");
		NanoVgBlitter.close();
		NanoVgBlitter.close();
		check(GlTexture.created == GlTexture.closed, "all pending and idle snapshot textures closed exactly once");

		ctx.destroy();
		WurstClient.MC = null;
		GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, callerDraw);
		GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, callerRead);
		check(!ctx.create(), "failed framebuffer initialization returns false");
		check(ctx.getHandle() == 0, "failed initialization cleans up native context");
		check(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING) == callerDraw, "failed initialization restores draw target");
		check(GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING) == callerRead, "failed initialization restores read target");
		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
		GL30.glDeleteFramebuffers(callerDraw);
		GL30.glDeleteFramebuffers(callerRead);
	}

	public static void main(String[] args)
	{
		if(!GLFW.glfwInit())
			throw new AssertionError("GLFW unavailable; requires a desktop OpenGL session");
		GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
		long window = GLFW.glfwCreateWindow(16, 16, "render regression", 0, 0);
		check(window != 0, "hidden GL window");
		try
		{
			GLFW.glfwMakeContextCurrent(window);
			GL.createCapabilities();
			System.out.println("GL=" + GL11.glGetString(GL11.GL_VERSION) + " / " + GL11.glGetString(GL11.GL_RENDERER));
			testGuard();
			testContextAndSnapshots();
			check(GL11.glGetError() == GL11.GL_NO_ERROR, "no GL errors");
			System.out.println("PASS: " + assertions + " assertions");
		}finally
		{
			GLFW.glfwDestroyWindow(window);
			GLFW.glfwTerminate();
		}
	}
}
