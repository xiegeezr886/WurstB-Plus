/*
 * Sound filtering adapted from Meteor Client, copyright Meteor Development.
 * SPDX-License-Identifier: GPL-3.0
 */
package net.wurstclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.wurstclient.WurstClient;
import net.wurstclient.hack.HackList;

@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin
{
	@Inject(method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)Lnet/minecraft/client/sounds/SoundEngine$PlayResult;",
		at = @At("HEAD"), cancellable = true)
	private void wurstBlockSound(SoundInstance sound, CallbackInfoReturnable<SoundEngine.PlayResult> ci)
	{
		HackList hax = WurstClient.INSTANCE.getHax();
		if(hax != null && hax.soundModifierHack.shouldBlock(sound.getLocation().toString()))
			ci.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
	}
}
