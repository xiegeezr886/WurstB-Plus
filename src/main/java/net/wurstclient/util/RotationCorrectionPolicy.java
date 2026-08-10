/*
 * Copyright (c) 2015-2026 CCBlueX
 * Copyright (c) 2025-2026 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

public enum RotationCorrectionPolicy
{
	;

	public static float packetRotation(float currentRotation,
		boolean relativeRotation)
	{
		if(!Float.isFinite(currentRotation))
			return 0;
		return relativeRotation ? 0 : currentRotation;
	}
}
