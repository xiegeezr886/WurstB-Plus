/* Hotbar selection adapted from LeavesHack 13daf96. SPDX-License-Identifier: AGPL-3.0-or-later */
package net.wurstclient.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.Gui;
import net.minecraft.world.entity.player.Inventory;
import net.wurstclient.leaves.LeavesSlots;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Gui.class)
public class LeavesHotbarMixin {
  @WrapOperation(
      method = "renderHotbar",
      at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/player/Inventory;selected:I"))
  private int leaves$serverSlot(Inventory inventory, Operation<Integer> original) {
    return LeavesSlots.INSTANCE.displaySlot(original.call(inventory));
  }
}
