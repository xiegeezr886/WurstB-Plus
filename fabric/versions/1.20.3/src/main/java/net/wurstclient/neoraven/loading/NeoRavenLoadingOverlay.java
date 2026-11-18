/* NeoRaven LoadingOverlay adaptation; GPL-3.0. See LOADING-NOTICE.txt. */
package net.wurstclient.neoraven.loading;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.wurstclient.WurstClient;
import net.wurstclient.gui.MainMenuBackground;
import net.wurstclient.hud2.nanovg.HudFonts;
import net.wurstclient.hud2.nanovg.NanoVGUI;

public final class NeoRavenLoadingOverlay {
  private NeoRavenLoadingOverlay() {}

  /** Use the client's language even when Minecraft's own language is different. */
  public static String message(Component component) {
    if (component == null) return "";
    if (component.getContents() instanceof TranslatableContents contents) {
      String key = "loading." + contents.getKey();
      String translated = WurstClient.INSTANCE.translate(key, contents.getArgs());
      if (!translated.equals(key)) return translated;
    }
    return component.getString();
  }

  public static void render(GuiGraphics graphics, Screen screen, String title, String subtitle) {
    int width = screen.width, height = screen.height;
    float fit = Math.min(1, height / 240F), size = 64 * fit;
    float anchor = Math.min(height - 24, height / 4F + 132);
    float subtitleY = anchor - 34 * fit,
        titleY = subtitleY - 22 * fit,
        cy = titleY - 26 * fit - size / 2;
    float cx = width / 2F;
    NanoVGUI.passSmooth(
        graphics,
        () -> {
          MainMenuBackground.render(width, height, 255);
          NanoVGUI.fill(0, 0, width, height, 0x59000000);
          text(title, cx, titleY, 14 * fit, 0xFFF0F0F5, width - 32);
          if (subtitle != null && !subtitle.isBlank())
            text(subtitle, cx, subtitleY, 10 * fit, 0xFFC0C0CD, width - 32);
        });
    graphics.flush();
    if (!NeoRavenLoadingShape.draw(cx, cy, size, 0xFFB5BEF0))
      NanoVGUI.pass(
          graphics,
          () ->
              NanoVGUI.fillRounded(
                  cx - size * .34F,
                  cy - size * .34F,
                  cx + size * .34F,
                  cy + size * .34F,
                  size * .34F,
                  0xFFB5BEF0));
  }

  private static void text(
      String value, float cx, float cy, float size, int color, float maxWidth) {
    String text = value == null ? "" : value;
    if (NanoVGUI.width(text, size, HudFonts.MEDIUM) > maxWidth) {
      int end = text.length();
      while (end > 0
          && NanoVGUI.width(text.substring(0, end) + "…", size, HudFonts.MEDIUM) > maxWidth) end--;
      text = text.substring(0, end) + "…";
    }
    NanoVGUI.text(
        text,
        cx - NanoVGUI.width(text, size, HudFonts.MEDIUM) / 2,
        cy - size / 2,
        color,
        size,
        HudFonts.MEDIUM);
  }
}
