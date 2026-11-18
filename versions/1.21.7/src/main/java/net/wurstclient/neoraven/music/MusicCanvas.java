/* NeoRaven music geometry adapter for NanoVG; GPL-3.0. */
package net.wurstclient.neoraven.music;

import net.wurstclient.gui.title.TitleGlassMaterial;
import net.wurstclient.hud2.nanovg.*;
import org.lwjgl.nanovg.NanoVG;
import org.lwjgl.system.MemoryStack;

/** All coordinates use NeoRaven's 840 x 520 design canvas, including input hit boxes. */
final class MusicCanvas {
  float x, y, scale = 1, alpha = 1;
  private final float[][] wavePoints = new float[512][2];

  private int color(int value) {
    return Material.scaleAlpha(value, alpha);
  }

  void rounded(float left, float top, float width, float height, float radius, int color) {
    NanoVGUI.fillRounded(
        x + left * scale,
        y + top * scale,
        x + (left + width) * scale,
        y + (top + height) * scale,
        radius * scale,
        color(color));
  }

  void rect(float left, float top, float width, float height, int color) {
    rounded(left, top, width, height, 0, color);
  }

  void outline(
      float left, float top, float width, float height, float radius, float stroke, int color) {
    NanoVGUI.outlineRounded(
        x + left * scale,
        y + top * scale,
        x + (left + width) * scale,
        y + (top + height) * scale,
        radius * scale,
        stroke * scale,
        color(color));
  }

  float width(String text, float size) {
    return NanoVGUI.width(text, size * scale, HudFonts.MEDIUM) / scale;
  }

  String trim(String text, float size, float maxWidth) {
    if (width(text, size) <= maxWidth) return text;
    int low = 0, high = text.length();
    while (low < high) {
      int mid = (low + high + 1) / 2;
      if (width(text.substring(0, mid) + "…", size) <= maxWidth) low = mid;
      else high = mid - 1;
    }
    if (low > 0 && Character.isHighSurrogate(text.charAt(low - 1))) low--;
    return text.substring(0, low) + "…";
  }

  void text(String text, float left, float centerY, float size, int color) {
    NanoVGUI.text(
        text,
        x + left * scale,
        y + (centerY - size / 2) * scale,
        color(color),
        size * scale,
        HudFonts.MEDIUM);
  }

  void center(String text, float centerX, float centerY, float size, int color) {
    text(text, centerX - width(text, size) / 2, centerY, size, color);
  }

  void right(String text, float right, float centerY, float size, int color) {
    text(text, right - width(text, size), centerY, size, color);
  }

  void icon(String glyph, float centerX, float centerY, float size, int color) {
    if (!HudFonts.MUSIC_ICONS.isLoaded()) return;
    long vg = NanoVGUI.context().getHandle();
    try (MemoryStack stack = MemoryStack.stackPush()) {
      var bounds = stack.mallocFloat(4);
      NanoVG.nvgFontFace(vg, HudFonts.MUSIC_ICONS.getName());
      NanoVG.nvgFontSize(vg, size * scale);
      NanoVG.nvgTextAlign(vg, NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_MIDDLE);
      NanoVG.nvgTextBounds(vg, 0, 0, glyph, bounds);
      NanoVGUI.text(
          glyph,
          x + centerX * scale - (bounds.get(0) + bounds.get(2)) / 2,
          y + centerY * scale - (bounds.get(1) + bounds.get(3)) / 2 - size * scale / 2,
          color(color),
          size * scale,
          HudFonts.MUSIC_ICONS);
    }
  }

  void glass(float left, float top, float width, float height, float radius, float hover) {
    TitleGlassMaterial.draw(
        x + left * scale,
        y + top * scale,
        width * scale,
        height * scale,
        radius * scale,
        Math.max(.65F, height * 2 / 110 * scale),
        hover,
        alpha);
  }

  void clip(float left, float top, float width, float height) {
    NanoVGUI.pushScissor(x + left * scale, y + top * scale, width * scale, height * scale);
  }

  void unclip() {
    NanoVGUI.popScissor();
  }

  void cover(
      String url, float left, float top, float size, float radius, int background, int foreground) {
    rounded(left, top, size, size, radius, background);
    var image = CoverCache.get(url);
    if (image != null)
      NanoVGUI.image(
          image,
          x + left * scale,
          y + top * scale,
          size * scale,
          size * scale,
          radius * scale,
          color(-1));
    else icon("\ue405", left + size / 2, top + size / 2, Math.min(24, size * .45F), foreground);
  }

  /** NeoRaven's moving wave: 40 px wavelength, 28 px/s and .42 amplitude for the player. */
  void wave(
      float left,
      float centerY,
      float width,
      float progress,
      int fill,
      int track,
      float playing,
      float phase) {
    progress = Math.max(0, Math.min(1, progress));
    float stroke = 3.24F;
    float end = width * progress;
    rounded(left + end, centerY - stroke / 2, width - end, stroke, stroke / 2, track);
    if (end <= 0) return;
    float ramp =
        progress <= .1F || progress >= .95F
            ? 0
            : progress < .14F
                ? (progress - .1F) / .04F
                : progress > .9F ? (.95F - progress) / .05F : 1;
    float amplitude = .42F * (9 - stroke) / 2 * ramp * playing;
    int count = Math.min(512, Math.max(2, (int) (end / 2) + 1));
    for (int i = 0; i < count; i++) {
      float px = end * i / (count - 1);
      wavePoints[i][0] = x + (left + px) * scale;
      wavePoints[i][1] =
          y + (centerY + (float) Math.sin((px - phase) * Math.PI / 20) * amplitude) * scale;
    }
    NanoVGUI.context().drawPolyline(wavePoints, count, stroke * scale, color(fill));
  }
}
