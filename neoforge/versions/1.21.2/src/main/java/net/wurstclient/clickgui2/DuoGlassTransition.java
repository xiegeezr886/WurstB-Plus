package net.wurstclient.clickgui2;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import net.minecraft.client.gui.GuiGraphics;
import net.wurstclient.WurstClient;
import net.wurstclient.hud2.nanovg.GlStateGuard;
import net.wurstclient.hud2.nanovg.NanoVGUI;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;

/** GPU-only transparent GUI adaptation of WindowsDuo's bottom-hinge glass. */
final class DuoGlassTransition implements AutoCloseable
{
	private static final DuoGlassTransition SHARED = new DuoGlassTransition();
	static DuoGlassTransition shared() { return SHARED; }
	private static final String VERTEX = """
		#version 150
		out vec2 uv;
		void main() {
			vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
			uv = p;
			gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
		}
		""";
	private RenderTarget layer, output;
	private int program, vertices;
	private int textureUniform, rectUniform, resolutionUniform, foldUniform;
	private boolean failed;
	private boolean captured;
	private float capturedX, capturedY, capturedWidth, capturedHeight;

	void render(GuiGraphics graphics, float fold, float x, float y, float width, float height,
		Consumer<RenderTarget> content)
	{
		var mc = WurstClient.MC;
		var main = mc.getMainRenderTarget();
		if(fold == 0 || failed || NanoVGUI.context().isGles())
		{
			captured = false;
			content.accept(main);
			return;
		}
		graphics.flush();
		boolean fallback = false;
		try(GlStateGuard ignored = GlStateGuard.capture())
		{
			ensure(main.width, main.height);
			if(!captured || x != capturedX || y != capturedY
				|| width != capturedWidth || height != capturedHeight)
			{
				layer.clear();
				layer.bindWrite(true);
				content.accept(layer);
				graphics.flush();
				GL13.glActiveTexture(GL13.GL_TEXTURE0);
				GL11.glBindTexture(GL11.GL_TEXTURE_2D, layer.getColorTextureId());
				GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
				captured = true;
				capturedX = x; capturedY = y; capturedWidth = width; capturedHeight = height;
			}
			GL13.glActiveTexture(GL13.GL_TEXTURE0);
			GL33.glBindSampler(0, 0);
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, layer.getColorTextureId());
			GL11.glDisable(GL11.GL_DEPTH_TEST);
			GL11.glDepthMask(false);
			GL11.glDisable(GL11.GL_CULL_FACE);
			GL11.glDisable(GL11.GL_SCISSOR_TEST);
			GL11.glDisable(GL11.GL_STENCIL_TEST);
			GL11.glDisable(GL30.GL_FRAMEBUFFER_SRGB);
			GL11.glColorMask(true, true, true, true);
			GL11.glEnable(GL11.GL_BLEND);
			GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD, GL14.GL_FUNC_ADD);
			GL14.glBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA,
				GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
			output.clear();
			output.bindWrite(true);
			GL20.glUseProgram(program);
			GL30.glBindVertexArray(vertices);
			float screenWidth = mc.getWindow().getGuiScaledWidth();
			float screenHeight = mc.getWindow().getGuiScaledHeight();
			GL20.glUniform1i(textureUniform, 0);
			GL20.glUniform4f(rectUniform, x / screenWidth, 1 - (y + height) / screenHeight,
				width / screenWidth, height / screenHeight);
			GL20.glUniform2f(resolutionUniform, width / screenWidth * main.width,
				height / screenHeight * main.height);
			GL20.glUniform1f(foldUniform, fold);
			GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
			// One linear sample at native resolution; all expensive blur work is capped at 540p.
			main.bindWrite(true);
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, output.getColorTextureId());
			GL20.glUniform1f(foldUniform, -1);
			GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
		}catch(IOException | RuntimeException e)
		{
			failed = true;
			fallback = true;
			System.err.println("[WindowsDuo] Glass transition unavailable: " + e);
		}finally
		{
			main.bindWrite(true);
		}
		if(fallback)
		{
			dispose();
			content.accept(main);
		}
	}

	private void ensure(int width, int height) throws IOException
	{
		if(program == 0)
		{
			String fragment;
			try(var stream = DuoGlassTransition.class.getResourceAsStream(
				"/assets/wurstpenguin/shaders/duo_glass.frag"))
			{
				if(stream == null) throw new IOException("Missing duo_glass.frag");
				fragment = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
			}
			int vertex = compile(GL20.GL_VERTEX_SHADER, VERTEX), pixel = 0;
			try
			{
				pixel = compile(GL20.GL_FRAGMENT_SHADER, fragment);
				program = GL20.glCreateProgram();
				GL20.glAttachShader(program, vertex);
				GL20.glAttachShader(program, pixel);
				GL20.glLinkProgram(program);
				if(GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE)
					throw new IllegalStateException(GL20.glGetProgramInfoLog(program));
			}finally
			{
				GL20.glDeleteShader(vertex);
				if(pixel != 0) GL20.glDeleteShader(pixel);
			}
			vertices = GL30.glGenVertexArrays();
			textureUniform = GL20.glGetUniformLocation(program, "uTex");
			rectUniform = GL20.glGetUniformLocation(program, "uRect");
			resolutionUniform = GL20.glGetUniformLocation(program, "uRes");
			foldUniform = GL20.glGetUniformLocation(program, "uFold");
		}
		float ratio = Math.min(1, Math.min(1280F / width, 720F / height));
		int w = Math.max(1, Math.round(width * ratio)), h = Math.max(1, Math.round(height * ratio));
		if(layer == null) { layer = new TextureTarget(w, h, false); captured = false; }
		else if(layer.width != w || layer.height != h) { layer.resize(w, h); captured = false; }
		float outputRatio = Math.min(1, Math.min(960F / width, 540F / height));
		int ow = Math.max(1, Math.round(width * outputRatio)), oh = Math.max(1, Math.round(height * outputRatio));
		if(output == null) { output = new TextureTarget(ow, oh, false); output.setFilterMode(GL11.GL_LINEAR); }
		else if(output.width != ow || output.height != oh) { output.resize(ow, oh); output.setFilterMode(GL11.GL_LINEAR); }
		output.setClearColor(0, 0, 0, 0);
		layer.setClearColor(0, 0, 0, 0);
		GL13.glActiveTexture(GL13.GL_TEXTURE0);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, layer.getColorTextureId());
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 6);
	}

	private static int compile(int type, String source)
	{
		int shader = GL20.glCreateShader(type);
		GL20.glShaderSource(shader, source);
		GL20.glCompileShader(shader);
		if(GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE)
		{
			String error = GL20.glGetShaderInfoLog(shader);
			GL20.glDeleteShader(shader);
			throw new IllegalStateException(error);
		}
		return shader;
	}

	@Override public void close()
	{
		// A bounded cache shared by both screens avoids shader compilation/allocation on every open.
		captured = false;
	}

	private void dispose()
	{
		if(layer != null) { layer.destroyBuffers(); layer = null; }
		if(output != null) { output.destroyBuffers(); output = null; }
		if(program != 0) { GL20.glDeleteProgram(program); program = 0; }
		if(vertices != 0) { GL30.glDeleteVertexArrays(vertices); vertices = 0; }
	}
}
