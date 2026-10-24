/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 *
 * Ported from InvincibleMachineGun (GPL-3.0).
 */
package net.wurstclient.hacks;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.RightClickListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.events.WorldChangeListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.util.RenderUtils;
import net.wurstclient.util.chunk.ChunkUtils;

@SearchTags({"fill esp", "container esp", "chest esp", "opened containers"})
public final class FillEspHack extends Hack implements UpdateListener,
	RenderListener, RightClickListener, WorldChangeListener
{
	private final EnumSetting<ShapeMode> shapeMode = new EnumSetting<>(
		"Shape mode", ShapeMode.values(), ShapeMode.BOTH);

	private final ColorSetting unopenedColor = new ColorSetting(
		"Unopened color", "Color for containers you have not interacted with.",
		new Color(255, 160, 0, 100));

	private final ColorSetting openedColor = new ColorSetting("Opened color",
		"Color for containers you have already opened.",
		new Color(0, 255, 0, 100));

	private final CheckboxSetting throughWalls = new CheckboxSetting(
		"Through walls", "Renders containers through solid blocks.", true);

	private final ArrayList<Container> containers = new ArrayList<>();
	private final Set<BlockPos> openedBlocks = new HashSet<>();

	public FillEspHack()
	{
		super("FillESP");
		setCategory(Category.RENDER);
		addSetting(shapeMode);
		addSetting(unopenedColor);
		addSetting(openedColor);
		addSetting(throughWalls);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(RenderListener.class, this);
		EVENTS.add(RightClickListener.class, this);
		EVENTS.add(WorldChangeListener.class, this);
		openedBlocks.clear();
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		EVENTS.remove(RightClickListener.class, this);
		EVENTS.remove(WorldChangeListener.class, this);
		containers.clear();
		openedBlocks.clear();
	}

	@Override
	public void onWorldChange(ClientLevel world)
	{
		containers.clear();
		openedBlocks.clear();
	}

	@Override
	public void onUpdate()
	{
		containers.clear();
		if(MC.level == null)
			return;

		ChunkUtils.getLoadedBlockEntities().forEach(be -> {
			if(isContainer(be))
				containers.add(new Container(be.getBlockPos().immutable(),
					be instanceof ChestBlockEntity
						|| be instanceof EnderChestBlockEntity));
		});
	}

	@Override
	public void onRightClick(RightClickEvent event)
	{
		if(MC.level == null || !(MC.hitResult instanceof BlockHitResult hit)
			|| MC.hitResult.getType() != HitResult.Type.BLOCK)
			return;

		BlockPos pos = hit.getBlockPos();
		BlockEntity be = MC.level.getBlockEntity(pos);
		if(be == null || !isContainer(be))
			return;

		openedBlocks.add(pos.immutable());

		if(be instanceof ChestBlockEntity)
		{
			BlockState state = MC.level.getBlockState(pos);
			if(!state.hasProperty(ChestBlock.TYPE))
				return;

			ChestType type = state.getValue(ChestBlock.TYPE);
			if(type == ChestType.SINGLE || !state.hasProperty(ChestBlock.FACING))
				return;

			Direction facing = state.getValue(ChestBlock.FACING);
			Direction neighborDir = type == ChestType.LEFT
				? facing.getClockWise() : facing.getCounterClockWise();
			openedBlocks.add(pos.relative(neighborDir).immutable());
		}
	}

	@Override
	public void onRender(PoseStack matrixStack, float partialTicks)
	{
		if(containers.isEmpty())
			return;

		boolean sides = shapeMode.getSelected().sides;
		boolean lines = shapeMode.getSelected().lines;
		boolean depthTest = !throughWalls.isChecked();

		ArrayList<AABB> fillBoxes = new ArrayList<>();
		ArrayList<Integer> fillColors = new ArrayList<>();
		ArrayList<AABB> lineBoxes = new ArrayList<>();
		ArrayList<Integer> lineColors = new ArrayList<>();

		for(Container container : containers)
		{
			boolean opened = openedBlocks.contains(container.pos);
			Color color =
				opened ? openedColor.getColor() : unopenedColor.getColor();
			AABB box = getBox(container);

			if(sides)
			{
				fillBoxes.add(box);
				fillColors.add(color.getRGB());
			}
			if(lines)
			{
				lineBoxes.add(box);
				lineColors.add(color.getRGB() & 0x00FFFFFF | 0xFF000000);
			}
		}

		if(!fillBoxes.isEmpty())
			RenderUtils.drawSolidBoxes(matrixStack,
				zip(fillBoxes, fillColors), depthTest);
		if(!lineBoxes.isEmpty())
			RenderUtils.drawOutlinedBoxes(matrixStack,
				zip(lineBoxes, lineColors), depthTest);
	}

	private AABB getBox(Container container)
	{
		BlockPos pos = container.pos;
		if(container.chest)
		{
			double shrink = 0.0625;
			return new AABB(pos.getX() + shrink, pos.getY(),
				pos.getZ() + shrink, pos.getX() + 1 - shrink,
				pos.getY() + 1 - shrink * 2, pos.getZ() + 1 - shrink);
		}
		return new AABB(pos);
	}

	private ArrayList<RenderUtils.ColoredBox> zip(ArrayList<AABB> boxes,
		ArrayList<Integer> colors)
	{
		ArrayList<RenderUtils.ColoredBox> result =
			new ArrayList<>(boxes.size());
		for(int i = 0; i < boxes.size(); i++)
			result.add(new RenderUtils.ColoredBox(boxes.get(i), colors.get(i)));
		return result;
	}

	private boolean isContainer(BlockEntity be)
	{
		return be instanceof ChestBlockEntity
			|| be instanceof BarrelBlockEntity
			|| be instanceof ShulkerBoxBlockEntity
			|| be instanceof EnderChestBlockEntity
			|| be instanceof HopperBlockEntity
			|| be instanceof DispenserBlockEntity
			|| be instanceof AbstractFurnaceBlockEntity;
	}

	private record Container(BlockPos pos, boolean chest)
	{
	}

	private enum ShapeMode
	{
		BOTH("Both", true, true),
		LINES("Lines only", false, true),
		SIDES("Sides only", true, false);

		private final String name;
		private final boolean sides;
		private final boolean lines;

		private ShapeMode(String name, boolean sides, boolean lines)
		{
			this.name = name;
			this.sides = sides;
			this.lines = lines;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}
}
