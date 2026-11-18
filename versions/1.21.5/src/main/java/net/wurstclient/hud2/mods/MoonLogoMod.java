package net.wurstclient.hud2.mods;

import net.wurstclient.gui.title.ClientLogo;
import net.wurstclient.hud2.HUDMod;
import net.wurstclient.settings.CheckboxSetting;

/** Moon WatermarkHudRenderer LOGO layout, with the user's menu artwork. */
public final class MoonLogoMod extends HUDMod {
  private final CheckboxSetting glow = new CheckboxSetting(
      "Logo glow", "Shows a glow around this HUD logo only.", true);

  public MoonLogoMod() {
    super("LogoHUD", "Logo HUD", "Shows the menu logo as a movable Moon-style watermark.");
    setTranslationKey("moon_logo");
    setDefaultPosition(4, 4);
    setDefaultEnabled(true);
    addSetting(glow);
  }

  @Override
  protected void draw() {
    // Moon uses a 68-unit logo. Fit the user's 3:2 artwork without stretching it.
    float height = 68 / 1.5F;
    ClientLogo.draw(nvg, getX(), getY(), height * getScale(), glow.isChecked());
    setSize(68, Math.round(height));
    markDrawn();
  }
}
