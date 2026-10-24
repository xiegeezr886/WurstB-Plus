/*
 * Copyright (c) 2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */

/* Ported from LeavesHack (AGPL-3.0). */
package net.wurstclient.hacks;

import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
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
import net.wurstclient.util.RenderUtils;

/**
 * Draws a fading ghost box around blocks that have just appeared near the
 * player. LeavesHack's original version hooked its own BlockUtil placement
 * event; without that event, this port detects block changes in a small area
 * around the player instead.
 */
@SearchTags({"place render", "PlaceRender", "placement preview",
	"ghost block"})
public final class PlaceRenderHack extends Hack
	implements UpdateListener, RenderListener, WorldChangeListener
{
	private final SliderSetting trackRange = new SliderSetting("Track range",
		"Radius around the player in which new blocks are detected.", 6, 2, 10,
		1, ValueDisplay.INTEGER);

	private final SliderSetting timeout = new SliderSetting("Timeout",
		"How long the placement preview stays visible.", 40, 5, 200, 1,
		ValueDisplay.INTEGER.withSuffix(" ticks"));

	private final SliderSetting fadeExponent = new SliderSetting(
		"Animation exponent",
		"Higher values keep the preview at full size for longer.", 3, 1, 10,
		1, ValueDisplay.INTEGER);

	private final CheckboxSetting renderFill = new CheckboxSetting("Fill",
		"Render the filled part of the preview.", true);

	private final CheckboxSetting depthTest = new CheckboxSetting("Depth test",
		"Whether the preview is hidden behind blocks.", false);

	private final ColorSetting fillColor =
		new ColorSetting("Fill color", "Color of the filled preview.",
			new Color(140, 142, 255, 100));

	private final ColorSetting lineColor =
		new ColorSetting("Line color", "Color of the preview outline.",
			new Color(140, 142, 255, 255));

	private final HashMap<BlockPos, BlockState> previous = new HashMap<>();
	private final ArrayList<Placement> placements = new ArrayList<>();

	private ClientLevel lastLevel;
	private int ticks;

	public PlaceRenderHack()
	{
		super("PlaceRender");
		setCategory(Category.RENDER);
		addSetting(trackRange);
		addSetting(timeout);
		addSetting(fadeExponent);
		addSetting(renderFill);
		addSetting(depthTest);
		addSetting(fillColor);
		addSetting(lineColor);
	}

	@Override
	protected void onEnable()
	{
		clear();
		lastLevel = MC.level;
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(RenderListener.class, this);
		EVENTS.add(WorldChangeListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		EVENTS.remove(WorldChangeListener.class, this);
		clear();
	}

	@Override
	public void onWorldChange(ClientLevel world)
	{
		clear();
		lastLevel = world;
	}

	private void clear()
	{
		previous.clear();
		placements.clear();
		ticks = 0;
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null)
		{
			clear();
			return;
		}

		if(MC.level != lastLevel)
		{
			clear();
			lastLevel = MC.level;
		}

		ticks++;
		scanForPlacements();

		int lifetime = timeout.getValueI();
		placements.removeIf(p -> ticks - p.startTick >= lifetime);
	}

	private void scanForPlacements()
	{
		int range = trackRange.getValueI();
		BlockPos center = MC.player.blockPosition();
		HashMap<BlockPos, BlockState> current = new HashMap<>();

		for(BlockPos pos : BlockPos.betweenClosed(center.offset(-range, -range,
			-range), center.offset(range, range, range)))
		{
			BlockPos immutable = pos.immutable();
			BlockState now = MC.level.getBlockState(immutable);
			BlockState before = previous.get(immutable);

			if(before != null && isNewlyPlaced(before, now))
				addPlacement(immutable);

			current.put(immutable, now);
		}

		previous.clear();
		previous.putAll(current);
	}

	private boolean isNewlyPlaced(BlockState before, BlockState now)
	{
		if(!before.isAir() && !before.canBeReplaced())
			return false;
		if(now.isAir() || now.canBeReplaced())
			return false;

		// Fluids, fire and similar blockless states have no item form.
		return now.getBlock().asItem() != Items.AIR;
	}

	private void addPlacement(BlockPos pos)
	{
		placements.add(new Placement(pos, ticks));
		if(placements.size() > 256)
			placements.remove(0);
	}

	@Override
	public void onRender(PoseStack matrixStack, float partialTicks)
	{
		if(MC.level == null || placements.isEmpty())
			return;

		int lifetime = timeout.getValueI();
		boolean fill = renderFill.isChecked();
		int fillRgb = fillColor.getColorI();
		int lineRgb = lineColor.getColorI();

		for(Placement placement : placements)
		{
			float progress = Mth.clamp(
				(ticks - placement.startTick + partialTicks) / (float)lifetime,
				0F, 1F);
			float size = (float)Math.pow(1 - progress,
				fadeExponent.getValue()) / 2;
			double centerX = placement.pos.getX() + 0.5;
			double centerY = placement.pos.getY() + 0.5;
			double centerZ = placement.pos.getZ() + 0.5;

			AABB box = new AABB(centerX - size, centerY - size, centerZ - size,
				centerX + size, centerY + size, centerZ + size);

			int alpha = (int)((1 - progress) * 255);
			if(fill)
				RenderUtils.drawSolidBox(matrixStack, box,
					withAlpha(fillRgb, alpha), depthTest.isChecked());
			RenderUtils.drawOutlinedBox(matrixStack, box,
				withAlpha(lineRgb, alpha), depthTest.isChecked());
		}
	}

	private static int withAlpha(int rgb, int alpha)
	{
		return alpha << 24 | (rgb & 0x00FFFFFF);
	}

	private static final class Placement
	{
		private final BlockPos pos;
		private final int startTick;

		private Placement(BlockPos pos, int startTick)
		{
			this.pos = pos;
			this.startTick = startTick;
		}
	}
}
