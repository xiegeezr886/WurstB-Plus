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
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.BlockBreakingProgressListener;
import net.wurstclient.events.PacketInputListener;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.events.WorldChangeListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.mixin.MultiPlayerGameModeAccessor;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.RenderUtils;

@SearchTags({"break indicators", "block break", "mining progress"})
public final class BreakIndicatorsHack extends Hack implements UpdateListener,
	RenderListener, PacketInputListener, BlockBreakingProgressListener,
	WorldChangeListener
{
	private final EnumSetting<ShapeMode> shapeMode = new EnumSetting<>(
		"Shape mode", "How the breaking progress is rendered.", ShapeMode.values(),
		ShapeMode.BOTH);

	private final CheckboxSetting ownBlocks = new CheckboxSetting("Own blocks",
		"Shows the progress of blocks you are breaking.", true);

	private final CheckboxSetting otherPlayers =
		new CheckboxSetting("Other players",
			"Shows the progress of blocks other players are breaking.", true);

	private final SliderSetting fillOpacity = new SliderSetting("Fill opacity",
		"Opacity of the filled area inside the breaking box.", 0.59, 0, 1, 0.01,
		ValueDisplay.PERCENTAGE)
			.visibleWhen(() -> shapeMode.getSelected().sides);

	private final SliderSetting lineOpacity = new SliderSetting("Line opacity",
		"Opacity of the breaking box outlines.", 0.59, 0, 1, 0.01,
		ValueDisplay.PERCENTAGE)
			.visibleWhen(() -> shapeMode.getSelected().lines);

	private final ColorSetting startSideColor = new ColorSetting(
		"Start fill color", "Fill color when the block is barely broken.",
		new Color(25, 252, 25)).visibleWhen(() -> shapeMode.getSelected().sides);

	private final ColorSetting startLineColor = new ColorSetting(
		"Start line color", "Line color when the block is barely broken.",
		new Color(25, 252, 25)).visibleWhen(() -> shapeMode.getSelected().lines);

	private final ColorSetting endSideColor = new ColorSetting("End fill color",
		"Fill color when the block is almost broken.",
		new Color(255, 25, 25)).visibleWhen(() -> shapeMode.getSelected().sides);

	private final ColorSetting endLineColor = new ColorSetting("End line color",
		"Line color when the block is almost broken.",
		new Color(255, 25, 25)).visibleWhen(() -> shapeMode.getSelected().lines);

	private final ConcurrentHashMap<Integer, Progress> breaking =
		new ConcurrentHashMap<>();

	public BreakIndicatorsHack()
	{
		super("BreakIndicators");
		setCategory(Category.RENDER);
		addSetting(shapeMode);
		addSetting(ownBlocks);
		addSetting(otherPlayers);
		addSetting(fillOpacity);
		addSetting(lineOpacity);
		addSetting(startSideColor);
		addSetting(startLineColor);
		addSetting(endSideColor);
		addSetting(endLineColor);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(RenderListener.class, this);
		EVENTS.add(PacketInputListener.class, this);
		EVENTS.add(BlockBreakingProgressListener.class, this);
		EVENTS.add(WorldChangeListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		EVENTS.remove(PacketInputListener.class, this);
		EVENTS.remove(BlockBreakingProgressListener.class, this);
		EVENTS.remove(WorldChangeListener.class, this);
		breaking.clear();
	}

	@Override
	public void onReceivedPacket(PacketInputEvent event)
	{
		if(!(event.getPacket() instanceof ClientboundBlockDestructionPacket packet)
			|| !otherPlayers.isChecked())
			return;

		int id = packet.getId();
		if(MC.player != null && id == MC.player.getId())
			return;

		int stage = packet.getProgress();
		if(stage < 0 || stage >= 10)
		{
			breaking.remove(id);
			return;
		}

		float progress = Mth.clamp((stage + 1) / 9F, 0, 1);
		breaking.put(id, new Progress(packet.getPos().immutable(), progress));
	}

	@Override
	public void onBlockBreakingProgress(BlockBreakingProgressEvent event)
	{
		if(!ownBlocks.isChecked() || MC.player == null || MC.gameMode == null)
			return;

		float progress = ((MultiPlayerGameModeAccessor)(Object)MC.gameMode)
			.getDestroyProgress();
		if(progress > 0)
			breaking.put(MC.player.getId(),
				new Progress(event.getBlockPos().immutable(), progress));
	}

	@Override
	public void onUpdate()
	{
		if(MC.level == null || MC.player == null || MC.gameMode == null)
		{
			breaking.clear();
			return;
		}

		int ownId = MC.player.getId();
		if(!ownBlocks.isChecked()
			|| !((MultiPlayerGameModeAccessor)(Object)MC.gameMode)
				.getIsDestroying())
			breaking.remove(ownId);

		if(!otherPlayers.isChecked())
			breaking.keySet().removeIf(id -> id != ownId);

		breaking.entrySet().removeIf(entry -> {
			BlockPos pos = entry.getValue().pos();
			return MC.level.getBlockState(pos).isAir();
		});
	}

	@Override
	public void onRender(PoseStack matrixStack, float partialTicks)
	{
		if(MC.level == null || breaking.isEmpty())
			return;

		int fillAlpha = (int)(Mth.clamp(fillOpacity.getValue(), 0, 1) * 255);
		int lineAlpha = (int)(Mth.clamp(lineOpacity.getValue(), 0, 1) * 255);
		boolean sides = shapeMode.getSelected().sides;
		boolean lines = shapeMode.getSelected().lines;

		for(Progress progress : breaking.values())
		{
			BlockState state = MC.level.getBlockState(progress.pos());
			if(state.isAir())
				continue;

			VoxelShape shape = state.getShape(MC.level, progress.pos());
			if(shape.isEmpty())
				continue;

			AABB orig = shape.bounds();
			float p = Mth.clamp(progress.progress(), 0, 1);
			double shrink = p / 2;
			AABB box = orig.deflate(orig.getXsize() * shrink,
				orig.getYsize() * shrink, orig.getZsize() * shrink)
				.move(progress.pos());

			if(sides)
			{
				int color = lerpColor(startSideColor.getColorI(),
					endSideColor.getColorI(), p, fillAlpha);
				RenderUtils.drawSolidBox(matrixStack, box, color, false);
			}

			if(lines)
			{
				int color = lerpColor(startLineColor.getColorI(),
					endLineColor.getColorI(), p, lineAlpha);
				RenderUtils.drawOutlinedBox(matrixStack, box, color, false);
			}
		}
	}

	@Override
	public void onWorldChange(ClientLevel world)
	{
		breaking.clear();
	}

	private static int lerpColor(int from, int to, float progress, int alpha)
	{
		int red = (int)Mth.lerp(progress, (from >> 16) & 0xFF,
			(to >> 16) & 0xFF);
		int green = (int)Mth.lerp(progress, (from >> 8) & 0xFF,
			(to >> 8) & 0xFF);
		int blue = (int)Mth.lerp(progress, from & 0xFF, to & 0xFF);
		return alpha << 24 | red << 16 | green << 8 | blue;
	}

	private record Progress(BlockPos pos, float progress)
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
