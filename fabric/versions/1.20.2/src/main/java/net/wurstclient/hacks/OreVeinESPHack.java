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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.events.WorldChangeListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.BlockListSetting;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.RenderUtils;
import net.wurstclient.util.chunk.ChunkUtils;

@SearchTags({"ore vein esp", "ore esp", "vein finder", "ore finder"})
public final class OreVeinESPHack extends Hack
	implements UpdateListener, RenderListener, WorldChangeListener
{
	private final BlockListSetting blocks = new BlockListSetting("Ores",
		"Blocks to search for and group into veins.",
		"minecraft:coal_ore", "minecraft:deepslate_coal_ore",
		"minecraft:copper_ore", "minecraft:deepslate_copper_ore",
		"minecraft:iron_ore", "minecraft:deepslate_iron_ore",
		"minecraft:gold_ore", "minecraft:deepslate_gold_ore",
		"minecraft:redstone_ore", "minecraft:deepslate_redstone_ore",
		"minecraft:lapis_ore", "minecraft:deepslate_lapis_ore",
		"minecraft:diamond_ore", "minecraft:deepslate_diamond_ore",
		"minecraft:emerald_ore", "minecraft:deepslate_emerald_ore",
		"minecraft:nether_quartz_ore", "minecraft:ancient_debris");

	private final SliderSetting radius = new SliderSetting("Chunk radius",
		"How many chunks around you are scanned.", 4, 1, 12, 1,
		ValueDisplay.INTEGER.withSuffix(" chunks"));

	private final SliderSetting updateInterval = new SliderSetting(
		"Update interval", "How often the loaded chunks are scanned again.",
		40, 5, 200, 5, ValueDisplay.INTEGER.withSuffix(" ticks"));

	private final SliderSetting minVeinSize = new SliderSetting(
		"Min vein size", "Ore clusters smaller than this are not rendered.",
		1, 1, 32, 1, ValueDisplay.INTEGER);

	private final SliderSetting maxVeins = new SliderSetting("Max veins",
		"Maximum number of vein boxes to render.", 256, 16, 2048, 16,
		ValueDisplay.INTEGER);

	private final ColorSetting color = new ColorSetting("Color",
		"The color of the vein boxes.", new Color(0, 255, 255));

	private final SliderSetting fillOpacity = new SliderSetting("Fill opacity",
		"Opacity of the filled area inside the vein boxes.", 0.2, 0, 1, 0.01,
		ValueDisplay.PERCENTAGE);

	private final SliderSetting lineOpacity = new SliderSetting("Line opacity",
		"Opacity of the vein box outlines.", 0.75, 0, 1, 0.01,
		ValueDisplay.PERCENTAGE);

	private final CheckboxSetting throughWalls = new CheckboxSetting(
		"Through walls", "Renders the vein boxes through solid blocks.", true);

	private final ArrayList<AABB> boxes = new ArrayList<>();
	private int timer;

	public OreVeinESPHack()
	{
		super("OreVeinESP");
		setCategory(Category.RENDER);
		addSetting(blocks);
		addSetting(radius);
		addSetting(updateInterval);
		addSetting(minVeinSize);
		addSetting(maxVeins);
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

		Set<Block> targets = new HashSet<>();
		for(String name : blocks.getBlockNames())
		{
			Block block = BlockUtils.getBlockFromName(name);
			if(block != Blocks.AIR)
				targets.add(block);
		}

		if(targets.isEmpty())
			return;

		int chunkRadius = radius.getValueI();
		ChunkPos center = MC.player.chunkPosition();
		Set<BlockPos> ores = new HashSet<>();

		for(LevelChunk chunk : ChunkUtils.getLoadedChunks().toList())
		{
			ChunkPos chunkPos = chunk.getPos();
			if(Math.abs(chunkPos.x - center.x) > chunkRadius
				|| Math.abs(chunkPos.z - center.z) > chunkRadius)
				continue;

			int chunkMinX = chunkPos.getMinBlockX();
			int chunkMinZ = chunkPos.getMinBlockZ();
			LevelChunkSection[] sections = chunk.getSections();
			int minSectionY = chunk.getMinSection();

			for(int i = 0; i < sections.length; i++)
			{
				LevelChunkSection section = sections[i];
				if(section == null || section.hasOnlyAir())
					continue;
				if(!section.maybeHas(
					state -> targets.contains(state.getBlock())))
					continue;

				int sectionMinY = (minSectionY + i) << 4;

				for(int y = 0; y < 16; y++)
					for(int x = 0; x < 16; x++)
						for(int z = 0; z < 16; z++)
						{
							if(!targets.contains(section
								.getBlockState(x, y, z).getBlock()))
								continue;

							ores.add(new BlockPos(chunkMinX + x,
								sectionMinY + y, chunkMinZ + z));
						}
			}
		}

		groupVeins(ores);
	}

	/**
	 * Groups adjacent ore blocks into vein bounding boxes.
	 */
	private void groupVeins(Set<BlockPos> ores)
	{
		Set<BlockPos> visited = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		int minSize = minVeinSize.getValueI();
		int limit = maxVeins.getValueI();

		for(BlockPos start : ores)
		{
			if(boxes.size() >= limit)
				return;
			if(!visited.add(start))
				continue;

			queue.clear();
			queue.add(start);

			int minX = start.getX();
			int minY = start.getY();
			int minZ = start.getZ();
			int maxX = minX;
			int maxY = minY;
			int maxZ = minZ;
			int count = 0;

			while(!queue.isEmpty())
			{
				BlockPos pos = queue.poll();
				count++;

				minX = Math.min(minX, pos.getX());
				minY = Math.min(minY, pos.getY());
				minZ = Math.min(minZ, pos.getZ());
				maxX = Math.max(maxX, pos.getX());
				maxY = Math.max(maxY, pos.getY());
				maxZ = Math.max(maxZ, pos.getZ());

				for(int dx = -1; dx <= 1; dx++)
					for(int dy = -1; dy <= 1; dy++)
						for(int dz = -1; dz <= 1; dz++)
						{
							if(dx == 0 && dy == 0 && dz == 0)
								continue;

							BlockPos next = pos.offset(dx, dy, dz);
							if(!ores.contains(next)
								|| !visited.add(next))
								continue;

							queue.add(next);
						}
			}

			if(count < minSize)
				continue;

			boxes.add(new AABB(minX, minY, minZ, maxX + 1, maxY + 1,
				maxZ + 1));
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
