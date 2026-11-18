/* NeoRaven ScaffoldModule candidates/faceFor adaptation. GPL-3.0. */
package net.wurstclient.neoraven;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

public final class RavenScaffoldSearch {
  private RavenScaffoldSearch() {}

  public static List<BlockPos> candidates(
      Vec3 player, Integer keptY, Predicate<BlockPos> replaceable) {
    var found = new LinkedHashSet<BlockPos>();
    for (int x = -5; x <= 5; x++)
      for (int z = -5; z <= 5; z++)
        for (int dy = -3; dy <= -1; dy++) {
          BlockPos support = BlockPos.containing(player.x + x, player.y + dy, player.z + z);
          if (keptY != null && support.getY() != keptY || replaceable.test(support)) continue;
          for (Direction side : Direction.values()) {
            BlockPos target = support.relative(side);
            if (keptY != null && target.getY() != keptY) continue;
            if (target.getY() >= player.y || !replaceable.test(target)) continue;
            found.add(target);
          }
        }
    var sorted = new ArrayList<>(found);
    sorted.sort(
        Comparator.comparingDouble(
            pos -> Vec3.atCenterOf(pos).distanceToSqr(player.add(0, -0.5, 0))));
    return sorted;
  }

  public static Direction faceFor(BlockPos target, Vec3 player, Predicate<BlockPos> support) {
    if (support.test(target.below())) return Direction.UP;
    double x = player.x - target.getX() - .5, z = player.z - target.getZ() - .5;
    Direction towards =
        Math.abs(x) > Math.abs(z)
            ? x > 0 ? Direction.EAST : Direction.WEST
            : z > 0 ? Direction.SOUTH : Direction.NORTH;
    if (support.test(target.relative(towards))) return towards.getOpposite();
    for (Direction side : Direction.Plane.HORIZONTAL)
      if (side != towards && support.test(target.relative(side))) return side.getOpposite();
    return null;
  }
}
