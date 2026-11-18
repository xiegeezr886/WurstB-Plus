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
      MultiBufferSource.immediate(new BufferBuilder(65536));
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
      int w = Math.max(1, Math.min(1280, main.width / 2)),
          h = Math.max(1, Math.round(main.height * (w / (float) main.width)));
      ensure(w, h);
      RenderSystem.disableScissor();
      mask.clear(false);
      mask.bindWrite(true);
      var camera = mc.gameRenderer.getMainCamera().getPosition();
      var frustum =
          new net.minecraft.client.renderer.culling.Frustum(
              matrices.last().pose(), RenderSystem.getProjectionMatrix());
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
        outlines.setColor(255, 255, 255, 255);
        MultiBufferSource maskOnly =
            type -> type.outline().isPresent() ? outlines.getBuffer(type.outline().get()) : DISCARD;
        for (var entity : selected) {
          matrices.pushPose();
          try {
            matrices.translate(
                Mth.lerp(partialTicks, entity.xOld, entity.getX()) - camera.x,
                Mth.lerp(partialTicks, entity.yOld, entity.getY()) - camera.y,
                Mth.lerp(partialTicks, entity.zOld, entity.getZ()) - camera.z);
            mc.getEntityRenderDispatcher()
                .getRenderer(entity)
                .render(
                    entity,
                    Mth.rotLerp(partialTicks, entity.yRotO, entity.getYRot()),
                    partialTicks,
                    matrices,
                    maskOnly,
                    15728880);
          } finally {
            matrices.popPose();
          }
        }
        outlines.endOutlineBatch();
      } finally {
        capturing = false;
      }
      var shader = MoonGlowProgram.INSTANCE;
      GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, blur.frameBufferId);
      GL11.glViewport(0, 0, w, h);
      shader.use(1);
      GL11.glDisable(GL11.GL_BLEND);
      GL11.glBindTexture(GL11.GL_TEXTURE_2D, mask.getColorTextureId());
      shader.pair("direction", 2F / w, 0);
      shader.draw();
      GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, main.frameBufferId);
      GL11.glViewport(0, 0, main.width, main.height);
      shader.use(2);
      GL11.glEnable(GL11.GL_BLEND);
      GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD, GL14.GL_FUNC_ADD);
      GL14.glBlendFuncSeparate(
          GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
      GL11.glBindTexture(GL11.GL_TEXTURE_2D, blur.getColorTextureId());
      shader.pair("direction", 0, 2F / h);
      GL13.glActiveTexture(GL13.GL_TEXTURE1);
      int oldTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D),
          oldSampler = GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
      try {
        GL33.glBindSampler(1, 0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, mask.getColorTextureId());
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
      mask = new TextureTarget(w, h, false, false);
      blur = new TextureTarget(w, h, false, false);
      mask.setClearColor(0, 0, 0, 0);
      blur.setClearColor(0, 0, 0, 0);
      mask.setFilterMode(GL11.GL_LINEAR);
      blur.setFilterMode(GL11.GL_LINEAR);
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
        public VertexConsumer vertex(double x, double y, double z) {
          return this;
        }

        public VertexConsumer color(int r, int g, int b, int a) {
          return this;
        }

        public VertexConsumer uv(float u, float v) {
          return this;
        }

        public VertexConsumer overlayCoords(int u, int v) {
          return this;
        }

        public VertexConsumer uv2(int u, int v) {
          return this;
        }

        public VertexConsumer normal(float x, float y, float z) {
          return this;
        }

        public void endVertex() {}

        public void defaultColor(int r, int g, int b, int a) {}

        public void unsetDefaultColor() {}
      };
}
