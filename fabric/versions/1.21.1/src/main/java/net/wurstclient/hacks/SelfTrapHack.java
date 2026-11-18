/* SelfTrap ported from MrBZBZ/LeavesHack 13daf96. SPDX-License-Identifier: AGPL-3.0-or-later */
package net.wurstclient.hacks;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.*;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.*;
import net.wurstclient.Category;
import net.wurstclient.events.*;
import net.wurstclient.hack.Hack;
import net.wurstclient.leaves.LeavesUtils;
import net.wurstclient.settings.*;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.*;

public final class SelfTrapHack extends Hack implements UpdateListener, PacketInputListener {
  private final SliderSetting range =
      new SliderSetting("Range", 5.0, 1, 6, 0.01, ValueDisplay.DECIMAL);
  private final SliderSetting delay =
      new SliderSetting("Delay", 0, 0, 500, 1, ValueDisplay.INTEGER);
  private final CheckboxSetting rotate = new CheckboxSetting("Rotate", true);
  private final SliderSetting blocksPer =
      new SliderSetting("BlocksPer", 2, 0, 6, 1, ValueDisplay.INTEGER);
  private final CheckboxSetting packetPlace = new CheckboxSetting("PacketPlace", true);
  private final CheckboxSetting enderChest = new CheckboxSetting("EnderChest", false);
  private final CheckboxSetting attack = new CheckboxSetting("Attack", true);
  private final CheckboxSetting antiSuicide = new CheckboxSetting("AntiSuicide", false);
  private final CheckboxSetting head = new CheckboxSetting("Head", true);
  private final CheckboxSetting feet = new CheckboxSetting("Feet", true);
  private final CheckboxSetting chest = new CheckboxSetting("Chest", true);
  private final CheckboxSetting extend = new CheckboxSetting("Extend", true);
  private final CheckboxSetting blockerExtend = new CheckboxSetting("BlockerExtend", true);
  private final CheckboxSetting center = new CheckboxSetting("Center", false);
  private final CheckboxSetting inventory = new CheckboxSetting("InventorySwap", true);
  private final CheckboxSetting usingPause = new CheckboxSetting("UsingPause", true);
  private final CheckboxSetting onlyMain = new CheckboxSetting("OnlyMain", false);
  private final CheckboxSetting allowNotOnGround = new CheckboxSetting("allowNotOnGround", true);
  private final CheckboxSetting moveDisable = new CheckboxSetting("MoveDisable", false);
  private final CheckboxSetting jumpDisable = new CheckboxSetting("JumpDisable", true);
  private final CheckboxSetting noBlockDisable = new CheckboxSetting("NoBlockDisable", false);
  private final CheckboxSetting flyDisable = new CheckboxSetting("FlyDisable", true);
  private Vec3 start;
  private Object owner, world;
  private long next;
  private boolean shouldCenter;
  private final Map<BlockPos, Long> mining = new ConcurrentHashMap<>();

  public SelfTrapHack() {
    super("SelfTrap");
    setCategory(Category.WORLD);
    for (Setting s :
        new Setting[] {
          range,
          delay,
          rotate,
          blocksPer,
          packetPlace,
          enderChest,
          attack,
          antiSuicide,
          head,
          feet,
          chest,
          extend,
          blockerExtend,
          center,
          inventory,
          usingPause,
          onlyMain,
          allowNotOnGround,
          moveDisable,
          jumpDisable,
          noBlockDisable,
          flyDisable
        }) addSetting(s);
  }

  @Override
  protected void onEnable() {
    owner = MC.player;
    world = MC.level;
    start = MC.player == null ? null : MC.player.position();
    next = 0;
    shouldCenter = true;
    mining.clear();
    EVENTS.add(UpdateListener.class, this);
    EVENTS.add(PacketInputListener.class, this);
  }

  @Override
  protected void onDisable() {
    EVENTS.remove(UpdateListener.class, this);
    EVENTS.remove(PacketInputListener.class, this);
    mining.clear();
  }

  @Override
  public void onReceivedPacket(PacketInputEvent event) {
    if (event.getPacket() instanceof ClientboundBlockDestructionPacket p) {
      if (p.getProgress() < 0 || p.getProgress() > 9) mining.remove(p.getPos());
      else mining.put(p.getPos().immutable(), System.nanoTime());
    }
  }

  @Override
  public void onUpdate() {
    if (owner != MC.player || world != MC.level || MC.player == null || !MC.player.isAlive()) {
      setEnabled(false);
      return;
    }
    if (!LeavesUtils.ready() || usingPause.isChecked() && LeavesUtils.using(onlyMain.isChecked()))
      return;
    if (start == null) start = MC.player.position();
    if (flyDisable.isChecked() && WURST.getHax().fireworkElytraFlyHack.isEnabled()
        || moveDisable.isChecked() && MC.player.position().distanceToSqr(start) > 1
        || jumpDisable.isChecked() && MC.player.input.jumping) {
      setEnabled(false);
      return;
    }
    if (!allowNotOnGround.isChecked() && !MC.player.onGround()) return;
    Item item = Items.OBSIDIAN;
    if (InventoryUtils.indexOf(item, inventory.isChecked() ? 36 : 9) < 0) {
      if (enderChest.isChecked()) item = Items.ENDER_CHEST;
      else {
        if (noBlockDisable.isChecked()) setEnabled(false);
        return;
      }
    }
    if (InventoryUtils.indexOf(item, inventory.isChecked() ? 36 : 9) < 0) {
      if (noBlockDisable.isChecked()) setEnabled(false);
      return;
    }
    BlockPos base = BlockPos.containing(MC.player.getX(), MC.player.getY() + 0.5, MC.player.getZ());
    if (center.isChecked() && shouldCenter && !MC.options.keyShift.isDown()) {
      Vec3 delta =
          new Vec3(base.getX() + 0.5 - MC.player.getX(), 0, base.getZ() + 0.5 - MC.player.getZ());
      if (Math.abs(delta.x) <= 0.2 && Math.abs(delta.z) <= 0.2) {
        MC.player.setDeltaMovement(0, MC.player.getDeltaMovement().y, 0);
        shouldCenter = false;
      } else {
        Vec3 velocity = delta.normalize().scale(Math.min(0.2873, delta.length()));
        MC.player.setDeltaMovement(velocity.x, MC.player.getDeltaMovement().y, velocity.z);
      }
    }
    if (System.nanoTime() < next) return;
    next = System.nanoTime() + delay.getValueI() * 1_000_000L;
    LinkedHashSet<BlockPos> positions = new LinkedHashSet<>();
    if (head.isChecked()) positions.add(base.above(2));
    if (feet.isChecked()) surround(positions, base);
    if (chest.isChecked()) surround(positions, base.above());
    mining.entrySet().removeIf(e -> System.nanoTime() - e.getValue() > 5_000_000_000L);
    if (blockerExtend.isChecked())
      for (BlockPos p : mining.keySet())
        if (p.distSqr(base) <= 9)
          for (Direction d : Direction.Plane.HORIZONTAL) positions.add(p.relative(d));
    int count = 0;
    Set<BlockPos> placed = new HashSet<>();
    for (BlockPos original : positions) {
      if (count >= blocksPer.getValueI()) break;
      BlockPos pos = original;
      if (pos.equals(WURST.getHax().packetMineHack.getTargetPos())
          && WURST.getHax().packetMineHack.isEnabled()) continue;
      if (!MC.level.getBlockState(pos).canBeReplaced()) continue;
      if (BlockPlacer.getBlockPlacingParams(pos) == null) {
        pos = null;
        for (Direction d : Direction.values()) {
          if (d == Direction.UP) continue;
          BlockPos helper = original.relative(d);
          if (MC.level.getBlockState(helper).canBeReplaced()
              && BlockPlacer.getBlockPlacingParams(helper) != null
              && clearEntities(helper)) {
            pos = helper;
            break;
          }
        }
        if (pos == null) continue;
      }
      if (placed.contains(pos) || !clearEntities(pos)) continue;
      if (LeavesUtils.place(
          pos,
          item,
          range.getValue(),
          inventory.isChecked(),
          rotate.isChecked(),
          packetPlace.isChecked())) {
        placed.add(pos);
        count++;
      }
    }
  }

  private void surround(Set<BlockPos> positions, BlockPos base) {
    for (Direction d : Direction.Plane.HORIZONTAL) {
      BlockPos pos = base.relative(d);
      positions.add(pos);
      positions.add(pos.below());
      if (extend.isChecked() && MC.player.getBoundingBox().intersects(new AABB(pos)))
        for (BlockPos p : List.of(pos, pos.below()))
          for (Direction d2 : Direction.Plane.HORIZONTAL) {
            BlockPos next = p.relative(d2);
            positions.add(next);
            if (MC.player.getBoundingBox().intersects(new AABB(next)))
              for (Direction d3 : Direction.Plane.HORIZONTAL) positions.add(next.relative(d3));
          }
    }
  }

  private boolean clearEntities(BlockPos pos) {
    for (Entity entity : MC.level.getEntities((Entity) null, new AABB(pos))) {
      if (!entity.isAlive()
          || entity instanceof net.minecraft.world.entity.item.ItemEntity
          || entity instanceof net.minecraft.world.entity.ExperienceOrb) continue;
      if (!(entity instanceof EndCrystal) || !attack.isChecked()) return false;
      if (antiSuicide.isChecked()
          && DamageUtils.calculateDamage(entity.position(), MC.player)
              >= MC.player.getHealth() + MC.player.getAbsorptionAmount()) return false;
      if (rotate.isChecked()) LeavesUtils.look(entity.position());
      try {
        MC.gameMode.attack(MC.player, entity);
      } finally {
        if (rotate.isChecked()) LeavesUtils.restoreLook();
      }
    }
    return true;
  }
}
