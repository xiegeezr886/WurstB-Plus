/*
 * Copyright (c) 2015-2026 CCBlueX
 * Copyright (c) 2025-2026 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public enum ProjectileThreatPolicy
{
	;

	private static final double MIN_SPEED_SQUARED = 1.0E-8;
	private static final double PREDICTION_TICKS = 20;

	public static boolean isThreat(Vec3 position, Vec3 velocity,
		AABB projectileBox, AABB playerBox)
	{
		if(!isFinite(position) || !isFinite(velocity)
			|| !isFinite(projectileBox) || !isFinite(playerBox))
			return false;

		double speedSquared = velocity.lengthSqr();
		if(speedSquared <= MIN_SPEED_SQUARED)
			return false;

		Vec3 toPlayer = playerBox.getCenter().subtract(position);
		double movingTowardThreshold =
			0.5 * Math.sqrt(speedSquared * toPlayer.lengthSqr());
		boolean movingToward = velocity.dot(toPlayer) > movingTowardThreshold;

		double padding = Math.max(projectileBox.getXsize(),
			Math.max(projectileBox.getYsize(), projectileBox.getZsize())) / 2;
		AABB expandedPlayerBox = playerBox.inflate(padding);
		Vec3 predictionEnd = position.add(velocity.scale(PREDICTION_TICKS));
		boolean willHit = !expandedPlayerBox.contains(position)
			&& expandedPlayerBox.clip(position, predictionEnd).isPresent();

		return movingToward || willHit;
	}

	public static double distanceSquared(Vec3 point, AABB box)
	{
		if(!isFinite(point) || !isFinite(box))
			return Double.POSITIVE_INFINITY;

		double dx = axisDistance(point.x, box.minX, box.maxX);
		double dy = axisDistance(point.y, box.minY, box.maxY);
		double dz = axisDistance(point.z, box.minZ, box.maxZ);
		return dx * dx + dy * dy + dz * dz;
	}

	private static double axisDistance(double value, double min, double max)
	{
		return Math.max(Math.max(min - value, 0), value - max);
	}

	private static boolean isFinite(Vec3 vec)
	{
		return vec != null && Double.isFinite(vec.x)
			&& Double.isFinite(vec.y) && Double.isFinite(vec.z);
	}

	private static boolean isFinite(AABB box)
	{
		return box != null && Double.isFinite(box.minX)
			&& Double.isFinite(box.minY) && Double.isFinite(box.minZ)
			&& Double.isFinite(box.maxX) && Double.isFinite(box.maxY)
			&& Double.isFinite(box.maxZ);
	}
}
