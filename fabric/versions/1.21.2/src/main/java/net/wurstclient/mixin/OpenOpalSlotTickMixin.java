/* Adapted directly from OpenOpal (GPL-3.0); see META-INF/licenses/openopal/LICENSE. */
package net.wurstclient.mixin;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.wurstclient.opal.SlotHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(MultiPlayerGameMode.class)
public class OpenOpalSlotTickMixin {
    @Inject(method = "tick()V", at = @At("HEAD"))
    private void opalTick(CallbackInfo ci) { SlotHelper.getInstance().tick(); }
}
