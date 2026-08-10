/*
 * Copyright (c) 2015-2026 CCBlueX
 * Copyright (c) 2025-2026 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

public enum AirJumpPolicy
{
	;

	public static boolean canJump(State state)
	{
		return state != null && state.pressedEdge() && !state.spectator()
			&& !state.passenger() && !state.flying() && !state.fallFlying()
			&& !state.inFluid() && !state.climbable()
			&& (!state.doubleJumpMode() || state.onGround()
				|| state.doubleJumpAvailable());
	}

	public record State(boolean pressedEdge, boolean onGround,
		boolean doubleJumpMode, boolean doubleJumpAvailable, boolean spectator,
		boolean passenger, boolean flying, boolean fallFlying, boolean inFluid,
		boolean climbable)
	{
	}
}
