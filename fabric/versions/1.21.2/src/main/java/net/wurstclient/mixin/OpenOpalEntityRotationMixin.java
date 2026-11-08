/* OpenOpal EntityMixin rotation invalidation; GPL-3.0. */
package net.wurstclient.mixin;
import net.minecraft.world.entity.Entity;
import net.wurstclient.WurstClient;
import net.wurstclient.opal.rotation.RotationHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Entity.class)
public class OpenOpalEntityRotationMixin {
    @Inject(method = {"setYRot(F)V", "setXRot(F)V"}, at = @At("HEAD"))
    private void opalRotationSet(float value, CallbackInfo ci) {
        if((Object)this == WurstClient.MC.player) RotationHelper.getClientHandler().onRotationSet();
    }
}
