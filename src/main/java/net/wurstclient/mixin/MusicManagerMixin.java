/*
 * Copyright (c) 2025-2026 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.sounds.MusicManager;
import net.minecraft.sounds.Music;

/**
 * The custom title screen has its own wallpaper and no menu music. Vanilla
 * still starts the menu track after a short delay; cancel it while a
 * TitleScreen is the current screen. In-game music is unaffected.
 */
@Mixin(MusicManager.class)
public abstract class MusicManagerMixin
{
	@Inject(at = @At("HEAD"), method = "startPlaying", cancellable = true)
	private void onStartPlaying(Music music, CallbackInfo ci)
	{
		if(Minecraft.getInstance().screen instanceof TitleScreen)
			ci.cancel();
	}
}
