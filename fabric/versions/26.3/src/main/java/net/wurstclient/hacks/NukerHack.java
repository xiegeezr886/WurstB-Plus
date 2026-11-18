/* NukerPlus adapted from LeavesHack 13daf96.
 * SPDX-License-Identifier: AGPL-3.0-or-later */
package net.wurstclient.hacks;

import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.Color;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.events.*;
import net.wurstclient.hack.Hack;
import net.wurstclient.leaves.LeavesColorSetting;
import net.wurstclient.leaves.LeavesUtils;
import net.wurstclient.mixin.MultiPlayerGameModeAccessor;
import net.wurstclient.settings.*;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.*;

public final class NukerHack extends Hack implements UpdateListener, RenderListener {
  private final EnumSetting<Shape> shape = new EnumSetting<>("Shape", Shape.values(), Shape.Sphere);
  private final EnumSetting<Mode> mode = new EnumSetting<>("Mode", Mode.values(), Mode.Flatten);
  private final SliderSetting range = number("Range", 4, 0, 16);
  private final SliderSetting up = number("Up", 1, 0, 16),
      down = number("Down", 1, 0, 16),
      left = number("Left", 1, 0, 16),
      right = number("Right", 1, 0, 16),
      forward = number("Forward", 1, 0, 16),
      back = number("Back", 1, 0, 16);
  private final SliderSetting delay = number("Delay", 0, 0, 100),
      clickDelay = number("ClickDelay", 8, 0, 100),
      maxBlocks = number("Max blocks per tick", 1, 1, 6);
  private final EnumSetting<SortMode> sort =
      new EnumSetting<>("Sort mode", SortMode.values(), SortMode.Closest);
  private final CheckboxSetting swing = new CheckboxSetting("Swing hand", true),
      packet = new CheckboxSetting("Packet mine", true),
      rotate = new CheckboxSetting("Rotate", true);
  private final EnumSetting<ListMode> listMode =
      new EnumSetting<>("List mode", ListMode.values(), ListMode.Blacklist);
  private final BlockListSetting
      blacklist = new BlockListSetting("Blacklist", "Blocks to exclude."),
      whitelist = new BlockListSetting("Whitelist", "Blocks allowed in whitelist mode.");
  private final CheckboxSetting bounding = new CheckboxSetting("Bounding box", true),
      broken = new CheckboxSetting("Broken blocks", true);
  private final EnumSetting<PrinterHack.Shape>
      boxShape =
          new EnumSetting<>("Nuke box mode", PrinterHack.Shape.values(), PrinterHack.Shape.BOTH),
      blockShape =
          new EnumSetting<>("Nuke block mode", PrinterHack.Shape.values(), PrinterHack.Shape.BOTH);
  private final LeavesColorSetting
      boxSide = new LeavesColorSetting("Box side color", "", new Color(255, 255, 255, 0)),
      boxLine = new LeavesColorSetting("Box line color", "", new Color(255, 255, 255, 0)),
      side = new LeavesColorSetting("Side color", "", new Color(255, 255, 255, 80)),
      line = new LeavesColorSetting("Line color", "", Color.WHITE);
  private final Map<BlockPos, Integer> recent = new LinkedHashMap<>();
  private BlockPos last;
  private AABB bounds;
  private int timer, idle;
  private long clicked;
  private net.minecraft.client.multiplayer.ClientLevel world;

  public NukerHack() {
    super("Nuker");
    setCategory(Category.WORLD);
    for (Setting s :
        new Setting[] {
          shape,
          mode,
          range,
          up,
          down,
          left,
          right,
          forward,
          back,
          delay,
          clickDelay,
          maxBlocks,
          sort,
          swing,
          packet,
          rotate,
          listMode,
          blacklist,
          whitelist,
          bounding,
          boxShape,
          boxSide,
          boxLine,
          broken,
          blockShape,
          side,
          line
        }) addSetting(s);
  }

  private static SliderSetting number(String name, int value, int min, int max) {
    return new SliderSetting(name, value, min, max, 1, ValueDisplay.INTEGER);
  }

  @Override
  protected void onEnable() {
    clear();
    EVENTS.add(UpdateListener.class, this);
    EVENTS.add(RenderListener.class, this);
  }

  @Override
  protected void onDisable() {
    EVENTS.remove(UpdateListener.class, this);
    EVENTS.remove(RenderListener.class, this);
    if (MC.gameMode != null && last != null && !packet.isChecked()) MC.gameMode.stopDestroyBlock();
    clear();
  }

  private void clear() {
    last = null;
    bounds = null;
    timer = idle = 0;
    clicked = 0;
    recent.clear();
    world = MC.level;
  }

  @Override
  public void onUpdate() {
    if (world != MC.level) clear();
    recent.replaceAll((pos, ticks) -> ticks - 1);
    recent.values().removeIf(ticks -> ticks <= 0);
    if (!LeavesUtils.ready()) return;
    if (packet.isChecked() && !WURST.getHax().packetMineHack.isEnabled()) {
      ChatUtils.error("Nuker requires PacketMine when Packet mine is enabled.");
      setEnabled(false);
      return;
    }
    ((MultiPlayerGameModeAccessor) MC.gameMode).setDestroyDelay(0);
    if (timer > 0) {
      timer--;
      return;
    }
    BlockPos center = MC.player.blockPosition();
    Direction facing = MC.player.getDirection(), sideways = facing.getClockWise();
    BlockPos a =
        center
            .relative(facing, -back.getValueI())
            .relative(sideways, -left.getValueI())
            .below(down.getValueI());
    BlockPos b =
        center
            .relative(facing, forward.getValueI())
            .relative(sideways, right.getValueI())
            .above(up.getValueI());
    int radius = range.getValueCeil();
    if (shape.getSelected() != Shape.Cube) {
      a = center.offset(-radius, -radius, -radius);
      b = center.offset(radius, radius, radius);
    }
    bounds =
        new AABB(
            Math.min(a.getX(), b.getX()),
            Math.min(a.getY(), b.getY()),
            Math.min(a.getZ(), b.getZ()),
            Math.max(a.getX(), b.getX()) + 1,
            Math.max(a.getY(), b.getY()) + 1,
            Math.max(a.getZ(), b.getZ()) + 1);
    ArrayList<BlockPos> blocks = new ArrayList<>();
    for (BlockPos mutable : BlockPos.betweenClosed(a, b)) {
      BlockPos pos = mutable.immutable();
      if (!MC.level.hasChunkAt(pos) || !MC.level.isInWorldBounds(pos)) continue;
      if (shape.getSelected() == Shape.Sphere
          && MC.player.position().distanceToSqr(Vec3.atCenterOf(pos)) > range.getValueSq())
        continue;
      if (shape.getSelected() == Shape.UniformCube && chebyshev(center, pos) >= range.getValue())
        continue;
      var state = MC.level.getBlockState(pos);
      float hardness = state.getDestroySpeed(MC.level, pos);
      if (state.isAir() || state.getFluidState().isSource() || hardness < 0) continue;
      if (mode.getSelected() == Mode.Flatten && pos.getY() < center.getY()) continue;
      if (mode.getSelected() == Mode.Smash && hardness != 0) continue;
      if (listMode.getSelected() == ListMode.Whitelist
          ? !whitelist.contains(state.getBlock())
          : blacklist.contains(state.getBlock())) continue;
      blocks.add(pos);
    }
    Comparator<BlockPos> byDistance =
        Comparator.comparingDouble(pos -> MC.player.position().distanceToSqr(Vec3.atCenterOf(pos)));
    switch (sort.getSelected()) {
      case Closest -> blocks.sort(byDistance);
      case Furthest -> blocks.sort(byDistance.reversed());
      case TopDown -> blocks.sort(Comparator.comparingInt((BlockPos pos) -> pos.getY()).reversed());
      default -> {}
    }
    if (blocks.isEmpty()) {
      if (++idle >= delay.getValueI()) last = null;
      return;
    }
    idle = 0;
    if (last != null && !last.equals(blocks.get(0))) {
      last = blocks.get(0);
      timer = delay.getValueI();
      if (timer > 0) return;
    }
    int count = 0;
    for (BlockPos pos : blocks) {
      if (count++ >= maxBlocks.getValueI()) break;
      if (packet.isChecked() && System.currentTimeMillis() - clicked < clickDelay.getValueI() * 50L)
        break;
      if (rotate.isChecked()) LeavesUtils.look(Vec3.atCenterOf(pos));
      try {
        if (packet.isChecked()) {
          WURST.getHax().packetMineHack.mine(pos);
          clicked = System.currentTimeMillis();
        } else MC.gameMode.continueDestroyBlock(pos, Direction.UP);
        if (swing.isChecked()) MC.player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
      } finally {
        if (rotate.isChecked()) LeavesUtils.restoreLook();
      }
      last = pos;
      recent.put(pos, 8);
      if (!packet.isChecked()
          && MC.level.getBlockState(pos).getDestroyProgress(MC.player, MC.level, pos) < 1) break;
    }
  }

  public static int chebyshev(BlockPos a, BlockPos b) {
    return Math.max(
        Math.max(Math.abs(a.getX() - b.getX()), Math.abs(a.getY() - b.getY())),
        Math.abs(a.getZ() - b.getZ()));
  }

  @Override
  public void onRender(PoseStack matrices, float partialTicks) {
    if (bounding.isChecked()
        && bounds != null
        && shape.getSelected() != Shape.Sphere
        && mode.getSelected() != Mode.Smash)
      draw(matrices, bounds, boxShape.getSelected(), boxSide.getColorI(), boxLine.getColorI());
    if (broken.isChecked())
      for (BlockPos pos : recent.keySet())
        draw(matrices, new AABB(pos), blockShape.getSelected(), side.getColorI(), line.getColorI());
  }

  private void draw(PoseStack m, AABB box, PrinterHack.Shape s, int fill, int edge) {
    if (s != PrinterHack.Shape.LINES) RenderUtils.drawSolidBox(m, box, fill, false);
    if (s != PrinterHack.Shape.SIDES) RenderUtils.drawOutlinedBox(m, box, edge, false);
  }

  public enum Shape {
    Cube,
    UniformCube,
    Sphere
  }

  public enum Mode {
    All,
    Flatten,
    Smash
  }

  public enum SortMode {
    None,
    Closest,
    Furthest,
    TopDown
  }

  public enum ListMode {
    Whitelist,
    Blacklist
  }
}
