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
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.RenderListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.EntityUtils;
import net.wurstclient.util.RenderUtils;

@SearchTags({"hitbox esp", "hitbox", "bounding box"})
public final class HitboxESPHack extends Hack implements RenderListener
{
	private final CheckboxSetting showPlayers =
		new CheckboxSetting("Players", "Shows player hitboxes.", true);

	private final CheckboxSetting showMobs = new CheckboxSetting("Mobs",
		"Shows the hitboxes of all non-player entities.", true);

	private final SliderSetting maxDistance = new SliderSetting("Max distance",
		"Entities farther away than this are not rendered.", 64, 4, 256, 4,
		ValueDisplay.INTEGER.withSuffix(" blocks"));

	private final ColorSetting color = new ColorSetting("Color",
		"The color of the hitboxes.", new Color(255, 255, 255));

	private final SliderSetting fillOpacity = new SliderSetting("Fill opacity",
		"Opacity of the filled area inside the hitboxes.", 0.2, 0, 1, 0.01,
		ValueDisplay.PERCENTAGE);

	private final SliderSetting lineOpacity = new SliderSetting("Line opacity",
		"Opacity of the hitbox outlines.", 0.75, 0, 1, 0.01,
		ValueDisplay.PERCENTAGE);

	private final CheckboxSetting throughWalls = new CheckboxSetting(
		"Through walls", "Renders the hitboxes through solid blocks.", true);

	public HitboxESPHack()
	{
		super("HitboxESP");
		setCategory(Category.RENDER);
		addSetting(showPlayers);
		addSetting(showMobs);
		addSetting(maxDistance);
		addSetting(color);
		addSetting(fillOpacity);
		addSetting(lineOpacity);
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

		double maxDistanceSq = maxDistance.getValueSq();
		List<AABB> boxes = new ArrayList<>();

		for(Entity entity : MC.level.entitiesForRendering())
		{
			if(entity == MC.player || entity.isRemoved())
				continue;

			boolean player = entity instanceof Player;
			if(player ? !showPlayers.isChecked() : !showMobs.isChecked())
				continue;

			if(MC.player.distanceToSqr(entity) > maxDistanceSq)
				continue;

			boxes.add(EntityUtils.getLerpedBox(entity, partialTicks));
		}

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
