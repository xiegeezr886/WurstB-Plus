/*
 * Copyright (c) 2025-2026 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixin;

import java.io.File;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.resources.SkinManager;

/**
 * The title screen reads the downloaded skin file directly. HttpTexture is a
 * SimpleTexture, so its pixels cannot be read back from the texture manager;
 * the on-disk cache is the only way to turn the 8x8 face into a rounded
 * avatar without waiting for the player to join a world.
 */
@Mixin(SkinManager.class)
public interface SkinManagerAccessor
{
	@Accessor("skinsDirectory")
	File wurst$skinsDirectory();
}
