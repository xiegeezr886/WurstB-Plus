/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.MouseHandler;
import net.wurstclient.event.EventManager;
import net.wurstclient.events.MouseButtonListener.MouseButtonEvent;
import net.wurstclient.events.MouseScrollListener.MouseScrollEvent;
import net.wurstclient.events.MouseUpdateListener.MouseUpdateEvent;

@Mixin(MouseHandler.class)
public class MouseMixin
{
	@Shadow
	private double accumulatedDX;
	@Shadow
	private double accumulatedDY;

	@Inject(at = @At("HEAD"), method = "onPress(JIII)V")
	private void onMouseButton(long window, int button, int action,
		int modifiers, CallbackInfo ci)
	{
		EventManager.fire(new MouseButtonEvent(button, action));
	}
	
	@Inject(at = @At("RETURN"), method = "onScroll(JDD)V")
	private void onOnMouseScroll(long window, double horizontal,
		double vertical, CallbackInfo ci)
	{
		EventManager.fire(new MouseScrollEvent(vertical));
	}
	
	@Inject(at = @At("HEAD"), method = "turnPlayer()V")
	private void onUpdateMouse(CallbackInfo ci)
	{
		MouseUpdateEvent event =
			new MouseUpdateEvent(accumulatedDX, accumulatedDY);
		EventManager.fire(event);
		accumulatedDX = event.getDeltaX();
		accumulatedDY = event.getDeltaY();
	}
}
