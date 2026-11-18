/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
package net.wurstclient.hacks;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.events.PacketInputListener;
import net.wurstclient.events.PostMotionListener;
import net.wurstclient.events.PreMotionListener;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.hack.HackConflictGroup;
import net.wurstclient.hacks.aura.AuraAttackClock;
import net.wurstclient.hacks.aura.OpenAura;
import net.wurstclient.hacks.aura.RavenTargeting;
import net.wurstclient.hacks.aura.RavenTargeting.Priority;
import net.wurstclient.hacks.aura.RavenTargeting.Target;
import net.wurstclient.neoraven.RavenRotation;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.SwingHandSetting.SwingHand;
import net.wurstclient.util.EntityUtils;
import net.wurstclient.util.MotionActionQueue;
import net.wurstclient.util.RenderUtils;
import net.wurstclient.util.Rotation;
import net.wurstclient.util.RotationQueue;

/**
 * Epsilon range/cooldown/rotation pipeline, enhanced with NeoRaven priorities, identity-based
 * switching, sensitivity quantization and optional smoothing. Adapted to Fabric 1.20.1; see
 * docs/KILLAURA-NEORAVEN-EPSILON-v1.5.2.md.
 */
public final class KillauraHack extends Hack
    implements UpdateListener,
        PreMotionListener,
        PostMotionListener,
        RenderListener,
        PacketInputListener {
  private final EnumSetting<Mode> mode = new EnumSetting<>("Mode", Mode.values(), Mode.SWITCH);

  private final SliderSetting range =
      new SliderSetting(
          "Range", "How far the aura can attack.", 3, 3, 6, 0.1, ValueDisplay.DECIMAL);
  private final SliderSetting rotationRange =
      new SliderSetting(
          "Rotation range",
          "How far the aura will look at targets without attacking.",
          5,
          3,
          8,
          0.1,
          ValueDisplay.DECIMAL);
  private final SliderSetting swingRange =
      new SliderSetting(
          "Swing range",
          "Range used for fake swings when nothing is being attacked.",
          5,
          3,
          8,
          0.1,
          ValueDisplay.DECIMAL);

  private final EnumSetting<CpsMode> cpsMode =
      new EnumSetting<>(
          "CPS mode",
          "Auto follows the weapon's attack speed; Manual uses a fixed CPS.",
          CpsMode.values(),
          CpsMode.AUTO);
  private final SliderSetting attackCps =
      new SliderSetting(
              "Attack CPS",
              "Attacks per second in manual mode.",
              10,
              1,
              20,
              1,
              ValueDisplay.INTEGER)
          .visibleWhen(() -> cpsMode.getSelected() == CpsMode.MANUAL);
  private final SliderSetting swingCps =
      new SliderSetting("Swing CPS", "Fake swings per second.", 10, 1, 20, 1, ValueDisplay.INTEGER);
  private final CheckboxSetting hideFakeSwings = new CheckboxSetting("Hide fake swings", true);

  private final CheckboxSetting requireWeapon =
      new CheckboxSetting(
          "Require weapon", "Only attacks while holding a sword, axe or pickaxe.", false);
  private final CheckboxSetting requireAttackKey =
      new CheckboxSetting(
          "Require attack key", "Only attacks while the attack key is held.", false);
  private final CheckboxSetting hitSelect =
      new CheckboxSetting(
          "Hit Select",
          "Times attacks using target hurt time, latency and the critical-hit window.",
          false);

  private final RavenTargeting targeting = new RavenTargeting();
  private final EnumSetting<Priority> priority =
      new EnumSetting<>("Target priority", Priority.values(), Priority.DISTANCE);
  private final SliderSetting switchDelay =
      new SliderSetting(
              "Switch delay",
              "Time to hold each target in Switch mode.",
              300,
              0,
              2000,
              50,
              ValueDisplay.INTEGER)
          .visibleWhen(() -> mode.getSelected() == Mode.SWITCH);
  private final CheckboxSetting targetInvisible = new CheckboxSetting("Target invisible", true);
  private final CheckboxSetting targetTeammates = new CheckboxSetting("Target teammates", true);
  private final MotionActionQueue<Target> actions = new MotionActionQueue<>();

  private final CheckboxSetting targetPlayers =
      new CheckboxSetting("Target players", "Attacks other players.", true);
  private final CheckboxSetting targetHostile =
      new CheckboxSetting("Target hostile", "Attacks hostile mobs.", false);
  private final CheckboxSetting targetPassive =
      new CheckboxSetting("Target passive", "Attacks passive animals.", false);
  private final CheckboxSetting targetFriendly =
      new CheckboxSetting(
          "Target friendly", "Attacks other living entities (villagers, golems).", true);

  private final SliderSetting fov =
      new SliderSetting(
          "FOV", "Only targets inside this field of view.", 180, 1, 180, 1, ValueDisplay.DEGREES);
  private final CheckboxSetting overrideRaycast =
      new CheckboxSetting("Override raycast", "Aims exactly at the target before attacking.", true);
  private final CheckboxSetting tickLookahead =
      new CheckboxSetting(
          "Tick lookahead", "Uses one tick of look-ahead for moving targets.", false);
  private final CheckboxSetting throughWalls =
      new CheckboxSetting("Through Walls", "Attacks targets without line of sight.", false);

  private final EnumSetting<AutoblockMode> autoblock =
      new EnumSetting<>("Autoblock", AutoblockMode.values(), AutoblockMode.OFF);
  private final CheckboxSetting keepSprintFov =
      new CheckboxSetting("Keep Sprint FOV", "Keeps sprinting after an attack.", false);
  private final CheckboxSetting grimKeepSprint =
      new CheckboxSetting("Grim Keep Sprint", "Keeps sprinting after an attack (Grim).", false);
  private final CheckboxSetting smartWeapon =
      new CheckboxSetting("SmartWeapon", "Switches to an axe when the target is shielding.", false);

  private final CheckboxSetting visualsBox =
      new CheckboxSetting("Box", "Draws a box around the target.", false);
  private final CheckboxSetting visualsHalo =
      new CheckboxSetting("Halo", "Draws a ring around the target.", true);

  private final EnumSetting<RotationMode> rotationMode =
      new EnumSetting<>("Rotation", RotationMode.values(), RotationMode.SILENT);
  private final SliderSetting rotationSpeed =
      new SliderSetting("Rotation speed", 180, 1, 180, 1, ValueDisplay.DEGREES);
  private final SliderSetting rotationSmoothing =
      new SliderSetting("Rotation smoothing", 0, 0, 9.9, 0.1, ValueDisplay.DECIMAL);
  private final SliderSetting targetCount =
      new SliderSetting("Targets", 3, 1, 10, 1, ValueDisplay.INTEGER);
  private final CheckboxSetting disableMining = new CheckboxSetting("Disable while mining", true);
  private final CheckboxSetting attackCooldown19 =
      new CheckboxSetting("1.9+ Attack Cooldown", false);
  private Rotation previousRotation;
  private final AuraAttackClock attackClock = new AuraAttackClock();
  private final AuraAttackClock swingClock = new AuraAttackClock();
  private RotationQueue rotationQueue;
  private Target currentTarget;
  private Entity renderTarget;
  private boolean fakeBlocking;
  private net.minecraft.client.player.LocalPlayer blockingPlayer;
  private InteractionHand blockingHand;
  private ItemStack blockingStack;
  private int restoreSlot = -1, axeSlot = -1;
  private int grimKeepTicks, grimDamageKeepTicks, grimDamageWindowTicks;
  private Object statePlayer, stateWorld;

  private static long now() {
    return System.nanoTime() / 1_000_000L;
  }

  public KillauraHack() {
    super("Killaura");
    setCategory(Category.COMBAT);
    addConflictGroup(HackConflictGroup.COMBAT_TARGETING);

    addSetting(rotationMode);
    addSetting(rotationSpeed);
    addSetting(rotationSmoothing);
    addSetting(targetCount);
    addSetting(disableMining);
    addSetting(attackCooldown19);
    addSetting(mode);
    addSetting(priority);
    addSetting(switchDelay);
    addSetting(targetInvisible);
    addSetting(targetTeammates);
    addSetting(range);
    addSetting(rotationRange);
    addSetting(swingRange);
    addSetting(cpsMode);
    addSetting(attackCps);
    addSetting(swingCps);
    addSetting(hideFakeSwings);
    addSetting(requireWeapon);
    addSetting(requireAttackKey);
    addSetting(hitSelect);
    addSetting(targetPlayers);
    addSetting(targetHostile);
    addSetting(targetPassive);
    addSetting(targetFriendly);
    addSetting(fov);
    addSetting(overrideRaycast);
    addSetting(tickLookahead);
    addSetting(throughWalls);
    addSetting(autoblock);
    addSetting(keepSprintFov);
    addSetting(grimKeepSprint);
    addSetting(smartWeapon);
    addSetting(visualsBox);
    addSetting(visualsHalo);
  }

  @Override
  protected void onEnable() {
    currentTarget = null;
    renderTarget = null;
    actions.clear();
    fakeBlocking = false;
    targeting.reset();
    restoreWeapon();
    grimKeepTicks = grimDamageKeepTicks = grimDamageWindowTicks = 0;
    attackClock.reset();
    swingClock.reset();
    previousRotation = null;
    rotationQueue = new RotationQueue(RotationQueue.Priority.COMBAT);
    rotationQueue.start();
    EVENTS.add(PacketInputListener.class, this);
    EVENTS.add(PreMotionListener.class, this);
    EVENTS.add(PostMotionListener.class, this);
    EVENTS.add(RenderListener.class, this);
  }

  @Override
  protected void onDisable() {
    EVENTS.remove(PacketInputListener.class, this);
    EVENTS.remove(PreMotionListener.class, this);
    EVENTS.remove(PostMotionListener.class, this);
    EVENTS.remove(RenderListener.class, this);
    if (rotationQueue != null) {
      rotationQueue.stop();
      rotationQueue = null;
    }
    stopBlocking();
    restoreWeapon();
    currentTarget = null;
    renderTarget = null;
    actions.clear();
    targeting.reset();
    grimKeepTicks = grimDamageKeepTicks = grimDamageWindowTicks = 0;
    attackClock.reset();
    swingClock.reset();
    previousRotation = null;
  }

  @Override
  public void onUpdate() {
    if (statePlayer != MC.player || stateWorld != MC.level) {
      clearTarget();
      attackClock.reset();
      swingClock.reset();
      grimDamageKeepTicks = grimDamageWindowTicks = 0;
      statePlayer = MC.player;
      stateWorld = MC.level;
    }
    if (grimKeepTicks > 0) grimKeepTicks--;
    if (grimDamageKeepTicks > 0) grimDamageKeepTicks--;
    if (grimDamageWindowTicks > 0) grimDamageWindowTicks--;
    actions.clear();
    if (!requirementsMet()) {
      clearTarget();
      return;
    }
    actions.begin(MC.player, MC.level, MC.player.tickCount);
    planTargets();
    updateAutoblock();
  }

  private void planTargets() {
    currentTarget =
        targeting.select(
            targetConfig(),
            priority.getSelected(),
            mode.getSelected() == Mode.SWITCH,
            switchDelay.getValueI(),
            targetCount.getValueI(),
            now());
    renderTarget = currentTarget == null ? null : currentTarget.entity();
    if (currentTarget == null) {
      rotationQueue.clear();
      previousRotation = null;
      restoreWeapon();
      return;
    }
    actions.schedule(currentTarget);
  }

  @Override
  public void onPreMotion() {
    if (!requirementsMet() || !actions.isCurrent(MC.player, MC.level, MC.player.tickCount)) {
      clearTarget();
      return;
    }
    Target refreshed = targeting.inspect(currentTarget.entity(), targetConfig());
    if (refreshed == null) {
      clearTarget();
      return;
    }
    currentTarget = refreshed;
    actions.schedule(refreshed);
    if (refreshed.distance() > Math.max(getRange(), rotationRange.getValue())) {
      rotationQueue.clear();
      restoreWeapon();
      return;
    }
    Rotation aim = refreshed.rotation();
    if (tickLookahead.isChecked()) {
      Vec3 velocity = refreshed.entity().getDeltaMovement();
      // Prediction affects aim only; PostMotion checks the live hit box.
      if (velocity.lengthSqr() <= 1)
        aim = OpenAura.rotationTo(MC.player.getEyePosition(), refreshed.point().add(velocity));
    }
    if (rotationMode.getSelected() == RotationMode.NONE) {
      rotationQueue.clear();
      previousRotation = null;
    } else {
      Rotation from =
          previousRotation == null
              ? new Rotation(MC.player.getYRot(), MC.player.getXRot())
              : previousRotation;
      Rotation next =
          RavenRotation.quantize(
              from,
              RavenRotation.smooth(
                  from,
                  net.wurstclient.epsilon.EpsilonAim.step(from, aim, rotationSpeed.getValue()),
                  rotationSmoothing.getValue()),
              MC.options.sensitivity().get());
      previousRotation = next;
      rotationQueue.setRotation(next);
      if (rotationMode.getSelected() == RotationMode.LOCK_VIEW) {
        MC.player.setYRot(next.yaw());
        MC.player.setXRot(next.pitch());
      }
    }
    if (refreshed.distance() <= getRange()) prepareWeapon(refreshed.entity());
    else restoreWeapon();
  }

  @Override
  public void onPostMotion() {
    if (!requirementsMet()) {
      clearTarget();
      return;
    }
    Target target = actions.consume(MC.player, MC.level, MC.player.tickCount);
    if (target == null) return;
    Target live = targeting.inspect(target.entity(), targetConfig());
    if (live == null) {
      clearTarget();
      return;
    }
    if (live.distance() > getRange()) {
      tryFakeSwing(live);
      return;
    }
    if (rotationMode.getSelected() != RotationMode.NONE && !rotationQueue.wasApplied()) return;
    Rotation sent =
        rotationMode.getSelected() == RotationMode.NONE
            ? new Rotation(MC.player.getYRot(), MC.player.getXRot())
            : rotationQueue.getLastSent();
    if (!targeting.canHit(live, sent, targetConfig())) return;
    if (!overrideRaycast.isChecked()
        && (!(MC.hitResult instanceof EntityHitResult hit) || hit.getEntity() != live.entity()))
      return;
    if (MC.player.isUsingItem()
        && !ownsBlock()
        && !WURST.getHax().multitaskHack.attackingEntities()) return;
    if (ownsBlock() && MC.options.keyUse.isDown()) return;
    boolean modern = attackCooldown19.isChecked() || cpsMode.getSelected() == CpsMode.AUTO;
    long time = now();
    if (!attackClock.ready(
        time, MC.player.tickCount, modern, MC.player.getAttackStrengthScale(0.5F))) return;
    var velocity = WURST.getHax().antiKnockbackHack;
    if (velocity.isEnabled() && velocity.isAttacking()) return;
    if (hitSelect.isChecked()
        && live.entity() instanceof net.minecraft.world.entity.player.Player) {
      var info = MC.getConnection().getPlayerInfo(MC.player.getUUID());
      int latencyTicks = info == null ? 0 : info.getLatency() / 50;
      if (live.entity().hurtTime > latencyTicks + 1
          && !(MC.player.hurtTime >= 6 && !velocity.isEnabled())
          && WURST.getHax().criticalsHack.getFallTicks() != 2) return;
    }
    stopBlocking();
    boolean sprinting = MC.player.isSprinting();
    try {
      MC.gameMode.attack(MC.player, live.entity());
      MC.player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
      attackClock.record(
          time,
          MC.player.tickCount,
          attackCps.getValue(),
          java.util.concurrent.ThreadLocalRandom.current().nextInt(-4, 5));
      swingClock.record(time, MC.player.tickCount, swingCps.getValue(), 0);
      if ((keepSprintFov.isChecked() || grimKeepSprint.isChecked()) && sprinting) {
        grimKeepTicks = 2;
        MC.player.setSprinting(true);
      }
    } finally {
      restoreWeapon();
    }
    updateAutoblock();
  }

  public boolean shouldKeepSprint() {
    return isEnabled()
        && grimKeepSprint.isChecked()
        && (grimKeepTicks > 0 || grimDamageKeepTicks > 0);
  }

  @Override
  public void onReceivedPacket(PacketInputEvent event) {
    if (!grimKeepSprint.isChecked() || MC.player == null) return;
    var player = MC.player;
    var world = MC.level;
    boolean damage =
        event.getPacket() instanceof ClientboundDamageEventPacket p
            && p.entityId() == player.getId();
    boolean velocity =
        event.getPacket() instanceof ClientboundSetEntityMotionPacket p
            && p.id() == player.getId();
    if (!damage && !velocity) return;
    MC.execute(
        () -> {
          if (!isEnabled() || MC.player != player || MC.level != world) return;
          if (damage) grimDamageWindowTicks = 4;
          else if (grimDamageWindowTicks > 0) {
            grimDamageKeepTicks = Math.max(grimDamageKeepTicks, 6);
            grimDamageWindowTicks = 0;
          }
        });
  }

  @Override
  public void onRender(PoseStack matrices, float partialTicks) {
    if (!(renderTarget instanceof LivingEntity living) || !living.isAlive()) return;

    Vec3 pos = EntityUtils.getLerpedPos(living, partialTicks).subtract(RenderUtils.getCameraPos());
    int color = WURST.getGui().getTheme().accent(0.85F);

    if (visualsBox.isChecked()) OpenAura.drawBox(matrices, pos, living, color);
    if (visualsHalo.isChecked()) OpenAura.drawHalo(matrices, pos, living);
  }

  public Entity getCurrentTarget() {
    return renderTarget;
  }

  public boolean shouldRenderFakeBlock() {
    return isEnabled()
        && autoblock.getSelected() == AutoblockMode.FAKE
        && fakeBlocking
        && findBlockableHand() != null;
  }

  public InteractionHand getFakeBlockingHand() {
    return shouldRenderFakeBlock() ? findBlockableHand() : null;
  }

  private RavenTargeting.Config targetConfig() {
    return new RavenTargeting.Config(
        Math.max(swingRange.getValue(), Math.max(getRange(), rotationRange.getValue())),
        getRange(),
        fov.getValue(),
        targetFlags(),
        throughWalls.isChecked(),
        targetInvisible.isChecked(),
        targetTeammates.isChecked());
  }

  private int targetFlags() {
    int flags = 0;
    if (targetPlayers.isChecked()) flags |= OpenAura.TARGET_PLAYERS;
    if (targetHostile.isChecked()) flags |= OpenAura.TARGET_HOSTILE;
    if (targetPassive.isChecked()) flags |= OpenAura.TARGET_PASSIVE;
    if (targetFriendly.isChecked()) flags |= OpenAura.TARGET_FRIENDLY;
    return flags;
  }

  public double getRange() {
    return range.getValue();
  }

  private boolean requirementsMet() {
    if (MC.player == null || MC.level == null || MC.gui.screen() != null || MC.gui.overlay() != null)
      return false;
    if (MC.gameMode == null
        || !MC.player.isAlive()
        || MC.player.isSpectator()
        || MC.player.isPassenger()
        || WURST.getHax().scaffoldWalkHack.isEnabled()) return false;
    if (disableMining.isChecked() && MC.gameMode.isDestroying()) return false;
    if (isConsumingFoodOrPotion()) return false;
    if (requireAttackKey.isChecked() && !MC.options.keyAttack.isDown()) return false;
    if (requireWeapon.isChecked()) {
      ItemStack held = net.wurstclient.opal.SlotHelper.getInstance().getMainHandStack(MC.player);
      if (!held.is(ItemTags.SWORDS) && !held.is(ItemTags.AXES) && !held.is(ItemTags.PICKAXES))
        return false;
    }
    return true;
  }

  private boolean isConsumingFoodOrPotion() {
    if (MC.player == null || !MC.player.isUsingItem()) return false;
    ItemStack stack = MC.player.getUseItem();
    return !stack.isEmpty() && (stack.has(net.minecraft.core.component.DataComponents.FOOD) || stack.getItem() instanceof PotionItem);
  }

  private void tryFakeSwing(Target target) {
    if (target.distance() > swingRange.getValue() || MC.player.isUsingItem()) return;
    long time = now();
    if (!swingClock.ready(time, MC.player.tickCount, false, 1)) return;
    if (hideFakeSwings.isChecked()) SwingHand.SERVER.swing(InteractionHand.MAIN_HAND);
    else MC.player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
    swingClock.record(time, MC.player.tickCount, swingCps.getValue(), 0);
  }

  private void prepareWeapon(LivingEntity target) {
    if (!smartWeapon.isChecked() || !target.isBlocking()) {
      restoreWeapon();
      return;
    }
    if (restoreSlot >= 0 || net.wurstclient.opal.SlotHelper.getInstance().isActive()) return;
    if (MC.player.getMainHandItem().is(ItemTags.AXES)) return;
    for (int slot = 0; slot < 9; slot++)
      if (MC.player.getInventory().getItem(slot).is(ItemTags.AXES)) {
        restoreSlot = MC.player.getInventory().getSelectedSlot();
        axeSlot = slot;
        MC.player.getInventory().setSelectedSlot(slot);
        MC.player.connection.send(new ServerboundSetCarriedItemPacket(slot));
        MC.player.resetAttackStrengthTicker();
        return;
      }
  }

  private void restoreWeapon() {
    if (restoreSlot >= 0
        && MC.player != null
        && MC.player == statePlayer
        && MC.level == stateWorld
        && MC.player.getInventory().getSelectedSlot() == axeSlot) {
      MC.player.getInventory().setSelectedSlot(restoreSlot);
      MC.player.connection.send(new ServerboundSetCarriedItemPacket(restoreSlot));
      MC.player.resetAttackStrengthTicker();
    }
    restoreSlot = axeSlot = -1;
  }

  private void updateAutoblock() {
    if (autoblock.getSelected() != AutoblockMode.VANILLA) stopBlocking();
    fakeBlocking =
        autoblock.getSelected() == AutoblockMode.FAKE
            && currentTarget != null
            && currentTarget.distance() <= getRange()
            && findBlockableHand() != null;
    if (autoblock.getSelected() != AutoblockMode.VANILLA
        || currentTarget == null
        || currentTarget.distance() > getRange()) {
      if (!fakeBlocking) stopBlocking();
      return;
    }
    if (MC.player.isUsingItem()) return;
    InteractionHand hand = findBlockableHand();
    if (hand == null) {
      stopBlocking();
      return;
    }
    // 1.20.1 uses shield use rather than 1.8 sword-block packets.
    MC.gameMode.useItem(MC.player, hand);
    if (MC.player.isUsingItem() && MC.player.getUsedItemHand() == hand) {
      blockingPlayer = MC.player;
      blockingHand = hand;
      blockingStack = MC.player.getUseItem();
    }
  }

  private boolean ownsBlock() {
    return blockingPlayer != null
        && blockingPlayer == MC.player
        && stateWorld == MC.level
        && MC.player.isUsingItem()
        && MC.player.getUsedItemHand() == blockingHand
        && MC.player.getUseItem() == blockingStack;
  }

  /** Keep our shield raised through vanilla input processing without clicks. */
  public boolean shouldHoldUseKey() {
    return isEnabled()
        && autoblock.getSelected() == AutoblockMode.VANILLA
        && currentTarget != null
        && ownsBlock();
  }

  private void stopBlocking() {
    fakeBlocking = false;
    if (ownsBlock() && MC.gameMode != null && !MC.options.keyUse.isDown())
      MC.gameMode.releaseUsingItem(MC.player);
    blockingPlayer = null;
    blockingHand = null;
    blockingStack = null;
  }

  private InteractionHand findBlockableHand() {
    if (MC.player == null) return null;
    for (InteractionHand hand : InteractionHand.values()) {
      ItemStack stack = MC.player.getItemInHand(hand);
      if (stack.getUseAnimation() == ItemUseAnimation.BLOCK
          && !MC.player.getCooldowns().isOnCooldown(stack)) return hand;
    }
    return null;
  }

  private void clearTarget() {
    targeting.reset();
    restoreWeapon();
    grimKeepTicks = grimDamageKeepTicks = grimDamageWindowTicks = 0;
    previousRotation = null;
    currentTarget = null;
    renderTarget = null;
    actions.clear();
    if (rotationQueue != null) rotationQueue.clear();
    stopBlocking();
  }

  public enum RotationMode {
    SILENT,
    LOCK_VIEW,
    NONE
  }

  public enum CpsMode {
    AUTO("Auto"),
    MANUAL("Manual");

    private final String name;

    private CpsMode(String name) {
      this.name = name;
    }

    @Override
    public String toString() {
      return name;
    }
  }

  public enum Mode {
    SINGLE("Single"),
    SWITCH("Switch");

    private final String name;

    private Mode(String name) {
      this.name = name;
    }

    @Override
    public String toString() {
      return name;
    }
  }

  public enum AutoblockMode {
    OFF("Off"),
    VANILLA("Vanilla"),
    FAKE("Fake");

    private final String name;

    private AutoblockMode(String name) {
      this.name = name;
    }

    @Override
    public String toString() {
      return name;
    }
  }
}
