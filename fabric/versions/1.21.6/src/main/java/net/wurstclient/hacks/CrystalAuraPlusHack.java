/* CrystalAuraPlus behavior adapted from LeavesHack 13daf96 (Meteor-derived).
 * SPDX-License-Identifier: AGPL-3.0-or-later */
package net.wurstclient.hacks;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.Color;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.wurstclient.Category;
import net.wurstclient.events.*;
import net.wurstclient.hack.Hack;
import net.wurstclient.leaves.*;
import net.wurstclient.leaves.LeavesColorSetting;
import net.wurstclient.mixin.MultiPlayerGameModeAccessor;
import net.wurstclient.settings.*;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.*;

public final class CrystalAuraPlusHack extends Hack
    implements UpdateListener, RenderListener, PacketInputListener, PacketOutputListener {
  private final SliderSetting targetRange =
      new SliderSetting("TargetRange", 10, 0, 16, 0.01, ValueDisplay.DECIMAL);
  private final CheckboxSetting predictMovement = new CheckboxSetting("PredictMovement", true);
  private final SliderSetting minDamage =
      new SliderSetting("MinDamage", 6, 0, 100, 0.01, ValueDisplay.DECIMAL);
  private final SliderSetting maxDamage =
      new SliderSetting("MaxDamage", 6, 0, 36, 0.01, ValueDisplay.DECIMAL);
  private final CheckboxSetting antiSuicide = new CheckboxSetting("AntiSuicide", true);
  private final CheckboxSetting rotate = new CheckboxSetting("Rotate", true);
  private final CheckboxSetting inventorySwap = new CheckboxSetting("InventorySwap", false);
  private final SliderSetting switchDelay =
      new SliderSetting("SwitchDelay", 0, 0, 100, 1, ValueDisplay.INTEGER);
  private final CheckboxSetting antiWeakness = new CheckboxSetting("AntiWeakness", true);
  private final CheckboxSetting doPlace = new CheckboxSetting("Place", true);
  private final SliderSetting placeDelay =
      new SliderSetting("PlaceDelay", 0, 0, 20, 1, ValueDisplay.INTEGER);
  private final SliderSetting placeRange =
      new SliderSetting("PlaceRange", 4.5, 0, 6, 0.01, ValueDisplay.DECIMAL);
  private final SliderSetting placeWallsRange =
      new SliderSetting("PlaceWallsRange", 4.5, 0, 6, 0.01, ValueDisplay.DECIMAL);
  private final CheckboxSetting placement112 = new CheckboxSetting("Placement112", false);
  private final SliderSetting supportDelay =
      new SliderSetting("SupportDelay", 1, 0, 100, 1, ValueDisplay.INTEGER);
  private final CheckboxSetting facePlace = new CheckboxSetting("FacePlace", false);
  private final SliderSetting facePlaceHealth =
      new SliderSetting("FacePlaceHealth", 8, 1, 36, 0.01, ValueDisplay.DECIMAL);
  private final SliderSetting facePlaceDurability =
      new SliderSetting("FacePlaceDurability", 2, 1, 100, 0.01, ValueDisplay.DECIMAL);
  private final CheckboxSetting facePlaceArmor =
      new CheckboxSetting("FacePlaceMissingArmor", false);
  private final CheckboxSetting doBreak = new CheckboxSetting("Break", true);
  private final SliderSetting breakDelay =
      new SliderSetting("BreakDelay", 0, 0, 20, 1, ValueDisplay.INTEGER);
  private final CheckboxSetting smartDelay = new CheckboxSetting("SmartDelay", false);
  private final SliderSetting breakRange =
      new SliderSetting("BreakRange", 4.5, 0, 6, 0.01, ValueDisplay.DECIMAL);
  private final SliderSetting breakWallsRange =
      new SliderSetting("BreakWallsRange", 4.5, 0, 6, 0.01, ValueDisplay.DECIMAL);
  private final CheckboxSetting onlyBreakOwn = new CheckboxSetting("OnlyOwn", false);
  private final SliderSetting breakAttempts =
      new SliderSetting("BreakAttempts", 2, 1, 5, 1, ValueDisplay.INTEGER);
  private final SliderSetting ticksExisted =
      new SliderSetting("TicksExisted", 0, 0, 100, 1, ValueDisplay.INTEGER);
  private final SliderSetting attackFrequency =
      new SliderSetting("AttackFrequency", 25, 1, 30, 1, ValueDisplay.INTEGER);
  private final CheckboxSetting fastBreak = new CheckboxSetting("Ak47", true);
  private final CheckboxSetting pauseOnLag = new CheckboxSetting("PauseOnLag", true);
  private final SliderSetting pauseHealth =
      new SliderSetting("PauseHealth", 5, 0, 36, 0.01, ValueDisplay.DECIMAL);
  private final CheckboxSetting render = new CheckboxSetting("Render", true);
  private final CheckboxSetting renderDmg = new CheckboxSetting("RenderDmg", true);
  private final LeavesColorSetting dmgColor =
      new LeavesColorSetting("DamageColor", new Color(140, 142, 255, 255));
  private final EnumSetting<PrinterHack.Shape> shapeMode =
      new EnumSetting<>("ShapeMode", PrinterHack.Shape.values(), PrinterHack.Shape.BOTH);
  private final LeavesColorSetting sideColor =
      new LeavesColorSetting("SideColor", new Color(140, 142, 255, 50));
  private final LeavesColorSetting lineColor =
      new LeavesColorSetting("LineColor", new Color(140, 142, 255, 255));
  private final SliderSetting renderSpeed =
      new SliderSetting("RenderSpeed", 0.05, 0, 1, 0.01, ValueDisplay.DECIMAL);
  private final SliderSetting renderH =
      new SliderSetting("RenderHeight", 0.1, 0, 1, 0.01, ValueDisplay.DECIMAL);

  private final EnumSetting<PreferMode> preferMode =
      new EnumSetting<>("PreferMode", PreferMode.values(), PreferMode.PreferAnchor);
  private final EnumSetting<AutoSwitchMode> autoSwitch =
      new EnumSetting<>("AutoSwitch", AutoSwitchMode.values(), AutoSwitchMode.Silent);
  private final EnumSetting<SupportMode> support =
      new EnumSetting<>("Support", SupportMode.values(), SupportMode.Fast);
  private final EnumSetting<PauseMode>
      pauseOnUse = new EnumSetting<>("PauseOnUse", PauseMode.values(), PauseMode.Both),
      pauseOnMine = new EnumSetting<>("PauseOnMine", PauseMode.values(), PauseMode.None);
  private final TextFieldSetting entities =
      new TextFieldSetting(
          "Entities",
          "Comma-separated entity IDs.",
          "minecraft:player,minecraft:warden,minecraft:wither");
  private final TextFieldSetting forceFacePlace =
      new TextFieldSetting(
          "ForceFacePlace",
          "Hold this key to force face placement (for example key.keyboard.r).",
          "");
  private final List<LivingEntity> targets = new ArrayList<>();
  private final Map<Integer, Integer> attempts = new HashMap<>(), waiting = new HashMap<>();
  private final Set<Integer> own = new HashSet<>();
  private BlockPos pendingPlace, renderPos;
  private Vec3 animated;
  private LivingEntity bestTarget;
  private double shownDamage;
  private int breakTimer,
      placeTimer,
      switchTimer,
      windowTicks,
      attacks,
      pendingTicks,
      lastActionTick = -1,
      weaknessSlot = -1,
      oldSlot = -1;
  private Object world;
  private volatile long lastPacket;

  public CrystalAuraPlusHack() {
    super("CrystalAuraPlus");
    setCategory(Category.COMBAT);
    for (Setting setting :
        new Setting[] {
          targetRange,
          predictMovement,
          minDamage,
          maxDamage,
          antiSuicide,
          rotate,
          inventorySwap,
          switchDelay,
          antiWeakness,
          doPlace,
          placeDelay,
          placeRange,
          placeWallsRange,
          placement112,
          supportDelay,
          facePlace,
          facePlaceHealth,
          facePlaceDurability,
          facePlaceArmor,
          doBreak,
          breakDelay,
          smartDelay,
          breakRange,
          breakWallsRange,
          onlyBreakOwn,
          breakAttempts,
          ticksExisted,
          attackFrequency,
          fastBreak,
          pauseOnLag,
          pauseHealth,
          render,
          renderDmg,
          dmgColor,
          shapeMode,
          sideColor,
          lineColor,
          renderSpeed,
          renderH,
          preferMode,
          autoSwitch,
          support,
          pauseOnUse,
          pauseOnMine,
          entities,
          forceFacePlace
        }) addSetting(setting);
  }

  @Override
  protected void onEnable() {
    clear();
    EVENTS.add(UpdateListener.class, this);
    EVENTS.add(RenderListener.class, this);
    EVENTS.add(PacketInputListener.class, this);
    EVENTS.add(PacketOutputListener.class, this);
  }

  @Override
  protected void onDisable() {
    EVENTS.remove(UpdateListener.class, this);
    EVENTS.remove(RenderListener.class, this);
    EVENTS.remove(PacketInputListener.class, this);
    EVENTS.remove(PacketOutputListener.class, this);
    restoreWeapon();
    clear();
  }

  private void clear() {
    targets.clear();
    attempts.clear();
    waiting.clear();
    own.clear();
    pendingPlace = renderPos = null;
    animated = null;
    bestTarget = null;
    breakTimer = placeTimer = switchTimer = windowTicks = attacks = pendingTicks = 0;
    lastActionTick = -1;
    weaknessSlot = oldSlot = -1;
    world = MC.level;
    lastPacket = System.nanoTime();
  }

  public boolean prefersCrystal() {
    return preferMode.getSelected() == PreferMode.PreferCrystal;
  }

  public boolean isBusy() {
    return isEnabled() && renderPos != null;
  }

  @Override
  public void onUpdate() {
    if (world != MC.level) clear();
    if (!LeavesUtils.ready()) {
      restoreWeapon();
      renderPos = null;
      return;
    }
    if (breakTimer > 0) breakTimer--;
    if (placeTimer > 0) placeTimer--;
    if (switchTimer > 0) switchTimer--;
    if (++windowTicks >= 20) {
      windowTicks = 0;
      attacks = 0;
    }
    if (pendingTicks > 0) pendingTicks--;
    else pendingPlace = null;
    waiting.replaceAll((id, ticks) -> ticks + 1);
    waiting.values().removeIf(ticks -> ticks > 3);
    attempts.keySet().removeIf(id -> !(MC.level.getEntity(id) instanceof EndCrystal));
    own.removeIf(id -> !(MC.level.getEntity(id) instanceof EndCrystal));
    findTargets();
    if (targets.isEmpty()) {
      renderPos = null;
      restoreWeapon();
      return;
    }
    if (lastActionTick != MC.player.tickCount) doBreak();
    if (lastActionTick != MC.player.tickCount) doPlace();
  }

  private void findTargets() {
    targets.clear();
    Set<String> selected =
        new HashSet<>(Arrays.asList(entities.getValue().replace(" ", "").split(",")));
    for (Entity entity : MC.level.entitiesForRendering()) {
      if (!(entity instanceof LivingEntity living)
          || entity == MC.player
          || !living.isAlive()
          || entity.isSpectator()
          || entity instanceof FakePlayerEntity) continue;
      if (entity instanceof Player player
          && (player.isCreative() || WURST.getFriends().contains(player.getScoreboardName())))
        continue;
      String id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
      if (!selected.contains(id) && !selected.contains(id.substring(id.indexOf(':') + 1))) continue;
      if (MC.player.distanceToSqr(entity) <= targetRange.getValueSq()) targets.add(living);
    }
  }

  private boolean pause(PauseMode process) {
    if (preferMode.getSelected() == PreferMode.PreferAnchor
        && WURST.getHax().autoAnchorHack.isBusy()) return true;
    if (pauseOnUse.getSelected().includes(process) && LeavesUtils.using(false)) return true;
    if (pauseOnMine.getSelected().includes(process)
        && ((MultiPlayerGameModeAccessor) MC.gameMode).getIsDestroying()) return true;
    return MC.player.getHealth() + MC.player.getAbsorptionAmount() <= pauseHealth.getValue()
        || pauseOnLag.isChecked() && System.nanoTime() - lastPacket > 1_000_000_000L;
  }

  private void doBreak() {
    if (!doBreak.isChecked()
        || breakTimer > 0
        || switchTimer > 0
        || attacks >= attackFrequency.getValueI()
        || pause(PauseMode.Break)) return;
    EndCrystal best = null;
    double damage = 0;
    for (Entity entity : MC.level.entitiesForRendering())
      if (entity instanceof EndCrystal crystal) {
        double value = breakDamage(crystal, true);
        if (value > damage) {
          damage = value;
          best = crystal;
        }
      }
    if (best != null) attack(best);
    else restoreWeapon();
  }

  private double breakDamage(EndCrystal crystal, boolean age) {
    if (!crystal.isAlive()
        || onlyBreakOwn.isChecked() && !own.contains(crystal.getId())
        || waiting.containsKey(crystal.getId())
        || attempts.getOrDefault(crystal.getId(), 0) >= breakAttempts.getValueI()
        || age && crystal.tickCount < ticksExisted.getValueI()) return 0;
    if (!inRange(crystal.position(), crystal.blockPosition(), false)
        || !safe(crystal.position(), crystal.blockPosition().below())) return 0;
    double damage = targetDamage(crystal.position(), crystal.blockPosition().below(), true, false);
    return damage >= minimum() ? damage : 0;
  }

  private void attack(EndCrystal crystal) {
    if (antiWeakness.isChecked()
        && MC.player.hasEffect(MobEffects.WEAKNESS)
        && !validWeapon(MC.player.getMainHandItem())) {
      int slot = -1;
      for (int i = 0; i < 9; i++)
        if (validWeapon(MC.player.getInventory().getItem(i))) {
          slot = i;
          break;
        }
      if (slot < 0) return;
      if (oldSlot < 0) oldSlot = MC.player.getInventory().getSelectedSlot();
      weaknessSlot = slot;
      MC.player.getInventory().setSelectedSlot(slot);
      net.wurstclient.leaves.LeavesSlots.INSTANCE.select(slot);
      switchTimer = Math.max(1, switchDelay.getValueI());
      return;
    }
    if (rotate.isChecked()) LeavesUtils.look(crystal.position());
    try {
      MC.gameMode.attack(MC.player, crystal);
      WURST.getHax().globalSettingHack.swing(true);
    } finally {
      if (rotate.isChecked()) LeavesUtils.restoreLook();
      restoreWeapon();
    }
    attacks++;
    lastActionTick = MC.player.tickCount;
    breakTimer = breakDelay.getValueI();
    attempts.merge(crystal.getId(), 1, Integer::sum);
    waiting.put(crystal.getId(), 0);
  }

  private boolean validWeapon(ItemStack stack) {
    double value = MC.player.getAttributeBaseValue(Attributes.ATTACK_DAMAGE);
    value += net.wurstclient.util.V152ItemCompat.weaponDamage(stack);
    if (MC.player.hasEffect(MobEffects.STRENGTH))
      value += 3 * (MC.player.getEffect(MobEffects.STRENGTH).getAmplifier() + 1);
    if (MC.player.hasEffect(MobEffects.WEAKNESS))
      value -= 4 * (MC.player.getEffect(MobEffects.WEAKNESS).getAmplifier() + 1);
    return value > 0;
  }

  private void restoreWeapon() {
    if (oldSlot >= 0
        && MC.player != null
        && world == MC.level
        && MC.player.getInventory().getSelectedSlot() == weaknessSlot) {
      MC.player.getInventory().setSelectedSlot(oldSlot);
      net.wurstclient.leaves.LeavesSlots.INSTANCE.select(oldSlot);
    }
    oldSlot = weaknessSlot = -1;
  }

  private void doPlace() {
    if (!doPlace.isChecked() || placeTimer > 0 || pause(PauseMode.Place)) {
      renderPos = null;
      return;
    }
    if (!hasItem(Items.END_CRYSTAL, true)) {
      renderPos = null;
      return;
    }
    for (Entity entity : MC.level.entitiesForRendering())
      if (entity instanceof EndCrystal crystal && breakDamage(crystal, false) > 0) return;
    BlockPos best = null;
    boolean bestSupport = true;
    double damage = 0;
    for (BlockPos mutable :
        BlockUtils.getAllInBox(MC.player.blockPosition(), placeRange.getValueCeil())) {
      BlockPos pos = mutable.immutable();
      var state = MC.level.getBlockState(pos);
      boolean existing = state.is(Blocks.OBSIDIAN) || state.is(Blocks.BEDROCK);
      if (!existing
          && (support.getSelected() == SupportMode.Disabled
              || !state.canBeReplaced()
              || BlockPlacer.getBlockPlacingParams(pos) == null
              || !hasItem(Items.OBSIDIAN, false))) continue;
      if (!MC.level.isEmptyBlock(pos.above())
          || placement112.isChecked() && !MC.level.isEmptyBlock(pos.above(2))) continue;
      Vec3 explosion = new Vec3(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
      if (!inRange(explosion, pos.above(), true)) continue;
      AABB bounds =
          new AABB(
              pos.getX(),
              pos.getY() + 1,
              pos.getZ(),
              pos.getX() + 1,
              pos.getY() + (placement112.isChecked() ? 2 : 3),
              pos.getZ() + 1);
      if (!MC.level
          .getEntities(
              (Entity) null, bounds, e -> !e.isSpectator() && !waiting.containsKey(e.getId()))
          .isEmpty()) continue;
      if (!safe(explosion, pos)) continue;
      double candidate =
          targetDamage(
              explosion, pos, false, !existing && support.getSelected() == SupportMode.Fast);
      if (candidate < minimum()) continue;
      if (best == null
          || existing && bestSupport
          || (existing == !bestSupport) && candidate > damage) {
        best = pos;
        bestSupport = !existing;
        damage = candidate;
      }
    }
    renderPos = best;
    shownDamage = damage;
    if (best == null) return;
    if (bestSupport) {
      if (!LeavesUtils.place(
          best,
          Items.OBSIDIAN,
          placeRange.getValue(),
          inventorySwap.isChecked(),
          rotate.isChecked())) return;
      placeTimer = supportDelay.getValueI() + placeDelay.getValueI();
      if (supportDelay.getValueI() > 0) {
        lastActionTick = MC.player.tickCount;
        return;
      }
    }
    if (placeCrystal(best)) {
      lastActionTick = MC.player.tickCount;
      placeTimer += placeDelay.getValueI();
    }
  }

  private boolean hasItem(Item item, boolean offhand) {
    if (MC.player.getMainHandItem().is(item) || offhand && MC.player.getOffhandItem().is(item))
      return true;
    return autoSwitch.getSelected() != AutoSwitchMode.None
        && InventoryUtils.indexOf(item, inventorySwap.isChecked() ? 36 : 9) >= 0;
  }

  private boolean placeCrystal(BlockPos base) {
    InteractionHand hand =
        MC.player.getOffhandItem().is(Items.END_CRYSTAL)
            ? InteractionHand.OFF_HAND
            : InteractionHand.MAIN_HAND;
    int slot = InventoryUtils.indexOf(Items.END_CRYSTAL, inventorySwap.isChecked() ? 36 : 9);
    InventorySwap swap = null;
    if (hand == InteractionHand.MAIN_HAND && autoSwitch.getSelected() != AutoSwitchMode.None) {
      if (autoSwitch.getSelected() == AutoSwitchMode.Normal && !inventorySwap.isChecked()) {
        if (slot < 0) return false;
        MC.player.getInventory().setSelectedSlot(slot);
        net.wurstclient.leaves.LeavesSlots.INSTANCE.select(slot);
      } else {
        swap = InventorySwap.acquire(slot);
        if (swap == null) return false;
      }
    }
    try {
      if (!MC.player.getItemInHand(hand).is(Items.END_CRYSTAL)) return false;
      BlockHitResult hit = hit(base);
      if (rotate.isChecked()) LeavesUtils.look(hit.getLocation());
      IMC.getInteractionManager().sendPlayerInteractBlockPacket(hand, hit);
      WURST.getHax().globalSettingHack.swing(false);
      pendingPlace = base.above();
      pendingTicks = 4;
      return true;
    } finally {
      if (rotate.isChecked()) LeavesUtils.restoreLook();
      if (swap != null) swap.close();
    }
  }

  private BlockHitResult hit(BlockPos base) {
    for (Direction face : Direction.values()) {
      Vec3 end = Vec3.atCenterOf(base).add(Vec3.atLowerCornerOf(face.getUnitVec3i()).scale(0.5));
      BlockHitResult result =
          MC.level.clip(
              new ClipContext(
                  MC.player.getEyePosition(),
                  end,
                  ClipContext.Block.COLLIDER,
                  ClipContext.Fluid.NONE,
                  MC.player));
      if (result.getType() == HitResult.Type.BLOCK && result.getBlockPos().equals(base))
        return result;
    }
    Direction face = base.getY() > MC.player.getEyeY() ? Direction.DOWN : Direction.UP;
    return new BlockHitResult(
        Vec3.atCenterOf(base).add(0, face == Direction.UP ? 0.5 : -0.5, 0), face, base, false);
  }

  private boolean safe(Vec3 source, BlockPos base) {
    double damage = LeavesDamage.crystal(source, MC.player, base, predictMovement.isChecked());
    return damage <= maxDamage.getValue()
        && (!antiSuicide.isChecked()
            || damage < MC.player.getHealth() + MC.player.getAbsorptionAmount());
  }

  private double targetDamage(Vec3 source, BlockPos base, boolean breaking, boolean fast) {
    double total = 0, best = 0;
    List<LivingEntity> tested =
        fast
            ? targets.stream().min(Comparator.comparingDouble(MC.player::distanceToSqr)).stream()
                .toList()
            : targets;
    for (LivingEntity target : tested) {
      if (smartDelay.isChecked() && breaking && target.hurtTime > 0) continue;
      double value = LeavesDamage.crystal(source, target, base, predictMovement.isChecked());
      total += value;
      if (value > best) {
        best = value;
        bestTarget = target;
      }
    }
    return total;
  }

  private double minimum() {
    if (!facePlace.isChecked()) return minDamage.getValue();
    boolean force = false;
    try {
      String key = forceFacePlace.getValue();
      if (!key.isBlank())
        force =
            InputConstants.isKeyDown(
                MC.getWindow().getWindow(), InputConstants.getKey(key).getValue());
    } catch (IllegalArgumentException ignored) {
    }
    if (force) return Math.min(1.5, minDamage.getValue());
    for (LivingEntity target : targets) {
      if (target.getHealth() + target.getAbsorptionAmount() <= facePlaceHealth.getValue())
        return Math.min(1.5, minDamage.getValue());
      for (ItemStack armor : net.wurstclient.util.V152ItemCompat.armorSlots(target))
        if (armor.isEmpty()
            ? facePlaceArmor.isChecked()
            : armor.isDamageableItem()
                && 100D * (armor.getMaxDamage() - armor.getDamageValue()) / armor.getMaxDamage()
                    <= facePlaceDurability.getValue()) return Math.min(1.5, minDamage.getValue());
    }
    return minDamage.getValue();
  }

  private boolean inRange(Vec3 position, BlockPos block, boolean placing) {
    var result =
        MC.level.clip(
            new ClipContext(
                MC.player.getEyePosition(),
                position,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                MC.player));
    boolean visible = result.getType() == HitResult.Type.MISS || result.getBlockPos().equals(block);
    double reach =
        (placing
                ? (visible ? placeRange : placeWallsRange)
                : (visible ? breakRange : breakWallsRange))
            .getValue();
    return MC.player.position().distanceToSqr(position) <= reach * reach;
  }

  public void onCrystalAdded(EndCrystal crystal) {
    if (!isEnabled() || !LeavesUtils.ready() || world != MC.level) return;
    if (pendingTicks > 0 && crystal.blockPosition().equals(pendingPlace)) {
      own.add(crystal.getId());
      pendingPlace = null;
      pendingTicks = 0;
    }
    if (fastBreak.isChecked()
        && doBreak.isChecked()
        && lastActionTick != MC.player.tickCount
        && attacks < attackFrequency.getValueI()
        && breakTimer == 0
        && switchTimer == 0
        && !pause(PauseMode.Break)
        && breakDamage(crystal, true) > 0) attack(crystal);
  }

  @Override
  public void onReceivedPacket(PacketInputEvent event) {
    lastPacket = System.nanoTime();
  }

  @Override
  public void onSentPacket(PacketOutputEvent event) {
    if (event.getPacket() instanceof ServerboundSetCarriedItemPacket)
      switchTimer = switchDelay.getValueI();
  }

  @Override
  public String getRenderName() {
    return getName() + (bestTarget == null ? "" : " [" + bestTarget.getName().getString() + "]");
  }

  @Override
  public void onRender(PoseStack matrices, float partialTicks) {
    if (renderPos == null || MC.level == null) {
      animated = null;
      return;
    }
    Vec3 target = Vec3.atLowerCornerOf(renderPos);
    animated =
        animated == null
            ? target
            : animated.add(target.subtract(animated).scale(renderSpeed.getValue()));
    if (render.isChecked()) {
      AABB box = new AABB(animated.add(0, 1 - renderH.getValue(), 0), animated.add(1, 1, 1));
      if (shapeMode.getSelected() != PrinterHack.Shape.LINES)
        RenderUtils.drawSolidBox(matrices, box, sideColor.getColorI(), false);
      if (shapeMode.getSelected() != PrinterHack.Shape.SIDES)
        RenderUtils.drawOutlinedBox(matrices, box, lineColor.getColorI(), false);
    }
    if (renderDmg.isChecked())
      LeavesRender.text(
          matrices,
          animated.add(0.5, 1 - renderH.getValue() / 2, 0.5),
          String.format(Locale.ROOT, "%.1f", shownDamage),
          dmgColor.getColorI(),
          1);
  }

  public enum PreferMode {
    PreferCrystal,
    PreferAnchor
  }

  public enum AutoSwitchMode {
    Normal,
    Silent,
    None
  }

  public enum SupportMode {
    Disabled,
    Accurate,
    Fast
  }

  public enum PauseMode {
    Both,
    Place,
    Break,
    None;

    boolean includes(PauseMode process) {
      return this == Both || this == process;
    }
  }
}
