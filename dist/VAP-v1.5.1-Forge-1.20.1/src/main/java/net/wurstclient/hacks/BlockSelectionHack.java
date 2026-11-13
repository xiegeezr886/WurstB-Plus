/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 *
 * Ported from Meteor Client (GPL-3.0).
 */
package net.wurstclient.hacks;

import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.RenderListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.RenderUtils;

@SearchTags({"block selection", "BlockOutline", "block outline",
	"selection box"})
public final class BlockSelectionHack extends Hack implements RenderListener
{
	private final ColorSetting lineColor = new ColorSetting("Line color",
		"The color of the selection outlines.", Color.WHITE);

	private final ColorSetting fillColor = new ColorSetting("Fill color",
		"The color that fills the selection boxes.", Color.WHITE);

	private final SliderSetting lineOpacity = new SliderSetting("Line opacity",
		"Opacity of the selection outlines.", 1, 0, 1, 0.01,
		ValueDisplay.PERCENTAGE);

	private final SliderSetting fillOpacity = new SliderSetting("Fill opacity",
		"Opacity of the filled area inside the selection boxes.", 0.2, 0, 1,
		0.01, ValueDisplay.PERCENTAGE);

	private final CheckboxSetting advanced = new CheckboxSetting("Advanced",
		"Renders the exact shape of the block instead of one big box.", true);

	private final CheckboxSetting singleSide = new CheckboxSetting(
		"Single side", "Only renders the side you are looking at.", false);

	private final CheckboxSetting hideInside = new CheckboxSetting(
		"Hide when inside block",
		"Stops rendering the selection while you are inside the target block.",
		true);

	private final CheckboxSetting throughWalls = new CheckboxSetting(
		"Through walls", "Renders the selection through solid blocks.", false);

	public BlockSelectionHack()
	{
		super("BlockSelection");
		setCategory(Category.RENDER);
		addSetting(lineColor);
		addSetting(fillColor);
		addSetting(lineOpacity);
		addSetting(fillOpacity);
		addSetting(advanced);
		addSetting(singleSide);
		addSetting(hideInside);
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
	public void onRender(PoseStack matrixStack, float partialTicks)
	{
		if(MC.level == null || MC.player == null)
			return;

		HitResult hitResult = MC.hitResult;
		if(!(hitResult instanceof BlockHitResult blockHit)
			|| hitResult.getType() == HitResult.Type.MISS)
			return;
		if(hideInside.isChecked() && blockHit.isInside())
			return;

		BlockPos pos = blockHit.getBlockPos();
		BlockState state = MC.level.getBlockState(pos);
		VoxelShape shape = state.getShape(MC.level, pos);
		if(shape.isEmpty())
			return;

		boolean depthTest = !throughWalls.isChecked();
		int line = lineColor.getColorI(lineOpacity.getValueF());
		int fill = fillColor.getColorI(fillOpacity.getValueF());

		if(singleSide.isChecked())
		{
			AABB box = getFaceBox(pos, shape.bounds(),
				blockHit.getDirection());
			drawBox(matrixStack, box, fill, line, depthTest);
			return;
		}

		if(advanced.isChecked())
		{
			List<AABB> shapeBoxes = new ArrayList<>();
			for(AABB box : shape.toAabbs())
				shapeBoxes.add(box.move(pos));

			if((fill >>> 24) != 0)
				RenderUtils.drawSolidBoxes(matrixStack, shapeBoxes, fill,
					depthTest);
			if((line >>> 24) != 0)
				RenderUtils.drawOutlinedBoxes(matrixStack, shapeBoxes, line,
					depthTest);
			return;
		}

		drawBox(matrixStack, shape.bounds().move(pos), fill, line,
			depthTest);
	}

	private void drawBox(PoseStack matrixStack, AABB box, int fill, int line,
		boolean depthTest)
	{
		if((fill >>> 24) != 0)
			RenderUtils.drawSolidBox(matrixStack, box, fill, depthTest);
		if((line >>> 24) != 0)
			RenderUtils.drawOutlinedBox(matrixStack, box, line, depthTest);
	}

	/**
	 * Builds a nearly flat box for the face that is being looked at. The small
	 * inset keeps every edge non-zero in length so that the line normals stay
	 * valid.
	 */
	private AABB getFaceBox(BlockPos pos, AABB bounds, Direction side)
	{
		double inset = 0.002;
		double x1 = pos.getX() + bounds.minX;
		double x2 = pos.getX() + bounds.maxX;
		double y1 = pos.getY() + bounds.minY;
		double y2 = pos.getY() + bounds.maxY;
		double z1 = pos.getZ() + bounds.minZ;
		double z2 = pos.getZ() + bounds.maxZ;

		switch(side)
		{
			case DOWN:
			y2 = y1 + inset;
			break;

			case UP:
			y1 = y2 - inset;
			break;

			case NORTH:
			z2 = z1 + inset;
			break;

			case SOUTH:
			z1 = z2 - inset;
			break;

			case WEST:
			x2 = x1 + inset;
			break;

			case EAST:
			x1 = x2 - inset;
			break;
		}

		return new AABB(x1, y1, z1, x2, y2, z2);
	}
}
