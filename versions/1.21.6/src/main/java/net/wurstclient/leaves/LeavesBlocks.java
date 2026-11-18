/* Shared block operations adapted from LeavesHack 13daf96.
 * SPDX-License-Identifier: AGPL-3.0-or-later */
package net.wurstclient.leaves;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.wurstclient.WurstClient;
import net.wurstclient.util.*;

public final class LeavesBlocks {
  private LeavesBlocks() {}

  public static Block getBlock(BlockPos pos) {
    return WurstClient.MC.level.getBlockState(pos).getBlock();
  }

  public static boolean canReplace(BlockPos pos) {
    return WurstClient.MC.level.getBlockState(pos).canBeReplaced();
  }

  public static boolean hasEntity(BlockPos pos, boolean ignored) {
    return !WurstClient.MC
        .level
        .getEntities((Entity) null, new AABB(pos), e -> e.isAlive() && e.blocksBuilding)
        .isEmpty();
  }

  public static List<BlockPos> getSphere(double radius) {
    var mc = WurstClient.MC;
    return BlockUtils.getAllInBoxStream(mc.player.blockPosition(), (int) Math.ceil(radius))
        .filter(pos -> mc.player.position().distanceToSqr(Vec3.atCenterOf(pos)) <= radius * radius)
        .map(BlockPos::immutable)
        .toList();
  }

  public static boolean canPlaceCrystal(BlockPos pos) {
    var mc = WurstClient.MC;
    Block base = getBlock(pos.below());
    return (base == Blocks.OBSIDIAN || base == Blocks.BEDROCK)
        && mc.level.isEmptyBlock(pos)
        && mc.level
            .getEntities(
                (Entity) null,
                new AABB(
                    pos.getX(),
                    pos.getY(),
                    pos.getZ(),
                    pos.getX() + 1,
                    pos.getY() + 2,
                    pos.getZ() + 1),
                e -> !e.isSpectator())
            .isEmpty();
  }

  public static boolean hasCrystal(BlockPos pos) {
    return !WurstClient.MC.level.getEntitiesOfClass(EndCrystal.class, new AABB(pos)).isEmpty();
  }

  public static boolean hasCrystalPlaceAccurate(BlockPos pos) {
    return WurstClient.MC.level.getEntitiesOfClass(EndCrystal.class, new AABB(pos)).stream()
        .anyMatch(e -> e.blockPosition().equals(pos));
  }

  /** Direction from the placed block toward its supporting neighbor. */
  public static Direction getPlaceSide(BlockPos pos, Object ignored) {
    var params = BlockPlacer.getBlockPlacingParams(pos);
    return params == null ? null : params.side().getOpposite();
  }

  public static Direction getClickSide(BlockPos pos) {
    return WurstClient.MC.player.getEyeY() > pos.getY() + 1 ? Direction.UP : Direction.DOWN;
  }

  public static boolean isGrimDirection(BlockPos support, Direction face) {
    var p = WurstClient.MC.player;
    AABB
        eyes =
            new AABB(p.getX(), p.getY() + 0.4, p.getZ(), p.getX(), p.getY() + 1.62, p.getZ())
                .inflate(0.0002),
        box = new AABB(support);
    if (box.intersects(eyes)) return true;
    return switch (face) {
      case NORTH -> eyes.minZ <= box.minZ;
      case SOUTH -> eyes.maxZ >= box.maxZ;
      case EAST -> eyes.maxX >= box.maxX;
      case WEST -> eyes.minX <= box.minX;
      case UP -> eyes.maxY >= box.maxY;
      case DOWN -> eyes.minY <= box.minY;
    };
  }

  public static void attackCrystal(BlockPos pos, boolean rotate, boolean ignored) {
    var mc = WurstClient.MC;
    for (EndCrystal crystal : mc.level.getEntitiesOfClass(EndCrystal.class, new AABB(pos))) {
      if (rotate) LeavesUtils.look(crystal.position());
      try {
        mc.gameMode.attack(mc.player, crystal);
        WurstClient.INSTANCE.getHax().globalSettingHack.swing(true);
      } finally {
        if (rotate) LeavesUtils.restoreLook();
      }
      return;
    }
  }
}
