/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
package net.wurstclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.MusicManager;

/**
 * Removes the vanilla main-menu background music (music.menu). Music played
 * while a world is loaded is untouched.
 */
@Mixin(MusicManager.class)
public abstract class MusicManagerMixin
{
	@Inject(at = @At("HEAD"), method = "tick", cancellable = true)
	private void wurst$stopMenuMusic(CallbackInfo ci)
	{
		if(Minecraft.getInstance().level == null)
		{
			((MusicManager)(Object)this).stopPlaying();
			ci.cancel();
		}
	}
}
