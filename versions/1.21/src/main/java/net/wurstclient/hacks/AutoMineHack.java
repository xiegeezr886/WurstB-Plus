/* AutoMine ported from MrBZBZ/LeavesHack 13daf96. SPDX-License-Identifier: AGPL-3.0-or-later */
package net.wurstclient.hacks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.events.*;
import net.wurstclient.hack.Hack;
import net.wurstclient.leaves.LeavesDamage;
import net.wurstclient.leaves.LeavesUtils;
import net.wurstclient.settings.*;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

public final class AutoMineHack extends Hack implements UpdateListener, PacketOutputListener {
  private final SliderSetting targetRange =
      new SliderSetting("TargetRange", 6, 0, 8, 1, ValueDisplay.INTEGER);
  private final CheckboxSetting doubleBreak = new CheckboxSetting("DoubleBreak", true);
  private final CheckboxSetting ignoreTerrain = new CheckboxSetting("IgnoreTerrain", true);
  private final CheckboxSetting burrow = new CheckboxSetting("Burrow", true);
  private final CheckboxSetting surround = new CheckboxSetting("Surround", true);
  private final SliderSetting delay =
      new SliderSetting("Delay", 300, 0, 1000, 1, ValueDisplay.INTEGER);
  private final SliderSetting airDelay =
      new SliderSetting("TargetAirDelay", 300, 0, 1000, 1, ValueDisplay.INTEGER);
  private final SliderSetting minDamage =
      new SliderSetting("MinCrystalDamage", 4, 0, 36, 0.1, ValueDisplay.DECIMAL);
  private static final Set<Block> HARD =
      Set.of(
          Blocks.OBSIDIAN,
          Blocks.ENDER_CHEST,
          Blocks.NETHERITE_BLOCK,
          Blocks.CRYING_OBSIDIAN,
          Blocks.RESPAWN_ANCHOR,
          Blocks.ANCIENT_DEBRIS,
          Blocks.ANVIL,
          Blocks.GLASS);
  private long next, airSince;
  private BlockPos tracked;
  private boolean waiting;
  private Object world;

  public AutoMineHack() {
    super("AutoMine");
    setCategory(Category.WORLD);
    for (Setting s :
        new Setting[] {
          targetRange, doubleBreak, ignoreTerrain, burrow, surround, delay, airDelay, minDamage
        }) addSetting(s);
  }

  @Override
  protected void onEnable() {
    clear();
    EVENTS.add(UpdateListener.class, this);
    EVENTS.add(PacketOutputListener.class, this);
  }

  @Override
  protected void onDisable() {
    EVENTS.remove(UpdateListener.class, this);
    EVENTS.remove(PacketOutputListener.class, this);
    clear();
  }

  private void clear() {
    tracked = null;
    waiting = false;
    next = airSince = 0;
    world = MC.level;
  }

  @Override
  public void onSentPacket(PacketOutputEvent event) {
    if (event.getPacket()
            instanceof net.minecraft.network.protocol.game.ServerboundPlayerActionPacket p
        && p.getAction()
            == net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action
                .STOP_DESTROY_BLOCK) next = System.nanoTime() + delay.getValueI() * 1_000_000L;
  }

  @Override
  public void onUpdate() {
    if (world != MC.level) clear();
    var mine = WURST.getHax().packetMineHack;
    if (!LeavesUtils.ready() || !mine.isEnabled() || System.nanoTime() < next) return;
    BlockPos primary = mine.getTargetPos(), second = mine.getSecondPos();
    if (doubleBreak.isChecked() && waiting) {
      if ((primary != null && !mine.isCompleted()) || second != null) return;
      waiting = false;
      return;
    }
    var enemy = LeavesUtils.enemy(targetRange.getValue());
    if (enemy == null) return;
    if (primary != null && !enemy.getBoundingBox().intersects(new AABB(primary))) {
      if (!primary.equals(tracked)) {
        tracked = primary;
        airSince = System.nanoTime();
      }
      if (mine.isCompleted()) {
        float damage =
            LeavesDamage.damage(
                Vec3.atCenterOf(primary), enemy, 6, ignoreTerrain.isChecked() ? primary : null);
        if (damage >= minDamage.getValue()) {
          if (!MC.level.isEmptyBlock(primary)) airSince = System.nanoTime();
          if (!MC.level.isEmptyBlock(primary)
              || System.nanoTime() - airSince < airDelay.getValueI() * 10_000_000L) return;
        }
      }
    } else {
      tracked = null;
      airSince = System.nanoTime();
    }
    if (!doubleBreak.isChecked() && second != null) return;
    ArrayList<BlockPos> positions = new ArrayList<>();
    BlockPos base = enemy.blockPosition();
    if (burrow.isChecked()) positions.add(base);
    if (surround.isChecked())
      for (Direction d : Direction.Plane.HORIZONTAL) positions.add(base.relative(d));
    positions.removeIf(
        p ->
            p.equals(primary)
                || p.equals(second)
                || !HARD.contains(MC.level.getBlockState(p).getBlock())
                || MC.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(p))
                    > mine.getRange() * mine.getRange());
    if (positions.isEmpty()) return;
    BlockPos chosen =
        positions.contains(base)
            ? base
            : positions.stream()
                .min(
                    Comparator.comparingDouble(
                        p -> MC.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(p))))
                .orElseThrow();
    if (mine.mine(chosen)) {
      next = System.nanoTime() + delay.getValueI() * 1_000_000L;
      tracked = mine.getTargetPos();
      airSince = System.nanoTime();
      waiting =
          doubleBreak.isChecked() && mine.getTargetPos() != null && mine.getSecondPos() != null;
    }
  }
}
