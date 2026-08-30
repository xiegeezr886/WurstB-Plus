/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
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
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.wurstclient.clickgui2.SmoothFontTextures;

/**
 * Registers glyph atlas pages belonging to the bundled CJK custom fonts
 * (wurst:pingfang / wurst:rise) as "smooth" so that AbstractTextureMixin
 * keeps linear filtering enabled on them. This removes the hard pixel dots
 * caused by GL_NEAREST sampling of densely-stroked CJK glyphs at fractional
 * scales, without touching any other texture.
 */
@Mixin(TextureManager.class)
public abstract class TextureManagerMixin
{
	@Inject(method = "register(Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/client/renderer/texture/AbstractTexture;)V",
		at = @At("HEAD"))
	private void wurst_onRegisterTexture(ResourceLocation location,
		AbstractTexture texture, CallbackInfo ci)
	{
		if(!location.getNamespace().equals("wurst"))
			return;

		String path = location.getPath();
		if(path.equals("pingfang") || path.equals("rise")
			|| path.startsWith("pingfang/") || path.startsWith("rise/"))
			SmoothFontTextures.register(texture);
	}
}
