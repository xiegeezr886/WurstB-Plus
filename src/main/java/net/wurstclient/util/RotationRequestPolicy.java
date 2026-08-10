/*
 * Copyright (c) 2025-2026 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

public enum RotationRequestPolicy
{
	;

	public static boolean isFresh(long requestTick, long currentTick,
		int leaseTicks)
	{
		return requestTick >= 0 && currentTick >= requestTick
			&& currentTick - requestTick <= Math.max(0, leaseTicks);
	}
}
