/*
 * This file contains a Forge/Mojmap adaptation of LiquidBounce's point
 * tracker and hit-box raytrace selection.
 *
 * Copyright (c) 2015-2026 CCBlueX
 * Copyright (c) 2025-2026 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public enum CombatAimPointPlanner
{
	;

	public static AimPoint find(AABB box, Vec3 eyes, Vec3 preferred,
		double visibleRange, double throughWallsRange,
		Predicate<Vec3> lineOfSight, ToDoubleFunction<Vec3> rotationScore)
	{
		if(box == null || eyes == null || preferred == null
			|| visibleRange < 0 || throughWallsRange < 0)
			return null;

		double visibleRangeSq = visibleRange * visibleRange;
		double wallsRangeSq = throughWallsRange * throughWallsRange;
		List<Vec3> points = sample(box, eyes, preferred);
		return points.stream().map(point -> {
			double distanceSq = eyes.distanceToSqr(point);
			boolean visible = lineOfSight.test(point);
			if(visible && distanceSq <= visibleRangeSq)
				return new AimPoint(point, false, distanceSq,
					rotationScore.applyAsDouble(point));
			if(!visible && distanceSq <= wallsRangeSq)
				return new AimPoint(point, true, distanceSq,
					rotationScore.applyAsDouble(point));
			return null;
		}).filter(point -> point != null)
			.min(Comparator.comparing(AimPoint::throughWalls)
				.thenComparingDouble(AimPoint::rotationScore)
				.thenComparingDouble(AimPoint::distanceSq))
			.orElse(null);
	}

	private static List<Vec3> sample(AABB box, Vec3 eyes, Vec3 preferred)
	{
		List<Vec3> points = new ArrayList<>(29);
		points.add(clamp(box, preferred));
		points.add(clamp(box, eyes));
		double[] factors = {0.15, 0.5, 0.85};
		for(double xFactor : factors)
			for(double yFactor : factors)
				for(double zFactor : factors)
					points.add(new Vec3(Mth.lerp(xFactor, box.minX, box.maxX),
						Mth.lerp(yFactor, box.minY, box.maxY),
						Mth.lerp(zFactor, box.minZ, box.maxZ)));
		return points;
	}

	private static Vec3 clamp(AABB box, Vec3 point)
	{
		return new Vec3(Mth.clamp(point.x, box.minX, box.maxX),
			Mth.clamp(point.y, box.minY, box.maxY),
			Mth.clamp(point.z, box.minZ, box.maxZ));
	}

	public record AimPoint(Vec3 point, boolean throughWalls, double distanceSq,
		double rotationScore)
	{
	}
}
