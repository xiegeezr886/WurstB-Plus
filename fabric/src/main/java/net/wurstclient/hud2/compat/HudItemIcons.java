/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
package net.wurstclient.hud2.compat;

import java.nio.ByteBuffer;
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

import net.minecraft.client.gui.GuiGraphics;
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

	private static final Map<String, Integer> CACHE = new LinkedHashMap<>();
	private static final Set<ItemStack> PENDING = new LinkedHashSet<>();
	private static TextureTarget target;
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

		if(PENDING.size() < 256)
			PENDING.add(stack.copy());
		return 0;
	}

	public static void capturePending(NanoVGContext nvg, int budget)
	{
		if(failed || PENDING.isEmpty() || WurstClient.MC.level == null
			|| WurstClient.MC.player == null)
			return;

		try(GlStateGuard ignored = GlStateGuard.capture())
		{
			Matrix4f oldProjection = new Matrix4f(
				RenderSystem.getProjectionMatrix());
			RenderSystem.getModelViewStack().pushPose();
			RenderSystem.getModelViewStack().setIdentity();
			RenderSystem.getModelViewStack().translate(0, 0, -11000);
			RenderSystem.applyModelViewMatrix();

			try
			{
				ensureTarget();
				target.setClearColor(0, 0, 0, 0);
				int captured = 0;
				java.util.Iterator<ItemStack> iterator = PENDING.iterator();
				while(iterator.hasNext() && captured < budget)
				{
					ItemStack stack = iterator.next();
					iterator.remove();
					captured++;
					capture(stack, nvg);
				}
			}finally
			{
				RenderSystem.getModelViewStack().popPose();
				RenderSystem.applyModelViewMatrix();
				RenderSystem.setProjectionMatrix(oldProjection,
					VertexSorting.ORTHOGRAPHIC_Z);
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
		target = new TextureTarget(TARGET_SIZE, TARGET_SIZE, true, false);
	}

	private static void capture(ItemStack stack, NanoVGContext nvg)
	{
		target.setClearColor(0, 0, 0, 0);
		target.clear(true);
		target.bindWrite(true);
		RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0,
			PROJECTION_SIZE, PROJECTION_SIZE, 0, 1000, 21000),
			VertexSorting.ORTHOGRAPHIC_Z);

		GuiGraphics graphics = new GuiGraphics(WurstClient.MC,
			WurstClient.MC.renderBuffers().bufferSource());
		graphics.renderItem(stack, 0, 0);
		WurstClient.MC.renderBuffers().bufferSource().endBatch();

		ByteBuffer pixels = MemoryUtil.memAlloc(SIZE * SIZE * 4);
		ByteBuffer flipped = MemoryUtil.memAlloc(SIZE * SIZE * 4);
		try
		{
			GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
			GL11.glReadPixels(0, 0, SIZE, SIZE, GL11.GL_RGBA,
				GL11.GL_UNSIGNED_BYTE, pixels);
			target.unbindWrite();

			for(int y = 0; y < SIZE; y++)
			{
				int source = (SIZE - 1 - y) * SIZE * 4;
				pixels.position(source);
				ByteBuffer row = pixels.slice();
				row.limit(SIZE * 4);
				flipped.put(row);
			}
			flipped.flip();

			int image = NanoVG.nvgCreateImageRGBA(nvg.getHandle(), SIZE, SIZE,
				NanoVG.NVG_IMAGE_NEAREST, flipped);
			CACHE.put(key(stack), image);
		}finally
		{
			MemoryUtil.memFree(pixels);
			MemoryUtil.memFree(flipped);
		}
	}

	private static String key(ItemStack stack)
	{
		String tag = stack.getTag() == null ? "" : stack.getTag().toString();
		return net.minecraft.core.registries.BuiltInRegistries.ITEM
			.getKey(stack.getItem()) + "|" + stack.getDamageValue() + "|"
			+ stack.getCount() + "|" + tag;
	}

	public static void clear()
	{
		if(target != null)
		{
			target.destroyBuffers();
			target = null;
		}
		CACHE.clear();
		PENDING.clear();
		failed = false;
	}
}
