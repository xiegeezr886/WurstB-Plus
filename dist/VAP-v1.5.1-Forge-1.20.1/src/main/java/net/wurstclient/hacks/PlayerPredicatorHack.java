/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
package net.wurstclient.hacks;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.world.entity.player.Player;
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

/**
 * Draws boxes at the positions nearby players are expected to reach based on
 * their current velocity.
 *
 * <p>Simplification: Soar reconstructed the target's real position from
 * entity movement packets through a mixin. This port uses the local velocity
 * of every nearby player instead, which needs no packet hooks, and renders a
 * short trail of predicted positions.
 */
@SearchTags({"player predicator", "prediction", "predicted position",
	"velocity", "movement"})
public final class PlayerPredicatorHack extends Hack implements RenderListener
{
	private final SliderSetting maxDistance = new SliderSetting("Max distance",
		"Players farther away than this are not predicted.", 128, 8, 512, 8,
		ValueDisplay.INTEGER.withSuffix(" blocks"));

	private final SliderSetting predictionTicks = new SliderSetting(
		"Prediction ticks", "How many ticks of movement are predicted.", 5, 1,
		20, 1, ValueDisplay.INTEGER.withSuffix(" ticks"));

	private final ColorSetting color = new ColorSetting("Color",
		"Color of the predicted position boxes.", new Color(255, 80, 80));

	private final CheckboxSetting fill = new CheckboxSetting("Fill",
		"Fills the predicted position boxes.", true);

	private final CheckboxSetting showTrail = new CheckboxSetting("Show trail",
		"Draws a box for every predicted tick instead of only the last one.",
		true);

	private final CheckboxSetting throughWalls = new CheckboxSetting(
		"Through walls", "Renders the boxes through solid blocks.", true);

	public PlayerPredicatorHack()
	{
		super("PlayerPredicator");
		setCategory(Category.RENDER);
		addSetting(maxDistance);
		addSetting(predictionTicks);
		addSetting(color);
		addSetting(fill);
		addSetting(showTrail);
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
		int ticks = predictionTicks.getValueI();
		boolean trail = showTrail.isChecked();
		boolean depthTest = !throughWalls.isChecked();
		int firstStep = trail ? 1 : ticks;
		ArrayList<RenderUtils.ColoredBox> fills = new ArrayList<>();
		ArrayList<RenderUtils.ColoredBox> lines = new ArrayList<>();

		for(Player player : MC.level.players())
		{
			if(player == MC.player || player.isRemoved()
				|| player.getHealth() <= 0
				|| MC.player.distanceToSqr(player) > maxDistanceSq)
				continue;

			Vec3 velocity = player.getDeltaMovement();
			if(velocity.lengthSqr() < 1.0E-6)
				continue;

			AABB box = EntityUtils.getLerpedBox(player, partialTicks);

			for(int step = firstStep; step <= ticks; step++)
			{
				Vec3 offset = velocity.scale(step);
				AABB predicted =
					box.move(offset.x, offset.y, offset.z);
				int alpha = trail
					? Math.max(45, 210 - step * 160 / Math.max(1, ticks))
					: 210;

				lines.add(new RenderUtils.ColoredBox(predicted,
					color.getColorI(alpha)));
				if(fill.isChecked())
					fills.add(new RenderUtils.ColoredBox(predicted,
						color.getColorI(Math.max(15, alpha / 4))));
			}
		}

		if(fills.isEmpty() && lines.isEmpty())
			return;

		if(!fills.isEmpty())
			RenderUtils.drawSolidBoxes(matrixStack, fills, depthTest);
		RenderUtils.drawOutlinedBoxes(matrixStack, lines, depthTest);
	}
}
