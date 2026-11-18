/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
package net.wurstclient.hud2.nanovg;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.renderer.texture.DynamicTexture;

/** Dynamic texture that receives the Skia HUD surface pixels each frame. */
public final class SkiaHudTexture extends DynamicTexture
{
	public SkiaHudTexture()
	{
		super(1, 1, false);
	}

	public void upload(byte[] rgba, int w, int h)
	{
		NativeImage image = getPixels();
		if(image == null || image.getWidth() != w || image.getHeight() != h)
		{
			image = new NativeImage(NativeImage.Format.RGBA, w, h, false);
			setPixels(image);
		}

		for(int y = 0; y < h; y++)
			for(int x = 0; x < w; x++)
			{
				int i = (y * w + x) * 4;
				int r = rgba[i] & 0xFF;
				int g = rgba[i + 1] & 0xFF;
				int b = rgba[i + 2] & 0xFF;
				int a = rgba[i + 3] & 0xFF;
				image.setPixelRGBA(x, y, a << 24 | b << 16 | g << 8 | r);
			}

		upload();
	}
}
