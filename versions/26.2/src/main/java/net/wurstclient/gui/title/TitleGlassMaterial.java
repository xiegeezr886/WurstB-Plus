package net.wurstclient.gui.title;

import net.wurstclient.hud2.nanovg.NanoVGUI;

/** Shared white translucent fill and rim from the Nextgen main-menu buttons. */
public final class TitleGlassMaterial {
  private TitleGlassMaterial() {}

  public static void draw(
      float x,
      float y,
      float width,
      float height,
      float radius,
      float stroke,
      float hover,
      float opacity) {
    float amount = Math.max(0, Math.min(1, hover));
    float alpha = Math.max(0, Math.min(1, opacity));
    NanoVGUI.fillRounded(
        x,
        y,
        x + width,
        y + height,
        radius,
        (Math.round(255 * (.08F + .12F * amount) * alpha) << 24) | 0xFFFFFF);
    NanoVGUI.outlineRounded(
        x,
        y,
        x + width,
        y + height,
        radius,
        stroke,
        (Math.round(255 * (.15F + .25F * amount) * alpha) << 24) | 0xFFFFFF);
  }
}
