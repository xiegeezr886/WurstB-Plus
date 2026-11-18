/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package net.wurstclient.hacks;

import net.minecraft.world.entity.player.Player;
import net.wurstclient.Category;
import net.wurstclient.hack.DontSaveState;
import net.wurstclient.hack.Hack;
import net.wurstclient.util.render.LsdPostEffect;

@DontSaveState
public final class LsdHack extends Hack {
  public LsdHack() {
    super("LSD");
    setCategory(Category.FUN);
  }

  @Override
  protected void onEnable() {
    if (!(MC.getCameraEntity() instanceof Player)) setEnabled(false);
  }

  @Override
  protected void onDisable() {
    LsdPostEffect.INSTANCE.close();
  }

  /** Called before GUI rendering, including frames after leaving a world. */
  public void prepareFrame() {
    if (MC.level == null || !(MC.getCameraEntity() instanceof Player))
      LsdPostEffect.INSTANCE.close();
  }

  /** Runs at the end of the world pass; HUD and screens remain untouched. */
  public void renderFrame() {
    if (!isEnabled() || MC.level == null
        || !(MC.getCameraEntity() instanceof Player)) return;
    var target = MC.getMainRenderTarget();
    try {
      LsdPostEffect.INSTANCE.render(target.frameBufferId, target.width, target.height);
    } catch (java.io.IOException | RuntimeException e) {
      System.err.println("[LSD] Post-effect render failed: " + e);
      e.printStackTrace();
      setEnabled(false);
    }
  }
}
