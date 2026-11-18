/* AutoAnchor placement/charge/detonation state machine from LeavesHack 13daf96.
 * SPDX-License-Identifier: AGPL-3.0-or-later */
package net.wurstclient.hacks;

import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.Color;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.wurstclient.Category;
import net.wurstclient.events.*;
import net.wurstclient.hack.Hack;
import net.wurstclient.leaves.*;
import net.wurstclient.leaves.LeavesColorSetting;
import net.wurstclient.settings.*;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.*;

public final class AutoAnchorHack extends Hack implements UpdateListener, RenderListener {
  private final SliderSetting targetRange =
      new SliderSetting("TargetRange", 12.0, 1, 20, 0.01, ValueDisplay.DECIMAL);
  private final SliderSetting range =
      new SliderSetting("range", 4.0, 1, 6, 0.01, ValueDisplay.DECIMAL);
  private final SliderSetting delay =
      new SliderSetting("delay-ms", 50, 0, 500, 1, ValueDisplay.INTEGER);
  private final SliderSetting explodeDelay =
      new SliderSetting("explode-delay-ms", 50, 0, 500, 1, ValueDisplay.INTEGER);
  private final SliderSetting minDamage =
      new SliderSetting("MinDamage", 4.0, 1, 36, 0.01, ValueDisplay.DECIMAL);
  private final SliderSetting maxSelfDmg =
      new SliderSetting("MaxSelfDmg", 12, 1, 36, 0.01, ValueDisplay.DECIMAL);
  private final SliderSetting minRatio =
      new SliderSetting("MinRatio", 2.0, 0.1, 10, 0.01, ValueDisplay.DECIMAL);
  private final SliderSetting maxTargets =
      new SliderSetting("MaxTargets", 4, 1, 8, 1, ValueDisplay.INTEGER);
  private final SliderSetting targetRadius =
      new SliderSetting("TargetRadius", 3.5, 1, 6, 0.01, ValueDisplay.DECIMAL);
  private final CheckboxSetting usingPause = new CheckboxSetting("UsingPause", true);
  private final CheckboxSetting onlyMain = new CheckboxSetting("OnlyMain", true);
  private final CheckboxSetting preferHead = new CheckboxSetting("PreferHead", true);
  private final CheckboxSetting placeHelper = new CheckboxSetting("PlaceHelper", true);
  private final CheckboxSetting noSuicide = new CheckboxSetting("NoSuicide", true);
  private final CheckboxSetting rotate = new CheckboxSetting("rotate", true);
  private final CheckboxSetting inventory = new CheckboxSetting("InventorySwap", true);
  private final CheckboxSetting renderDmg = new CheckboxSetting("RenderDmg", true);
  private final LeavesColorSetting dmgColor =
      new LeavesColorSetting("DamageColor", new Color(255, 255, 255, 255));
  private final EnumSetting<PrinterHack.Shape> shapeMode =
      new EnumSetting<>("Shape Mode", PrinterHack.Shape.values(), PrinterHack.Shape.BOTH);
  private final LeavesColorSetting lineColor =
      new LeavesColorSetting("Line", new Color(255, 255, 255, 255));
  private final LeavesColorSetting sideColor =
      new LeavesColorSetting("Side", new Color(255, 255, 255, 10));
  private final SliderSetting renderSpeed =
      new SliderSetting("RenderSpeed", 0.1, 0, 1, 0.01, ValueDisplay.DECIMAL);
  private BlockPos currentPos;
  private Vec3 rendered;
  private final Map<BlockPos, Long> anchors = new HashMap<>();
  private List<Player> targets = List.of();
  private Object world;
  private long nextPlace, nextExplode;
  private float damage;

  public AutoAnchorHack() {
    super("AutoAnchor");
    setCategory(Category.COMBAT);
    for (Setting s :
        new Setting[] {
          targetRange,
          range,
          delay,
          explodeDelay,
          minDamage,
          maxSelfDmg,
          minRatio,
          maxTargets,
          targetRadius,
          usingPause,
          onlyMain,
          preferHead,
          placeHelper,
          noSuicide,
          rotate,
          inventory,
          renderDmg,
          dmgColor,
          shapeMode,
          lineColor,
          sideColor,
          renderSpeed
        }) addSetting(s);
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
    clear();
  }

  private void clear() {
    world = MC.level;
    currentPos = null;
    rendered = null;
    anchors.clear();
    targets = List.of();
    nextPlace = nextExplode = 0;
  }

  public boolean isBusy() {
    return isEnabled() && currentPos != null;
  }

  public BlockPos getCurrentPos() {
    return isEnabled() ? currentPos : null;
  }

  @Override
  public void onUpdate() {
    if (world != MC.level) clear();
    if (!LeavesUtils.ready() || MC.level.dimensionType().respawnAnchorWorks()) {
      currentPos = null;
      return;
    }
    if (WURST.getHax().crystalAuraPlusHack.prefersCrystal()
        && WURST.getHax().crystalAuraPlusHack.isBusy()) {
      currentPos = null;
      return;
    }
    targets =
        MC.level.players().stream()
            .filter(
                p ->
                    p != MC.player
                        && p.isAlive()
                        && !p.isSpectator()
                        && !WURST.getFriends().contains(p.getScoreboardName())
                        && MC.player.distanceToSqr(p) <= targetRange.getValueSq())
            .sorted(Comparator.comparingDouble(MC.player::distanceToSqr))
            .limit(maxTargets.getValueI())
            .map(p -> (Player) p)
            .toList();
    if (targets.isEmpty()) {
      currentPos = null;
      return;
    }
    if (usingPause.isChecked() && LeavesUtils.using(onlyMain.isChecked())) return;
    if (InventoryUtils.indexOf(Items.RESPAWN_ANCHOR, inventory.isChecked() ? 36 : 9) < 0
        || InventoryUtils.indexOf(Items.GLOWSTONE, inventory.isChecked() ? 36 : 9) < 0) {
      currentPos = null;
      return;
    }
    long now = System.nanoTime();
    anchors.entrySet().removeIf(e -> now - e.getValue() > 1_200_000_000L);
    currentPos = select();
    if (currentPos != null) damage = targetDamage(currentPos);
    if (currentPos != null
        && now >= nextPlace
        && MC.level.getBlockState(currentPos).canBeReplaced()
        && validDamage(currentPos)) {
      if (LeavesUtils.place(
          currentPos,
          Items.RESPAWN_ANCHOR,
          range.getValue(),
          inventory.isChecked(),
          rotate.isChecked())) {
        anchors.put(currentPos, now);
        nextPlace = now + delay.getValueI() * 1_000_000L;
      }
    }
    if (now < nextExplode) return;
    Set<BlockPos> candidates = new HashSet<>(anchors.keySet());
    if (currentPos != null) candidates.add(currentPos);
    BlockPos best =
        candidates.stream()
            .filter(
                p ->
                    MC.level.getBlockState(p).is(Blocks.RESPAWN_ANCHOR)
                        && inRange(p)
                        && validDamage(p))
            .max(Comparator.comparingDouble(this::targetDamage))
            .orElse(null);
    if (best == null) return;
    Item item =
        MC.level.getBlockState(best).getValue(RespawnAnchorBlock.CHARGE) < 1
            ? Items.GLOWSTONE
            : Items.RESPAWN_ANCHOR;
    try (InventorySwap swap =
        InventorySwap.acquire(InventoryUtils.indexOf(item, inventory.isChecked() ? 36 : 9))) {
      if (swap == null) return;
      Vec3 hit = Vec3.atCenterOf(best).add(0, 0.5, 0);
      if (rotate.isChecked()) LeavesUtils.look(hit);
      try {
        MC.gameMode.useItemOn(
            MC.player,
            InteractionHand.MAIN_HAND,
            new BlockHitResult(hit, Direction.UP, best, false));
      } finally {
        if (rotate.isChecked()) LeavesUtils.restoreLook();
      }
      anchors.put(best, now);
      damage = targetDamage(best);
      nextExplode = now + explodeDelay.getValueI() * 1_000_000L;
    }
  }

  private BlockPos select() {
    if (preferHead.isChecked()) {
      BlockPos head = targets.get(0).blockPosition().above(2);
      if (inRange(head) && validDamage(head)) {
        if (placeable(head)) return head;
        if (placeHelper.isChecked() && System.nanoTime() >= nextPlace)
          for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos helper = head.relative(dir);
            if (placeable(helper)
                && LeavesUtils.place(
                    helper,
                    Items.RESPAWN_ANCHOR,
                    range.getValue(),
                    inventory.isChecked(),
                    rotate.isChecked())) {
              anchors.put(helper, System.nanoTime());
              nextPlace = System.nanoTime() + delay.getValueI() * 1_000_000L;
              return head;
            }
          }
      }
    }
    return BlockUtils.getAllInBoxStream(MC.player.blockPosition(), range.getValueCeil())
        .filter(this::inRange)
        .filter(this::placeable)
        .filter(this::validDamage)
        .max(Comparator.comparingDouble(this::targetDamage))
        .map(BlockPos::immutable)
        .orElse(null);
  }

  private boolean inRange(BlockPos pos) {
    return MC.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) <= range.getValueSq()
        && targets.stream()
            .anyMatch(
                p ->
                    p.position().add(0, 1, 0).distanceToSqr(Vec3.atCenterOf(pos))
                        < targetRadius.getValueSq());
  }

  private boolean placeable(BlockPos pos) {
    return MC.level.getBlockState(pos).is(Blocks.RESPAWN_ANCHOR)
        || MC.level.getBlockState(pos).canBeReplaced()
            && BlockPlacer.getBlockPlacingParams(pos) != null
            && MC.level
                .getEntities(
                    (net.minecraft.world.entity.Entity) null,
                    new AABB(pos),
                    e -> e.isAlive() && e.blocksBuilding)
                .isEmpty();
  }

  private float targetDamage(BlockPos pos) {
    float best = 0;
    for (Player p : targets)
      best = Math.max(best, LeavesDamage.damage(Vec3.atCenterOf(pos), p, 5, pos));
    return best;
  }

  private boolean validDamage(BlockPos pos) {
    float enemy = targetDamage(pos),
        self = LeavesDamage.damage(Vec3.atCenterOf(pos), MC.player, 5, pos);
    return enemy >= minDamage.getValue()
        && self <= maxSelfDmg.getValue()
        && (self <= 0 || enemy / self >= minRatio.getValue())
        && (!noSuicide.isChecked()
            || self < MC.player.getHealth() + MC.player.getAbsorptionAmount());
  }

  @Override
  public void onRender(PoseStack matrices, float partialTicks) {
    if (currentPos == null || MC.level == null) {
      rendered = null;
      return;
    }
    Vec3 to = Vec3.atLowerCornerOf(currentPos);
    rendered =
        rendered == null ? to : rendered.add(to.subtract(rendered).scale(renderSpeed.getValue()));
    AABB box = new AABB(rendered, rendered.add(1, 1, 1));
    if (shapeMode.getSelected() != PrinterHack.Shape.LINES)
      RenderUtils.drawSolidBox(matrices, box, sideColor.getColorI(), false);
    if (shapeMode.getSelected() != PrinterHack.Shape.SIDES)
      RenderUtils.drawOutlinedBox(matrices, box, lineColor.getColorI(), false);
    if (renderDmg.isChecked())
      LeavesRender.text(
          matrices,
          rendered.add(0.5, 0.5, 0.5),
          String.format(java.util.Locale.ROOT, "%.1f", damage),
          dmgColor.getColorI(),
          1);
  }
}
