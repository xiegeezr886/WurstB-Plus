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
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.events.WorldChangeListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.RenderUtils;

/**
 * Simplified port of Meteor's Trail module. Instead of spawning particles, this
 * version records the last few player positions and renders them as a fading
 * line strip.
 */
@SearchTags({"trail", "breadcrumbs", "footprints", "path"})
public final class TrailHack extends Hack
	implements UpdateListener, RenderListener, WorldChangeListener
{
	private final SliderSetting maxPoints = new SliderSetting("Max points",
		"The maximum number of positions to keep.", 500, 50, 5000, 50,
		ValueDisplay.INTEGER);

	private final SliderSetting minDistance = new SliderSetting(
		"Min distance",
		"How far you have to move before another point is added.", 0.25, 0.05,
		2, 0.05, ValueDisplay.DECIMAL.withSuffix(" blocks"));

	private final SliderSetting opacity = new SliderSetting("Opacity",
		"Opacity of the newest part of the trail.", 1, 0, 1, 0.01,
		ValueDisplay.PERCENTAGE);

	private final ColorSetting color = new ColorSetting("Color",
		"The color of the trail.", Color.CYAN);

	private final List<Vec3> points = new ArrayList<>();
	private Vec3 lastPos;

	public TrailHack()
	{
		super("Trail");
		setCategory(Category.RENDER);
		addSetting(maxPoints);
		addSetting(minDistance);
		addSetting(opacity);
		addSetting(color);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(RenderListener.class, this);
		EVENTS.add(WorldChangeListener.class, this);
		points.clear();
		lastPos = null;
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		EVENTS.remove(WorldChangeListener.class, this);
		points.clear();
		lastPos = null;
	}

	@Override
	public void onWorldChange(ClientLevel world)
	{
		points.clear();
		lastPos = null;
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null)
			return;

		Vec3 pos = MC.player.position();
		if(lastPos == null)
		{
			points.add(pos);
			lastPos = pos;
			return;
		}

		if(pos.distanceTo(lastPos) < minDistance.getValue())
			return;

		points.add(pos);
		lastPos = pos;

		int max = maxPoints.getValueI();
		while(points.size() > max)
			points.remove(0);
	}

	@Override
	public void onRender(PoseStack matrixStack, float partialTicks)
	{
		if(MC.level == null || MC.player == null || points.size() < 2)
			return;

		int n = points.size();
		float maxAlpha = opacity.getValueF();

		for(int i = 0; i < n - 1; i++)
		{
			Vec3 start = points.get(i).add(0, 0.1, 0);
			Vec3 end = points.get(i + 1).add(0, 0.1, 0);
			float alpha = maxAlpha * (i + 1) / (float)(n - 1);

			RenderUtils.drawLine(matrixStack, start, end,
				color.getColorI(alpha), true);
		}
	}
}
