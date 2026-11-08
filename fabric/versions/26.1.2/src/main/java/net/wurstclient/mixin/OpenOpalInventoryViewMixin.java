/* OpenOpal InventoryScreenMixin visual rotation guard; GPL-3.0. */
package net.wurstclient.mixin;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.wurstclient.opal.rotation.RotationHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(InventoryScreen.class)
public class OpenOpalInventoryViewMixin {
    @Inject(method = "extractEntityInInventoryFollowsMouse", at = @At("HEAD"))
    private static void opalViewStart(CallbackInfo ci) { RotationHelper.getClientHandler().setTicking(true); }
    @Inject(method = "extractEntityInInventoryFollowsMouse", at = @At("RETURN"))
    private static void opalViewEnd(CallbackInfo ci) { RotationHelper.getClientHandler().setTicking(false); }
}
