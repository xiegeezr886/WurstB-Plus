package net.wurstclient.hacks;

import kaptainwutax.seedcrackerX.SeedCracker;
import kaptainwutax.seedcrackerX.config.Config;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.DontSaveState;
import net.wurstclient.hack.Hack;
import net.wurstclient.seed.SeedCrackerBridge;
import net.wurstclient.settings.CheckboxSetting;

@DontSaveState
@SearchTags({"seed", "seedcrackerx", "种子", "种子破解"})
public final class SeedCrackerHack extends Hack implements UpdateListener {
  private final CheckboxSetting autoMine =
      new CheckboxSetting(
          "Mine when cracked",
          "Starts SeedMine after a verified seed is found, using BaritoneMine's Ores list.",
          false);
  private final CheckboxSetting render =
      new CheckboxSetting(
          "Show structures", "Highlights structures detected by SeedCrackerX.", false);
  private boolean startedMine;
  private boolean handledSeed;

  public SeedCrackerHack() {
    super("SeedCracker");
    setCategory(Category.WORLD);
    addSetting(autoMine);
    addSetting(render);
  }

  @Override
  protected boolean canEnable() {
    return MC.player != null && MC.level != null;
  }

  @Override
  protected void onEnable() {
    startedMine = handledSeed = false;
    Config.get().active = true;
    Config.get().databaseSubmits = false;
    EVENTS.add(UpdateListener.class, this);
    new kaptainwutax.seedcrackerX.finder.ReloadFinders().reload();
    onUpdate();
  }

  @Override
  protected void onDisable() {
    EVENTS.remove(UpdateListener.class, this);
    Config.get().active = false;
    Config.get().render = Config.RenderType.OFF;
    SeedCracker.get().reset();
    if (startedMine && WURST.getHax().seedMineHack.isEnabled())
      WURST.getHax().seedMineHack.setEnabled(false);
    startedMine = false;
  }

  @Override
  public void onUpdate() {
    Config.get().render = render.isChecked() ? Config.RenderType.XRAY : Config.RenderType.OFF;
    if (SeedCrackerBridge.SESSION.seed().isPresent()) onVerifiedSeed();
  }

  public void onVerifiedSeed() {
    if (!isEnabled()
        || handledSeed
        || !autoMine.isChecked()
        || MC.player == null
        || MC.gui.screen() != null
        || !SeedCrackerBridge.SESSION.seed().isPresent()) return;
    handledSeed = true;
    var mine = WURST.getHax().seedMineHack;
    if (!mine.isEnabled()) {
      mine.setEnabled(true);
      startedMine = mine.isEnabled();
    }
  }

  @Override
  public String getRenderName() {
    return getName()
        + (SeedCrackerBridge.SESSION.seed().isPresent() ? " [Ready]" : " [Collecting]");
  }
}
