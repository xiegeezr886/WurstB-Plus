/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
package net.wurstclient.hud2.mods;

import java.awt.Color;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.wurstclient.hud2.HUDMod;

public final class MinimapMod extends HUDMod
{
	private static final float SIZE = 110;
	private static final double RANGE = 40;

	public MinimapMod()
	{
		super("Minimap", "Minimap", "A radar-style minimap of nearby entities.");
		setTranslationKey("minimap");
		setDefaultPosition(350, 180);
	}

	@Override
	protected void draw()
	{
		drawBackground(SIZE, SIZE);
		float center = SIZE / 2;
		for(int i = 1; i < 4; i++)
		{
			float offset = i * SIZE / 4;
			drawRect(offset, 6, 1, SIZE - 12,
				new Color(255, 255, 255, 20));
			drawRect(6, offset, SIZE - 12, 1,
				new Color(255, 255, 255, 20));
		}

		if(MC.player == null || MC.level == null)
		{
			setSize(Math.round(SIZE), Math.round(SIZE));
			return;
		}

		for(Entity entity : MC.level.entitiesForRendering())
		{
			if(entity == MC.player || entity.isRemoved())
				continue;

			double dx = entity.getX() - MC.player.getX();
			double dz = entity.getZ() - MC.player.getZ();
			double distance = Math.sqrt(dx * dx + dz * dz);
			if(distance > RANGE)
				continue;

			float x = (float)(center + dx / RANGE * (center - 6));
			float y = (float)(center + dz / RANGE * (center - 6));
			Color color = entity instanceof Player
				? new Color(255, 70, 70)
				: entity instanceof LivingEntity ? new Color(255, 170, 40)
					: new Color(190, 190, 190);
			drawCircle(x, y, 2, color);
		}

		drawCircle(center, center, 3,
			new Color(accentColor(255), true));
		setSize(Math.round(SIZE), Math.round(SIZE));
	}
}
