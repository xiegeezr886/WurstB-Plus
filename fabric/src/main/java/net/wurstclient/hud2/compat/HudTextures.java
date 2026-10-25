/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
package net.wurstclient.hud2.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.wurstclient.WurstClient;

/**
 * Version-specific bridge from a texture path to the OpenGL texture id that
 * NanoVG needs.
 *
 * <p>1.20.1 keeps raw texture ids on {@link AbstractTexture}. Newer versions
 * replace this file with a GpuTexture-based implementation.
 */
public final class HudTextures
{
	private HudTextures() {}

	public static int id(String path)
	{
		if(path == null || path.isBlank())
			return -1;

		try
		{
			ResourceLocation location = new ResourceLocation(path);
			AbstractTexture texture = Minecraft.getInstance()
				.getTextureManager().getTexture(location);
			return texture == null ? -1 : texture.getId();
		}catch(Exception e)
		{
			return -1;
		}
	}

	public static int localPlayerSkin()
	{
		if(WurstClient.MC.player == null)
			return -1;
		return id(WurstClient.MC.player.getSkinTextureLocation().toString());
	}
}
