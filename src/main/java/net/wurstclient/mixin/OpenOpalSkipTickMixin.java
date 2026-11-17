/* Direct adaptation of OpenOpal ClientWorldMixin.hookSkipTicks (GPL-3.0). */
package net.wurstclient.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.wurstclient.WurstClient;
import net.wurstclient.opal.SkipTickUtility;

@Mixin(ClientLevel.class)
public class OpenOpalSkipTickMixin {
    @WrapOperation(method = "tickNonPassenger(Lnet/minecraft/world/entity/Entity;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;tick()V"))
    private void opalSkipTick(Entity entity, Operation<Void> original) {
        if(entity == WurstClient.MC.player && WurstClient.INSTANCE.isEnabled()
            && WurstClient.INSTANCE.getHax().scaffoldWalkHack.isEnabled()
            && SkipTickUtility.consumeSkipTick())
            return;
        original.call(entity);
    }
}
