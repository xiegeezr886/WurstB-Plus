/* AutoRefreshTrade adapted from LeavesHack 13daf96.
 * SPDX-License-Identifier: AGPL-3.0-or-later */
package net.wurstclient.hacks;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Comparator;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.wurstclient.Category;
import net.wurstclient.events.*;
import net.wurstclient.hack.Hack;
import net.wurstclient.leaves.*;
import net.wurstclient.settings.*;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.*;

public final class AutoRefreshTradeHack extends Hack implements UpdateListener, RenderListener {
  private final SliderSetting range = new SliderSetting("Range", 5, 0, 12, 1, ValueDisplay.INTEGER);
  private final SliderSetting wallRange =
      new SliderSetting("WallRange", 5, 0, 12, 1, ValueDisplay.INTEGER);
  private final SliderSetting waitMine =
      new SliderSetting(
          "WaitMineDelay", 5000, 0, 10000, 50, ValueDisplay.INTEGER.withSuffix(" ms"));
  private final EnchantmentListSetting wanted = new EnchantmentListSetting("EnchantmentsList");
  private final SliderSetting level = new SliderSetting("Level", 3, 0, 5, 1, ValueDisplay.INTEGER);
  private BlockPos workstation;
  private Villager target;
  private Phase phase = Phase.OPEN;
  private long next;
  private Object world;

  public AutoRefreshTradeHack() {
    super("AutoRefreshTrade");
    setCategory(Category.PLAYER);
    for (Setting s : new Setting[] {range, wallRange, waitMine, wanted, level}) addSetting(s);
  }

  @Override
  protected void onEnable() {
    reset();
    EVENTS.add(UpdateListener.class, this);
    EVENTS.add(RenderListener.class, this);
  }

  @Override
  protected void onDisable() {
    EVENTS.remove(UpdateListener.class, this);
    EVENTS.remove(RenderListener.class, this);
    if (phase == Phase.BREAK && MC.gameMode != null) MC.gameMode.stopDestroyBlock();
    reset();
  }

  private void reset() {
    workstation = null;
    target = null;
    phase = Phase.OPEN;
    next = 0;
    world = MC.level;
  }

  @Override
  public void onUpdate() {
    if (world != MC.level) reset();
    if (MC.player == null || MC.level == null || MC.gameMode == null) return;
    if (MC.options.keyDown.isDown()) {
      setEnabled(false);
      return;
    }
    if (target != null
        && (!target.isAlive() || MC.player.distanceToSqr(target) > range.getValueSq())) {
      reset();
      return;
    }
    if (MC.gui.screen() instanceof MerchantScreen screen) {
      if (target == null || phase != Phase.OPEN) return;
      var offers = screen.getMenu().getOffers();
      // Offers arrive after the screen opens; an empty list is not a failed roll.
      if (offers.isEmpty()) return;
      boolean found = offers.stream().anyMatch(offer -> matches(offer.getResult()));
      MC.player.closeContainer();
      if (found) {
        ChatUtils.message("AutoRefreshTrade: found a requested enchantment.");
        setEnabled(false);
        return;
      }
      workstation = MC.player.blockPosition().relative(MC.player.getDirection());
      if (!MC.level.getBlockState(workstation).is(Blocks.LECTERN)) {
        ChatUtils.error("AutoRefreshTrade: face the lectern directly in front of you.");
        setEnabled(false);
        return;
      }
      if (target.getVillagerXp() > 0) {
        ChatUtils.error("AutoRefreshTrade: this villager's trades are already locked.");
        setEnabled(false);
        return;
      }
      phase = Phase.BREAK;
      next = 0;
    }
    if (!LeavesUtils.ready()) return;
    long now = System.currentTimeMillis();
    switch (phase) {
      case OPEN -> {
        if (now < next) return;
        if (target == null)
          target =
              MC
                  .level
                  .getEntitiesOfClass(
                      Villager.class,
                      MC.player.getBoundingBox().inflate(range.getValue()),
                      v ->
                          v.isAlive()
                              && !v.isBaby()
                              && MC.player.distanceToSqr(v) <= range.getValueSq()
                              && (MC.player.hasLineOfSight(v)
                                  || MC.player.distanceToSqr(v) <= wallRange.getValueSq()))
                  .stream()
                  .min(Comparator.comparingDouble(MC.player::distanceToSqr))
                  .orElse(null);
        if (target == null) return;
        LeavesUtils.look(target.getEyePosition());
        try {
          MC.gameMode.interact(MC.player, target, new net.minecraft.world.phys.EntityHitResult(target, target.getEyePosition()), InteractionHand.MAIN_HAND);
        } finally {
          LeavesUtils.restoreLook();
        }
        next = now + 500;
      }
      case BREAK -> {
        if (MC.level.getBlockState(workstation).isAir()) {
          phase = Phase.PLACE;
          next = now + waitMine.getValueI();
          return;
        }
        if (!MC.level.getBlockState(workstation).is(Blocks.LECTERN)) {
          setEnabled(false);
          return;
        }
        BlockBreaker.breakOneBlock(workstation);
      }
      case PLACE -> {
        if (now < next) return;
        if (MC.level.getBlockState(workstation).is(Blocks.LECTERN)) {
          phase = Phase.OPEN;
          next = now + 500;
          return;
        }
        if (InventoryUtils.indexOf(Items.LECTERN, 9) < 0) return;
        LeavesUtils.place(workstation, Items.LECTERN, range.getValue(), false, true);
        next = now + 250;
      }
    }
  }

  private boolean matches(ItemStack stack) {
    if (!stack.is(Items.ENCHANTED_BOOK)) return false;
    return EnchantmentHelper.getEnchantmentsForCrafting(stack).entrySet().stream()
        .anyMatch(
            e ->
                wanted.contains(e.getKey())
                    && qualifies(e.getIntValue(), level.getValueI(), e.getKey().value().getMaxLevel()));
  }

  public static boolean qualifies(int offered, int requested, int maximum) {
    return offered >= requested || offered == maximum;
  }

  @Override
  public void onRender(PoseStack matrices, float partialTicks) {
    if (workstation == null) return;
    RenderUtils.drawSolidBox(matrices, new AABB(workstation), 0x5032E8FC, false);
    RenderUtils.drawOutlinedBox(matrices, new AABB(workstation), 0xFF32E8FC, false);
  }

  private enum Phase {
    OPEN,
    BREAK,
    PLACE
  }
}
