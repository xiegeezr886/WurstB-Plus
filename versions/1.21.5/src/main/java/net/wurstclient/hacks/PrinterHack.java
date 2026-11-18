/* Printer behavior adapted from MrBZBZ/LeavesHack 13daf96.
 * SPDX-License-Identifier: AGPL-3.0-or-later */
package net.wurstclient.hacks;

import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.events.*;
import net.wurstclient.hack.Hack;
import net.wurstclient.leaves.InventorySwap;
import net.wurstclient.leaves.LeavesColorSetting;
import net.wurstclient.leaves.LitematicaBridge;
import net.wurstclient.settings.*;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.*;

/** Prints actual Litematica states, validating the placement orientation first. */
public final class PrinterHack extends Hack
    implements UpdateListener, RenderListener, MoveInputListener {
  private final CheckboxSetting rotate =
      new CheckboxSetting("Rotate", "Rotate before placing blocks.", true);
  private final SliderSetting radius =
      new SliderSetting("Radius", "Schematic printing range.", 4, 1, 6, 1, ValueDisplay.INTEGER);
  private final CheckboxSetting inventorySwap =
      new CheckboxSetting(
          "Inventory swap", "Use materials from the main inventory and restore the slot.", true);
  private final CheckboxSetting safeWalk =
      new CheckboxSetting("Safe walk", "Prevent walking off edges while printing.", true);
  private final CheckboxSetting ignoreSneak =
      new CheckboxSetting("Ignore sneak", "Sneak when placing against interactive blocks.", true);
  private final SliderSetting shiftTime =
      new SliderSetting(
          "Shift time",
          "Wait before placing against interactive blocks.",
          100,
          0,
          1000,
          10,
          ValueDisplay.INTEGER.withSuffix(" ms"));
  private final SliderSetting sneakSpeed =
      new SliderSetting(
          "Sneak speed",
          "Movement percentage while waiting to place.",
          0,
          0,
          20,
          1,
          ValueDisplay.INTEGER);
  private final EnumSetting<ListMode> listMode =
      new EnumSetting<>("List mode", ListMode.values(), ListMode.BLACKLIST);
  private final BlockListSetting blacklist =
      new BlockListSetting("Blacklist", "Schematic blocks to skip.");
  private final BlockListSetting whitelist =
      new BlockListSetting("Whitelist", "Schematic blocks allowed in whitelist mode.");
  private final LeavesColorSetting lineColor =
      new LeavesColorSetting("Line color", "Printing outline color.", Color.WHITE);
  private final LeavesColorSetting sideColor =
      new LeavesColorSetting("Side color", "Printing fill color.", new Color(255, 255, 255, 50));
  private final EnumSetting<Shape> shape =
      new EnumSetting<>("Shape mode", Shape.values(), Shape.BOTH);
  private BlockPos preview, pending;
  private long readyAt;
  private net.minecraft.client.player.LocalPlayer owner;

  public PrinterHack() {
    super("Printer");
    setCategory(Category.WORLD);
    for (Setting s :
        new Setting[] {
          rotate,
          radius,
          inventorySwap,
          safeWalk,
          ignoreSneak,
          shiftTime,
          sneakSpeed,
          listMode,
          blacklist,
          whitelist,
          shape,
          lineColor,
          sideColor
        }) addSetting(s);
  }

  @Override
  protected void onEnable() {
    clear();
    EVENTS.add(UpdateListener.class, this);
    EVENTS.add(RenderListener.class, this);
    EVENTS.add(MoveInputListener.class, this);
    if (!net.wurstclient.util.PlatformUtils.isModLoaded("litematica")) {
      ChatUtils.error("Printer requires Litematica and MaLiLib.");
      setEnabled(false);
    }
  }

  @Override
  protected void onDisable() {
    EVENTS.remove(UpdateListener.class, this);
    EVENTS.remove(RenderListener.class, this);
    EVENTS.remove(MoveInputListener.class, this);
    clear();
  }

  private void clear() {
    preview = pending = null;
    readyAt = 0;
    owner = MC.player;
  }

  @Override
  public void onUpdate() {
    if (owner != MC.player) clear();
    preview = null;
    if (MC.player == null
        || MC.level == null
        || MC.gameMode == null
        || MC.screen != null
        || MC.player.isSpectator()
        || MC.player.containerMenu != MC.player.inventoryMenu
        || !MC.player.inventoryMenu.getCarried().isEmpty()) {
      clear();
      return;
    }
    var schematic = LitematicaBridge.getWorld();
    if (schematic == null) {
      pending = null;
      return;
    }
    ArrayList<BlockPos> positions = new ArrayList<>();
    for (BlockPos pos : BlockUtils.getAllInBox(MC.player.blockPosition(), radius.getValueI()))
      if (MC.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos))
          <= radius.getValue() * radius.getValue()) positions.add(pos.immutable());
    positions.sort(
        Comparator.comparingDouble(
            p -> MC.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(p))));
    for (BlockPos pos : positions) {
      BlockState required = schematic.getBlockState(pos);
      if (required.isAir()
          || !(required.getBlock().asItem() instanceof BlockItem)
          || !MC.level.getBlockState(pos).canBeReplaced()) continue;
      if (listMode.getSelected() == ListMode.BLACKLIST
          ? blacklist.contains(required.getBlock())
          : !whitelist.contains(required.getBlock())) continue;
      if (!MC.level.isUnobstructed(
          required, pos, net.minecraft.world.phys.shapes.CollisionContext.empty())) continue;
      int slot =
          InventoryUtils.indexOf(required.getBlock().asItem(), inventorySwap.isChecked() ? 36 : 9);
      if (slot < 0) continue;
      try (InventorySwap swap = InventorySwap.acquire(slot)) {
        if (swap == null) return;
        if (print(pos, required)) return;
      }
    }
    pending = null;
  }

  private boolean print(BlockPos pos, BlockState required) {
    float yaw = MC.player.getYRot(), pitch = MC.player.getXRot();
    try {
      for (Direction side : Direction.values()) {
        BlockPos neighbor = pos.relative(side);
        BlockState support = MC.level.getBlockState(neighbor);
        if (support.canBeReplaced() || support.getShape(MC.level, neighbor).isEmpty()) continue;
        Vec3 hit = Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(side.getUnitVec3i()).scale(0.5));
        if (required.hasProperty(BlockStateProperties.SLAB_TYPE)
            && side.getAxis() != Direction.Axis.Y)
          hit =
              new Vec3(
                  hit.x,
                  pos.getY()
                      + (required.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.TOP
                          ? 0.9
                          : 0.1),
                  hit.z);
        BlockHitResult result = new BlockHitResult(hit, side.getOpposite(), neighbor, false);
        // Try the current direction first, then the six cardinal orientations.
        for (int orientation = 0; orientation < (rotate.isChecked() ? 7 : 1); orientation++) {
          float targetYaw = orientation == 0 ? yaw : (orientation - 1) % 4 * 90;
          float targetPitch = orientation == 5 ? -90 : orientation == 6 ? 90 : pitch;
          MC.player.setYRot(targetYaw);
          MC.player.setXRot(targetPitch);
          BlockPlaceContext context =
              new BlockPlaceContext(
                  MC.player, InteractionHand.MAIN_HAND, MC.player.getMainHandItem(), result);
          BlockState predicted = required.getBlock().getStateForPlacement(context);
          if (!context.getClickedPos().equals(pos) || !matchesOrientation(required, predicted))
            continue;
          boolean sneak =
              support.hasBlockEntity()
                  || support.getBlock() instanceof net.minecraft.world.level.block.DoorBlock
                  || support.getBlock() instanceof net.minecraft.world.level.block.TrapDoorBlock
                  || support.getBlock() instanceof net.minecraft.world.level.block.FenceGateBlock
                  || support.getBlock()
                      instanceof net.minecraft.world.level.block.CraftingTableBlock;
          if (sneak && !ignoreSneak.isChecked() && !MC.player.isShiftKeyDown()) continue;
          preview = pos;
          if (sneak && ignoreSneak.isChecked()) {
            if (!pos.equals(pending)) {
              pending = pos;
              readyAt = System.nanoTime() + shiftTime.getValueI() * 1_000_000L;
            }
            if (System.nanoTime() < readyAt) return true;
          }
          boolean pressed = sneak && !MC.player.isShiftKeyDown();
          try {
            if (pressed)
              MC.player.connection.send(
                  new ServerboundPlayerCommandPacket(
                      MC.player, ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY));
            if (rotate.isChecked())
              net.wurstclient.leaves.LeavesUtils.rotate(targetYaw, targetPitch);
            if (MC.gameMode
                .useItemOn(MC.player, InteractionHand.MAIN_HAND, result)
                .consumesAction()) WURST.getHax().globalSettingHack.swing(false);
          } finally {
            if (pressed)
              MC.player.connection.send(
                  new ServerboundPlayerCommandPacket(
                      MC.player, ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));
            if (rotate.isChecked() && WURST.getHax().globalSettingHack.snapBack())
              MC.player.connection.send(
                  new ServerboundMovePlayerPacket.Rot(yaw, pitch, MC.player.onGround(), net.wurstclient.WurstClient.MC.player.horizontalCollision));
          }
          pending = null;
          return true;
        }
      }
    } finally {
      MC.player.setYRot(yaw);
      MC.player.setXRot(pitch);
    }
    return false;
  }

  public static boolean matchesOrientation(BlockState required, BlockState predicted) {
    if (predicted == null || required.getBlock() != predicted.getBlock()) return false;
    for (var property : required.getProperties()) {
      String name = property.getName();
      if ((name.equals("facing")
              || name.equals("axis")
              || name.equals("half")
              || name.equals("type")
              || name.equals("rotation"))
          && !required.getValue(property).equals(predicted.getValue(property))) return false;
    }
    return true;
  }

  public boolean shouldSafeWalk() {
    return isEnabled()
        && safeWalk.isChecked()
        && MC.player != null
        && MC.player.onGround()
        && !MC.player.isSpectator()
        && !MC.player.getAbilities().flying
        && !MC.player.isFallFlying();
  }

  @Override
  public void onMoveInput(MoveInputEvent event) {
    if (pending == null || System.nanoTime() >= readyAt) return;
    float speed = sneakSpeed.getValueF() / 100F;
    event.setForward(event.getForward() * speed);
    event.setSideways(event.getSideways() * speed);
  }

  @Override
  public void onRender(PoseStack matrices, float partialTicks) {
    if (preview == null || MC.level == null) return;
    if (shape.getSelected() != Shape.LINES)
      RenderUtils.drawSolidBox(matrices, new AABB(preview), sideColor.getColorI(), false);
    if (shape.getSelected() != Shape.SIDES)
      RenderUtils.drawOutlinedBox(matrices, new AABB(preview), lineColor.getColorI(), false);
  }

  public enum ListMode {
    BLACKLIST,
    WHITELIST
  }

  public enum Shape {
    BOTH,
    LINES,
    SIDES
  }
}
