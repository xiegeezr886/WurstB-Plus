/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
package net.wurstclient.hacks;

import java.awt.Color;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.world.phys.AABB;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.RenderUtils;

@SearchTags({"chunk animator", "chunk animation", "chunk fade", "smooth chunks"})
public final class ChunkAnimatorHack extends Hack
	implements UpdateListener, RenderListener
{
	private final SliderSetting duration = new SliderSetting("Duration",
		"How long newly loaded chunks stay highlighted, in seconds.",
		1, 0, 5, 0.1, ValueDisplay.DECIMAL);

	private final SliderSetting radius = new SliderSetting("Radius",
		"Chunk radius to watch for newly loaded chunks.", 8, 2, 16, 1,
		ValueDisplay.INTEGER);

	private final CheckboxSetting highlight = new CheckboxSetting("Highlight",
		"Draws a fading box around newly loaded chunks.", true);

	private final ColorSetting color = new ColorSetting("Color",
		"Color of the chunk load highlight.", new Color(80, 200, 255));

	private final CheckboxSetting throughWalls = new CheckboxSetting(
		"Through walls", "Draws the highlight through solid blocks.", true);

	private final Map<Long, Long> highlights = new HashMap<>();
	private final Set<Long> seen = new HashSet<>();
	private long lastScan;

	private Object fadeOption;
	private Object oldFadeValue;
	private Method fadeSet;
	private Method fadeGet;

	public ChunkAnimatorHack()
	{
		super("ChunkAnimator");
		setCategory(Category.RENDER);
		addSetting(duration);
		addSetting(radius);
		addSetting(highlight);
		addSetting(color);
		addSetting(throughWalls);
	}

	@Override
	protected void onEnable()
	{
		highlights.clear();
		seen.clear();
		lastScan = 0;

		// 1.21.11+ has a built-in chunk section fade option. Use it when
		// present; older versions fall back to the highlight below.
		try
		{
			Method getter = MC.options.getClass()
				.getMethod("chunkSectionFadeInTime");
			fadeOption = getter.invoke(MC.options);
			fadeGet = fadeOption.getClass().getMethod("get");
			fadeSet = fadeOption.getClass().getMethod("set", Object.class);
			oldFadeValue = fadeGet.invoke(fadeOption);
			fadeSet.invoke(fadeOption, duration.getValue());
		}catch(Exception ignored)
		{
			fadeOption = null;
			fadeSet = null;
			fadeGet = null;
		}

		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(RenderListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(RenderListener.class, this);

		if(fadeOption != null && fadeSet != null && oldFadeValue != null)
		{
			try
			{
				fadeSet.invoke(fadeOption, oldFadeValue);
			}catch(Exception ignored)
			{}
		}

		fadeOption = null;
		fadeSet = null;
		fadeGet = null;
		oldFadeValue = null;
		highlights.clear();
		seen.clear();
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null)
			return;

		if(fadeOption != null && fadeSet != null)
		{
			try
			{
				fadeSet.invoke(fadeOption, duration.getValue());
			}catch(Exception ignored)
			{}
		}

		long now = System.currentTimeMillis();
		if(now - lastScan < 200)
			return;
		lastScan = now;

		int cx = MC.player.getBlockX() >> 4;
		int cz = MC.player.getBlockZ() >> 4;
		int r = radius.getValueI();

		Set<Long> present = new HashSet<>();
		for(int dx = -r; dx <= r; dx++)
			for(int dz = -r; dz <= r; dz++)
			{
				int x = cx + dx;
				int z = cz + dz;
				if(!MC.level.getChunkSource().hasChunk(x, z))
					continue;

				long pos = chunkKey(x, z);
				present.add(pos);
				if(seen.add(pos))
					highlights.put(pos, now);
			}

		seen.retainAll(present);
		long keep = (long)(duration.getValue() * 1000.0) + 250L;
		highlights.entrySet()
			.removeIf(entry -> now - entry.getValue() > keep);
	}

	@Override
	public void onRender(PoseStack poseStack, float partialTicks)
	{
		if(!highlight.isChecked() || MC.level == null || MC.player == null
			|| highlights.isEmpty() || duration.getValue() <= 0)
			return;

		long now = System.currentTimeMillis();
		long total = (long)(duration.getValue() * 1000.0);
		int minY = getLevelMinY();
		int maxY = getLevelMaxY();
		boolean depthTest = !throughWalls.isChecked();
		Color base = color.getColor();

		for(Map.Entry<Long, Long> entry : highlights.entrySet())
		{
			long elapsed = now - entry.getValue();
			if(elapsed < 0 || elapsed >= total)
				continue;

			float fade = 1F - elapsed / (float)total;
			int alpha = Math.max(0, Math.min(255, (int)(fade * 200)));
			int argb = alpha << 24 | base.getRGB() & 0xFFFFFF;

			long pos = entry.getKey();
			int x = keyX(pos) << 4;
			int z = keyZ(pos) << 4;
			AABB box = new AABB(x, minY, z, x + 16, maxY, z + 16);
			RenderUtils.drawOutlinedBox(poseStack, box, argb, depthTest);
		}
	}

	private static long chunkKey(int x, int z)
	{
		return ((long)x << 32) ^ (z & 0xFFFFFFFFL);
	}

	private static int keyX(long key)
	{
		return (int)(key >> 32);
	}

	private static int keyZ(long key)
	{
		return (int)key;
	}

	private int getLevelMinY()
	{
		try
		{
			return ((Number)MC.level.getClass().getMethod("getMinBuildHeight")
				.invoke(MC.level)).intValue();
		}catch(Exception ignored)
		{}

		try
		{
			return ((Number)MC.level.getClass().getMethod("getMinY")
				.invoke(MC.level)).intValue();
		}catch(Exception ignored)
		{}

		return -64;
	}

	private int getLevelMaxY()
	{
		try
		{
			return ((Number)MC.level.getClass().getMethod("getMaxBuildHeight")
				.invoke(MC.level)).intValue();
		}catch(Exception ignored)
		{}

		try
		{
			return ((Number)MC.level.getClass().getMethod("getMaxY")
				.invoke(MC.level)).intValue();
		}catch(Exception ignored)
		{}

		return 320;
	}
}
