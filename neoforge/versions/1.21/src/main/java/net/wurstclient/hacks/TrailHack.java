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
import org.joml.Matrix4f;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.WurstRenderLayers;
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

		Vec3 cam = RenderUtils.getCameraPos();
		Matrix4f matrix = matrixStack.last().pose();
		int argb = color.getColorI();
		float r = ((argb >> 16) & 0xFF) / 255F;
		float g = ((argb >> 8) & 0xFF) / 255F;
		float b = (argb & 0xFF) / 255F;
		float maxAlpha = opacity.getValueF();

		var vcp = RenderUtils.getVCP();
		var layer = WurstRenderLayers.getLineStrip(true);
		var buf = vcp.getBuffer(layer);
		int n = points.size();

		for(int i = 0; i < n - 1; i++)
		{
			Vec3 start = points.get(i);
			Vec3 end = points.get(i + 1);

			float alphaStart = maxAlpha * i / (float)(n - 1);
			float alphaEnd = maxAlpha * (i + 1) / (float)(n - 1);

			float x1 = (float)(start.x - cam.x);
			float y1 = (float)(start.y - cam.y + 0.1);
			float z1 = (float)(start.z - cam.z);
			float x2 = (float)(end.x - cam.x);
			float y2 = (float)(end.y - cam.y + 0.1);
			float z2 = (float)(end.z - cam.z);

			float dx = x2 - x1;
			float dy = y2 - y1;
			float dz = z2 - z1;
			float length = Math.max(1.0E-4F,
				(float)Math.sqrt(dx * dx + dy * dy + dz * dz));
			float nx = dx / length;
			float ny = dy / length;
			float nz = dz / length;

			buf.addVertex(matrix, x1, y1, z1)
				.setColor(r, g, b, alphaStart).setNormal(nx, ny, nz);
			buf.addVertex(matrix, x2, y2, z2)
				.setColor(r, g, b, alphaEnd).setNormal(nx, ny, nz);
		}

		vcp.endBatch(layer);
	}
}
