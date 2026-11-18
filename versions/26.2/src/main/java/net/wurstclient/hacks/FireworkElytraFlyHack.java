/* FireworkElytraFly adapted from LeavesHack 13daf96 for 1.20.1.
 * SPDX-License-Identifier: AGPL-3.0-or-later */
package net.wurstclient.hacks;

import net.minecraft.network.protocol.game.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.events.*;
import net.wurstclient.hack.Hack;
import net.wurstclient.leaves.InventorySwap;
import net.wurstclient.leaves.LeavesUtils;
import net.wurstclient.mixin.LeavesFireworkAccessor;
import net.wurstclient.mixin.OpenOpalMovementAccess;
import net.wurstclient.settings.*;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.*;

public final class FireworkElytraFlyHack extends Hack
    implements UpdateListener, PacketOutputListener, MoveInputListener {
  private final EnumSetting<Mode> mode =
      new EnumSetting<>("Mode", Mode.values(), Mode.GrimDurability);
  private final EnumSetting<FireWorkMode> fireworks =
      new EnumSetting<>("FireWorkMode", FireWorkMode.values(), FireWorkMode.Auto);
  private final SliderSetting packetDelay =
      new SliderSetting("PacketDelay", 3, 0, 100, 1, ValueDisplay.INTEGER);
  private final CheckboxSetting stand = new CheckboxSetting("Stand", true),
      freePitch = new CheckboxSetting("FreePitch", false),
      noSprint = new CheckboxSetting("NoSprint", true),
      autoJump = new CheckboxSetting("AutoJump", true),
      releaseSneak = new CheckboxSetting("ReleaseSneak", true),
      pressSneak = new CheckboxSetting("PressSneak", true);
  private final SliderSetting
      releaseDelay =
          new SliderSetting(
              "ReleaseDelay", 100, 0, 1000, 1, ValueDisplay.INTEGER.withSuffix(" ms")),
      fireworkDelay =
          new SliderSetting(
              "FireWorkDelay", 1000, 0, 3000, 10, ValueDisplay.INTEGER.withSuffix(" ms"));
  private final CheckboxSetting checkFirework = new CheckboxSetting("CheckFireWork", true);
  private final SliderSetting checkTime =
      new SliderSetting("CheckTime", 30, 0, 50, 1, ValueDisplay.INTEGER);
  private final CheckboxSetting inventory = new CheckboxSetting("InventorySwap", true),
      control = new CheckboxSetting("Control", true);
  private final SliderSetting fallSpeed =
      new SliderSetting("FallSpeed", 0.02, 0, 3, 0.01, ValueDisplay.DECIMAL);
  private final CheckboxSetting
      horizontalNoGravity = new CheckboxSetting("HorizontalNoGravity", false),
      debug = new CheckboxSetting("DeBug", false);
  private float yaw, pitch;
  private int ticks;
  private long lastRocket, pendingUntil, releaseAt;
  private boolean usingRocket;
  private net.minecraft.client.player.LocalPlayer owner;

  public FireworkElytraFlyHack() {
    super("FireworkElytraFly");
    setCategory(Category.MOVEMENT);
    for (Setting s :
        new Setting[] {
          mode,
          fireworks,
          packetDelay,
          stand,
          freePitch,
          noSprint,
          autoJump,
          releaseSneak,
          pressSneak,
          releaseDelay,
          fireworkDelay,
          checkFirework,
          checkTime,
          inventory,
          control,
          fallSpeed,
          horizontalNoGravity,
          debug
        }) addSetting(s);
  }

  @Override
  protected void onEnable() {
    reset();
    releaseAt = 0;
    EVENTS.add(UpdateListener.class, this);
    EVENTS.add(PacketOutputListener.class, this);
    EVENTS.add(MoveInputListener.class, this);
    if (MC.player != null && noSprint.isChecked()) {
      MC.player.setSprinting(false);
      MC.player.connection.send(
          new ServerboundPlayerCommandPacket(
              MC.player, ServerboundPlayerCommandPacket.Action.STOP_SPRINTING));
    }
  }

  private void reset() {
    owner = MC.player;
    ticks = 0;
    lastRocket = pendingUntil = 0;
    usingRocket = false;
    yaw = owner == null ? 0 : owner.getYRot();
    pitch = owner == null ? 0 : owner.getXRot();
  }

  @Override
  protected void onDisable() {
    EVENTS.remove(PacketOutputListener.class, this);
    pendingUntil = 0;
    if (MC.player == owner && owner != null && pressSneak.isChecked()) {
      owner.connection.send(
          net.wurstclient.util.render.V152Input.sneak(owner, true));
      releaseAt =
          System.currentTimeMillis() + (releaseSneak.isChecked() ? releaseDelay.getValueI() : 0);
    } else detach();
  }

  private void detach() {
    EVENTS.remove(UpdateListener.class, this);
    EVENTS.remove(MoveInputListener.class, this);
    releaseAt = 0;
  }

  @Override
  public void onUpdate() {
    long now = System.currentTimeMillis();
    if (!isEnabled()) {
      if (owner != MC.player || now >= releaseAt) {
        if (owner == MC.player && owner != null && !MC.options.keyShift.isDown())
          owner.connection.send(
              net.wurstclient.util.render.V152Input.sneak(owner, false));
        detach();
      }
      return;
    }
    if (owner != MC.player) reset();
    if (!LeavesUtils.ready()) return;
    usingRocket = false;
    if (checkFirework.isChecked())
      for (var entity : MC.level.entitiesForRendering())
        if (entity instanceof FireworkRocketEntity rocket) {
          var access = (LeavesFireworkAccessor) rocket;
          if ((rocket.getOwner() == MC.player || access.leaves$getAttached() == MC.player)
              && access.leaves$getLife() <= checkTime.getValueI()) {
            usingRocket = true;
            pendingUntil = 0;
            break;
          }
        }
    if (noSprint.isChecked()) MC.player.setSprinting(false);
    if (MC.player.isInWater() || MC.player.isInLava() || MC.player.isPassenger()) return;
    if (MC.player.onGround()) {
      if (autoJump.isChecked()) MC.player.jumpFromGround();
      return;
    }
    updateRotation();
    ticks++;
    if (mode.getSelected() == Mode.Legit) {
      if (!usable(MC.player.getItemBySlot(EquipmentSlot.CHEST))) return;
      if (!MC.player.isFallFlying()) startFlying();
      useRocket();
    } else if (!MC.player.isFallFlying() && ticks > packetDelay.getValueI()) {
      int slot = -1;
      for (int i = 0; i < 36; i++)
        if (usable(MC.player.getInventory().getItem(i))) {
          slot = i;
          break;
        }
      if (slot < 0
          || net.wurstclient.util.V152ItemCompat.bindingCurse(MC.player.getItemBySlot(EquipmentSlot.CHEST)))
        return;
      int source = InventoryUtils.toNetworkSlot(slot);
      exchangeChest(source);
      try {
        startFlying();
        useRocket();
      } finally {
        exchangeChest(source);
      }
      ticks = 0;
    }
    if (debug.isChecked() && MC.player.tickCount % 20 == 0)
      ChatUtils.message("FireworkElytraFly: yaw=%s pitch=%s rocket=%s", yaw, pitch, usingRocket);
  }

  private static boolean usable(ItemStack stack) {
    return stack.is(Items.ELYTRA) && (stack.getDamageValue() < stack.getMaxDamage() - 1);
  }

  private void exchangeChest(int source) {
    int menu = MC.player.inventoryMenu.containerId;
    MC.gameMode.handleContainerInput(menu, source, 0, ContainerInput.PICKUP, MC.player);
    MC.gameMode.handleContainerInput(menu, 6, 0, ContainerInput.PICKUP, MC.player);
    if (!MC.player.inventoryMenu.getCarried().isEmpty())
      MC.gameMode.handleContainerInput(menu, source, 0, ContainerInput.PICKUP, MC.player);
  }

  private void startFlying() {
    MC.player.connection.send(
        new ServerboundPlayerCommandPacket(
            MC.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
    MC.player.startFallFlying();
  }

  private void useRocket() {
    long now = System.currentTimeMillis();
    if (fireworks.getSelected() == FireWorkMode.None || usingRocket || now < pendingUntil) return;
    if (fireworks.getSelected() == FireWorkMode.Delay
        && (!wantToMove() || now - lastRocket < fireworkDelay.getValue())) return;
    InteractionHand hand =
        MC.player.getOffhandItem().is(Items.FIREWORK_ROCKET)
            ? InteractionHand.OFF_HAND
            : InteractionHand.MAIN_HAND;
    try (InventorySwap swap =
        hand == InteractionHand.MAIN_HAND
            ? InventorySwap.acquire(
                InventoryUtils.indexOf(Items.FIREWORK_ROCKET, inventory.isChecked() ? 36 : 9))
            : null) {
      if (hand == InteractionHand.MAIN_HAND && swap == null) return;
      MC.player.connection.send(
          new ServerboundMovePlayerPacket.Rot(yaw, pitch, MC.player.onGround(), net.wurstclient.WurstClient.MC.player.horizontalCollision));
      MC.gameMode.useItem(MC.player, hand);
      lastRocket = now;
      pendingUntil = now + 500;
    }
  }

  private boolean horizontal() {
    return MC.options.keyUp.isDown()
        || MC.options.keyDown.isDown()
        || MC.options.keyLeft.isDown()
        || MC.options.keyRight.isDown();
  }

  private boolean wantToMove() {
    return horizontal() || MC.options.keyJump.isDown() || MC.options.keyShift.isDown();
  }

  private void updateRotation() {
    if (WURST.getHax().followHack.isFollowing()) return;
    int forward = (MC.options.keyUp.isDown() ? 1 : 0) - (MC.options.keyDown.isDown() ? 1 : 0),
        side = (MC.options.keyLeft.isDown() ? 1 : 0) - (MC.options.keyRight.isDown() ? 1 : 0);
    yaw = movementYaw(MC.player.getYRot(), forward, side);
    pitch = MC.player.getXRot();
    boolean up = MC.options.keyJump.isDown(), down = MC.options.keyShift.isDown();
    if (up && down) pitch = -3;
    else if (up) pitch = horizontal() ? -45 : -90;
    else if (down) pitch = horizontal() ? 45 : 90;
    else if (horizontal() && (!freePitch.isChecked() || !MC.options.keyUp.isDown()))
      pitch = horizontalNoGravity.isChecked() ? 0 : -1.9F;
  }

  public static float movementYaw(float yaw, int forward, int sideways) {
    return forward == 0 && sideways == 0
        ? yaw
        : yaw + (float) Math.toDegrees(Math.atan2(-sideways, forward));
  }

  public void setFlightRotation(float yaw, float pitch) {
    this.yaw = yaw;
    this.pitch = pitch;
  }

  public Vec3 flightDirection() {
    return Vec3.directionFromRotation(pitch, yaw);
  }

  public boolean controlsFlight() {
    return isEnabled() && control.isChecked() && MC.player != null && MC.player.isFallFlying();
  }

  public boolean standingFlight() {
    return isEnabled() && stand.isChecked();
  }

  public boolean requiresEquippedElytra() {
    return isEnabled() && mode.getSelected() == Mode.Legit;
  }

  public void onTravel() {
    if (controlsFlight()
        && !WURST.getHax().followHack.isFollowing()
        && (MC.gui.screen() != null || !wantToMove()))
      MC.player.setDeltaMovement(0, fallSpeed.getValue(), 0);
  }

  @Override
  public void onSentPacket(PacketOutputEvent event) {
    if (controlsFlight()
        && event.getPacket() instanceof ServerboundMovePlayerPacket packet
        && packet.hasRotation()) {
      var access = (OpenOpalMovementAccess) packet;
      access.opal$setYaw(yaw);
      access.opal$setPitch(pitch);
    }
  }

  @Override
  public void onMoveInput(MoveInputEvent event) {
    if (!isEnabled()) {
      if (owner == MC.player && releaseAt > System.currentTimeMillis()) event.setSneak(true);
      return;
    }
    if (controlsFlight()) {
      event.setForward(0);
      event.setSideways(0);
      event.setJump(false);
      event.setSneak(false);
    }
  }

  @Override
  public String getRenderName() {
    int count = 0;
    if (MC.player != null)
      for (int i = 0; i < (inventory.isChecked() ? 36 : 9); i++) {
        ItemStack stack = MC.player.getInventory().getItem(i);
        if (stack.is(Items.FIREWORK_ROCKET)) count += stack.getCount();
      }
    return getName() + " [F:" + count + "]";
  }

  public enum Mode {
    Legit,
    GrimDurability
  }

  public enum FireWorkMode {
    Auto,
    Delay,
    None
  }
}
