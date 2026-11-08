/* OpenOpal PostMoveEvent adapter; GPL-3.0. */
package net.wurstclient.mixin;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.WurstClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(LocalPlayer.class)
public class OpenOpalPostMoveMixin {
    @Inject(method = "move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V", at = @At("RETURN"))
    private void opalPostMove(MoverType type, Vec3 delta, CallbackInfo ci) {
        if(!WurstClient.INSTANCE.isEnabled()) return;
        var scaffold = WurstClient.INSTANCE.getHax().scaffoldWalkHack;
        if(scaffold.isEnabled()) scaffold.onOpalPostMove();
    }
}
