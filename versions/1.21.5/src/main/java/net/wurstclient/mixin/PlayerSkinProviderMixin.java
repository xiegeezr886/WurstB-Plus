package net.wurstclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.authlib.GameProfile;

import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.client.resources.SkinManager;
import net.wurstclient.WurstClient;

@Mixin(SkinManager.class)
public abstract class PlayerSkinProviderMixin
{
	@Inject(
		method = "getInsecureSkin(Lcom/mojang/authlib/GameProfile;)Lnet/minecraft/client/resources/PlayerSkin;",
		at = @At("HEAD"),
		cancellable = true)
	private void soar$protectSkin(GameProfile profile,
		CallbackInfoReturnable<PlayerSkin> cir)
	{
		if(WurstClient.MC.player == null
			|| WurstClient.INSTANCE.getHax() == null
			|| !WurstClient.INSTANCE.getHax().skinProtectHack.isEnabled())
			return;

		if(profile.getId().equals(WurstClient.MC.player.getUUID()))
			cir.setReturnValue(DefaultPlayerSkin.get(profile));
	}
}
