package net.wurstclient.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.wurstclient.util.AirJumpPolicy.State;

final class AirJumpPolicyTest
{
	@Test
	void freeModeRequiresAJumpPressEdge()
	{
		assertTrue(AirJumpPolicy.canJump(state(true, false, false, false)));
		assertFalse(AirJumpPolicy.canJump(state(false, false, false, false)));
	}

	@Test
	void doubleModeConsumesOnlyTheAirJump()
	{
		assertTrue(AirJumpPolicy.canJump(state(true, true, true, false)));
		assertTrue(AirJumpPolicy.canJump(state(true, false, true, true)));
		assertFalse(AirJumpPolicy.canJump(state(true, false, true, false)));
	}

	@Test
	void rejectsIncompatibleMovementStates()
	{
		assertFalse(AirJumpPolicy.canJump(new State(true, false, false, true,
			true, false, false, false, false, false)));
		assertFalse(AirJumpPolicy.canJump(new State(true, false, false, true,
			false, true, false, false, false, false)));
		assertFalse(AirJumpPolicy.canJump(new State(true, false, false, true,
			false, false, true, false, false, false)));
		assertFalse(AirJumpPolicy.canJump(new State(true, false, false, true,
			false, false, false, true, false, false)));
		assertFalse(AirJumpPolicy.canJump(new State(true, false, false, true,
			false, false, false, false, true, false)));
		assertFalse(AirJumpPolicy.canJump(new State(true, false, false, true,
			false, false, false, false, false, true)));
	}

	private static State state(boolean pressed, boolean onGround,
		boolean doubleMode, boolean available)
	{
		return new State(pressed, onGround, doubleMode, available, false, false,
			false, false, false, false);
	}
}
