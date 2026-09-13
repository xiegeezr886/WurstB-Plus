/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.altmanager;

import java.util.HashMap;
import java.util.UUID;
import net.wurstclient.util.render.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.DefaultPlayerSkin;
// PlayerSkin removed in MC 26.1.2
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.systems.RenderSystem;

public final class AltRenderer
{
	private static final HashMap<String, Identifier> loadedSkins =
		new HashMap<>();
	
	private static Identifier getSkinTexture(String name)
	{
		if(name.isEmpty())
			name = "Steve";
		
		Identifier texture = loadedSkins.get(name);
		if(texture == null)
		{
			UUID uuid = UUIDUtil.createOfflinePlayerUUID(name);
			GameProfile profile = new GameProfile(uuid, name);
			PlayerInfo entry = new PlayerInfo(profile, false);
			texture = entry.getSkin().body().texturePath();
			loadedSkins.put(name, texture);
		}
		
		return texture;
	}
	
	public static void drawAltFace(GuiGraphicsExtractor context, String name, int x,
		int y, int w, int h, boolean selected)
	{
		try
		{
			Identifier texture = getSkinTexture(name);
			
			if(selected)
			{
				// Shader color managed by render pipeline
			}else
			{
				// Shader color managed by render pipeline
			}
			// Face
			int fw = 192;
			int fh = 192;
			int u = 24;
			int v = 24;
			context.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, w, h, fw, fh);
			
			// Hat
			fw = 192;
			fh = 192;
			u = 120;
			v = 24;
			context.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, w, h, fw, fh);
		// Shader color managed by render pipeline
		}catch(Exception e)
		{
			e.printStackTrace();
		}
	}
	
	public static void drawAltBody(GuiGraphicsExtractor context, String name, int x,
		int y, int width, int height)
	{
		try
		{
			Identifier texture = getSkinTexture(name);
		// Shader color managed by render pipeline
			boolean slim = DefaultPlayerSkin
				.get(UUIDUtil.createOfflinePlayerUUID(name)).model()
				== PlayerModelType.SLIM;
			
			// Face
			x = x + width / 4;
			y = y + 0;
			int w = width / 2;
			int h = height / 4;
			int fw = height * 2;
			int fh = height * 2;
			float u = height / 4;
			float v = height / 4;
			context.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, w, h, fw, fh);
			
			// Hat
			x = x + 0;
			y = y + 0;
			w = width / 2;
			h = height / 4;
			u = height / 4 * 5;
			v = height / 4;
			context.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, w, h, fw, fh);
			
			// Chest
			x = x + 0;
			y = y + height / 4;
			w = width / 2;
			h = height / 8 * 3;
			u = height / 4 * 2.5F;
			v = height / 4 * 2.5F;
			context.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, w, h, fw, fh);
			
			// Jacket
			x = x + 0;
			y = y + 0;
			w = width / 2;
			h = height / 8 * 3;
			u = height / 4 * 2.5F;
			v = height / 4 * 4.5F;
			context.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, w, h, fw, fh);
			
			// Left Arm
			x = x - width / 16 * (slim ? 3 : 4);
			y = y + (slim ? height / 32 : 0);
			w = width / 16 * (slim ? 3 : 4);
			h = height / 8 * 3;
			u = height / 4 * 5.5F;
			v = height / 4 * 2.5F;
			context.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, w, h, fw, fh);
			
			// Left Sleeve
			x = x + 0;
			y = y + 0;
			w = width / 16 * (slim ? 3 : 4);
			h = height / 8 * 3;
			u = height / 4 * 5.5F;
			v = height / 4 * 4.5F;
			context.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, w, h, fw, fh);
			
			// Right Arm
			x = x + width / 16 * (slim ? 11 : 12);
			y = y + 0;
			w = width / 16 * (slim ? 3 : 4);
			h = height / 8 * 3;
			u = height / 4 * 5.5F;
			v = height / 4 * 2.5F;
			context.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, w, h, fw, fh);
			
			// Right Sleeve
			x = x + 0;
			y = y + 0;
			w = width / 16 * (slim ? 3 : 4);
			h = height / 8 * 3;
			u = height / 4 * 5.5F;
			v = height / 4 * 4.5F;
			context.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, w, h, fw, fh);
			
			// Left Leg
			x = x - width / 2;
			y = y + height / 32 * (slim ? 11 : 12);
			w = width / 4;
			h = height / 8 * 3;
			u = height / 4 * 0.5F;
			v = height / 4 * 2.5F;
			context.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, w, h, fw, fh);
			
			// Left Pants
			x = x + 0;
			y = y + 0;
			w = width / 4;
			h = height / 8 * 3;
			u = height / 4 * 0.5F;
			v = height / 4 * 4.5F;
			context.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, w, h, fw, fh);
			
			// Right Leg
			x = x + width / 4;
			y = y + 0;
			w = width / 4;
			h = height / 8 * 3;
			u = height / 4 * 0.5F;
			v = height / 4 * 2.5F;
			context.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, w, h, fw, fh);
			
			// Right Pants
			x = x + 0;
			y = y + 0;
			w = width / 4;
			h = height / 8 * 3;
			u = height / 4 * 0.5F;
			v = height / 4 * 4.5F;
			context.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, w, h, fw, fh);
			
		}catch(Exception e)
		{
			e.printStackTrace();
		}
	}
	
	public static void drawAltBack(GuiGraphicsExtractor context, String name, int x,
		int y, int width, int height)
	{
		try
		{
			Identifier texture = getSkinTexture(name);
		// Shader color managed by render pipeline
			boolean slim = DefaultPlayerSkin
				.get(UUIDUtil.createOfflinePlayerUUID(name)).model()
				== PlayerModelType.SLIM;
			
			// Face
			x = x + width / 4;
			y = y + 0;
			int w = width / 2;
			int h = height / 4;
			int fw = height * 2;
			int fh = height * 2;
			float u = height / 4 * 3;
			float v = height / 4;
			context.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, w, h, fw, fh);
			
			// Hat
			x = x + 0;
			y = y + 0;
			w = width / 2;
			h = height / 4;
			u = height / 4 * 7;
			v = height / 4;
			context.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, w, h, fw, fh);
			
			// Chest
			x = x + 0;
			y = y + height / 4;
			w = width / 2;
			h = height / 8 * 3;
			u = height / 4 * 4;
			v = height / 4 * 2.5F;
			context.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, w, h, fw, fh);
			
			// Jacket
			x = x + 0;
			y = y + 0;
			w = width / 2;
			h = height / 8 * 3;
			u = height / 4 * 4;
			v = height / 4 * 4.5F;
			context.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, w, h, fw, fh);
			
			// Left Arm
			x = x - width / 16 * (slim ? 3 : 4);
			y = y + (slim ? height / 32 : 0);
			w = width / 16 * (slim ? 3 : 4);
			h = height / 8 * 3;
			u = height / 4 * (slim ? 6.375F : 6.5F);
			v = height / 4 * 2.5F;
			context.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, w, h, fw, fh);
			
			// Left Sleeve
			x = x + 0;
			y = y + 0;
			w = width / 16 * (slim ? 3 : 4);
			h = height / 8 * 3;
			u = height / 4 * (slim ? 6.375F : 6.5F);
			v = height / 4 * 4.5F;
			context.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, w, h, fw, fh);
			
			// Right Arm
			x = x + width / 16 * (slim ? 11 : 12);
			y = y + 0;
			w = width / 16 * (slim ? 3 : 4);
			h = height / 8 * 3;
			u = height / 4 * (slim ? 6.375F : 6.5F);
			v = height / 4 * 2.5F;
			context.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, w, h, fw, fh);
			
			// Right Sleeve
			x = x + 0;
			y = y + 0;
			w = width / 16 * (slim ? 3 : 4);
			h = height / 8 * 3;
			u = height / 4 * (slim ? 6.375F : 6.5F);
			v = height / 4 * 4.5F;
			context.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, w, h, fw, fh);
			
			// Left Leg
			x = x - width / 2;
			y = y + height / 32 * (slim ? 11 : 12);
			w = width / 4;
			h = height / 8 * 3;
			u = height / 4 * 1.5F;
			v = height / 4 * 2.5F;
			context.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, w, h, fw, fh);
			
			// Left Pants
			x = x + 0;
			y = y + 0;
			w = width / 4;
			h = height / 8 * 3;
			u = height / 4 * 1.5F;
			v = height / 4 * 4.5F;
			context.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, w, h, fw, fh);
			
			// Right Leg
			x = x + width / 4;
			y = y + 0;
			w = width / 4;
			h = height / 8 * 3;
			u = height / 4 * 1.5F;
			v = height / 4 * 2.5F;
			context.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, w, h, fw, fh);
			
			// Right Pants
			x = x + 0;
			y = y + 0;
			w = width / 4;
			h = height / 8 * 3;
			u = height / 4 * 1.5F;
			v = height / 4 * 4.5F;
			context.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, w, h, fw, fh);
			
		}catch(Exception e)
		{
			e.printStackTrace();
		}
	}
}
