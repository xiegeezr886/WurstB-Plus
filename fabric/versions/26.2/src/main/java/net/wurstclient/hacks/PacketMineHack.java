/* PacketMine state machine ported from MrBZBZ/LeavesHack 13daf96.
 * SPDX-License-Identifier: AGPL-3.0-or-later */
package net.wurstclient.hacks;

import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.Color;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.events.*;
import net.wurstclient.hack.Hack;
import net.wurstclient.leaves.LeavesColorSetting;
import net.wurstclient.leaves.LeavesUtils;
import net.wurstclient.settings.*;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.*;

public final class PacketMineHack extends Hack
    implements UpdateListener, RenderListener, HandleBlockBreakingListener {
  private final CheckboxSetting usingPause = new CheckboxSetting("UsingPause", true);
  private final CheckboxSetting onlyMain = new CheckboxSetting("OnlyMain", true);
  private final EnumSetting<MineSwitchMode> autoSwitch =
      new EnumSetting<>("AutoSwitch", MineSwitchMode.values(), MineSwitchMode.Silent);
  private final SliderSetting range = new SliderSetting("Range", 6, 0, 12, 1, ValueDisplay.INTEGER);
  private final SliderSetting maxBreaks =
      new SliderSetting("TryBreakTime", 6, 0, 10, 1, ValueDisplay.INTEGER);
  private final CheckboxSetting preferAnchor = new CheckboxSetting("PreferAnchor", true);
  private final CheckboxSetting preferWeb = new CheckboxSetting("PreferAutoWeb", true);
  private final CheckboxSetting farCancel = new CheckboxSetting("FarCancel", true);
  private final CheckboxSetting swing = new CheckboxSetting("SwingHand", true);
  private final CheckboxSetting instantMine = new CheckboxSetting("InstantMine", false);
  private final SliderSetting instantDelay =
      new SliderSetting("InstantDelay", 10, 0, 1000, 1, ValueDisplay.INTEGER);
  private final CheckboxSetting fastBypass = new CheckboxSetting("FastBypass", true);
  private final CheckboxSetting doubleBreak = new CheckboxSetting("DoubleBreak", false);
  private final CheckboxSetting checkGround = new CheckboxSetting("CheckGround", true);
  private final CheckboxSetting bypassGround = new CheckboxSetting("BypassGround", false);
  private final SliderSetting switchDamage =
      new SliderSetting("SwitchDamage", 95, 0, 100, 1, ValueDisplay.INTEGER);
  private final SliderSetting switchTime =
      new SliderSetting("SwitchTime", 100, 0, 1000, 1, ValueDisplay.INTEGER);
  private final SliderSetting mineDelay =
      new SliderSetting("MineDelay", 300, 0, 1000, 1, ValueDisplay.INTEGER);
  private final SliderSetting packetDelay =
      new SliderSetting("PacketDelay", 0, 0, 1000, 1, ValueDisplay.INTEGER);
  private final SliderSetting mineDamage =
      new SliderSetting("Damage", 0.8, 0, 2.0, 0.01, ValueDisplay.DECIMAL);
  private final SliderSetting animationExp =
      new SliderSetting("Animation Exponent", 3, 0, 10, 0.01, ValueDisplay.DECIMAL);
  private final CheckboxSetting renderProgress = new CheckboxSetting("RenderProgress", true);
  private final LeavesColorSetting targetColor =
      new LeavesColorSetting("TargetColor", new Color(140, 142, 255, 255));
  private final LeavesColorSetting secondColor =
      new LeavesColorSetting("SecondColor", new Color(140, 142, 255, 255));
  private final EnumSetting<PrinterHack.Shape> shapeMode =
      new EnumSetting<>("ShapeMode", PrinterHack.Shape.values(), PrinterHack.Shape.BOTH);
  private final LeavesColorSetting sideStartColor =
      new LeavesColorSetting("SideStart", new Color(140, 142, 255, 50));
  private final LeavesColorSetting sideEndColor =
      new LeavesColorSetting("SideEnd", new Color(140, 142, 255, 50));
  private final LeavesColorSetting lineStartColor =
      new LeavesColorSetting("LineStart", new Color(140, 142, 255, 255));
  private final LeavesColorSetting lineEndColor =
      new LeavesColorSetting("LineEnd", new Color(140, 142, 255, 255));
  private final LeavesColorSetting secondSideStartColor =
      new LeavesColorSetting("SecondSideStart", new Color(140, 142, 255, 50));
  private final LeavesColorSetting secondSideEndColor =
      new LeavesColorSetting("SecondSideEnd", new Color(140, 142, 255, 50));
  private final LeavesColorSetting secondLineStartColor =
      new LeavesColorSetting("SecondLineStart", new Color(140, 142, 255, 255));
  private final LeavesColorSetting secondLineEndColor =
      new LeavesColorSetting("SecondLineEnd", new Color(140, 142, 255, 255));
  private Target primary, secondary;
  private Object world, owner;
  private long lastTime, nextSelect, restoreAt;
  private int oldSlot = -1, toolSlot = -1;
  private boolean changedClientSlot;
  private MineSwitchMode switchedMode;

  public PacketMineHack() {
    super("PacketMine");
    setCategory(Category.WORLD);
    for (Setting s :
        new Setting[] {
          usingPause,
          onlyMain,
          autoSwitch,
          range,
          maxBreaks,
          preferAnchor,
          preferWeb,
          farCancel,
          swing,
          instantMine,
          instantDelay,
          fastBypass,
          doubleBreak,
          checkGround,
          bypassGround,
          switchDamage,
          switchTime,
          mineDelay,
          packetDelay,
          mineDamage,
          animationExp,
          renderProgress,
          targetColor,
          secondColor,
          shapeMode,
          sideStartColor,
          sideEndColor,
          lineStartColor,
          lineEndColor,
          secondSideStartColor,
          secondSideEndColor,
          secondLineStartColor,
          secondLineEndColor
        }) addSetting(s);
  }

  @Override
  protected void onEnable() {
    reset();
    EVENTS.add(UpdateListener.class, this);
    EVENTS.add(RenderListener.class, this);
    EVENTS.add(HandleBlockBreakingListener.class, this);
  }

  @Override
  protected void onDisable() {
    EVENTS.remove(UpdateListener.class, this);
    EVENTS.remove(RenderListener.class, this);
    EVENTS.remove(HandleBlockBreakingListener.class, this);
    abort(primary);
    abort(secondary);
    restoreTool();
    reset();
  }

  private void reset() {
    primary = secondary = null;
    world = MC.level;
    owner = MC.player;
    oldSlot = -1;
    nextSelect = restoreAt = 0;
    lastTime = System.nanoTime();
  }

  public double getRange() {
    return range.getValue();
  }

  public BlockPos getTargetPos() {
    return primary == null ? null : primary.pos;
  }

  public BlockPos getSecondPos() {
    return secondary == null ? null : secondary.pos;
  }

  public boolean isCompleted() {
    return primary != null && primary.completed;
  }

  public boolean mine(BlockPos pos) {
    if (!isEnabled()
        || !LeavesUtils.ready()
        || pos == null
        || MC.level.getBlockState(pos).isAir()
        || BlockUtils.isUnbreakable(pos)
        || MC.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos))
            > range.getValue() * range.getValue()) return false;
    if (primary != null && primary.pos.equals(pos)
        || secondary != null && secondary.pos.equals(pos)) return true;
    if (System.nanoTime() < nextSelect) return false;
    if (doubleBreak.isChecked() && primary != null && !primary.completed && secondary == null)
      secondary = primary;
    else abort(primary);
    primary = new Target(pos.immutable());
    nextSelect = System.nanoTime() + mineDelay.getValueI() * 1_000_000L;
    return true;
  }

  @Override
  public void onHandleBlockBreaking(HandleBlockBreakingEvent event) {
    event.cancel();
  }

  @Override
  public void onUpdate() {
    if (world != MC.level || owner != MC.player) {
      reset();
      return;
    }
    long now = System.nanoTime();
    double delta = Math.min((now - lastTime) / 1_000_000_000D, 0.25);
    lastTime = now;
    if (oldSlot >= 0 && now >= restoreAt) restoreTool();
    if (MC.player == null || MC.level == null || MC.gameMode == null) return;
    if (!doubleBreak.isChecked()) {
      abort(secondary);
      secondary = null;
    }
    if (secondary != null && !tick(secondary, delta, true)) secondary = null;
    if (primary != null && !tick(primary, delta, false)) primary = null;
  }

  private boolean tick(Target target, double delta, boolean second) {
    if (farCancel.isChecked()
        && MC.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(target.pos))
            > range.getValue() * range.getValue()) {
      abort(target);
      return false;
    }
    var state = MC.level.getBlockState(target.pos);
    if (state.isAir()) {
      target.attempts = 0;
      if (instantMine.isChecked() && !second) {
        target.completed = true;
        target.progress = 1;
        return true;
      }
      return false;
    }
    if (BlockUtils.isUnbreakable(target.pos)) return false;
    if (!target.started) {
      send(Action.START_DESTROY_BLOCK, target.pos);
      if (fastBypass.isChecked()) send(Action.START_DESTROY_BLOCK, new BlockPos(0, -999, 0));
      target.started = true;
      target.earlyStopAt = System.nanoTime() + packetDelay.getValueI() * 1_000_000L;
      if (swing.isChecked()) WURST.getHax().globalSettingHack.swing(true);
      return true;
    }
    if (doubleBreak.isChecked() && !target.earlyStop && System.nanoTime() >= target.earlyStopAt) {
      send(Action.STOP_DESTROY_BLOCK, target.pos);
      target.earlyStop = true;
    }
    int tool = bestTool(target.pos);
    target.progress =
        Math.min(
            1,
            target.progress
                + delta
                    * (checkGround.isChecked() && !MC.player.onGround() ? 4 : 20)
                    / mineTicks(target.pos, tool)
                    / Math.max(0.01, mineDamage.getValue()));
    boolean pause =
        MC.gui.screen() != null || usingPause.isChecked() && LeavesUtils.using(onlyMain.isChecked());
    if (pause) return true;
    if (preferWeb.isChecked()
        && target.pos.equals(WURST.getHax().autoWebHack.getWebPos())
        && state.is(Blocks.COBWEB)) return true;
    if (preferAnchor.isChecked() && WURST.getHax().autoAnchorHack.isBusy()) return true;
    if (target.progress * 100 >= switchDamage.getValue()
        && doubleBreak.isChecked()
        && secondary != null) switchTool(tool);
    if (target.progress < 1) return true;
    if (System.nanoTime() < target.nextStop) return true;
    if (maxBreaks.getValueI() > 0 && target.attempts++ >= maxBreaks.getValueI() * 10) return false;
    switchTool(tool);
    if (bypassGround.isChecked() && !MC.player.isFallFlying() && !MC.player.onGround())
      MC.player.connection.send(
          new ServerboundMovePlayerPacket.PosRot(
              MC.player.getX(),
              MC.player.getY() + 1E-9,
              MC.player.getZ(),
              MC.player.getYRot(),
              MC.player.getXRot(),
              true, net.wurstclient.WurstClient.MC.player.horizontalCollision));
    if (swing.isChecked()) MC.player.swing(InteractionHand.MAIN_HAND);
    if (!second) send(Action.STOP_DESTROY_BLOCK, target.pos);
    target.completed = true;
    target.nextStop = System.nanoTime() + instantDelay.getValueI() * 1_000_000L;
    return instantMine.isChecked() && !second;
  }

  private void switchTool(int slot) {
    if (slot < 0 || autoSwitch.getSelected() == MineSwitchMode.None) return;
    if (oldSlot < 0) {
      oldSlot = MC.player.getInventory().getSelectedSlot();
      switchedMode = autoSwitch.getSelected();
    }
    changedClientSlot =
        switchedMode == MineSwitchMode.Delay && WURST.getHax().globalSettingHack.clientSwitch();
    if (changedClientSlot) MC.player.getInventory().setSelectedSlot(slot);
    toolSlot = slot;
    net.wurstclient.leaves.LeavesSlots.INSTANCE.select(slot);
    restoreAt = System.nanoTime() + switchTime.getValueI() * 1_000_000L;
  }

  private void restoreTool() {
    if (oldSlot < 0) return;
    if (owner == MC.player && world == MC.level && MC.player != null) {
      if (changedClientSlot && MC.player.getInventory().getSelectedSlot() == toolSlot)
        MC.player.getInventory().setSelectedSlot(oldSlot);
      net.wurstclient.leaves.LeavesSlots.INSTANCE.select(MC.player.getInventory().getSelectedSlot());
    }
    oldSlot = -1;
  }

  private void send(Action action, BlockPos pos) {
    IMC.getInteractionManager()
        .sendPlayerActionC2SPacket(
            action, pos, net.wurstclient.leaves.LeavesBlocks.getClickSide(pos));
  }

  private void abort(Target target) {
    if (target != null
        && target.started
        && owner == MC.player
        && world == MC.level
        && MC.player != null
        && MC.gameMode != null) send(Action.ABORT_DESTROY_BLOCK, target.pos);
  }

  private int bestTool(BlockPos pos) {
    int best = -1;
    double fastest = 1;
    for (int i = 0; i < 9; i++) {
      ItemStack stack = MC.player.getInventory().getItem(i);
      double speed =
          stack.getDestroySpeed(MC.level.getBlockState(pos))
              + net.wurstclient.util.EnchantmentUtils.getLevel(Enchantments.EFFICIENCY, stack);
      if (speed > fastest) {
        fastest = speed;
        best = i;
      }
    }
    return best;
  }

  private double mineTicks(BlockPos pos, int slot) {
    var state = MC.level.getBlockState(pos);
    float hardness = state.getDestroySpeed(MC.level, pos);
    if (hardness < 0) return Double.POSITIVE_INFINITY;
    if (hardness == 0) return 1;
    ItemStack stack =
        slot < 0 ? MC.player.getMainHandItem() : MC.player.getInventory().getItem(slot);
    double speed = stack.getDestroySpeed(state);
    int efficiency =
        net.wurstclient.util.EnchantmentUtils.getLevel(Enchantments.EFFICIENCY, stack);
    if (speed > 1 && efficiency > 0) speed += efficiency * efficiency + 1;
    if (MC.player.hasEffect(MobEffects.HASTE))
      speed *= 1 + (MC.player.getEffect(MobEffects.HASTE).getAmplifier() + 1) * 0.2;
    if (MC.player.hasEffect(MobEffects.MINING_FATIGUE))
      speed *=
          switch (MC.player.getEffect(MobEffects.MINING_FATIGUE).getAmplifier()) {
            case 0 -> 0.3;
            case 1 -> 0.09;
            case 2 -> 0.0027;
            default -> 0.00081;
          };
    return hardness
        * (stack.isCorrectToolForDrops(state) || !state.requiresCorrectToolForDrops() ? 30 : 100)
        / Math.max(speed, 0.00001);
  }

  @Override
  public void onRender(PoseStack matrices, float partialTicks) {
    if (MC.level == null) return;
    draw(matrices, primary, false);
    draw(matrices, secondary, true);
  }

  private void draw(PoseStack matrices, Target target, boolean second) {
    if (target == null) return;
    double progress = 1 - Math.pow(1 - target.progress, animationExp.getValue());
    double size = progress / 2;
    Vec3 center = Vec3.atCenterOf(target.pos);
    AABB box = new AABB(center.subtract(size, size, size), center.add(size, size, size));
    int fill =
        lerp(
            (second ? secondSideStartColor : sideStartColor).getColorI(),
            (second ? secondSideEndColor : sideEndColor).getColorI(),
            progress);
    int line =
        lerp(
            (second ? secondLineStartColor : lineStartColor).getColorI(),
            (second ? secondLineEndColor : lineEndColor).getColorI(),
            progress);
    if (shapeMode.getSelected() != PrinterHack.Shape.LINES)
      RenderUtils.drawSolidBox(matrices, box, fill, false);
    if (shapeMode.getSelected() != PrinterHack.Shape.SIDES)
      RenderUtils.drawOutlinedBox(matrices, box, line, false);
    if (renderProgress.isChecked()) {
      var camera = MC.gameRenderer.mainCamera();
      var pos = center.subtract(camera.position());
      String text = target.completed ? "Done" : (int) (target.progress * 100) + "%";
      matrices.pushPose();
      matrices.translate(pos.x, pos.y, pos.z);
      matrices.mulPose(camera.rotation());
      matrices.scale(-0.025F, -0.025F, 0.025F);
      RenderUtils.submitText(
          matrices,
          -MC.font.width(text) / 2F,
          0,
          net.minecraft.network.chat.Component.literal(text).getVisualOrderText(),
          false,
          net.minecraft.client.gui.Font.DisplayMode.SEE_THROUGH,
          0xF000F0,
          (second ? secondColor : targetColor).getColorI(),
          0,
          0);
      matrices.popPose();
    }
  }

  private static int lerp(int start, int end, double progress) {
    int out = 0;
    for (int shift = 0; shift < 32; shift += 8)
      out |=
          (int)
                  Math.round(
                      ((start >>> shift) & 255) * (1 - progress)
                          + ((end >>> shift) & 255) * progress)
              << shift;
    return out;
  }

  private static final class Target {
    final BlockPos pos;
    double progress;
    boolean started, completed, earlyStop;
    int attempts;
    long earlyStopAt, nextStop;

    Target(BlockPos pos) {
      this.pos = pos;
    }
  }

  public enum MineSwitchMode {
    None,
    Delay,
    Silent
  }
}
