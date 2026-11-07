/*
 * Sound filtering adapted from Meteor Client, copyright Meteor Development.
 * SPDX-License-Identifier: GPL-3.0
 */
package net.wurstclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.wurstclient.WurstClient;
import net.wurstclient.hack.HackList;

@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin
{
	@Inject(method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)V",
		at = @At("HEAD"), cancellable = true)
	private void wurstBlockSound(SoundInstance sound, CallbackInfo ci)
	{
		HackList hax = WurstClient.INSTANCE.getHax();
		if(hax != null && hax.soundModifierHack.shouldBlock(sound.getLocation().toString()))
			ci.cancel();
	}
}
