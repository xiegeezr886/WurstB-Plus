/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
package net.wurstclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.player.KeyboardInput;
import net.wurstclient.event.EventManager;
import net.wurstclient.events.MoveInputListener.MoveInputEvent;

/**
 * Fires {@link MoveInputEvent} after the vanilla movement impulses have been
 * computed, so hacks (Scaffold, Telly, SilenceTelly) can add a jump or cancel
 * movement exactly like OpenOpal's {@code KeyboardInputMixin}.
 */
@Mixin(KeyboardInput.class)
public class KeyboardInputMixin
{
	@Inject(at = @At("TAIL"), method = "tick(ZF)V")
	private void onTick(boolean slowDown, float slowDownFactor,
		CallbackInfo ci)
	{
		KeyboardInput self = (KeyboardInput)(Object)this;
		MoveInputEvent event = new MoveInputEvent(self.forwardImpulse,
			self.leftImpulse, self.jumping, self.shiftKeyDown);
		EventManager.fire(event);

		self.forwardImpulse = event.getForward();
		self.leftImpulse = event.getSideways();
		self.jumping = event.isJump();
		self.shiftKeyDown = event.isSneak();
	}
}
