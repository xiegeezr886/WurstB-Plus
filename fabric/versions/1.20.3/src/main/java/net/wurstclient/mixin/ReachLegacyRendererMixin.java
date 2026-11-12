/* Adapted from Meteor Client. SPDX-License-Identifier: GPL-3.0 */
package net.wurstclient.mixin;

import net.minecraft.client.renderer.GameRenderer;
import net.wurstclient.WurstClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(GameRenderer.class)
public abstract class ReachLegacyRendererMixin
{
	@ModifyConstant(method = "pick(F)V", constant = @Constant(doubleValue = 6.0),
		require = 0)
	private double wurstEntitySearchDistance(double original)
	{
		var hax = WurstClient.INSTANCE.getHax();
		return hax == null || !hax.reachHack.isEnabled() ? original
			: hax.reachHack.getEntityRange();
	}
}
