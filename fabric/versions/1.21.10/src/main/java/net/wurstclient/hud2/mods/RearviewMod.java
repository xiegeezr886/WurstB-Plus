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
import net.minecraft.world.phys.Vec3;
import net.wurstclient.hud2.HUDMod;

public final class RearviewMod extends HUDMod
{
	private static final float WIDTH = 170;
	private static final float HEIGHT = 100;
	private static final double RANGE = 48;

	public RearviewMod()
	{
		super("Rearview", "Rearview",
			"A radar showing entities behind the player.");
		setTranslationKey("rearview");
		setDefaultPosition(350, 300);
	}

	@Override
	protected void draw()
	{
		drawBackground(WIDTH, HEIGHT);
		if(MC.player == null || MC.level == null)
		{
			setSize(Math.round(WIDTH), Math.round(HEIGHT));
			return;
		}

		Vec3 look = MC.player.getLookAngle();
		Vec3 right = look.cross(new Vec3(0, 1, 0));
		if(right.lengthSqr() < 1.0E-4)
			right = new Vec3(1, 0, 0);
		else
			right = right.normalize();

		float centerX = WIDTH / 2;
		float bottom = HEIGHT - 12;
		for(Entity entity : MC.level.entitiesForRendering())
		{
			if(entity == MC.player || entity.isRemoved()
				|| !entity.isAlive())
				continue;

			Vec3 relative = entity.position().subtract(MC.player.position());
			double forward = look.dot(relative);
			if(forward >= 0 || relative.lengthSqr() > RANGE * RANGE)
				continue;

			double lateral = right.dot(relative);
			double depth = -forward;
			float x = (float)(centerX + lateral / RANGE * (WIDTH / 2 - 6));
			float y = (float)(bottom - depth / RANGE * (HEIGHT - 22));
			Color color = entity instanceof Player
				? new Color(255, 85, 85)
				: entity instanceof LivingEntity ? new Color(255, 170, 0)
					: new Color(170, 170, 170);
			drawCircle(x, y, 2, color);
		}

		drawRect(centerX, bottom + 2, 1, 6,
			new Color(accentColor(255), true));
		setSize(Math.round(WIDTH), Math.round(HEIGHT));
	}
}
