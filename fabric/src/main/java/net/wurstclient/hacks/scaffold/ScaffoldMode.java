/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
package net.wurstclient.hacks.scaffold;

import net.minecraft.world.phys.BlockHitResult;
import net.wurstclient.events.MoveInputListener;
import net.wurstclient.hacks.ScaffoldWalkHack;

/**
 * One OpenOpal Scaffold mode. The mode object owns all per-mode state and
 * receives the same lifecycle callbacks the reference {@code ModuleMode}
 * receives, while {@code ScaffoldWalkHack} provides the shared search,
 * rotation and placement machinery.
 */
public interface ScaffoldMode
{
	/**
	 * @return true if this mode drives the whole tick itself (Heypixel,
	 *         Hypixel, SilenceTelly, Telly) instead of using the shared
	 *         same-Y + placement flow.
	 */
	default boolean handlesTick()
	{
		return false;
	}

	default void onEnable()
	{}

	default void onDisable()
	{}

	/**
	 * Called at the start of the player tick when {@link #handlesTick()} is
	 * true, or after the shared flow has been prepared when it is false.
	 */
	default void onTick()
	{}

	/**
	 * Called from the movement-input hook before {@code LocalPlayer.aiStep()}
	 * consumes the input. Modes that auto-jump implement this, mirroring
	 * OpenOpal's {@code MoveInputEvent} subscriptions.
	 */
	default void onMoveInput(MoveInputListener.MoveInputEvent event)
	{}

	/**
	 * Called after the outgoing movement packet has been sent. This is the
	 * Wurst equivalent of OpenOpal's {@code PostMovementPacketEvent}.
	 */
	default void onPostMotion()
	{}

	/**
	 * Called after a block has been successfully placed by the shared
	 * placement code.
	 */
	default void onBlockPlaced(BlockHitResult hitResult)
	{}

	/**
	 * @return a rotation that must be written into the outgoing movement
	 *         packet for this tick, or null to use the shared rotation.
	 */
	default ScaffoldWalkHack.RotationRequest getPacketRotation()
	{
		return null;
	}
}
