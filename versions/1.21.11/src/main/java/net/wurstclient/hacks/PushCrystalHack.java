/* PushCrystal build solver adapted from LeavesHack 13daf96.
 * SPDX-License-Identifier: AGPL-3.0-or-later */
package net.wurstclient.hacks;

import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.Color;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.wurstclient.Category;
import net.wurstclient.events.*;
import net.wurstclient.hack.Hack;
import net.wurstclient.leaves.*;
import net.wurstclient.leaves.LeavesColorSetting;
import net.wurstclient.leaves.PistonBuildSolver.BlockKind;
import net.wurstclient.leaves.PistonBuildSolver.Placement;
import net.wurstclient.settings.*;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.*;

public final class PushCrystalHack extends Hack implements UpdateListener, RenderListener {
  private final SliderSetting targetRange =
      new SliderSetting("TargetRange", 6.0, 1.0, 16.0, 0.01, ValueDisplay.DECIMAL);
  private final SliderSetting searchRange =
      new SliderSetting("SearchRange", 5.0, 1.0, 8.0, 0.01, ValueDisplay.DECIMAL);
  private final SliderSetting placeRange =
      new SliderSetting("PlaceRange", 4.5, 1.0, 6.0, 0.01, ValueDisplay.DECIMAL);
  private final SliderSetting minDamage =
      new SliderSetting("MinDamage", 4.0, 0.0, 36.0, 0.01, ValueDisplay.DECIMAL);
  private final SliderSetting maxSelfDamage =
      new SliderSetting("MaxSelfDamage", 12.0, 0.0, 36.0, 0.01, ValueDisplay.DECIMAL);
  private final CheckboxSetting noSuicide = new CheckboxSetting("NoSuicide", true);
  private final SliderSetting actionDelay =
      new SliderSetting("ActionDelay", 3, 0, 10, 1, ValueDisplay.INTEGER);
  private final SliderSetting breakDelay =
      new SliderSetting("BreakDelay", 200, 0, 500, 1, ValueDisplay.INTEGER);
  private final CheckboxSetting preferCrystal = new CheckboxSetting("PreferCrystal", true);
  private final CheckboxSetting pauseWhileUsing = new CheckboxSetting("PauseWhileUsing", true);
  private final CheckboxSetting rotate = new CheckboxSetting("Rotate", true);
  private final CheckboxSetting packetPlace = new CheckboxSetting("PacketPlace", true);
  private final CheckboxSetting mine = new CheckboxSetting("Mine", true);
  private final CheckboxSetting yawDeceive = new CheckboxSetting("YawDeceive", true);
  private final SliderSetting maxBuildBlocks =
      new SliderSetting("MaxBuildBlocks", 5, 1, 9, 1, ValueDisplay.INTEGER);
  private final CheckboxSetting inventorySwap = new CheckboxSetting("InventorySwap", true);
  private final CheckboxSetting render = new CheckboxSetting("Render", true);
  private final EnumSetting<PrinterHack.Shape> shapeMode =
      new EnumSetting<>("ShapeMode", PrinterHack.Shape.values(), PrinterHack.Shape.BOTH);
  private final LeavesColorSetting crystalColor =
      new LeavesColorSetting("CrystalColor", new Color(255, 0, 0, 80));
  private final LeavesColorSetting pistonColor =
      new LeavesColorSetting("PistonColor", new Color(255, 255, 255, 80));
  private final LeavesColorSetting redstoneColor =
      new LeavesColorSetting("RedstoneColor", new Color(255, 100, 0, 80));

  private Player target;
  private Player lastTarget;
  private BlockPos lastBestPos;
  private CrystalPushPlan currentPlan;
  private BlockPos pendingBreakPos;
  private int pendingBreakTicks;
  private BlockPos lastPiston;
  private Direction lastPistonDirection;
  private int actionCooldown;
  private long lastBreak;
  private Object world;
  private List<FinalCandidate> searchCandidates;
  private int candidateIndex, directionIndex;
  private PistonBuildSolver solver;
  private Vec3 searchOrigin;

  public PushCrystalHack() {
    super("PushCrystal");
    setCategory(Category.COMBAT);
    for (Setting s :
        new Setting[] {
          targetRange,
          searchRange,
          placeRange,
          minDamage,
          maxSelfDamage,
          noSuicide,
          actionDelay,
          breakDelay,
          preferCrystal,
          pauseWhileUsing,
          rotate,
          packetPlace,
          mine,
          yawDeceive,
          maxBuildBlocks,
          inventorySwap,
          render,
          shapeMode,
          crystalColor,
          pistonColor,
          redstoneColor
        }) addSetting(s);
  }

  @Override
  protected void onEnable() {
    EVENTS.add(UpdateListener.class, this);
    EVENTS.add(RenderListener.class, this);
    reset();
  }

  private void reset() {
    world = MC.level;
    searchCandidates = null;
    solver = null;
    target = null;
    lastTarget = null;
    lastBestPos = null;
    currentPlan = null;
    pendingBreakPos = null;
    pendingBreakTicks = 0;
    lastPiston = null;
    lastPistonDirection = null;
    actionCooldown = 0;
    lastBreak = 0;
  }

  @Override
  protected void onDisable() {
    EVENTS.remove(UpdateListener.class, this);
    EVENTS.remove(RenderListener.class, this);
    target = null;
    lastTarget = null;
    lastBestPos = null;
    currentPlan = null;
    pendingBreakPos = null;
    lastPiston = null;
    lastPistonDirection = null;
  }

  @Override
  public String getRenderName() {
    return getName() + (target == null ? "" : " [" + target.getName().getString() + "]");
  }

  @Override
  public void onUpdate() {
    if (world != MC.level) reset();
    if (!LeavesUtils.ready()) return;
    if (MC.player == null || MC.level == null) return;
    if (preferCrystal.isChecked() && WURST.getHax().crystalAuraPlusHack.isBusy()) return;
    if (mine.isChecked() && lastPiston != null && lastPistonDirection != null) {
      if (LeavesBlocks.getBlock(lastPiston) instanceof PistonBaseBlock
          && LeavesBlocks.getBlock(lastPiston.relative(lastPistonDirection))
              instanceof PistonHeadBlock) {
        Direction side = LeavesBlocks.getClickSide(lastPiston);
        if (side != null) {
          MC.gameMode.startDestroyBlock(lastPiston, side);
          lastPiston = null;
          lastPistonDirection = null;
        }
      }
    }
    target = LeavesUtils.enemy(targetRange.getValue());
    if (target == null) {
      lastTarget = null;
      lastBestPos = null;
      currentPlan = null;
      pendingBreakPos = null;
      return;
    }
    if (target != lastTarget) {
      lastTarget = target;
      searchCandidates = null;
      solver = null;
      lastBestPos = null;
    }
    if (pendingBreakPos != null) {
      pendingBreakTicks++;
      if (LeavesBlocks.hasCrystalPlaceAccurate(pendingBreakPos)) {
        LeavesBlocks.attackCrystal(pendingBreakPos, rotate.isChecked(), false);
        pendingBreakPos = null;
        pendingBreakTicks = 0;
        currentPlan = null;
        actionCooldown = actionDelay.getValueI();
      } else if (pendingBreakTicks > 3) {
        pendingBreakPos = null;
        pendingBreakTicks = 0;
      }
      return;
    }

    if (actionCooldown > 0) {
      actionCooldown--;
      return;
    }
    if (pauseWhileUsing.isChecked() && (MC.player.isUsingItem() || MC.options.keyUse.isDown()))
      return;
    int crystalSlot = findItem(Items.END_CRYSTAL);
    int pistonSlot = findClass(PistonBaseBlock.class);
    int redstoneSlot = findItem(Items.REDSTONE_BLOCK);
    if (crystalSlot == -1 || pistonSlot == -1 || redstoneSlot == -1) return;
    CrystalPushPlan plan = findPlan(target);
    currentPlan = plan;
    if (plan == null) return;
    if (breakBlockingCrystal(plan.pos2())) return;
    if (execute(plan, crystalSlot, pistonSlot, redstoneSlot)) {
      pendingBreakPos = plan.pos1();
      pendingBreakTicks = 0;
      actionCooldown = actionDelay.getValueI();
    }
  }

  @Override
  public void onRender(PoseStack matrices, float partialTicks) {
    if (!render.isChecked() || currentPlan == null) return;
    draw(matrices, currentPlan.pos1(), crystalColor.getColorI());
    for (BlockPos pos : currentPlan.pistonPositions()) draw(matrices, pos, pistonColor.getColorI());
    for (BlockPos pos : currentPlan.redstonePositions())
      draw(matrices, pos, redstoneColor.getColorI());
  }

  private void draw(PoseStack matrices, BlockPos pos, int color) {
    if (shapeMode.getSelected() != PrinterHack.Shape.LINES)
      RenderUtils.drawSolidBox(matrices, new AABB(pos), color, false);
    if (shapeMode.getSelected() != PrinterHack.Shape.SIDES)
      RenderUtils.drawOutlinedBox(matrices, new AABB(pos), color, false);
  }

  private CrystalPushPlan findPlan(Player target) {
    if (searchCandidates == null || searchOrigin.distanceToSqr(target.position()) > 0.25) {
      searchCandidates = findFinalCandidates(target);
      searchOrigin = target.position();
      candidateIndex = directionIndex = 0;
      solver = null;
      if (lastBestPos != null && !searchCandidates.isEmpty()) {
        FinalCandidate best = searchCandidates.get(0);
        for (int i = 0; i < searchCandidates.size(); i++)
          if (searchCandidates.get(i).pos().equals(lastBestPos)
              && searchCandidates.get(i).targetDamage() >= best.targetDamage() * 0.95) {
            FinalCandidate cached = searchCandidates.remove(i);
            searchCandidates.add(0, cached);
            break;
          }
      }
    }
    while (candidateIndex < searchCandidates.size()) {
      FinalCandidate candidate = searchCandidates.get(candidateIndex);
      if (directionIndex >= HORIZONTAL_DIRECTIONS.length) {
        candidateIndex++;
        directionIndex = 0;
        solver = null;
        continue;
      }
      Direction direction = HORIZONTAL_DIRECTIONS[directionIndex], facing = direction.getOpposite();
      BlockPos source = candidate.pos().relative(direction);
      if (!yawDeceive.isChecked() && direction != MC.player.getDirection()
          || !LeavesBlocks.canPlaceCrystal(source) && !LeavesBlocks.hasCrystal(source)) {
        directionIndex++;
        solver = null;
        continue;
      }
      if (solver == null)
        solver =
            new PistonBuildSolver(
                new PistonBuildSolver.WorldView() {
                  public boolean pistonAt(BlockPos pos, Direction face) {
                    var state = MC.level.getBlockState(pos);
                    return state.getBlock() instanceof PistonBaseBlock
                        && state.getValue(PistonBaseBlock.FACING) == face;
                  }

                  public boolean redstoneAt(BlockPos pos) {
                    return MC.level.getBlockState(pos).is(Blocks.REDSTONE_BLOCK);
                  }

                  public boolean replaceable(BlockPos pos) {
                    return LeavesBlocks.canReplace(pos);
                  }

                  public boolean canPlace(BlockPos pos, Set<BlockPos> planned) {
                    return canPlaceVirtual(pos, planned);
                  }
                },
                source,
                facing,
                getPistonCandidates(source, facing),
                maxBuildBlocks.getValueI());
      List<Placement> steps = solver.step(2048);
      if (steps != null) {
        List<BlockPos> pistons = new ArrayList<>(), redstone = new ArrayList<>();
        for (Placement step : steps)
          (step.kind() == BlockKind.PISTON ? pistons : redstone).add(step.pos());
        CrystalPushPlan plan =
            new CrystalPushPlan(
                candidate.pos(),
                source,
                facing,
                pistons,
                redstone,
                steps,
                candidate.targetDamage(),
                candidate.selfDamage());
        directionIndex++;
        solver = null;
        if (isWithinPlaceRange(plan)) {
          lastBestPos = candidate.pos();
          searchCandidates = null;
          return plan;
        }
      } else if (solver.done()) {
        solver = null;
        directionIndex++;
      }
      // Continue the same breadth-first search next tick without blocking input/rendering.
      return null;
    }
    searchCandidates = null;
    lastBestPos = null;
    return null;
  }

  private List<FinalCandidate> findFinalCandidates(Player target) {
    List<FinalCandidate> candidates = new ArrayList<>();
    for (BlockPos candidate : LeavesBlocks.getSphere(searchRange.getValue())) {
      boolean hasPlaceablePos2 = false;
      for (Direction direction : HORIZONTAL_DIRECTIONS) {
        BlockPos pos2 = candidate.relative(direction);
        if (LeavesBlocks.canPlaceCrystal(pos2) || LeavesBlocks.hasCrystal(pos2)) {
          hasPlaceablePos2 = true;
          break;
        }
      }
      if (!hasPlaceablePos2) continue;

      Vec3 explosion = crystalVector(candidate);
      float damage = LeavesDamage.damage(explosion, target, 6, null);
      float selfDamage = LeavesDamage.damage(explosion, MC.player, 6, null);
      if (!Float.isFinite(damage) || !Float.isFinite(selfDamage)) continue;
      if (damage < minDamage.getValue() || selfDamage > maxSelfDamage.getValue()) continue;
      if (noSuicide.isChecked()
          && selfDamage > (MC.player.getHealth() + MC.player.getAbsorptionAmount())) continue;

      candidates.add(
          new FinalCandidate(
              candidate.immutable(),
              damage,
              selfDamage,
              MC.player.getEyePosition().distanceToSqr(explosion)));
    }
    candidates.sort(
        (a, b) -> {
          int damageOrder = Float.compare(b.targetDamage(), a.targetDamage());
          if (damageOrder != 0) return damageOrder;
          return Double.compare(a.distance(), b.distance());
        });
    return candidates;
  }

  private boolean breakBlockingCrystal(BlockPos pos) {
    if (LeavesBlocks.canPlaceCrystal(pos)) return false;
    if (!LeavesBlocks.hasCrystal(pos)) return true;
    if (System.currentTimeMillis() - lastBreak < breakDelay.getValue()) return true;
    LeavesBlocks.attackCrystal(pos, rotate.isChecked(), false);
    lastBreak = System.currentTimeMillis();
    actionCooldown = actionDelay.getValueI();
    return true;
  }

  private List<BlockPos> getPistonCandidates(BlockPos pos2, Direction face1) {
    BlockPos plane = pos2.relative(face1.getOpposite());
    Direction horizontal = face1.getClockWise();
    List<BlockPos> candidates = new ArrayList<>(6);
    for (int y = 0; y <= 1; y++) {
      for (int lateral = -1; lateral <= 1; lateral++) {
        candidates.add(plane.above(y).relative(horizontal, lateral));
      }
    }
    return candidates;
  }

  private boolean execute(CrystalPushPlan plan, int crystalSlot, int pistonSlot, int redstoneSlot) {
    Set<BlockPos> planned = new HashSet<>();
    lastPiston = null;
    lastPistonDirection = null;
    for (Placement placement : plan.placements()) {
      BlockState state = MC.level.getBlockState(placement.pos());
      if (isExpectedBlock(state, placement)) {
        // 世界中已有同朝向活塞或红石块时，不重复放置，但仍加入虚拟状态。
        planned.add(placement.pos());
        if (placement.kind() == BlockKind.PISTON) {
          lastPiston = placement.pos();
          lastPistonDirection = plan.face1();
        }
        continue;
      }
      if (!canPlaceVirtual(placement.pos(), planned)
          || !place(
              placement,
              planned,
              placement.kind() == BlockKind.PISTON ? pistonSlot : redstoneSlot)) {
        return false;
      }
      planned.add(placement.pos());
      if (placement.kind() == BlockKind.PISTON) {
        lastPiston = placement.pos();
        lastPistonDirection = plan.face1();
      }
    }
    if (lastPiston == null) {
      for (BlockPos candidate : getPistonCandidates(plan.pos2(), plan.face1())) {
        if (LeavesBlocks.getBlock(candidate) instanceof PistonBaseBlock) {
          lastPiston = candidate;
          lastPistonDirection = plan.face1();
          break;
        }
      }
    }
    if (!LeavesBlocks.canPlaceCrystal(plan.pos2())) return false;
    if (!placeCrystal(plan.pos2(), crystalSlot)) return false;
    return true;
  }

  private boolean place(Placement placement, Set<BlockPos> planned, int slot) {
    Direction direction = getVirtualPlaceSide(placement.pos(), planned);
    if (direction == null || slot < 0) return false;
    try (InventorySwap swap = InventorySwap.acquire(slot)) {
      if (swap == null) return false;
      BlockPos neighbor = placement.pos().relative(direction);
      Vec3 point =
          Vec3.atCenterOf(neighbor)
              .add(Vec3.atLowerCornerOf(direction.getOpposite().getUnitVec3i()).scale(0.5));
      if (rotate.isChecked()) {
        if (placement.kind() == BlockKind.PISTON && yawDeceive.isChecked()) {
          Direction facing = placement.facing().getOpposite();
          LeavesUtils.rotate(facing.toYRot(), 5);
        } else LeavesUtils.look(point);
      }
      boolean sneak = !MC.player.isShiftKeyDown();
      try {
        if (sneak)
          MC.player.connection.send(
              net.wurstclient.util.render.V152Input.sneak(MC.player, true));
        BlockHitResult hit = new BlockHitResult(point, direction.getOpposite(), neighbor, false);
        if (packetPlace.isChecked())
          IMC.getInteractionManager().sendPlayerInteractBlockPacket(InteractionHand.MAIN_HAND, hit);
        else if (!MC.gameMode.useItemOn(MC.player, InteractionHand.MAIN_HAND, hit).consumesAction())
          return false;
        WURST.getHax().globalSettingHack.swing(false);
        WURST.getHax().placeRenderHack.record(placement.pos(), false);
        return true;
      } finally {
        if (sneak)
          MC.player.connection.send(
              net.wurstclient.util.render.V152Input.sneak(MC.player, false));
        if (rotate.isChecked()) LeavesUtils.restoreLook();
      }
    }
  }

  private boolean placeCrystal(BlockPos pos, int slot) {
    try (InventorySwap swap = InventorySwap.acquire(slot)) {
      if (swap == null) return false;
      Vec3 point = Vec3.atCenterOf(pos.below()).add(0, 0.5, 0);
      if (rotate.isChecked()) LeavesUtils.look(point);
      try {
        IMC.getInteractionManager()
            .sendPlayerInteractBlockPacket(
                InteractionHand.MAIN_HAND,
                new BlockHitResult(point, Direction.UP, pos.below(), false));
        WURST.getHax().globalSettingHack.swing(false);
        return true;
      } finally {
        if (rotate.isChecked()) LeavesUtils.restoreLook();
      }
    }
  }

  private boolean isWithinPlaceRange(CrystalPushPlan plan) {
    if (MC.player.position().distanceToSqr(Vec3.atCenterOf(plan.pos2())) > placeRange.getValueSq())
      return false;
    for (Placement placement : plan.placements()) {
      if (MC.player.position().distanceToSqr(Vec3.atCenterOf(placement.pos()))
          > placeRange.getValueSq()) return false;
    }
    return true;
  }

  private boolean isExpectedBlock(BlockState state, Placement placement) {
    return placement.kind() == BlockKind.PISTON
            && state.getBlock() instanceof PistonBaseBlock
            && state.getValue(PistonBaseBlock.FACING) == placement.facing()
        || placement.kind() == BlockKind.REDSTONE && state.getBlock() == Blocks.REDSTONE_BLOCK;
  }

  private Vec3 crystalVector(BlockPos pos) {
    return new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
  }

  private int findItem(Item item) {
    return InventoryUtils.indexOf(item, inventorySwap.isChecked() ? 36 : 9);
  }

  private int findClass(Class<?> itemClass) {
    for (int i = 0; i < (inventorySwap.isChecked() ? 36 : 9); i++)
      if (MC.player.getInventory().getItem(i).getItem() instanceof BlockItem block
          && itemClass.isInstance(block.getBlock())) return i;
    return -1;
  }

  private boolean canPlaceVirtual(BlockPos pos, Set<BlockPos> planned) {
    if (MC.player.position().distanceToSqr(Vec3.atCenterOf(pos)) > placeRange.getValueSq()
        || planned.contains(pos)
        || !LeavesBlocks.canReplace(pos)
        || LeavesBlocks.hasEntity(pos, false)) return false;
    return getVirtualPlaceSide(pos, planned) != null;
  }

  private Direction getVirtualPlaceSide(BlockPos pos, Set<BlockPos> planned) {
    Direction realSide = LeavesBlocks.getPlaceSide(pos, null);
    if (realSide != null) return realSide;

    for (Direction side : Direction.values()) {
      BlockPos support = pos.relative(side);
      if (planned.contains(support) && isVirtualSupportDirection(support, side.getOpposite()))
        return side;
      if (isSolidSupport(support) && LeavesBlocks.isGrimDirection(support, side.getOpposite()))
        return side;
    }
    return null;
  }

  private boolean isVirtualSupportDirection(BlockPos support, Direction direction) {
    double minEyeY = MC.player.getY() + 0.4;
    double maxEyeY = MC.player.getY() + 1.62;
    AABB eyePositions =
        new AABB(
                MC.player.getX(),
                minEyeY,
                MC.player.getZ(),
                MC.player.getX(),
                maxEyeY,
                MC.player.getZ())
            .inflate(0.0002);
    AABB supportAABB = new AABB(support);

    boolean intersects =
        supportAABB.maxX - 1.0e-7 > eyePositions.minX
            && supportAABB.minX + 1.0e-7 < eyePositions.maxX
            && supportAABB.maxY - 1.0e-7 > eyePositions.minY
            && supportAABB.minY + 1.0e-7 < eyePositions.maxY
            && supportAABB.maxZ - 1.0e-7 > eyePositions.minZ
            && supportAABB.minZ + 1.0e-7 < eyePositions.maxZ;
    if (intersects) return true;

    return switch (direction) {
      case NORTH -> eyePositions.minZ <= supportAABB.minZ;
      case SOUTH -> eyePositions.maxZ >= supportAABB.maxZ;
      case EAST -> eyePositions.maxX >= supportAABB.maxX;
      case WEST -> eyePositions.minX <= supportAABB.minX;
      case UP -> eyePositions.maxY >= supportAABB.maxY;
      case DOWN -> eyePositions.minY <= supportAABB.minY;
    };
  }

  private boolean isSolidSupport(BlockPos pos) {
    BlockState state = MC.level.getBlockState(pos);
    return state.isSolidRender() || state.getBlock() == Blocks.REDSTONE_BLOCK;
  }

  private static final Direction[] HORIZONTAL_DIRECTIONS = {
    Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
  };

  private record FinalCandidate(
      BlockPos pos, float targetDamage, float selfDamage, double distance) {}

  public record CrystalPushPlan(
      BlockPos pos1,
      BlockPos pos2,
      Direction face1,
      List<BlockPos> pistonPositions,
      List<BlockPos> redstonePositions,
      List<Placement> placements,
      float targetDamage,
      float selfDamage) {
    public CrystalPushPlan {
      pos1 = pos1.immutable();
      pos2 = pos2.immutable();
      pistonPositions = List.copyOf(pistonPositions);
      redstonePositions = List.copyOf(redstonePositions);
      placements = List.copyOf(placements);
    }
  }
}
