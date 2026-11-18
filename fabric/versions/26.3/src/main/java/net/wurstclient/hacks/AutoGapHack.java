/* NeoRaven AutoGappleModule eat/buffer/cooldown lifecycle, GPL-3.0. */
package net.wurstclient.hacks;

import java.util.ArrayDeque;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.wurstclient.Category;
import net.wurstclient.epsilon.EpsilonTargets;
import net.wurstclient.events.*;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.*;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

public final class AutoGapHack extends Hack
    implements UpdateListener, PacketOutputListener, MoveInputListener {
  private enum Mode {
    MOONLIGHT,
    SILENCE_FIX
  }

  private final EnumSetting<Mode> mode = new EnumSetting<>("Mode", Mode.values(), Mode.MOONLIGHT);
  private final SliderSetting health =
      new SliderSetting("Health", 16, 1, 20, .5, ValueDisplay.DECIMAL);
  private final SliderSetting delay =
      new SliderSetting("Delay", 500, 0, 2000, 50, ValueDisplay.INTEGER);
  private final SliderSetting hold =
      new SliderSetting("Hold packets", 32, 1, 80, 1, ValueDisplay.INTEGER);
  private final CheckboxSetting stopMove = new CheckboxSetting("Stop move when eating", true);
  private final CheckboxSetting allowEgap =
      new CheckboxSetting("Allow enchanted golden apple", true);
  private final ArrayDeque<Packet<?>> held = new ArrayDeque<>();
  private Object player, world, connection;
  private boolean eating, replaying;
  private int original = -1, apple = -1, cooldown, moves;
  private long lastUse;

  public AutoGapHack() {
    super("AutoGap");
    setCategory(Category.COMBAT);
    for (Setting s : new Setting[] {mode, health, delay, hold, stopMove, allowEgap}) addSetting(s);
  }

  protected void onEnable() {
    clear();
    WURST.getHax().autoEatHack.setEnabled(false);
    EVENTS.add(UpdateListener.class, this);
    EVENTS.add(PacketOutputListener.class, this);
    EVENTS.add(MoveInputListener.class, this);
  }

  protected void onDisable() {
    EVENTS.remove(UpdateListener.class, this);
    EVENTS.remove(PacketOutputListener.class, this);
    EVENTS.remove(MoveInputListener.class, this);
    stop();
  }

  private void clear() {
    held.clear();
    eating = false;
    original = apple = -1;
    cooldown = moves = 0;
    player = MC.player;
    world = MC.level;
    connection = MC.getConnection();
    lastUse = System.nanoTime();
  }

  private boolean current() {
    return player == MC.player && world == MC.level && connection == MC.getConnection();
  }

  private void release() {
    if (!current() || MC.getConnection() == null) {
      held.clear();
      moves = 0;
      return;
    }
    replaying = true;
    try {
      while (!held.isEmpty()) MC.getConnection().send(held.removeFirst());
    } finally {
      replaying = false;
      moves = 0;
    }
  }

  private void restore() {
    if (current()
        && MC.player != null
        && original >= 0
        && MC.player.getInventory().getSelectedSlot() == apple) MC.player.getInventory().setSelectedSlot(original);
    original = apple = -1;
  }

  private void stop() {
    boolean owned = shouldHoldUseKey();
    eating = false;
    release();
    if (owned && MC.gameMode != null) MC.gameMode.releaseUsingItem(MC.player);
    restore();
    cooldown = 0;
  }

  public boolean shouldHoldUseKey() {
    return isEnabled()
        && current()
        && MC.player != null
        && cooldown > 0
        && MC.player.getInventory().getSelectedSlot() == apple;
  }

  public void onUpdate() {
    if (!current()) clear();
    if (!EpsilonTargets.ready() || MC.gui.screen() != null || MC.player.isCreative()) {
      stop();
      return;
    }
    if (cooldown > 0) {
      if (MC.player.getInventory().getSelectedSlot() != apple) {
        stop();
        lastUse = System.nanoTime();
        return;
      }
      if (--cooldown == 0) {
        restore();
        eating = false;
        release();
      }
      return;
    }
    int slot = findApple();
    if (slot < 0
        || mode.getSelected() == Mode.MOONLIGHT
            && (MC.player.getHealth() >= health.getValue()
                || (System.nanoTime() - lastUse) / 1_000_000 < delay.getValue())) {
      stop();
      return;
    }
    if (MC.player.isUsingItem()) return;
    eating = true;
    if (moves < hold.getValueI()) return;
    original = MC.player.getInventory().getSelectedSlot();
    apple = slot;
    MC.player.getInventory().setSelectedSlot(slot);
    var result = MC.gameMode.useItem(MC.player, InteractionHand.MAIN_HAND);
    release();
    if (result.consumesAction()) {
      cooldown = 34;
      lastUse = System.nanoTime();
    } else stop();
  }

  public void onSentPacket(PacketOutputEvent event) {
    if (!eating
        || replaying
        || !current()
        || !(event.getPacket() instanceof ServerboundMovePlayerPacket)) return;
    moves++;
    // The upstream SilenceFix explicitly passes motion through in singleplayer.
    if (mode.getSelected() == Mode.SILENCE_FIX && MC.hasSingleplayerServer()) return;
    held.addLast(event.getPacket());
    event.cancel();
    if (held.size() >= 128) release();
  }

  public void onMoveInput(MoveInputEvent event) {
    if (eating && (stopMove.isChecked() || mode.getSelected() == Mode.SILENCE_FIX)) {
      event.setForward(0);
      event.setSideways(0);
      if (MC.player != null) MC.player.setSprinting(false);
    }
  }

  private int findApple() {
    for (int i = 0; i < 9; i++) {
      var stack = MC.player.getInventory().getItem(i);
      if (stack.is(Items.GOLDEN_APPLE)
          || allowEgap.isChecked() && stack.is(Items.ENCHANTED_GOLDEN_APPLE)) return i;
    }
    return -1;
  }

  public String getRenderName() {
    return getName() + (eating ? " [" + Math.min(100, moves * 100 / hold.getValueI()) + "%]" : "");
  }
}
