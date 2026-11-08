/* Adapted directly from OpenOpal (GPL-3.0); see META-INF/licenses/openopal/LICENSE. */
package net.wurstclient.mixin;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.item.ItemStack;
import net.wurstclient.WurstClient;
import net.wurstclient.opal.SlotHelper;
import net.wurstclient.opal.rotation.RotationHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
@Mixin(ItemInHandRenderer.class)
public class OpenOpalHandsMixin {
    @ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/player/LocalPlayer;getMainHandItem()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack opalHeldItem(ItemStack actual) {
        return SlotHelper.getInstance().getMainHandStack(WurstClient.MC.player);
    }
    @ModifyExpressionValue(method = "submitHandsWithItems", at = @At(value = "FIELD",
        target = "Lnet/minecraft/client/player/LocalPlayer;xBobO:F"))
    private float opalxBobO(float original) { return RotationHelper.getClientHandler().getLastRenderPitchOr(original); }
    @ModifyExpressionValue(method = "submitHandsWithItems", at = @At(value = "FIELD",
        target = "Lnet/minecraft/client/player/LocalPlayer;xBob:F"))
    private float opalxBob(float original) { return RotationHelper.getClientHandler().getRenderPitchOr(original); }
    @ModifyExpressionValue(method = "submitHandsWithItems", at = @At(value = "FIELD",
        target = "Lnet/minecraft/client/player/LocalPlayer;yBobO:F"))
    private float opalyBobO(float original) { return RotationHelper.getClientHandler().getLastRenderYawOr(original); }
    @ModifyExpressionValue(method = "submitHandsWithItems", at = @At(value = "FIELD",
        target = "Lnet/minecraft/client/player/LocalPlayer;yBob:F"))
    private float opalyBob(float original) { return RotationHelper.getClientHandler().getRenderYawOr(original); }
    @ModifyExpressionValue(method = "submitHandsWithItems", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/player/LocalPlayer;getViewXRot(F)F"))
    private float opalX(float original) { return RotationHelper.getClientHandler().getPitchOr(original); }
    @ModifyExpressionValue(method = "submitHandsWithItems", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/player/LocalPlayer;getViewYRot(F)F"))
    private float opalY(float original) { return RotationHelper.getClientHandler().getYawOr(original); }
}
