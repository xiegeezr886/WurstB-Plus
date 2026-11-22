package net.wurstclient.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(IntegratedServer.class)
abstract class IntegratedServerChatRefreshMixin
{
	// A font upload during a server-thread chat refresh can abort the world save.
	@WrapOperation(method = "updatePermissionAndChatAbilities", require = 0,
		at = @At(value = "INVOKE", target =
			"Lnet/minecraft/client/player/LocalPlayer;refreshChatAbilities()V"))
	private void wurst$refreshChatOnClient(LocalPlayer player, Operation<Void> refresh)
	{
		Minecraft client = Minecraft.getInstance();
		if(client.isSameThread())
			refresh.call(player);
		else if(client.isRunning())
			client.execute(() -> {
				if(client.isRunning() && client.player == player)
					refresh.call(player);
			});
	}
}
