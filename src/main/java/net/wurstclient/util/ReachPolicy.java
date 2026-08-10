/*
 * Copyright (c) 2025-2026 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

public enum ReachPolicy
{
	;

	public static float resolveEntityRange(float configuredRange,
		float fallbackRange, boolean playerAvailable, boolean sprinting,
		boolean onlyWhileSprinting, boolean inFluid, boolean disableInFluid)
	{
		float fallback = sanitize(fallbackRange, 3);
		float configured = sanitize(configuredRange, fallback);
		if(!playerAvailable || onlyWhileSprinting && !sprinting
			|| disableInFluid && inFluid)
			return fallback;
		return configured;
	}

	private static float sanitize(float value, float fallback)
	{
		return Float.isFinite(value) && value >= 0 ? value : fallback;
	}
}
