package net.wurstclient.hacks;

import net.wurstclient.Category;
import net.wurstclient.hack.Hack;
import net.wurstclient.neoraven.music.MusicScreen;
import net.wurstclient.settings.SliderSetting;

/** One-shot screen action; playback survives closing the window. */
public final class MusicPlayerHack extends Hack {
  private final SliderSetting guiScale =
      new SliderSetting(
          "GUI Scale",
          "Scales the music player within the screen bounds.",
          1,
          .5,
          2,
          .05,
          SliderSetting.ValueDisplay.DECIMAL);

  public MusicPlayerHack() {
    super("MusicPlayer");
    setCategory(Category.MISC);
    addSetting(guiScale);
  }

  public float guiScale() {
    return guiScale.getValueF();
  }

  @Override
  protected void onEnable() {
    if (!(MC.gui.screen() instanceof MusicScreen)) MC.setScreenAndShow(new MusicScreen(MC.gui.screen()));
    setEnabled(false);
  }
}
