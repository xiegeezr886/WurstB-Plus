package net.wurstclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.wurstclient.WurstClient;

/** Routes actual container-screen mouse gestures to the visible island overlay. */
@Mixin(AbstractContainerScreen.class)
public abstract class IslandContainerScreenMixin
{
	@Unique private int wurst$islandButtons;

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void wurst$islandClick(double x, double y, int button, CallbackInfoReturnable<Boolean> cir)
	{
		var hud = WurstClient.INSTANCE.getHudManager();
		if(hud != null && hud.clickContainerIsland(x, y, button))
		{
			wurst$islandButtons |= 1 << button;
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
	private void wurst$islandRelease(double x, double y, int button, CallbackInfoReturnable<Boolean> cir)
	{
		if((wurst$islandButtons & 1 << button) != 0)
		{
			wurst$islandButtons &= ~(1 << button);
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
	private void wurst$islandDrag(double x, double y, int button, double dx, double dy,
		CallbackInfoReturnable<Boolean> cir)
	{
		if((wurst$islandButtons & 1 << button) != 0) cir.setReturnValue(true);
	}
}
