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
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.wurstclient.clickgui2.SmoothFontTextures;

/**
 * Forces GL_LINEAR filtering on glyph atlas textures belonging to the bundled
 * CJK custom fonts. Minecraft's font render types set GL_NEAREST on every draw
 * (TextureStateShard blur=false), which turns the anti-aliased edges of dense
 * CJK glyphs into hard single-pixel dots when they are drawn at fractional
 * scales. By overriding the filter for exactly those textures we get smooth
 * glyph edges while every other texture stays untouched.
 */
@Mixin(AbstractTexture.class)
public abstract class AbstractTextureMixin
{
	@ModifyVariable(method = "setFilter(ZZ)V", at = @At("HEAD"),
		index = 1, argsOnly = true)
	private boolean wurst_forceLinearFilter(boolean blur)
	{
		if(SmoothFontTextures.isSmooth((AbstractTexture)(Object)this))
			return true;
		return blur;
	}
}
