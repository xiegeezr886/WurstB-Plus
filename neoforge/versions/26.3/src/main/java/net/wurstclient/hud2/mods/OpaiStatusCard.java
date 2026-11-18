package net.wurstclient.hud2.mods;

import net.wurstclient.hud2.nanovg.NanoVGContext;
import org.lwjgl.nanovg.NVGColor;
import org.lwjgl.nanovg.NVGPaint;
import org.lwjgl.nanovg.NanoVG;
import org.lwjgl.system.MemoryStack;

/** Shared Opai status-card material; see META-INF/licenses/opai-effects. */
final class OpaiStatusCard {
  private OpaiStatusCard() {}

  static void draw(NanoVGContext nvg, float x, float y, float w, float h, float scale, int accent) {
    nvg.drawHorizontalGradientRoundedRect(
        x, y, w, h, 6 * scale, tintedBackground(accent), 0x8F121216);
  }

  static int tintedBackground(int rgb) {
    // CSS color-mix(in srgb, effect 20%, #121216b8), premultiplied alpha.
    float a = .2F + .8F * 184 / 255F;
    int r = Math.round(((rgb >> 16 & 255) * .2F + 18 * .8F * 184 / 255F) / a);
    int g = Math.round(((rgb >> 8 & 255) * .2F + 18 * .8F * 184 / 255F) / a);
    int b = Math.round(((rgb & 255) * .2F + 22 * .8F * 184 / 255F) / a);
    return Math.round(a * 255) << 24 | r << 16 | g << 8 | b;
  }

  static void enchanted(
      NanoVGContext nvg, float x, float y, float w, float h, float scale, long millis) {
    long vg = nvg.getHandle();
    float phase = (millis % 3600) / 3600F;
    float center = x - 40 * scale + phase * (w + 80 * scale);
    float cy = y + h / 2, band = 14 * scale, reach = h + w;
    try (MemoryStack stack = MemoryStack.stackPush()) {
      NVGColor inner = NVGColor.malloc(stack), outer = NVGColor.malloc(stack);
      NanoVG.nvgRGBA((byte) 180, (byte) 135, (byte) 255, (byte) 34, inner);
      NanoVG.nvgRGBA((byte) 180, (byte) 135, (byte) 255, (byte) 0, outer);
      NVGPaint paint = NVGPaint.malloc(stack);
      NanoVG.nvgSave(vg);
      try {
        NanoVG.nvgTranslate(vg, center, cy);
        NanoVG.nvgRotate(vg, .35F);
        NanoVG.nvgFillPaint(
            vg,
            NanoVG.nvgBoxGradient(
                vg, -band / 2, -reach, band, reach * 2, 0, 24 * scale, inner, outer, paint));
        NanoVG.nvgRotate(vg, -.35F);
        NanoVG.nvgTranslate(vg, -center, -cy);
        NanoVG.nvgBeginPath(vg);
        NanoVG.nvgRoundedRect(vg, x, y, w, h, 6 * scale);
        NanoVG.nvgFill(vg);
      } finally {
        NanoVG.nvgRestore(vg);
      }
    }
  }
}
