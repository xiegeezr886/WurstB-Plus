/* Epsilon Criticals fall-window sprint control. GPL-3.0. */
package net.wurstclient.hacks;

import net.wurstclient.Category;
import net.wurstclient.epsilon.EpsilonTargets;
import net.wurstclient.events.*;
import net.wurstclient.hack.Hack;

public final class CriticalsHack extends Hack implements UpdateListener, MoveInputListener {
  private int fallTicks;
  private Object player, world;

  public CriticalsHack() {
    super("Criticals");
    setCategory(Category.COMBAT);
  }

  protected void onEnable() {
    fallTicks = 0;
    EVENTS.add(UpdateListener.class, this);
    EVENTS.add(MoveInputListener.class, this);
  }

  protected void onDisable() {
    fallTicks = 0;
    EVENTS.remove(UpdateListener.class, this);
    EVENTS.remove(MoveInputListener.class, this);
  }

  public int getFallTicks() {
    return isEnabled() ? fallTicks : 0;
  }

  private boolean canCrit() {
    return EpsilonTargets.ready()
        && MC.gui.screen() == null
        && MC.player.fallDistance > 0
        && !MC.player.onGround()
        && !MC.player.onClimbable()
        && !MC.player.isInWater()
        && !MC.player.isPassenger();
  }

  public void onUpdate() {
    if (player != MC.player || world != MC.level) {
      fallTicks = 0;
      player = MC.player;
      world = MC.level;
    }
    fallTicks = canCrit() && MC.player.fallDistance < 1 ? fallTicks + 1 : 0;
  }

  public void onMoveInput(MoveInputEvent event) {
    if (fallTicks > 0
        && fallTicks < 3
        && canCrit()
        && MC.player.isSprinting()
        && event.getForward() > 0
        && WURST.getHax().killauraHack.getCurrentTarget() != null) MC.player.setSprinting(false);
  }
}
