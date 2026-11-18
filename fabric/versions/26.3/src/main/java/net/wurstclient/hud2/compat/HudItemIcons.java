/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
package net.wurstclient.hud2.compat;

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.joml.Matrix4f;
import org.lwjgl.nanovg.NanoVG;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryUtil;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;


import net.minecraft.world.item.ItemStack;
import net.wurstclient.WurstClient;
import net.wurstclient.hud2.nanovg.GlStateGuard;
import net.wurstclient.hud2.nanovg.NanoVGContext;

/**
 * Captures item icons into NanoVG images.
 *
 * <p>Modern Minecraft no longer exposes a stable "draw item into a texture"
 * API, so the icon is rendered once into an offscreen target, read back and
 * uploaded to NanoVG. Captures are budgeted per frame and cached by item
 * identity, damage and NBT hash.
 */
public final class HudItemIcons
{
	private static final int PROJECTION_SIZE = 16;
	private static final int SIZE = 32;
	private static final int TARGET_SIZE = 32;
	private static final int MAX_CACHE = 512;

	// Access-order map: the least recently drawn icon is evicted first.
	private static final Map<String, Integer> CACHE =
		new LinkedHashMap<>(16, 0.75F, true);
	private static final Map<String, ItemStack> PENDING = new LinkedHashMap<>();
	private static final Set<String> EMPTY = new LinkedHashSet<>();
	private static TextureTarget target;
	private static long nvgHandle;
	private static boolean failed;

	private HudItemIcons() {}

	public static int get(ItemStack stack)
	{
		if(stack == null || stack.isEmpty())
			return 0;

		String key = key(stack);
		Integer cached = CACHE.get(key);
		if(cached != null)
			return cached;

		if(!failed && !EMPTY.contains(key) && PENDING.size() < 256)
			PENDING.computeIfAbsent(key, ignored -> stack.copy());
		return 0;
	}

	public static void capturePending(NanoVGContext nvg, int budget)
	{
		if(failed || PENDING.isEmpty() || !nvg.create())
			return;

		nvgHandle = nvg.getHandle();
        net.wurstclient.util.render.V152GuiBridge.flushMain();
		try(GlStateGuard ignored = GlStateGuard.capture())
		{
            {
                ensureTarget();
				int captured = 0;
				java.util.Iterator<ItemStack> iterator = PENDING.values().iterator();
				while(iterator.hasNext() && captured < budget)
				{
					ItemStack stack = iterator.next();
					iterator.remove();
					captured++;
					capture(stack, nvg);
				}
			}
		}catch(Throwable t)
		{
			failed = true;
			System.err.println("[HUD] Item icon capture disabled");
			t.printStackTrace();
		}
	}

	private static void ensureTarget()
	{
		if(target != null)
			return;
		target = new TextureTarget("WurstB v1.5.2", TARGET_SIZE, TARGET_SIZE, com.mojang.renderpearl.api.GpuFormat.RGBA8_UNORM, com.mojang.renderpearl.api.GpuFormat.D32_FLOAT);
	}

	private static void capture(ItemStack stack, NanoVGContext nvg) throws Exception
	{
		com.mojang.renderpearl.backend.opengl.GlStateManager._disableScissorTest();
		GL11.glDisable(GL11.GL_STENCIL_TEST);
		com.mojang.renderpearl.backend.opengl.GlStateManager._colorMask(15);
		com.mojang.renderpearl.backend.opengl.GlStateManager._depthMask(true);
		net.wurstclient.util.render.V152Gpu.clear(target);
		net.wurstclient.util.render.V152Gpu.bind(target, true);

		ByteBuffer pixels = MemoryUtil.memAlloc(SIZE * SIZE * 4);
		ByteBuffer flipped = MemoryUtil.memAlloc(SIZE * SIZE * 4);
		try(var targetScope = net.wurstclient.util.render.V152Gpu.target(target))
		{
			net.wurstclient.util.render.V152GuiBridge.item(stack, target);


			net.wurstclient.util.render.V152Gpu.readRgba(target, SIZE, SIZE, pixels);
			boolean visible = false;
			for(int i = 3; i < SIZE * SIZE * 4; i += 4)
				if(pixels.get(i) != 0) { visible = true; break; }
			if(!visible)
			{
				if(EMPTY.size() >= MAX_CACHE) EMPTY.clear();
				EMPTY.add(key(stack));
				return;
			}

			for(int y = 0; y < SIZE; y++)
			{
				int source = (SIZE - 1 - y) * SIZE * 4;
				pixels.position(source);
				ByteBuffer row = pixels.slice();
				row.limit(SIZE * 4);
				flipped.put(row);
			}
			flipped.flip();

			int image;
            // Native NanoVG texture uploads bypass Minecraft's texture cache.
            // Restore bindings before the next item in this capture batch.
            try(GlStateGuard uploadState = GlStateGuard.capture()) {
                org.lwjgl.opengl.GL15.glBindBuffer(org.lwjgl.opengl.GL21.GL_PIXEL_UNPACK_BUFFER, 0);
                GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
                GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
                GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
                GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
                image = NanoVG.nvgCreateImageRGBA(nvg.getHandle(), SIZE, SIZE,
                    NanoVG.NVG_IMAGE_NEAREST, flipped);
            }
			CACHE.put(key(stack), image);
			evictIfNeeded(nvg.getHandle());
		}finally
		{
			// Always unbind: a failed render used to leave the 32x32 target
			// bound, so the rest of the frame was drawn into it.
			org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER, 0);
			MemoryUtil.memFree(pixels);
			MemoryUtil.memFree(flipped);
		}
	}

	private static void evictIfNeeded(long handle)
	{
		while(CACHE.size() > MAX_CACHE)
		{
			Iterator<Map.Entry<String, Integer>> iterator =
				CACHE.entrySet().iterator();
			if(!iterator.hasNext())
				return;

			int image = iterator.next().getValue();
			iterator.remove();
			if(handle != 0)
				NanoVG.nvgDeleteImage(handle, image);
		}
	}

	private static String key(ItemStack stack)
	{
		String tag = stack.getComponents().toString();
		return net.minecraft.core.registries.BuiltInRegistries.ITEM
			.getKey(stack.getItem()) + "|" + stack.getDamageValue() + "|"
			+ stack.getCount() + "|" + tag;
	}

	public static void clear()
	{
		long handle = nvgHandle;
		for(int image : CACHE.values())
			if(handle != 0)
				NanoVG.nvgDeleteImage(handle, image);

		if(target != null)
		{
			target.destroyBuffers();
			target = null;
		}
		CACHE.clear();
		PENDING.clear();
		EMPTY.clear();
		nvgHandle = 0;
		failed = false;
	}
}
