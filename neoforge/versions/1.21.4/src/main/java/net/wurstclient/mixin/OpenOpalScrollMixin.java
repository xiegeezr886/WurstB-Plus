/* Adapted directly from OpenOpal (GPL-3.0); see META-INF/licenses/openopal/LICENSE. */
package net.wurstclient.mixin;
import net.minecraft.client.MouseHandler;
import net.minecraft.world.entity.player.Inventory;
import net.wurstclient.opal.SlotHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
@Mixin(MouseHandler.class)
public class OpenOpalScrollMixin {
    @WrapOperation(method = "onScroll(JDD)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;setSelectedHotbarSlot(I)V"))
    private void opalScroll(Inventory inventory, int slot, Operation<Void> original) {
        if(!SlotHelper.getInstance().onScrollTo(slot, inventory.selected)) original.call(inventory, slot);
    }
}
