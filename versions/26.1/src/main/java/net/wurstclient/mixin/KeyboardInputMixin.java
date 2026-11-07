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
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
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
	@Inject(at = @At("TAIL"), method = "tick()V")
	private void onTick(CallbackInfo ci)
	{
		KeyboardInput self = (KeyboardInput)(Object)this;
		Input keys = self.keyPresses;
		Vec2 move = self.getMoveVector();
		MoveInputEvent event = new MoveInputEvent(move.y, move.x, keys.jump(),
			keys.shift());
		EventManager.fire(event);

		if(keys.jump() == event.isJump() && keys.shift() == event.isSneak())
			return;
		self.keyPresses = new Input(keys.forward(), keys.backward(),
			keys.left(), keys.right(), event.isJump(), event.isSneak(),
			keys.sprint());
	}
}
