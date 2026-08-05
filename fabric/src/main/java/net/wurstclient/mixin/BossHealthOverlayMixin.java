package net.wurstclient.mixin;

import java.util.Map;
import java.util.UUID;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.world.BossEvent;
import net.wurstclient.WurstClient;
import net.wurstclient.hack.HackList;
import net.wurstclient.mixinterface.IBossHealthOverlay;

@Mixin(BossHealthOverlay.class)
public abstract class BossHealthOverlayMixin implements IBossHealthOverlay
{
	@Accessor("events")
	@Override
	public abstract Map<UUID, ? extends BossEvent> wurst_getEvents();

	@Inject(at = @At("HEAD"), method = "render", cancellable = true)
	private void onRender(GuiGraphics graphics, CallbackInfo ci)
	{
		HackList hax = WurstClient.INSTANCE.getHax();
		if(hax != null && hax.bossStackHack.isEnabled())
			ci.cancel();
	}
}
