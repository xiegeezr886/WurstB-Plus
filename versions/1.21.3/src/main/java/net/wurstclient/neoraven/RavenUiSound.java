/* NeoRaven menu gui.button.press mapped to Minecraft 1.20.1. */
package net.wurstclient.neoraven;

import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.wurstclient.WurstClient;

public final class RavenUiSound {
  private RavenUiSound() {}

  public static void click() {
    if (WurstClient.MC != null)
      WurstClient.MC
          .getSoundManager()
          .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1));
  }
}
