/* Adapted directly from OpenOpal (GPL-3.0); see META-INF/licenses/openopal/LICENSE. */
package net.wurstclient.mixin;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.wurstclient.opal.SlotHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Minecraft.class)
public class OpenOpalSlotInputMixin {
    @Redirect(method = "handleKeybinds()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;setSelectedSlot(I)V"))
    private void opalSlot(Inventory inventory, int slot) {
        if(!SlotHelper.getInstance().onSelect(slot)) inventory.setSelectedSlot(slot);
    }
    @Inject(method = "handleKeybinds()V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z", ordinal = 0))
    private void opalSyncSlot(CallbackInfo ci) { SlotHelper.getInstance().sync(false, false); }
}
