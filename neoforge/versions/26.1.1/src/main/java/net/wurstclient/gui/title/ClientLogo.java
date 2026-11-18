package net.wurstclient.gui.title;

import net.wurstclient.WurstClient;
import net.wurstclient.hud2.compat.HudTextures;
import net.wurstclient.hud2.nanovg.NanoVGContext;

/** Shared menu/HUD artwork; the contour glow is opt-in and pre-rendered. */
public final class ClientLogo {
  private static final String LOGO = "wurstpenguin:title-nextgen/wurst-logo.png";
  private static final String GLOW = "wurstpenguin:title-nextgen/wurst-logo-glow.png";

  private ClientLogo() {}

  public static void draw(NanoVGContext nvg, float x, float y, float height) {
    var manager = WurstClient.INSTANCE.getHudManager();
    draw(nvg, x, y, height, manager != null && manager.getSettings().hasLogoGlow());
  }

  public static void draw(NanoVGContext nvg, float x, float y, float height, boolean glow) {
    if (glow) {
      float padding = height * .2F;
      nvg.drawImage(
          HudTextures.id(GLOW),
          x - padding,
          y - padding,
          height * 1.5F + padding * 2,
          height + padding * 2,
          0,
          0xE6FFFFFF);
    }
    nvg.drawImage(HudTextures.id(LOGO), x, y, height * 1.5F, height, 0, 0xFFFFFFFF);
  }
}
