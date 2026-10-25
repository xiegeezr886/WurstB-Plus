/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
package net.wurstclient.hud2.mods;

import java.util.List;

import net.wurstclient.hud2.HUDMod;
import net.wurstclient.hud2.nanovg.HudFonts;
import net.wurstclient.waypoints.Waypoint;

public final class WaypointMod extends HUDMod
{
	public WaypointMod()
	{
		super("Waypoint", "Waypoint", "Lists the saved waypoints.");
		setTranslationKey("waypoint");
		setDefaultPosition(350, 850);
	}

	@Override
	protected void draw()
	{
		if(WURST.getWaypointsManager() == null || MC.player == null)
			return;

		List<Waypoint> waypoints = WURST.getWaypointsManager()
			.getAllWaypoints();
		if(waypoints.isEmpty())
		{
			drawBackground(140, 20);
			drawText("No waypoints", 6, 5, 9, HudFonts.REGULAR,
				getFontColor(200));
			setSize(140, 20);
			return;
		}

		float width = 140;
		int rows = Math.min(waypoints.size(), 10);
		for(int i = 0; i < rows; i++)
			width = Math.max(width,
				getTextWidth(line(waypoints.get(i)), 9,
					HudFonts.REGULAR) + 12);
		float height = 6 + rows * 13;
		drawBackground(width, height);
		for(int i = 0; i < rows; i++)
			drawText(line(waypoints.get(i)), 6, 4 + i * 13, 9,
				HudFonts.REGULAR);
		setSize(Math.round(width), Math.round(height));
	}

	private String line(Waypoint waypoint)
	{
		double distance = Math.sqrt(MC.player.distanceToSqr(
			waypoint.getPos().getX(), waypoint.getPos().getY(),
			waypoint.getPos().getZ()));
		return waypoint.getName() + "  " + Math.round(distance) + "m";
	}
}
