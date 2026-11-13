/* Adapted directly from OpenOpal (GPL-3.0); see META-INF/licenses/openopal/LICENSE. */
package net.wurstclient.mixin;
import net.minecraft.client.gui.Gui;
import net.minecraft.world.item.ItemStack;
import net.wurstclient.WurstClient;
import net.wurstclient.opal.SlotHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
@Mixin(Gui.class)
public class OpenOpalSlotHudMixin {
    @ModifyExpressionValue(method = "*", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/player/Inventory;selected:I", opcode = org.objectweb.asm.Opcodes.GETFIELD))
    private int opalHotbar(int actual) {
        var helper = SlotHelper.getInstance();
        return helper.isActive() && helper.getSilence() == SlotHelper.Silence.FULL
            ? helper.getVisualSlot() : actual;
    }
    @ModifyExpressionValue(method = "*", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/player/Inventory;getSelected()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack opalName(ItemStack actual) {
        var helper = SlotHelper.getInstance();
        return helper.isActive() && helper.getSilence() == SlotHelper.Silence.FULL
            ? helper.getMainHandStack(WurstClient.MC.player) : actual;
    }
}
