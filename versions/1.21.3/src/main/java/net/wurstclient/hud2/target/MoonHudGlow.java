/* Moon flowing glow tint with bounded, local HUD coverage. Apache-2.0. */
package net.wurstclient.hud2.target;

import com.mojang.blaze3d.pipeline.TextureTarget;
import java.util.ArrayList;
import net.wurstclient.hud2.nanovg.*;
import net.wurstclient.util.render.MoonGlowProgram;
import org.lwjgl.nanovg.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryStack;

public final class MoonHudGlow implements AutoCloseable {
  private final ArrayList<Slot> slots = new ArrayList<>();
  private long frame = -1;
  private int cursor;
  private boolean failed;

  public void draw(
      NanoVGContext nvg, float x, float y, float width, float height, float radius, float scale) {
    if (failed || nvg == null || nvg.isGles() || width <= 0 || height <= 0) return;
    if (frame != nvg.getFrameSerial()) {
      frame = nvg.getFrameSerial();
      cursor = 0;
    }
    if (cursor >= 64) return;
    if (cursor == slots.size()) slots.add(new Slot());
    Slot slot = slots.get(cursor++);
    float pad = 12 * scale, w = width + pad * 2, h = height + pad * 2;
    float density = Math.min(2, nvg.getGuiScale());
    int tw = Math.min(1024, Math.max(32, ((int) Math.ceil(w * density) + 31) / 32 * 32));
    int th = Math.min(512, Math.max(32, ((int) Math.ceil(h * density) + 31) / 32 * 32));
    try (GlStateGuard ignored = GlStateGuard.capture()) {
      slot.ensure(nvg, tw, th);
      GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, slot.target.frameBufferId);
      GL11.glViewport(0, 0, tw, th);
      var shader = MoonGlowProgram.INSTANCE;
      shader.use(0);
      GL11.glDisable(GL11.GL_BLEND);
      shader.pair("extent", w, h);
      shader.pair("cardSize", width, height);
      shader.scalar("radius", Math.min(radius, Math.min(width, height) / 2));
      shader.scalar("feather", 4 * scale);
      shader.draw();
    } catch (Exception e) {
      failed = true;
      System.err.println("[MoonGlow] HUD disabled: " + e);
      return;
    }
    long vg = nvg.getHandle();
    try (MemoryStack stack = MemoryStack.stackPush()) {
      NVGPaint paint = NVGPaint.malloc(stack);
      NanoVG.nvgImagePattern(vg, x - pad, y - pad, w, h, 0, slot.image, 1, paint);
      NanoVG.nvgBeginPath(vg);
      NanoVG.nvgRect(vg, x - pad, y - pad, w, h);
      NanoVG.nvgFillPaint(vg, paint);
      NanoVG.nvgFill(vg);
    }
  }

  public void close() {
    for (Slot slot : slots) slot.close();
    slots.clear();
    frame = -1;
    cursor = 0;
    failed = false;
  }

  private static final class Slot {
    TextureTarget target;
    int image;
    long context;

    void ensure(NanoVGContext nvg, int w, int h) {
      if (target != null && (target.width != w || target.height != h || context != nvg.getHandle()))
        close();
      if (target == null) {
        target = new TextureTarget(w, h, false);
        context = nvg.getHandle();
        image =
            NanoVGGL3.nvglCreateImageFromHandle(
                context,
                target.getColorTextureId(),
                w,
                h,
                NanoVGGL3.NVG_IMAGE_NODELETE | NanoVG.NVG_IMAGE_FLIPY);
        if (image == 0) throw new IllegalStateException("Could not wrap Moon HUD texture");
      }
    }

    void close() {
      if (image != 0 && context == NanoVGContext.get().getHandle())
        NanoVG.nvgDeleteImage(context, image);
      if (target != null) target.destroyBuffers();
      target = null;
      image = 0;
    }
  }
}
