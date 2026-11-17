/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
package net.wurstclient.events;

import java.util.ArrayList;

import net.wurstclient.event.Event;
import net.wurstclient.event.Listener;

/**
 * Fired after {@code KeyboardInput.tick()} has computed this tick's movement
 * input, before {@code LocalPlayer.aiStep()} consumes it. Listeners may change
 * the forward/sideways impulses, the jump flag or the sneak flag.
 *
 * <p>Mirrors OpenOpal's {@code MoveInputEvent}.
 */
public interface MoveInputListener extends Listener
{
	public void onMoveInput(MoveInputEvent event);

	public static class MoveInputEvent extends Event<MoveInputListener>
	{
		private float forward;
		private float sideways;
		private boolean jump;
		private boolean sneak;

		public MoveInputEvent(float forward, float sideways, boolean jump,
			boolean sneak)
		{
			this.forward = forward;
			this.sideways = sideways;
			this.jump = jump;
			this.sneak = sneak;
		}

		public float getForward()
		{
			return forward;
		}

		public void setForward(float forward)
		{
			this.forward = forward;
		}

		public float getSideways()
		{
			return sideways;
		}

		public void setSideways(float sideways)
		{
			this.sideways = sideways;
		}

		public boolean isJump()
		{
			return jump;
		}

		public void setJump(boolean jump)
		{
			this.jump = jump;
		}

		public boolean isSneak()
		{
			return sneak;
		}

		public void setSneak(boolean sneak)
		{
			this.sneak = sneak;
		}

		@Override
		public void fire(ArrayList<MoveInputListener> listeners)
		{
			for(MoveInputListener listener : listeners)
				listener.onMoveInput(this);
		}

		@Override
		public Class<MoveInputListener> getListenerType()
		{
			return MoveInputListener.class;
		}
	}
}
