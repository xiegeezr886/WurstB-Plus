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
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.joml.Quaternionf;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PacketInputListener;
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

@SearchTags({"mine esp", "mining esp", "break progress", "mining progress"})
public final class MineEspHack extends Hack
	implements PacketInputListener, UpdateListener, RenderListener,
	WorldChangeListener
{
	private final SliderSetting range = new SliderSetting("Range",
		"Only mining activity within this distance is shown.", 15, 0, 50, 1,
		ValueDisplay.INTEGER.withSuffix(" blocks"));

	private final SliderSetting maxTime = new SliderSetting("Keep time",
		"How long a finished box stays visible while fading out.", 3, 0, 20,
		0.5, ValueDisplay.DECIMAL.withSuffix("s"));

	private final EnumSetting<ShapeMode> shapeMode = new EnumSetting<>(
		"Shape mode", ShapeMode.values(), ShapeMode.BOTH);

	private final ColorSetting lineColor =
		new ColorSetting("Line color", new Color(255, 0, 0));

	private final ColorSetting sideColor =
		new ColorSetting("Fill color", new Color(255, 0, 0, 50));

	private final CheckboxSetting renderName =
		new CheckboxSetting("Show name", true);

	private final CheckboxSetting renderProgress =
		new CheckboxSetting("Show progress", true);

	private final CheckboxSetting smoothProgress = new CheckboxSetting(
		"Smooth progress", "Animates the displayed percentage smoothly.", true)
			.visibleWhen(renderProgress::isChecked);

	private final CheckboxSetting renderBlock =
		new CheckboxSetting("Show block name", true);

	private final SliderSetting textScale = new SliderSetting("Text scale",
		"How large the label is.", 1, 0.5, 3, 0.05, ValueDisplay.DECIMAL);

	private final ColorSetting textColor =
		new ColorSetting("Text color", new Color(255, 255, 255));

	private final ArrayList<RenderInfo> renders = new ArrayList<>();
	private final ConcurrentLinkedQueue<PendingUpdate> pendingUpdates =
		new ConcurrentLinkedQueue<>();

	public MineEspHack()
	{
		super("MineESP");
		setCategory(Category.RENDER);
		addSetting(range);
		addSetting(maxTime);
		addSetting(shapeMode);
		addSetting(lineColor);
		addSetting(sideColor);
		addSetting(renderName);
		addSetting(renderProgress);
		addSetting(smoothProgress);
		addSetting(renderBlock);
		addSetting(textScale);
		addSetting(textColor);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(PacketInputListener.class, this);
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(RenderListener.class, this);
		EVENTS.add(WorldChangeListener.class, this);
		renders.clear();
		pendingUpdates.clear();
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(PacketInputListener.class, this);
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		EVENTS.remove(WorldChangeListener.class, this);
		renders.clear();
		pendingUpdates.clear();
	}

	@Override
	public void onWorldChange(ClientLevel world)
	{
		renders.clear();
		pendingUpdates.clear();
	}

	@Override
	public void onReceivedPacket(PacketInputEvent event)
	{
		if(!(event.getPacket() instanceof ClientboundBlockDestructionPacket packet)
			|| MC.level == null || MC.player == null)
			return;

		BlockPos pos = packet.getPos();
		Vec3 center = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5,
			pos.getZ() + 0.5);
		if(MC.player.getEyePosition().distanceTo(center) > range.getValue())
			return;

		BlockState state = MC.level.getBlockState(pos);
		String blockName = state.isAir() ? "Air"
			: state.getBlock().getName().getString();

		pendingUpdates.add(new PendingUpdate(pos.immutable(), packet.getId(),
			packet.getProgress(), blockName));
	}

	@Override
	public void onUpdate()
	{
		if(MC.level == null)
		{
			renders.clear();
			pendingUpdates.clear();
			return;
		}

		while(!pendingUpdates.isEmpty())
		{
			PendingUpdate update = pendingUpdates.poll();
			RenderInfo existing = null;
			for(RenderInfo info : renders)
				if(info.id == update.id && info.pos.equals(update.pos))
				{
					existing = info;
					break;
				}

			if(update.stage >= 0 && update.stage <= 9)
			{
				if(existing != null)
				{
					existing.time = System.currentTimeMillis();
					existing.stage = update.stage;
					if(!update.blockName.equals("Air"))
						existing.blockName = update.blockName;
				}else
					renders.add(new RenderInfo(update.pos, update.id,
						System.currentTimeMillis(), update.stage,
						update.blockName));
			}
		}

		long now = System.currentTimeMillis();
		long keep = Math.max(50, Math.round(maxTime.getValue() * 1000));
		Iterator<RenderInfo> it = renders.iterator();
		while(it.hasNext())
			if(now > it.next().time + keep)
				it.remove();
	}

	@Override
	public void onRender(PoseStack matrixStack, float partialTicks)
	{
		if(renders.isEmpty())
			return;

		boolean sides = shapeMode.getSelected().sides;
		boolean lines = shapeMode.getSelected().lines;
		long now = System.currentTimeMillis();
		long keep = Math.max(50, Math.round(maxTime.getValue() * 1000));
		Vec3 camPos = MC.gameRenderer.mainCamera().position();
		Quaternionf cameraRotation =
			new Quaternionf(MC.gameRenderer.mainCamera().rotation());
		float scale = 0.025F * textScale.getValueF();

		for(RenderInfo info : renders)
		{
			double lifeDelta = Math.min((now - info.time) / (double)keep, 1);
			float target = (info.stage + 1) * 10F;
			if(smoothProgress.isChecked())
				info.animatedProgress =
					Mth.lerp(0.2F, info.animatedProgress, target);
			else
				info.animatedProgress = target;

			double progress =
				1 - Math.pow(1 - Math.min(lifeDelta * 4, 1), 5);
			AABB box = getBox(info.pos, progress);
			int side = withAlpha(sideColor.getColorI(),
				(int)(sideColor.getColor().getAlpha() * (1 - lifeDelta)));
			int line = withAlpha(lineColor.getColorI(),
				(int)(lineColor.getColor().getAlpha() * (1 - lifeDelta)));

			if(sides)
				RenderUtils.drawSolidBox(matrixStack, box, side, false);
			if(lines)
				RenderUtils.drawOutlinedBox(matrixStack, box, line, false);

			if(!renderName.isChecked() && !renderProgress.isChecked()
				&& !renderBlock.isChecked())
				continue;

			String text = getLabel(info);
			if(text.isEmpty())
				continue;

			int color = withAlpha(textColor.getColorI(),
				(int)(textColor.getColor().getAlpha() * (1 - lifeDelta)));
			matrixStack.pushPose();
			matrixStack.translate(info.pos.getX() + 0.5 - camPos.x,
				info.pos.getY() + 0.5 - camPos.y,
				info.pos.getZ() + 0.5 - camPos.z);
			matrixStack.rotate(cameraRotation);
			matrixStack.scale(-scale, -scale, scale);
			RenderUtils.submitText(matrixStack, -MC.font.width(text) / 2F, 0,
				Component.literal(text).getVisualOrderText(), false,
				Font.DisplayMode.SEE_THROUGH, 0xF000F0, color, 0, 0);
			matrixStack.popPose();
		}
	}

	private String getLabel(RenderInfo info)
	{
		StringBuilder text = new StringBuilder();
		if(renderName.isChecked())
		{
			Entity entity = MC.level == null ? null
				: MC.level.getEntity(info.id);
			text.append(entity != null ? entity.getName().getString()
				: "Unknown");
		}
		if(renderProgress.isChecked())
		{
			if(!text.isEmpty())
				text.append(' ');
			text.append((int)Math.min(100,
				Math.ceil(info.animatedProgress))).append('%');
		}
		if(renderBlock.isChecked())
		{
			if(!text.isEmpty())
				text.append(' ');
			text.append(info.blockName);
		}
		return text.toString();
	}

	private AABB getBox(BlockPos pos, double progress)
	{
		return new AABB(pos.getX() + 0.5 - progress / 2,
			pos.getY() + 0.5 - progress / 2,
			pos.getZ() + 0.5 - progress / 2, pos.getX() + 0.5 + progress / 2,
			pos.getY() + 0.5 + progress / 2,
			pos.getZ() + 0.5 + progress / 2);
	}

	private static int withAlpha(int color, int alpha)
	{
		return color & 0x00FFFFFF | alpha << 24;
	}

	private record PendingUpdate(BlockPos pos, int id, int stage,
		String blockName)
	{
	}

	private static final class RenderInfo
	{
		private final BlockPos pos;
		private final int id;
		private long time;
		private int stage;
		private float animatedProgress;
		private String blockName;

		private RenderInfo(BlockPos pos, int id, long time, int stage,
			String blockName)
		{
			this.pos = pos;
			this.id = id;
			this.time = time;
			this.stage = stage;
			this.animatedProgress = (stage + 1) * 10F;
			this.blockName = blockName;
		}
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
