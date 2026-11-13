/* Adapted from Meteor Client. SPDX-License-Identifier: GPL-3.0 */
package net.wurstclient.mixin;

import net.minecraft.client.renderer.GameRenderer;
import net.wurstclient.WurstClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

@Mixin(GameRenderer.class)
public abstract class ReachLegacyRendererMixin
{
	// Compose with reach-entity-attributes instead of claiming its constant.
	@ModifyExpressionValue(method = "pick(F)V", at = @At(value = "CONSTANT", args = "doubleValue=6.0"),
		require = 0)
	private double wurstEntitySearchDistance(double original)
	{
		var hax = WurstClient.INSTANCE.getHax();
		return hax == null || !hax.reachHack.isEnabled() ? original
			: hax.reachHack.getEntityRange();
	}
}
