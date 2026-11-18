/* Moon-style model mask, separable glow and flowing tint. Apache-2.0. */
package net.wurstclient.util.render;

import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import java.util.*;
import net.minecraft.client.renderer.*;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.wurstclient.WurstClient;
import net.wurstclient.hud2.nanovg.GlStateGuard;
import org.lwjgl.opengl.*;

public final class MoonEntityGlow implements AutoCloseable {
  public static final MoonEntityGlow INSTANCE = new MoonEntityGlow();
  private final Set<LivingEntity> queued = Collections.newSetFromMap(new IdentityHashMap<>());
  private final MultiBufferSource.BufferSource buffers =
      MultiBufferSource.immediate(new ByteBufferBuilder(65536));
  private final OutlineBufferSource outlines = new OutlineBufferSource(buffers);
  private TextureTarget mask, blur;
  private boolean capturing, failed;

  public RenderTarget captureTarget() {
    return capturing ? mask : null;
  }

  public void queue(Collection<? extends LivingEntity> entities, boolean throughWalls) {
    if (failed) return;
    for (var entity : entities)
      if (entity.isAlive()
          && !entity.isRemoved()
          && (throughWalls || WurstClient.MC.player.hasLineOfSight(entity))) queued.add(entity);
  }

  public void flush(PoseStack matrices, float partialTicks) {
    if (queued.isEmpty()) return;
    var mc = WurstClient.MC;
    try (GlStateGuard ignored = GlStateGuard.capture();
        RenderScope scope = RenderScope.capture()) {
      if (failed || mc.level == null) return;
      var main = mc.getMainRenderTarget();
      // One bounded mask/blur pair for all enabled entity ESP modules.
      int w = Math.max(1, Math.min(1280, (main.width + 1) / 2)),
          h = Math.max(1, Math.round(main.height * (w / (float) main.width)));
      ensure(w, h);
      com.mojang.blaze3d.opengl.GlStateManager._disableScissorTest();
      net.wurstclient.util.render.V152Gpu.clear(mask);
      net.wurstclient.util.render.V152Gpu.bind(mask, true);
      var camera = mc.gameRenderer.getMainCamera().getPosition();
      var frustum =
          new net.minecraft.client.renderer.culling.Frustum(
              new org.joml.Matrix4f(matrices.last().pose()), net.wurstclient.events.RenderListener.RenderEvent.getLastProjection());
      frustum.prepare(camera.x, camera.y, camera.z);
      var selected =
          queued.stream()
              .filter(e -> e.level() == mc.level)
              .filter(e -> frustum.isVisible(e.getBoundingBox().inflate(.5)))
              .sorted(Comparator.comparingDouble(e -> e.distanceToSqr(camera)))
              .limit(64)
              .toList();
      capturing = true;
      try {
        com.mojang.blaze3d.opengl.GlStateManager._colorMask(true, true, true, true);
        V152Gpu.setShaderColor(1, 1, 1, 1);
        com.mojang.blaze3d.opengl.GlStateManager._disableDepthTest();
        com.mojang.blaze3d.opengl.GlStateManager._disableCull();
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        outlines.setColor(255, 255, 255, 255);
        MultiBufferSource maskOnly =
            type -> type.outline().map(outlines::getBuffer).orElse(DISCARD);
        for (var entity : selected) {
          matrices.pushPose();
          try {
            matrices.translate(
                Mth.lerp(partialTicks, entity.xOld, entity.getX()) - camera.x,
                Mth.lerp(partialTicks, entity.yOld, entity.getY()) - camera.y,
                Mth.lerp(partialTicks, entity.zOld, entity.getZ()) - camera.z);
            mc.getEntityRenderDispatcher().render(entity, 0, 0, 0, partialTicks, matrices, maskOnly, 15728880);
          } finally {
            matrices.popPose();
          }
        }
        outlines.endOutlineBatch();
      } finally {
        capturing = false;
      }
      var shader = MoonGlowProgram.INSTANCE;
      GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, net.wurstclient.util.render.V152Gpu.fbo(blur));
      GL11.glViewport(0, 0, w, h);
      shader.use(1);
      GL11.glDisable(GL11.GL_BLEND);
      GL11.glBindTexture(GL11.GL_TEXTURE_2D, net.wurstclient.util.render.V152Gpu.texture(mask));
      shader.pair("direction", 2F / w, 0);
      shader.draw();
      GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, net.wurstclient.util.render.V152Gpu.fbo(main));
      GL11.glViewport(0, 0, main.width, main.height);
      shader.use(2);
      GL11.glEnable(GL11.GL_BLEND);
      GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD, GL14.GL_FUNC_ADD);
      GL14.glBlendFuncSeparate(
          GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
      GL11.glBindTexture(GL11.GL_TEXTURE_2D, net.wurstclient.util.render.V152Gpu.texture(blur));
      shader.pair("direction", 0, 2F / h);
      GL13.glActiveTexture(GL13.GL_TEXTURE1);
      int oldTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D),
          oldSampler = GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
      try {
        GL33.glBindSampler(1, 0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, net.wurstclient.util.render.V152Gpu.texture(mask));
        shader.draw();
      } finally {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, oldTexture);
        GL33.glBindSampler(1, oldSampler);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
      }
    } catch (Exception e) {
      failed = true;
      System.err.println("[MoonGlow] Entity glow disabled: " + e);
    } finally {
      capturing = false;
      queued.clear();
    }
  }

  private void ensure(int w, int h) {
    if (mask != null && (mask.width != w || mask.height != h)) {
      mask.destroyBuffers();
      blur.destroyBuffers();
      mask = blur = null;
    }
    if (mask == null) {
      mask = new TextureTarget("WurstB v1.5.2", w, h, false);
      blur = new TextureTarget("WurstB v1.5.2", w, h, false);
      mask.setFilterMode(com.mojang.blaze3d.textures.FilterMode.LINEAR);
      blur.setFilterMode(com.mojang.blaze3d.textures.FilterMode.LINEAR);
    }
  }

  public void close() {
    if (mask != null) mask.destroyBuffers();
    if (blur != null) blur.destroyBuffers();
    mask = blur = null;
    queued.clear();
    capturing = false;
    failed = false;
  }

  private static final VertexConsumer DISCARD =
      new VertexConsumer() {
        public VertexConsumer addVertex(float x, float y, float z) {
          return this;
        }

        public VertexConsumer setColor(int r, int g, int b, int a) {
          return this;
        }

        public VertexConsumer setUv(float u, float v) {
          return this;
        }

        public VertexConsumer setUv1(int u, int v) {
          return this;
        }

        public VertexConsumer setUv2(int u, int v) {
          return this;
        }

        public VertexConsumer setNormal(float x, float y, float z) {
          return this;
        }

        public void endVertex() {}

        public void defaultColor(int r, int g, int b, int a) {}

        public void unsetDefaultColor() {}
      };
}
