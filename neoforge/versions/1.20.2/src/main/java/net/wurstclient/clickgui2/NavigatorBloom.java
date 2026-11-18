package net.wurstclient.clickgui2;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import net.minecraft.client.gui.GuiGraphics;
import net.wurstclient.WurstClient;
import net.wurstclient.hud2.nanovg.GlStateGuard;
import net.wurstclient.hud2.nanovg.NanoVGUI;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;

/** Rise's two-pass Gaussian bloom, with explicit Minecraft GL state restoration. */
final class NavigatorBloom implements AutoCloseable
{
    private static final int RADIUS = 14;
    private static final String VERTEX = """
        #version 150
        out vec2 uv;
        void main() {
            vec2 position = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
            uv = position;
            gl_Position = vec4(position * 2.0 - 1.0, 0.0, 1.0);
        }
        """;
    private RenderTarget input, output;
    private int program, vertices;
    private boolean failed;

    void render(GuiGraphics graphics, Runnable mask)
    {
        if(failed || !NanoVGUI.context().isAvailable() || NanoVGUI.context().isGles()) return;
        graphics.flush();
        try(GlStateGuard ignored = GlStateGuard.capture())
        {
            var main = WurstClient.MC.getMainRenderTarget();
            ensure(main.width, main.height);
            input.clear(false);
            NanoVGUI.passTo(graphics, input, mask);
            output.clear(false);
            int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            GL13.glActiveTexture(GL13.GL_TEXTURE1);
            int texture1 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            int sampler1 = GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
            try
            {
                GL33.glBindSampler(0, 0);
                GL33.glBindSampler(1, 0);
                GL11.glDisable(GL11.GL_DEPTH_TEST);
                GL11.glDepthMask(false);
                GL11.glDisable(GL11.GL_CULL_FACE);
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
                GL11.glDisable(GL11.GL_STENCIL_TEST);
                GL11.glDisable(GL30.GL_FRAMEBUFFER_SRGB);
                GL11.glColorMask(true, true, true, true);
                GL11.glEnable(GL11.GL_BLEND);
                GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD, GL14.GL_FUNC_ADD);
                GL20.glUseProgram(program);
                GL30.glBindVertexArray(vertices);
                GL20.glUniform2f(uniform("u_texel_size"), 1F / main.width, 1F / main.height);
                GL20.glUniform1i(uniform("u_diffuse_sampler"), 0);
                GL20.glUniform1i(uniform("u_other_sampler"), 1);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, input.getColorTextureId());

                output.bindWrite(true);
                GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_SRC_ALPHA);
                pass(input, 2, 0, true);
                main.bindWrite(true);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                pass(output, 0, 2, false);
            }finally
            {
                GL13.glActiveTexture(GL13.GL_TEXTURE1);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture1);
                GL33.glBindSampler(1, sampler1);
                GL13.glActiveTexture(active);
            }
        }catch(RuntimeException | IOException exception)
        {
            failed = true;
            System.err.println("[Navigator] Bloom unavailable: " + exception);
            close();
        }
    }

    private void pass(RenderTarget source, float x, float y, boolean premultiplied)
    {
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, source.getColorTextureId());
        GL20.glUniform2f(uniform("u_direction"), x, y);
        GL20.glUniform1i(uniform("u_premultiplied"), premultiplied ? 1 : 0);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
    }

    private void ensure(int width, int height) throws IOException
    {
        if(program == 0)
        {
            String fragment;
            try(var stream = NavigatorBloom.class.getClassLoader().getResourceAsStream(
                "assets/wurstpenguin/navigator/shader/bloom.frag"))
            {
                if(stream == null) throw new IOException("Missing bloom shader");
                fragment = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
            int vertex = 0, pixel = 0;
            try
            {
                vertex = compile(GL20.GL_VERTEX_SHADER, VERTEX);
                pixel = compile(GL20.GL_FRAGMENT_SHADER, fragment);
                program = GL20.glCreateProgram();
                GL20.glAttachShader(program, vertex);
                GL20.glAttachShader(program, pixel);
                GL20.glLinkProgram(program);
                if(GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE)
                    throw new IllegalStateException(GL20.glGetProgramInfoLog(program));
            }finally
            {
                if(vertex != 0) GL20.glDeleteShader(vertex);
                if(pixel != 0) GL20.glDeleteShader(pixel);
            }
            vertices = GL30.glGenVertexArrays();
            GL20.glUseProgram(program);
            // Rise's GaussianKernel normalizes samples 0..13. Element 14 is zero.
            float[] kernel = new float[24];
            float sigma = RADIUS / 2F, total = 0;
            for(int i = 0; i < RADIUS; i++)
            {
                float ratio = i / sigma;
                kernel[i] = (float)Math.exp(-0.5 * ratio * ratio) / (sigma * 2.5066283F);
                total += kernel[i] * (i == 0 ? 1 : 2);
            }
            for(int i = 0; i < RADIUS; i++) kernel[i] /= total;
            GL20.glUniform1fv(uniform("u_kernel"), kernel);
            GL20.glUniform1f(uniform("u_radius"), RADIUS);
        }
        if(input == null)
        {
            input = new TextureTarget(width, height, false, false);
            output = new TextureTarget(width, height, false, false);
            input.setClearColor(0, 0, 0, 0);
            output.setClearColor(0, 0, 0, 0);
        }else if(input.width != width || input.height != height)
        {
            input.resize(width, height, false);
            output.resize(width, height, false);
        }
    }

    private int uniform(String name) { return GL20.glGetUniformLocation(program, name); }

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
        if(input == null && output == null && program == 0 && vertices == 0) return;
        try(GlStateGuard ignored = GlStateGuard.capture())
        {
            if(input != null) input.destroyBuffers();
            if(output != null) output.destroyBuffers();
            if(program != 0) GL20.glDeleteProgram(program);
            if(vertices != 0) GL30.glDeleteVertexArrays(vertices);
            input = output = null;
            program = vertices = 0;
        }
    }
}
