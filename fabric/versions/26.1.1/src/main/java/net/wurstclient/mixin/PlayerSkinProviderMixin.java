package net.wurstclient.mixin;

import java.util.function.Supplier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.authlib.GameProfile;

import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.world.entity.player.PlayerSkin;
import net.wurstclient.WurstClient;

@Mixin(SkinManager.class)
public abstract class PlayerSkinProviderMixin
{
	@Inject(
		method = "createLookup(Lcom/mojang/authlib/GameProfile;Z)Ljava/util/function/Supplier;",
		at = @At("HEAD"),
		cancellable = true)
	private void soar$protectSkin(GameProfile profile, boolean secure,
		CallbackInfoReturnable<Supplier<PlayerSkin>> cir)
	{
		if(WurstClient.MC.player == null
			|| WurstClient.INSTANCE.getHax() == null
			|| !WurstClient.INSTANCE.getHax().skinProtectHack.isEnabled())
			return;

		if(profile.id().equals(WurstClient.MC.player.getUUID()))
			cir.setReturnValue(() -> DefaultPlayerSkin.get(profile));
	}
}
