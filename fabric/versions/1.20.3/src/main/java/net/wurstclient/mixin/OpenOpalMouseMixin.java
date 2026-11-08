/* Adapted directly from OpenOpal (GPL-3.0); see META-INF/licenses/openopal/LICENSE. */
package net.wurstclient.mixin;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.wurstclient.WurstClient;
import net.wurstclient.opal.rotation.RotationHelper;
import net.wurstclient.opal.rotation.MouseUpdateEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

@Mixin(MouseHandler.class)
public class OpenOpalMouseMixin {
    @Shadow private double accumulatedDX;
    @Shadow private double accumulatedDY;
    @Unique private boolean opalHandled;
    @Inject(method = "turnPlayer()V", at = @At(value = "FIELD",
        target = "Lnet/minecraft/client/Options;smoothCamera:Z"))
    private void opalUpdate( CallbackInfo ci) {
        double sensitivity = WurstClient.MC.options.sensitivity().get() * 0.6F + 0.2F;
        double multiplier = sensitivity * sensitivity * sensitivity * 8.0D;
        MouseUpdateEvent event = new MouseUpdateEvent(accumulatedDX, accumulatedDY,
            multiplier, RotationHelper.getHandler().isUnlockCursor());
        RotationHelper.onMouseUpdate(event);
        accumulatedDX = event.getDeltaX();
        accumulatedDY = event.getDeltaY();
        opalHandled = event.isHandled();
    }
    @ModifyExpressionValue(method = "turnPlayer()V", at = @At(value = "FIELD",
        target = "Lnet/minecraft/client/Options;smoothCamera:Z"))
    private boolean opalSmooth(boolean original) { return original && !opalHandled; }
    @ModifyExpressionValue(method = "turnPlayer()V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/player/LocalPlayer;isScoping()Z"))
    private boolean opalScope(boolean original) { return original && !opalHandled; }
    @Inject(method = "turnPlayer()V", at = @At("RETURN"))
    private void opalTail( CallbackInfo ci) {
        RotationHelper.getClientHandler().onPostMouseUpdate();
        opalHandled = false;
    }
    @ModifyExpressionValue(method = "turnPlayer()V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/MouseHandler;isMouseGrabbed()Z"))
    private boolean opalCursor(boolean original) {
        return original || RotationHelper.getHandler().isForcingTick();
    }
}
