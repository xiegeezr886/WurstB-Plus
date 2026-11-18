package net.wurstclient.leaves;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.wurstclient.leaves.PistonBuildSolver.*;
import org.junit.jupiter.api.Test;

final class PistonBuildSolverTest {
  private static final BlockPos CRYSTAL = new BlockPos(0, 1, 0),
      PISTON = new BlockPos(1, 1, 0),
      POWER = new BlockPos(1, 1, 1),
      BRIDGE = new BlockPos(1, 0, 0);

  private static final class View implements WorldView {
    boolean existing;
    final Set<BlockPos> allowed = new HashSet<>();

    public boolean pistonAt(BlockPos pos, Direction facing) {
      return existing && pos.equals(PISTON) && facing == Direction.WEST;
    }

    public boolean redstoneAt(BlockPos pos) {
      return false;
    }

    public boolean replaceable(BlockPos pos) {
      return allowed.contains(pos);
    }

    public boolean canPlace(BlockPos pos, Set<BlockPos> planned) {
      if (!allowed.contains(pos)) return false;
      if (pos.equals(BRIDGE)) return true;
      if (pos.equals(PISTON)) return planned.contains(BRIDGE);
      return pos.equals(POWER) && (existing || planned.contains(PISTON));
    }
  }

  private List<Placement> solve(PistonBuildSolver solver) {
    for (int i = 0; i < 500 && !solver.done(); i++) solver.step(1);
    assertTrue(solver.done());
    return solver.step(1);
  }

  @Test
  void findsSupportThenPistonInDependencyOrder() {
    View view = new View();
    view.allowed.addAll(List.of(BRIDGE, PISTON, POWER));
    var result = solve(new PistonBuildSolver(view, CRYSTAL, Direction.WEST, List.of(PISTON), 3));
    assertNotNull(result);
    assertEquals(BRIDGE, result.get(0).pos());
    assertEquals(PISTON, result.get(1).pos());
    assertEquals(BlockKind.PISTON, result.get(1).kind());
    // The redstone support itself also powers the piston, so the shortest plan has two blocks.
    assertEquals(2, result.size());
  }

  @Test
  void honorsBlockBudgetAndReusesExistingFacingPiston() {
    View view = new View();
    view.allowed.addAll(List.of(BRIDGE, PISTON, POWER));
    assertNull(solve(new PistonBuildSolver(view, CRYSTAL, Direction.WEST, List.of(PISTON), 1)));
    view.existing = true;
    var result = solve(new PistonBuildSolver(view, CRYSTAL, Direction.WEST, List.of(PISTON), 1));
    assertEquals(1, result.size());
    assertEquals(BlockKind.REDSTONE, result.get(0).kind());
  }

  @Test
  void excludesTheCrystalCellFromPowerCandidates() {
    View view = new View();
    view.existing = true;
    view.allowed.add(CRYSTAL);
    assertNull(solve(new PistonBuildSolver(view, CRYSTAL, Direction.WEST, List.of(PISTON), 3)));
  }
}
