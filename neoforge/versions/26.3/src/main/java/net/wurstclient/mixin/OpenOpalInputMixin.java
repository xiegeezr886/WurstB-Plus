/* Adapted directly from OpenOpal (GPL-3.0); see META-INF/licenses/openopal/LICENSE. */
package net.wurstclient.mixin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.wurstclient.settings.SwingHandSetting.SwingHand;
import net.wurstclient.WurstClient;
import net.wurstclient.opal.MouseHelper;
import net.wurstclient.opal.MouseButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** OpenOpal MinecraftClientMixin event ordering, mapped to Mojmap. */
@Mixin(Minecraft.class)
public class OpenOpalInputMixin {
    @Inject(method = "tick()V", at = @At("HEAD"))
    private void opalPreTick(CallbackInfo ci) {
        if (WurstClient.MC == null || WurstClient.INSTANCE.getHax() == null) return;
        // preTick must still reset rotations when the client is disabled.
        net.wurstclient.opal.rotation.RotationHelper.preTick();
        if (!WurstClient.INSTANCE.isEnabled()) return;
        var hax = WurstClient.INSTANCE.getHax();
        if (hax.scaffoldWalkHack.isEnabled()) hax.scaffoldWalkHack.onUpdate();
        if (hax.killauraHack.isEnabled()) hax.killauraHack.onUpdate();
    }
    @Inject(method = "handleKeybinds()V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z", ordinal = 0))
    private void opalMouseInput(CallbackInfo ci) {
        if (!WurstClient.INSTANCE.isEnabled() || WurstClient.INSTANCE.getHax() == null) return;
        var hax = WurstClient.INSTANCE.getHax();
        if (hax.scaffoldWalkHack.isEnabled()) hax.scaffoldWalkHack.onOpalMouseInput();
        if (hax.killauraHack.isEnabled()) hax.killauraHack.onOpalMouseInput();
    }
    @Inject(method = "handleKeybinds()V", at = @At("TAIL"))
    private void opalInputTail(CallbackInfo ci) { MouseHelper.getInstance().tick(); }

    @Redirect(method = "handleKeybinds()V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/KeyMapping;isDown()Z"))
    private boolean opalIsPressed(KeyMapping key) {
        MouseButton button = MouseHelper.getButtonFromBinding(key);
        return button == null ? key.isDown() : button.isPressed();
    }
    @Redirect(method = "handleKeybinds()V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/KeyMapping;consumeClick()Z"),
        slice = @Slice(from = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z", ordinal = 0)))
    private boolean opalWasPressed(KeyMapping key) {
        MouseButton button = MouseHelper.getButtonFromBinding(key);
        return button == null ? key.consumeClick() : button.wasPressed();
    }
    @Redirect(method = "startAttack()Z", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/player/LocalPlayer;swing(Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/component/SwingAnimation;Z)Z"))
    private boolean opalLeftSwing(LocalPlayer player, InteractionHand hand,
        net.minecraft.world.item.component.SwingAnimation animation, boolean send) {
        return MouseHelper.getLeftButton().isShowSwings() && player.swing(hand, animation, send);
    }
    @Redirect(method = "startUseItem()V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/player/LocalPlayer;swing(Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/component/SwingAnimation;Z)Z"))
    private boolean opalRightSwing(LocalPlayer player, InteractionHand hand,
        net.minecraft.world.item.component.SwingAnimation animation, boolean send) {
        return MouseHelper.getRightButton().isShowSwings() && player.swing(hand, animation, send);
    }
}
