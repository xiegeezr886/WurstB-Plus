package net.wurstclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.wurstclient.hud2.nanovg.NanoVgBlitter;

@Mixin(GuiRenderState.class)
public abstract class NanoVgGuiRenderStateMixin
{
	@Inject(method = "reset()V", at = @At("RETURN"))
	private void wurst$releaseSnapshots(CallbackInfo ci)
	{
		NanoVgBlitter.releaseFrame((GuiRenderState)(Object)this);
	}
}
