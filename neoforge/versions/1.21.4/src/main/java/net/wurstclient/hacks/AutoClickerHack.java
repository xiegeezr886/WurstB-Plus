/* Epsilon AutoClicker with NeoRaven weapon/block gates. GPL-3.0. */
package net.wurstclient.hacks;

import java.util.Random;
import net.minecraft.client.KeyMapping;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.wurstclient.Category;
import net.wurstclient.epsilon.*;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.mixin.KeyMappingAccessor;
import net.wurstclient.settings.*;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

public final class AutoClickerHack extends Hack implements UpdateListener {
  private final EnumSetting<Button> button =
      new EnumSetting<>("Button", Button.values(), Button.BOTH);
  private final SliderSetting leftMin = number("Left min CPS", 8, 1, 520),
      leftMax = number("Left max CPS", 12, 1, 520);
  private final SliderSetting rightMin = number("Right min CPS", 8, 1, 520),
      rightMax = number("Right max CPS", 12, 1, 520);
  private final CheckboxSetting leftJitter = new CheckboxSetting("Left jitter", false),
      rightJitter = new CheckboxSetting("Right jitter", false);
  private final CheckboxSetting burst = new CheckboxSetting("Enable burst", true),
      doubleClick = new CheckboxSetting("Enable double click", true),
      miss = new CheckboxSetting("Enable miss", true);
  private final SliderSetting burstChance = number("Burst chance", 15, 0, 100),
      duration = number("Burst duration", 800, 100, 3000),
      decay = number("Decay duration", 1200, 100, 5000);
  private final SliderSetting
      multiplier = new SliderSetting("Burst CPS multiplier", 1.8, 1, 3, .1, ValueDisplay.DECIMAL),
      curve = new SliderSetting("Decay curve", 1.5, 1, 3, .1, ValueDisplay.DECIMAL);
  private final SliderSetting doubleChance = number("Double click chance", 8, 0, 100),
      doubleDelay = number("Double click delay", 40, 10, 100),
      missChance = number("Miss chance", 5, 0, 100);
  private final CheckboxSetting weapon = new CheckboxSetting("Weapon only", false),
      blocks = new CheckboxSetting("Blocks only", false),
      breaking = new CheckboxSetting("Break blocks", true);
  private final EpsilonClickState left = new EpsilonClickState(new Random()),
      right = new EpsilonClickState(new Random());
  private Object player, world;

  private static SliderSetting number(String name, int value, int min, int max) {
    return new SliderSetting(name, value, min, max, 1, ValueDisplay.INTEGER);
  }

  public AutoClickerHack() {
    super("AutoClicker");
    setCategory(Category.COMBAT);
    for (Setting s :
        new Setting[] {
          button,
          leftMin,
          leftMax,
          rightMin,
          rightMax,
          leftJitter,
          rightJitter,
          burst,
          burstChance,
          duration,
          multiplier,
          decay,
          curve,
          doubleClick,
          doubleChance,
          doubleDelay,
          miss,
          missChance,
          weapon,
          blocks,
          breaking
        }) addSetting(s);
  }

  protected void onEnable() {
    left.reset();
    right.reset();
    EVENTS.add(UpdateListener.class, this);
  }

  protected void onDisable() {
    left.reset();
    right.reset();
    EVENTS.remove(UpdateListener.class, this);
  }

  public void onUpdate() {
    if (player != MC.player || world != MC.level) {
      left.reset();
      right.reset();
      player = MC.player;
      world = MC.level;
    }
    if (!EpsilonTargets.ready() || MC.screen != null) {
      left.reset();
      right.reset();
      return;
    }
    long now = System.nanoTime() / 1000000;
    boolean attack =
        button.getSelected() != Button.RIGHT
            && MC.options.keyAttack.isDown()
            && (!weapon.isChecked() || EpsilonTargets.weapon())
            && !MC.player.isUsingItem()
            && !WURST.getHax().killauraHack.isEnabled()
            && !WURST.getHax().multiAuraHack.isEnabled();
    if (MC.gameMode.isDestroying() && breaking.isChecked()) attack = false;
    if (attack) click(left, MC.options.keyAttack, leftMin, leftMax, leftJitter, now);
    else left.reset();
    boolean use =
        button.getSelected() != Button.LEFT
            && MC.options.keyUse.isDown()
            && (!blocks.isChecked() || MC.player.getMainHandItem().getItem() instanceof BlockItem)
            && !WURST.getHax().scaffoldWalkHack.isEnabled();
    if (use) click(right, MC.options.keyUse, rightMin, rightMax, rightJitter, now);
    else right.reset();
  }

  private void click(
      EpsilonClickState state,
      KeyMapping key,
      SliderSetting min,
      SliderSetting max,
      CheckboxSetting jitter,
      long now) {
    var config =
        new EpsilonClickState.Config(
            min.getValueI(),
            max.getValueI(),
            burst.isChecked(),
            burstChance.getValueI(),
            duration.getValueI(),
            multiplier.getValue(),
            decay.getValueI(),
            curve.getValue(),
            doubleClick.isChecked(),
            doubleChance.getValueI(),
            doubleDelay.getValueI(),
            miss.isChecked(),
            missChance.getValueI());
    int count = state.tick(now, config);
    for (int i = 0; i < count; i++) KeyMapping.click(((KeyMappingAccessor) key).wurst$getKey());
    if (count > 0 && jitter.isChecked()) {
      MC.player.setYRot(MC.player.getYRot() + (float) (Math.random() - .5) * .5F);
      MC.player.setXRot(
          Mth.clamp(MC.player.getXRot() + (float) (Math.random() - .5) * .5F, -90, 90));
    }
  }

  public enum Button {
    LEFT,
    RIGHT,
    BOTH
  }
}
