/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
package net.wurstclient.hacks;


import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.hack.HackConflictGroup;
import net.wurstclient.hacks.aura.OpenAura;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.SwingHandSetting;
import net.wurstclient.settings.SwingHandSetting.SwingHand;

/** Epsilon MultiAura batch attack budget; no idle catch-up queue. GPL-3.0. */
@SearchTags({"multi aura", "ForceField", "force field"})
public final class MultiAuraHack extends Hack implements UpdateListener {
  private final SliderSetting range =
      new SliderSetting("Range", 5, 1, 6, 0.05, ValueDisplay.DECIMAL);

  private final SliderSetting minCps =
      new SliderSetting("Min CPS", 10, 1, 20, 1, ValueDisplay.INTEGER);
  private final SliderSetting maxCps =
      new SliderSetting("Max CPS", 12, 1, 20, 1, ValueDisplay.INTEGER);
  private double attacks;
  private Object player, world;

  private final SliderSetting fov =
      new SliderSetting("FOV", 360, 30, 360, 10, ValueDisplay.DEGREES);

  private final SwingHandSetting swingHand =
      new SwingHandSetting(SwingHandSetting.genericCombatDescription(this), SwingHand.CLIENT);

  private final CheckboxSetting targetPlayers =
      new CheckboxSetting("Target players", "Attacks other players.", true);
  private final CheckboxSetting targetHostile =
      new CheckboxSetting("Target hostile", "Attacks hostile mobs.", true);
  private final CheckboxSetting targetPassive =
      new CheckboxSetting("Target passive", "Attacks passive animals.", true);
  private final CheckboxSetting targetFriendly =
      new CheckboxSetting(
          "Target friendly", "Attacks other living entities (villagers, golems).", true);

  private Entity currentTarget;

  public MultiAuraHack() {
    super("MultiAura");
    setCategory(Category.COMBAT);
    addConflictGroup(HackConflictGroup.COMBAT_TARGETING);

    addSetting(range);
    addSetting(minCps);
    addSetting(maxCps);
    addSetting(fov);
    addSetting(swingHand);
    addSetting(targetPlayers);
    addSetting(targetHostile);
    addSetting(targetPassive);
    addSetting(targetFriendly);
  }

  @Override
  protected void onEnable() {
    WURST.getHax().killauraHack.setEnabled(false);
    currentTarget = null;
    attacks = 0;
    EVENTS.add(UpdateListener.class, this);
  }

  @Override
  protected void onDisable() {
    EVENTS.remove(UpdateListener.class, this);
    currentTarget = null;
  }

  @Override
  public void onUpdate() {
    if (player != MC.player || world != MC.level) {
      attacks = 0;
      currentTarget = null;
      player = MC.player;
      world = MC.level;
    }
    if (!net.wurstclient.epsilon.EpsilonTargets.ready()
        || MC.gui.screen() != null
        || MC.player.isUsingItem()
        || WURST.getHax().scaffoldWalkHack.isEnabled()) {
      attacks = 0;
      currentTarget = null;
      return;
    }
    var targets =
        net.wurstclient.epsilon.EpsilonTargets.acquire(
            range.getValue(), fov.getValue(), targetFlags(), true, true);
    if (targets.isEmpty()) {
      attacks = 0;
      currentTarget = null;
      return;
    }
    currentTarget = targets.get(0);
    double lo = Math.min(minCps.getValue(), maxCps.getValue()),
        hi = Math.max(minCps.getValue(), maxCps.getValue());
    attacks = Math.min(1.999, attacks + (lo + Math.random() * (hi - lo)) / 20);
    if (attacks < 1) return;
    attacks -= 1;
    for (var target : targets)
      if (net.wurstclient.epsilon.EpsilonTargets.valid(target)
          && net.wurstclient.epsilon.EpsilonTargets.distance(target) <= range.getValue())
        MC.gameMode.attack(MC.player, target);
    swingHand.getSelected().swing(net.minecraft.world.InteractionHand.MAIN_HAND);
  }

  public Entity getCurrentTarget() {
    return currentTarget;
  }

  public InteractionHand getFakeBlockingHand() {
    return null;
  }

  private int targetFlags() {
    int flags = 0;
    if (targetPlayers.isChecked()) flags |= OpenAura.TARGET_PLAYERS;
    if (targetHostile.isChecked()) flags |= OpenAura.TARGET_HOSTILE;
    if (targetPassive.isChecked()) flags |= OpenAura.TARGET_PASSIVE;
    if (targetFriendly.isChecked()) flags |= OpenAura.TARGET_FRIENDLY;
    return flags;
  }
}
