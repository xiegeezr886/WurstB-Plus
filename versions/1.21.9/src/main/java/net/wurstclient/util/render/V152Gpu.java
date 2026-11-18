package net.wurstclient.util.render;

import com.mojang.blaze3d.opengl.GlDevice;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.wurstclient.hud2.nanovg.GlStateGuard;
import org.joml.Matrix4f;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryStack;

/** OpenGL bridge for immediate NanoVG-adjacent meshes on the 1.21.5 GPU API.
 * Minecraft still owns every RenderTarget texture and its associated FBO.
 * Mesh attributes determine the color/textured shader variant, replacing the
 * removed global CoreShaders selection. All temporary GL state is restored. */
public final class V152Gpu {
  private static int program, vao, vbo, ebo;
  private static RenderTarget override;
  private static Matrix4f immediateProjection;
  private static boolean combineModelView;
  private static float[] tint = {1, 1, 1, 1};
  public record Projection(Matrix4f matrix, boolean combine) {}
  public static Projection projection() { return new Projection(immediateProjection, combineModelView); }
  public static void restoreProjection(Projection previous) {
    immediateProjection = previous.matrix(); combineModelView = previous.combine();
  }
  public static void setProjection(Matrix4f matrix, boolean combine) {
    immediateProjection = new Matrix4f(matrix); combineModelView = combine;
  }
  public static float[] getShaderColor() { return tint; }
  public static void setShaderColor(float r, float g, float b, float a) { tint = new float[]{r,g,b,a}; }
  private static Matrix4f transform() {
    var window = Minecraft.getInstance().getWindow();
    Matrix4f matrix = immediateProjection == null
        ? new Matrix4f().setOrtho(0, window.getGuiScaledWidth(), window.getGuiScaledHeight(), 0, -1, 1)
        : new Matrix4f(immediateProjection);
    return combineModelView ? matrix.mul(RenderSystem.getModelViewMatrix()) : matrix;
  }
  private V152Gpu() {}

  public static RenderTarget targetOverride() { return override; }
  public static AutoCloseable target(RenderTarget target) {
    RenderTarget previous = override;
    override = target;
    return () -> override = previous;
  }
  public static int texture(RenderTarget target) {
    return ((GlTexture)target.getColorTexture()).glId();
  }
  public static int fbo(RenderTarget target) {
    return ((GlTexture)target.getColorTexture()).getFbo(
        ((GlDevice)RenderSystem.getDevice()).directStateAccess(), target.getDepthTexture());
  }
  public static void bind(RenderTarget target, boolean viewport) {
    GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo(target));
    if (viewport) GL11.glViewport(0, 0, target.width, target.height);
  }
  public static void clear(RenderTarget target) {
    var encoder = RenderSystem.getDevice().createCommandEncoder();
    if (target.useDepth)
      encoder.clearColorAndDepthTextures(target.getColorTexture(), 0, target.getDepthTexture(), 1.0);
    else encoder.clearColorTexture(target.getColorTexture(), 0);
  }
  /** Read into a tightly packed CPU buffer, independent of Minecraft's PBO
   * and screenshot/readback pixel-store state. A stale row length or skip
   * value would otherwise write beyond the allocated icon buffer. */
  public static void readRgba(RenderTarget target, int width, int height, java.nio.ByteBuffer pixels) {
    if (!pixels.isDirect() || pixels.remaining() < Math.multiplyExact(Math.multiplyExact(width, height), 4))
      throw new IllegalArgumentException("Insufficient direct RGBA buffer");
    int alignment = GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
    int rowLength = GL11.glGetInteger(GL11.GL_PACK_ROW_LENGTH);
    int skipPixels = GL11.glGetInteger(GL11.GL_PACK_SKIP_PIXELS);
    int skipRows = GL11.glGetInteger(GL11.GL_PACK_SKIP_ROWS);
    int buffer = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
    try (var ignored = GlStateGuard.capture()) {
      bind(target, false);
      GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
      GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
      GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
      GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, 0);
      GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, 0);
      GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, 0);
      GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
    } finally {
      GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, alignment);
      GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, rowLength);
      GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, skipPixels);
      GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, skipRows);
      GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, buffer);
    }
  }
  public static void bindTexture(int unit, int id) {
    com.mojang.blaze3d.opengl.GlStateManager._activeTexture(GL13.GL_TEXTURE0 + unit);
    com.mojang.blaze3d.opengl.GlStateManager._bindTexture(id);
  }
  public static void bindTexture(int unit, ResourceLocation id) {
    bindTexture(unit, ((GlTexture)Minecraft.getInstance().getTextureManager().getTexture(id).getTexture()).glId());
  }

  public static void draw(MeshData mesh) {
    if (mesh == null) return;
    try (mesh; var ignored = GlStateGuard.capture(); var memory = MemoryStack.stackPush()) {
      ensure();
      GL30.glBindVertexArray(vao);
      GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
      GL15.glBufferData(GL15.GL_ARRAY_BUFFER, mesh.vertexBuffer(), GL15.GL_STREAM_DRAW);
      var state = mesh.drawState();
      var format = state.format();
      attribute(format, VertexFormatElement.POSITION, 0, 3, GL11.GL_FLOAT, false);
      attribute(format, VertexFormatElement.UV0, 1, 2, GL11.GL_FLOAT, false);
      attribute(format, VertexFormatElement.COLOR, 2, 4, GL11.GL_UNSIGNED_BYTE, true);
      if (!format.contains(VertexFormatElement.COLOR)) GL20.glVertexAttrib4f(2, 1, 1, 1, 1);
      GL20.glUseProgram(program);
      GL20.glUniformMatrix4fv(GL20.glGetUniformLocation(program, "Transform"), false,
          transform().get(memory.mallocFloat(16)));
      GL20.glUniform4fv(GL20.glGetUniformLocation(program, "Tint"), getShaderColor());
      GL20.glUniform1i(GL20.glGetUniformLocation(program, "Textured"), format.contains(VertexFormatElement.UV0) ? 1 : 0);
      GL20.glUniform1i(GL20.glGetUniformLocation(program, "Image"), 0);
      GL33.glBindSampler(0, 0);
      if (state.mode() == VertexFormat.Mode.QUADS) {
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo);
        if (mesh.indexBuffer() != null) {
          GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, mesh.indexBuffer(), GL15.GL_STREAM_DRAW);
          GL11.glDrawElements(GL11.GL_TRIANGLES, state.indexCount(),
              state.indexType() == VertexFormat.IndexType.SHORT ? GL11.GL_UNSIGNED_SHORT : GL11.GL_UNSIGNED_INT, 0);
        } else {
          var indices = memory.mallocInt(state.indexCount());
          for (int i = 0; i < state.vertexCount(); i += 4)
            indices.put(i).put(i + 1).put(i + 2).put(i + 2).put(i + 3).put(i);
          indices.flip();
          GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, indices, GL15.GL_STREAM_DRAW);
          GL11.glDrawElements(GL11.GL_TRIANGLES, state.indexCount(), GL11.GL_UNSIGNED_INT, 0);
        }
      } else {
        int mode = switch (state.mode()) {
          case LINES, DEBUG_LINES -> GL11.GL_LINES;
          case LINE_STRIP, DEBUG_LINE_STRIP -> GL11.GL_LINE_STRIP;
          case TRIANGLE_STRIP -> GL11.GL_TRIANGLE_STRIP;
          case TRIANGLE_FAN -> GL11.GL_TRIANGLE_FAN;
          default -> GL11.GL_TRIANGLES;
        };
        GL11.glDrawArrays(mode, 0, state.vertexCount());
      }
    }
  }
  private static void attribute(VertexFormat format, VertexFormatElement element,
      int index, int size, int type, boolean normalized) {
    if (!format.contains(element)) { GL20.glDisableVertexAttribArray(index); return; }
    GL20.glEnableVertexAttribArray(index);
    GL20.glVertexAttribPointer(index, size, type, normalized, format.getVertexSize(), format.getOffset(element));
  }
  private static void ensure() {
    if (program != 0) return;
    int vertex = compile(GL20.GL_VERTEX_SHADER, """
        #version 150
        in vec3 Position; in vec2 UV; in vec4 Color;
        uniform mat4 Transform; out vec2 texCoord; out vec4 vertexColor;
        void main() { gl_Position=Transform*vec4(Position,1.0); texCoord=UV; vertexColor=Color; }
        """);
    int fragment = compile(GL20.GL_FRAGMENT_SHADER, """
        #version 150
        uniform sampler2D Image; uniform int Textured; uniform vec4 Tint;
        in vec2 texCoord; in vec4 vertexColor; out vec4 fragColor;
        void main() { fragColor=Tint*vertexColor*(Textured==1?texture(Image,texCoord):vec4(1.0)); }
        """);
    int candidate = GL20.glCreateProgram();
    try {
      GL20.glAttachShader(candidate, vertex); GL20.glAttachShader(candidate, fragment);
      GL20.glBindAttribLocation(candidate, 0, "Position");
      GL20.glBindAttribLocation(candidate, 1, "UV");
      GL20.glBindAttribLocation(candidate, 2, "Color");
      GL20.glLinkProgram(candidate);
      if (GL20.glGetProgrami(candidate, GL20.GL_LINK_STATUS) == 0)
        throw new IllegalStateException(GL20.glGetProgramInfoLog(candidate));
      program = candidate;
    } finally {
      GL20.glDeleteShader(vertex); GL20.glDeleteShader(fragment);
      if (program == 0) GL20.glDeleteProgram(candidate);
    }
    vao=GL30.glGenVertexArrays(); vbo=GL15.glGenBuffers(); ebo=GL15.glGenBuffers();
  }
  private static int compile(int type, String source) {
    int shader=GL20.glCreateShader(type);
    GL20.glShaderSource(shader, source); GL20.glCompileShader(shader);
    if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) {
      String error=GL20.glGetShaderInfoLog(shader); GL20.glDeleteShader(shader);
      throw new IllegalStateException(error);
    }
    return shader;
  }
  public static void close() {
    if (program != 0) GL20.glDeleteProgram(program);
    if (vao != 0) GL30.glDeleteVertexArrays(vao);
    if (vbo != 0) GL15.glDeleteBuffers(vbo);
    if (ebo != 0) GL15.glDeleteBuffers(ebo);
    program=vao=vbo=ebo=0;
  }
}
