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
import java.util.ArrayList;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.RenderListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.RenderUtils;

/**
 * Renders the borders of the player's current chunk and its neighbors as
 * full-height boxes.
 *
 * <p>Simplification: Soar also drew a dense yellow 16x16 grid inside the
 * current chunk. This port only draws the chunk borders, with an adjustable
 * radius.
 */
@SearchTags({"chunk borders", "chunk", "borders", "chunk boundaries",
	"chunk grid"})
public final class ChunkBordersHack extends Hack implements RenderListener
{
	private final SliderSetting radius = new SliderSetting("Radius",
		"Chunk radius around the player to draw borders for.", 1, 0, 4, 1,
		ValueDisplay.INTEGER);

	private final ColorSetting color = new ColorSetting("Color",
		"Color of the chunk borders.", Color.BLUE);

	private final CheckboxSetting throughWalls = new CheckboxSetting(
		"Through walls", "Renders the chunk borders through solid blocks.",
		false);

	public ChunkBordersHack()
	{
		super("ChunkBorders");
		setCategory(Category.RENDER);
		addSetting(radius);
		addSetting(color);
		addSetting(throughWalls);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(RenderListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(RenderListener.class, this);
	}

	@Override
	public void onRender(PoseStack poseStack, float partialTicks)
	{
		if(MC.level == null || MC.player == null)
			return;

		ChunkPos center = MC.player.chunkPosition();
		int minY = MC.level.getMinY();
		int maxY = MC.level.getMaxY() + 1;
		int r = radius.getValueI();
		int lineColor = color.getColorI(0xC0);
		boolean depthTest = !throughWalls.isChecked();

		ArrayList<AABB> boxes = new ArrayList<>();
		for(int dx = -r; dx <= r; dx++)
			for(int dz = -r; dz <= r; dz++)
			{
				int x = (center.x + dx) << 4;
				int z = (center.z + dz) << 4;
				boxes.add(new AABB(x, minY, z, x + 16, maxY, z + 16));
			}

		RenderUtils.drawOutlinedBoxes(poseStack, boxes, lineColor, depthTest);
	}
}
