/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
/* Ported from Meteor Client (GPL-3.0). */
package net.wurstclient.hacks;

import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.Color;
import java.util.ArrayList;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.events.WorldChangeListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.RenderUtils;

@SearchTags({"void esp", "bedrock holes", "void holes", "nether roof"})
public final class VoidESPHack extends Hack implements UpdateListener,
	RenderListener, WorldChangeListener
{
	private final CheckboxSetting airOnly = new CheckboxSetting("Air only",
		"Checks whether the hole is air instead of non-bedrock.", false);

	private final SliderSetting horizontalRadius = new SliderSetting(
		"Horizontal radius", "How far around the player to search for holes.",
		64, 8, 256, 8, ValueDisplay.INTEGER.withSuffix(" blocks"));

	private final SliderSetting holeHeight = new SliderSetting("Hole height",
		"The minimum height of a hole to be rendered.", 1, 1, 5, 1,
		ValueDisplay.INTEGER);

	private final CheckboxSetting netherRoof = new CheckboxSetting("Nether roof",
		"Also checks for holes in the nether roof at y=127.", true);

	private final EnumSetting<ShapeMode> shapeMode = new EnumSetting<>(
		"Shape mode", "How the holes are rendered.", ShapeMode.values(),
		ShapeMode.BOTH);

	private final SliderSetting fillOpacity = new SliderSetting("Fill opacity",
		"Opacity of the filled area inside hole boxes.", 0.25, 0, 1, 0.01,
		ValueDisplay.PERCENTAGE)
			.visibleWhen(() -> shapeMode.getSelected().sides);

	private final SliderSetting lineOpacity = new SliderSetting("Line opacity",
		"Opacity of the hole box outlines.", 0.5, 0, 1, 0.01,
		ValueDisplay.PERCENTAGE)
			.visibleWhen(() -> shapeMode.getSelected().lines);

	private final ColorSetting sideColor = new ColorSetting("Fill color",
		"The color that fills holes in the void.", new Color(225, 25, 25))
			.visibleWhen(() -> shapeMode.getSelected().sides);

	private final ColorSetting lineColor = new ColorSetting("Line color",
		"The color of the lines around holes in the void.",
		new Color(225, 25, 255))
			.visibleWhen(() -> shapeMode.getSelected().lines);

	private final ArrayList<Hole> holes = new ArrayList<>();
	private int scanCooldown;

	public VoidESPHack()
	{
		super("VoidESP");
		setCategory(Category.RENDER);
		addSetting(airOnly);
		addSetting(horizontalRadius);
		addSetting(holeHeight);
		addSetting(netherRoof);
		addSetting(shapeMode);
		addSetting(fillOpacity);
		addSetting(lineOpacity);
		addSetting(sideColor);
		addSetting(lineColor);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(RenderListener.class, this);
		EVENTS.add(WorldChangeListener.class, this);
		scanCooldown = 0;
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		EVENTS.remove(WorldChangeListener.class, this);
		holes.clear();
	}

	@Override
	public void onUpdate()
	{
		ClientLevel level = MC.level;
		if(level == null || MC.player == null)
			return;

		if(++scanCooldown < 10)
			return;
		scanCooldown = 0;

		holes.clear();
		if(level.dimension() == Level.END)
			return;

		boolean nether = level.dimension() == Level.NETHER;
		boolean checkNetherRoof = nether && netherRoof.isChecked();
		int radius = horizontalRadius.getValueI();
		BlockPos center = MC.player.blockPosition();
		int minY = level.getMinY();

		for(int x = center.getX() - radius; x <= center.getX() + radius; x++)
			for(int z = center.getZ() - radius; z <= center.getZ()
				+ radius; z++)
			{
				if(!level.hasChunk(x >> 4, z >> 4))
					continue;

				if(isHole(level, x, minY, z, false))
					holes.add(new Hole(new BlockPos(x, minY, z)));

				if(checkNetherRoof && isHole(level, x, 127, z, true))
					holes.add(new Hole(new BlockPos(x, 127, z)));
			}
	}

	private boolean isHole(ClientLevel level, int x, int y, int z,
		boolean nether)
	{
		int height = holeHeight.getValueI();
		for(int i = 0; i < height; i++)
		{
			BlockPos pos = new BlockPos(x, nether ? y - i : y + i, z);
			BlockState state = level.getBlockState(pos);

			if(airOnly.isChecked() ? !state.isAir()
				: state.getBlock() == Blocks.BEDROCK)
				return false;
		}

		return true;
	}

	@Override
	public void onRender(PoseStack matrixStack, float partialTicks)
	{
		if(holes.isEmpty())
			return;

		boolean sides = shapeMode.getSelected().sides;
		boolean lines = shapeMode.getSelected().lines;
		int fillColor = sideColor.getColorI(fillOpacity.getValueF());
		int outlineColor = lineColor.getColorI(lineOpacity.getValueF());

		for(Hole hole : holes)
		{
			BlockPos pos = hole.pos();
			AABB box = new AABB(Vec3.atLowerCornerOf(pos),
				Vec3.atLowerCornerOf(pos.offset(1, 1, 1)));

			if(sides)
				RenderUtils.drawSolidBox(matrixStack, box, fillColor, false);
			if(lines)
				RenderUtils.drawOutlinedBox(matrixStack, box, outlineColor,
					false);
		}
	}

	@Override
	public void onWorldChange(ClientLevel world)
	{
		holes.clear();
		scanCooldown = 0;
	}

	private record Hole(BlockPos pos)
	{}

	private enum ShapeMode
	{
		BOTH("Both", true, true),
		LINES("Lines only", false, true),
		SIDES("Sides only", true, false);

		private final String name;
		private final boolean sides;
		private final boolean lines;

		ShapeMode(String name, boolean sides, boolean lines)
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
