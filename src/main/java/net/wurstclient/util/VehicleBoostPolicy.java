/*
 * Copyright (c) 2015-2026 CCBlueX
 * Copyright (c) 2025-2026 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

import net.minecraft.world.phys.Vec3;

public enum VehicleBoostPolicy
{
	;

	public static Vec3 velocity(float yaw, double horizontalSpeed,
		double verticalSpeed)
	{
		double safeYaw = Float.isFinite(yaw) ? yaw : 0;
		double safeHorizontal = Double.isFinite(horizontalSpeed)
			? Math.max(0, horizontalSpeed) : 0;
		double safeVertical = Double.isFinite(verticalSpeed)
			? Math.max(0, verticalSpeed) : 0;
		double angle = Math.toRadians(safeYaw);
		return new Vec3(-Math.sin(angle) * safeHorizontal, safeVertical,
			Math.cos(angle) * safeHorizontal);
	}
}
