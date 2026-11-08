/* OpenOpal final movement-rotation correction; GPL-3.0. */
package net.wurstclient.mixin;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(ServerboundMovePlayerPacket.class)
public interface OpenOpalMovementAccess {
    @Mutable @Accessor("yRot") void opal$setYaw(float yaw);
    @Mutable @Accessor("xRot") void opal$setPitch(float pitch);
}
