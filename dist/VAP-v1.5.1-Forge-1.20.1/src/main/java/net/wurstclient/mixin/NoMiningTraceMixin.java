/* SPDX-License-Identifier: GPL-3.0 */
package net.wurstclient.mixin;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;
import net.wurstclient.WurstClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(net.minecraft.client.renderer.GameRenderer.class)
public abstract class NoMiningTraceMixin
{
	@Inject(method = "pick(F)V", at = @At("TAIL"))
	private void wurstSelectBlock(float partialTicks, CallbackInfo ci)
	{
		Minecraft mc = Minecraft.getInstance();
		var hax = WurstClient.INSTANCE.getHax();
		if(hax == null || mc.gameMode == null)
			return;
		HitResult selected = hax.noMiningTraceHack.selectBlock(mc.hitResult,
			mc.getCameraEntity(), hax.reachHack.isEnabled()
				? hax.reachHack.getBlockRange() : mc.gameMode.getPickRange(), partialTicks);
		if(selected != mc.hitResult)
		{
			mc.hitResult = selected;
			mc.crosshairPickEntity = null;
		}
	}
}
