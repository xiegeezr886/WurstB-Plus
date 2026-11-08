/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
package net.wurstclient.hud2.nanovg;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Queues HUD text drawn through NanoVG and renders it with the Android Skia
 * backend as ordinary Minecraft textures.
 *
 * <p>NanoVG's own text path uploads glyph data into the main GL context
 * while a frame is being recorded; on some Android GL wrappers that is what
 * turns the screen black. Skia rasterizes the string off-screen instead and
 * the resulting texture is uploaded by Minecraft outside any NanoVG frame.
 */
public final class SkiaTextCache
{
	private static final int MAX_CACHE = 256;

	public static volatile boolean enabled;

	private static final Map<String, Entry> CACHE =
		new LinkedHashMap<>(64, 0.75F, true);
	private static final List<Draw> QUEUE = new ArrayList<>();
	private static int nextId;

	private SkiaTextCache() {}

	private static final class Entry
	{
		final int width;
		final int height;
		final ResourceLocation id;

		Entry(int width, int height, ResourceLocation id)
		{
			this.width = width;
			this.height = height;
			this.id = id;
		}
	}

	private record Draw(String text, float x, float y, int argb, float size,
		HudFont font)
	{
	}

	public static void queue(String text, float x, float y, int argb,
		float size, HudFont font)
	{
		if(!enabled || text == null || text.isEmpty())
			return;
		QUEUE.add(new Draw(text, x, y, argb, size, font));
	}

	public static void flush(GuiGraphics graphics)
	{
		if(QUEUE.isEmpty())
			return;

		Minecraft mc = Minecraft.getInstance();
		float scale = (float)mc.getWindow().getGuiScale();
		if(scale <= 0)
			scale = 1;

		for(Draw draw : QUEUE)
		{
			int px = Math.max(1, Math.round(draw.size * scale));
			Entry entry = entry(draw.text, draw.font, px, draw.argb);
			if(entry == null)
				continue;

			float width = entry.width / scale;
			float height = entry.height / scale;
			float top = draw.y + draw.size / 2F - height / 2F;

			graphics.blit(entry.id, Math.round(draw.x), Math.round(top),
				(int)Math.ceil(width), (int)Math.ceil(height), 0, 0,
				entry.width, entry.height, entry.width, entry.height);
		}
		QUEUE.clear();
	}

	private static Entry entry(String text, HudFont font, int pxSize,
		int argb)
	{
		String key =
			font.getResource() + '|' + pxSize + '|' + argb + '|' + text;
		Entry cached = CACHE.get(key);
		if(cached != null)
			return cached;

		SkiaNative.Raster raster =
			SkiaNative.rasterize(text, font, pxSize, argb);
		if(raster == null)
			return null;

		try
		{
			NativeImage image =
				new NativeImage(NativeImage.Format.RGBA, raster.width,
					raster.height, false);
			byte[] pixels = raster.rgba;
			for(int y = 0; y < raster.height; y++)
				for(int x = 0; x < raster.width; x++)
				{
					int i = (y * raster.width + x) * 4;
					int r = pixels[i] & 0xFF;
					int g = pixels[i + 1] & 0xFF;
					int b = pixels[i + 2] & 0xFF;
					int a = pixels[i + 3] & 0xFF;
					image.setPixelRGBA(x, y, a << 24 | b << 16 | g << 8 | r);
				}

			DynamicTexture texture = new DynamicTexture(image);
			ResourceLocation id = new ResourceLocation("wurstpenguin",
				"skia_text/" + nextId++);
			Minecraft.getInstance().getTextureManager().register(id, texture);

			Entry entry = new Entry(raster.width, raster.height, id);
			CACHE.put(key, entry);
			while(CACHE.size() > MAX_CACHE)
				CACHE.remove(CACHE.keySet().iterator().next());
			return entry;
		}catch(Throwable t)
		{
			System.err.println("[HUD] Skia text texture failed: " + t);
			t.printStackTrace();
			return null;
		}
	}
}
