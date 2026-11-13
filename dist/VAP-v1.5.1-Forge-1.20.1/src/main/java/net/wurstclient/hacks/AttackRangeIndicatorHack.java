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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
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

@SearchTags({"attack range", "reach indicator", "reach circle"})
public final class AttackRangeIndicatorHack extends Hack
	implements RenderListener
{
	private static final int CIRCLE_SEGMENTS = 64;

	private final SliderSetting range = new SliderSetting("Range",
		"The attack reach to draw the circle at.", 3, 1, 6, 0.1,
		ValueDisplay.DECIMAL.withSuffix(" blocks"));

	private final CheckboxSetting showTargets =
		new CheckboxSetting("Show targets",
			"Draws a box around living entities inside the attack range.", true);

	private final ColorSetting circleColor = new ColorSetting("Circle color",
		"The color of the attack range circle.", new Color(0, 255, 0, 200));

	private final ColorSetting targetColor = new ColorSetting("Target color",
		"The color of the boxes around targets in range.",
		new Color(255, 80, 80, 200));

	private final CheckboxSetting throughWalls = new CheckboxSetting(
		"Through walls", "Renders the indicator through solid blocks.", true);

	public AttackRangeIndicatorHack()
	{
		super("AttackRangeIndicator");
		setCategory(Category.RENDER);
		addSetting(range);
		addSetting(showTargets);
		addSetting(circleColor);
		addSetting(targetColor);
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

		boolean depthTest = !throughWalls.isChecked();
		double radius = range.getValue();
		Vec3 center = EntityUtils.getLerpedPos(MC.player, partialTicks);

		List<Vec3> points = new ArrayList<>(CIRCLE_SEGMENTS + 1);
		for(int i = 0; i <= CIRCLE_SEGMENTS; i++)
		{
			double angle = Math.PI * 2 * i / CIRCLE_SEGMENTS;
			points.add(new Vec3(center.x + Math.cos(angle) * radius,
				center.y + 0.03, center.z + Math.sin(angle) * radius));
		}
		RenderUtils.drawCurvedLine(matrixStack, points,
			circleColor.getColorI(), depthTest);

		if(!showTargets.isChecked())
			return;

		List<AABB> boxes = new ArrayList<>();
		for(Entity entity : MC.level.entitiesForRendering())
		{
			if(entity == MC.player || entity.isRemoved()
				|| !(entity instanceof LivingEntity living)
				|| living.getHealth() <= 0)
				continue;

			if(MC.player.distanceTo(entity) > radius)
				continue;

			boxes.add(EntityUtils.getLerpedBox(entity, partialTicks));
		}

		if(boxes.isEmpty())
			return;

		RenderUtils.drawSolidBoxes(matrixStack, boxes,
			targetColor.getColorI(0.25F), depthTest);
		RenderUtils.drawOutlinedBoxes(matrixStack, boxes,
			targetColor.getColorI(), depthTest);
	}
}
