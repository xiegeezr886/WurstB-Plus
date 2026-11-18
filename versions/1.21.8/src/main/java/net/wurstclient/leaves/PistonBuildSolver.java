/* Bounded, resumable form of LeavesHack PushCrystal's breadth-first build solver.
 * SPDX-License-Identifier: AGPL-3.0-or-later */
package net.wurstclient.leaves;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public final class PistonBuildSolver {
  public enum BlockKind {
    PISTON,
    REDSTONE
  }

  public record Placement(BlockPos pos, BlockKind kind, Direction facing) {
    public Placement {
      pos = pos.immutable();
    }
  }

  public interface WorldView {
    boolean pistonAt(BlockPos pos, Direction facing);

    boolean redstoneAt(BlockPos pos);

    boolean replaceable(BlockPos pos);

    boolean canPlace(BlockPos pos, Set<BlockPos> planned);
  }

  private record State(Map<BlockPos, BlockKind> blocks, List<Placement> steps) {}

  private final WorldView world;
  private final BlockPos crystal;
  private final Direction facing;
  private final List<BlockPos> pistons, redstone;
  private final int maxBlocks;
  private final ArrayDeque<State> queue = new ArrayDeque<>();
  private final Set<Map<BlockPos, BlockKind>> visited = new HashSet<>();
  private List<Placement> result;
  private boolean done;

  public PistonBuildSolver(
      WorldView world, BlockPos crystal, Direction facing, List<BlockPos> pistons, int maxBlocks) {
    this.world = world;
    this.crystal = crystal.immutable();
    this.facing = facing;
    this.pistons = List.copyOf(pistons);
    this.maxBlocks = maxBlocks;
    LinkedHashSet<BlockPos> candidates = new LinkedHashSet<>();
    for (BlockPos piston : pistons)
      for (Direction side : Direction.values()) {
        if (side == facing) continue;
        BlockPos pos = piston.relative(side);
        if (!pos.equals(crystal)
            && !pistons.contains(pos)
            && (world.redstoneAt(pos) || world.replaceable(pos))) candidates.add(pos);
      }
    redstone = List.copyOf(candidates);
    queue.add(new State(Map.of(), List.of()));
    visited.add(Map.of());
  }

  /** Consumes at most budget states. A null result while !done() means resume next tick. */
  public List<Placement> step(int budget) {
    while (!done && !queue.isEmpty() && budget-- > 0) {
      State state = queue.removeFirst();
      if (complete(state.blocks)) {
        result = state.steps;
        done = true;
        queue.clear();
        return result;
      }
      if (state.steps.size() >= maxBlocks) continue;
      for (BlockPos pos : pistons)
        if (!state.blocks.containsKey(pos)
            && !world.pistonAt(pos, facing)
            && world.canPlace(pos, state.blocks.keySet())) add(state, pos, BlockKind.PISTON);
      for (BlockPos pos : redstone)
        if (!state.blocks.containsKey(pos)
            && !world.redstoneAt(pos)
            && world.canPlace(pos, state.blocks.keySet())) add(state, pos, BlockKind.REDSTONE);
      // A malicious/dense world cannot grow an unbounded search on the render thread.
      if (visited.size() > 100000) {
        done = true;
        queue.clear();
        return null;
      }
    }
    if (queue.isEmpty()) done = true;
    return result;
  }

  public boolean done() {
    return done;
  }

  private void add(State state, BlockPos pos, BlockKind kind) {
    Map<BlockPos, BlockKind> blocks = new HashMap<>(state.blocks);
    blocks.put(pos, kind);
    Map<BlockPos, BlockKind> frozen = Map.copyOf(blocks);
    if (!visited.add(frozen)) return;
    List<Placement> steps = new ArrayList<>(state.steps);
    steps.add(new Placement(pos, kind, kind == BlockKind.PISTON ? facing : null));
    queue.addLast(new State(frozen, List.copyOf(steps)));
  }

  private boolean complete(Map<BlockPos, BlockKind> blocks) {
    for (BlockPos piston : pistons) {
      if (!world.pistonAt(piston, facing) && blocks.get(piston) != BlockKind.PISTON) continue;
      for (Direction side : Direction.values()) {
        BlockPos power = piston.relative(side);
        if (!power.equals(crystal)
            && (world.redstoneAt(power) || blocks.get(power) == BlockKind.REDSTONE)) return true;
      }
    }
    return false;
  }
}
