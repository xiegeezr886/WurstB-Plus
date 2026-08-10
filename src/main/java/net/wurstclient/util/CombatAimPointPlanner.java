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
import java.util.Objects;
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
			|| !isFinite(box) || !isFinite(eyes) || !isFinite(preferred)
			|| !Double.isFinite(visibleRange) || visibleRange < 0
			|| !Double.isFinite(throughWallsRange) || throughWallsRange < 0)
			return null;
		Objects.requireNonNull(lineOfSight, "lineOfSight");
		Objects.requireNonNull(rotationScore, "rotationScore");

		double visibleRangeSq = visibleRange * visibleRange;
		double wallsRangeSq = throughWallsRange * throughWallsRange;
		double maximumRangeSq = Math.max(visibleRangeSq, wallsRangeSq);
		List<Vec3> points = sample(box, eyes, preferred);
		return points.stream().distinct().map(point -> {
			double distanceSq = eyes.distanceToSqr(point);
			if(!Double.isFinite(distanceSq) || distanceSq > maximumRangeSq)
				return null;
			boolean visible = lineOfSight.test(point);
			boolean throughWalls = !visible;
			if(visible ? distanceSq > visibleRangeSq
				: distanceSq > wallsRangeSq)
				return null;
			double score = rotationScore.applyAsDouble(point);
			return Double.isFinite(score)
				? new AimPoint(point, throughWalls, distanceSq, score) : null;
		}).filter(point -> point != null)
			.min(Comparator.comparing(AimPoint::throughWalls)
				.thenComparingDouble(AimPoint::rotationScore)
				.thenComparingDouble(AimPoint::distanceSq))
			.orElse(null);
	}

	private static boolean isFinite(AABB box)
	{
		return Double.isFinite(box.minX) && Double.isFinite(box.minY)
			&& Double.isFinite(box.minZ) && Double.isFinite(box.maxX)
			&& Double.isFinite(box.maxY) && Double.isFinite(box.maxZ);
	}

	private static boolean isFinite(Vec3 point)
	{
		return Double.isFinite(point.x) && Double.isFinite(point.y)
			&& Double.isFinite(point.z);
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
