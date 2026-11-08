/* Adapted directly from OpenOpal (GPL-3.0); see META-INF/licenses/openopal/LICENSE. */
package net.wurstclient.mixin;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.wurstclient.WurstClient;
import net.wurstclient.opal.rotation.RotationHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
@Mixin(Camera.class)
public class OpenOpalCameraMixin {
    @Redirect(method = "setup", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/Entity;getViewYRot(F)F"))
    private float opalYaw(Entity entity, float delta) {
        return entity == WurstClient.MC.player
            ? RotationHelper.getClientHandler().getYawOr(entity.getViewYRot(delta)) : entity.getViewYRot(delta);
    }
    @Redirect(method = "setup", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/Entity;getViewXRot(F)F"))
    private float opalPitch(Entity entity, float delta) {
        return entity == WurstClient.MC.player
            ? RotationHelper.getClientHandler().getPitchOr(entity.getViewXRot(delta)) : entity.getViewXRot(delta);
    }
}
