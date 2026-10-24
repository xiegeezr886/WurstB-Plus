/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 *
 * Ported from InvincibleMachineGun (GPL-3.0).
 */
package net.wurstclient.hacks;

import java.awt.Color;
import java.util.ArrayList;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.events.WorldChangeListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.RenderUtils;
import net.wurstclient.util.chunk.ChunkUtils;

@SearchTags({"lava esp", "lava source", "lava finder"})
public final class LavaESPHack extends Hack
	implements UpdateListener, RenderListener, WorldChangeListener
{
	private final SliderSetting range = new SliderSetting("Range",
		"How far away lava source blocks are searched for.", 32, 8, 128, 4,
		ValueDisplay.INTEGER.withSuffix(" blocks"));

	private final SliderSetting updateInterval = new SliderSetting(
		"Update interval", "How often the loaded chunks are scanned again.",
		20, 1, 100, 1, ValueDisplay.INTEGER.withSuffix(" ticks"));

	private final SliderSetting maxLava = new SliderSetting("Max lava blocks",
		"Maximum number of lava blocks to render.", 512, 16, 4096, 16,
		ValueDisplay.INTEGER);

	private final ColorSetting color = new ColorSetting("Color",
		"The color of the highlighted lava source blocks.",
		new Color(255, 80, 0));

	private final SliderSetting fillOpacity = new SliderSetting("Fill opacity",
		"Opacity of the filled area inside the boxes.", 0.25, 0, 1, 0.01,
		ValueDisplay.PERCENTAGE);

	private final SliderSetting lineOpacity = new SliderSetting("Line opacity",
		"Opacity of the box outlines.", 0.75, 0, 1, 0.01,
		ValueDisplay.PERCENTAGE);

	private final CheckboxSetting throughWalls = new CheckboxSetting(
		"Through walls", "Renders the boxes through solid blocks.", true);

	private final ArrayList<AABB> boxes = new ArrayList<>();
	private int timer;

	public LavaESPHack()
	{
		super("LavaESP");
		setCategory(Category.RENDER);
		addSetting(range);
		addSetting(updateInterval);
		addSetting(maxLava);
		addSetting(color);
		addSetting(fillOpacity);
		addSetting(lineOpacity);
		addSetting(throughWalls);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(RenderListener.class, this);
		EVENTS.add(WorldChangeListener.class, this);
		boxes.clear();
		timer = 0;
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		EVENTS.remove(WorldChangeListener.class, this);
		boxes.clear();
		timer = 0;
	}

	@Override
	public void onWorldChange(ClientLevel world)
	{
		boxes.clear();
		timer = 0;
	}

	@Override
	public void onUpdate()
	{
		if(MC.level == null || MC.player == null)
			return;

		if(timer > 0)
		{
			timer--;
			return;
		}
		timer = updateInterval.getValueI();

		scan();
	}

	private void scan()
	{
		boxes.clear();

		int radius = range.getValueI();
		BlockPos center = MC.player.blockPosition();
		int minX = center.getX() - radius;
		int maxX = center.getX() + radius;
		int minY = Math.max(MC.level.getMinBuildHeight(),
			center.getY() - radius);
		int maxY = Math.min(MC.level.getMaxBuildHeight(),
			center.getY() + radius);
		int minZ = center.getZ() - radius;
		int maxZ = center.getZ() + radius;
		int limit = maxLava.getValueI();

		for(LevelChunk chunk : ChunkUtils.getLoadedChunks().toList())
		{
			int chunkMinX = chunk.getPos().getMinBlockX();
			int chunkMinZ = chunk.getPos().getMinBlockZ();
			int startX = Math.max(chunkMinX, minX);
			int endX = Math.min(chunkMinX + 15, maxX);
			int startZ = Math.max(chunkMinZ, minZ);
			int endZ = Math.min(chunkMinZ + 15, maxZ);

			if(startX > endX || startZ > endZ)
				continue;

			LevelChunkSection[] sections = chunk.getSections();
			int minSectionY = chunk.getMinBuildHeight() >> 4;

			for(int i = 0; i < sections.length; i++)
			{
				LevelChunkSection section = sections[i];
				if(section == null || section.hasOnlyAir())
					continue;

				int sectionMinY = (minSectionY + i) << 4;
				if(sectionMinY > maxY || sectionMinY + 15 < minY)
					continue;
				if(!section.maybeHas(state -> state.getFluidState()
					.getType() == Fluids.LAVA))
					continue;

				for(int y = 0; y < 16; y++)
				{
					int blockY = sectionMinY + y;
					if(blockY < minY || blockY > maxY)
						continue;

					for(int x = 0; x < 16; x++)
					{
						int blockX = chunkMinX + x;
						if(blockX < startX || blockX > endX)
							continue;

						for(int z = 0; z < 16; z++)
						{
							int blockZ = chunkMinZ + z;
							if(blockZ < startZ || blockZ > endZ)
								continue;

							BlockState state =
								section.getBlockState(x, y, z);
							FluidState fluid = state.getFluidState();
							if(fluid.isEmpty() || !fluid.isSource()
								|| fluid.getType() != Fluids.LAVA)
								continue;

							boxes.add(BlockUtils.getBoundingBox(
								new BlockPos(blockX, blockY, blockZ)));
							if(boxes.size() >= limit)
								return;
						}
					}
				}
			}
		}
	}

	@Override
	public void onRender(PoseStack matrixStack, float partialTicks)
	{
		if(boxes.isEmpty())
			return;

		boolean depthTest = !throughWalls.isChecked();
		int fillColor = color.getColorI(fillOpacity.getValueF());
		int lineColor = color.getColorI(lineOpacity.getValueF());

		if((fillColor >>> 24) != 0)
			RenderUtils.drawSolidBoxes(matrixStack, boxes, fillColor,
				depthTest);
		if((lineColor >>> 24) != 0)
			RenderUtils.drawOutlinedBoxes(matrixStack, boxes, lineColor,
				depthTest);
	}
}
