/* SPDX-License-Identifier: GPL-3.0 */
package net.wurstclient.mixin;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;
import net.wurstclient.WurstClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
@Mixin(net.minecraft.client.renderer.GameRenderer.class)
public abstract class NoMiningTraceMixin
{
	@ModifyReturnValue(method = "pick(Lnet/minecraft/world/entity/Entity;DDF)Lnet/minecraft/world/phys/HitResult;", at = @At("RETURN"))
	private HitResult wurstSelectBlock(HitResult original, Entity camera,
		double blockRange, double entityRange, float partialTicks)
	{
		var hax = WurstClient.INSTANCE.getHax();
		return hax == null ? original : hax.noMiningTraceHack
			.selectBlock(original, camera, blockRange, partialTicks);
	}
}
