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

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;

import net.minecraft.client.MouseHandler;
import net.minecraft.world.entity.player.Inventory;
import net.wurstclient.WurstClient;
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
	
	@Inject(at = @At("HEAD"), method = "turnPlayer(D)V")
	private void onUpdateMouse(double timeDelta, CallbackInfo ci)
	{
		MouseUpdateEvent event =
			new MouseUpdateEvent(accumulatedDX, accumulatedDY);
		EventManager.fire(event);
		accumulatedDX = event.getDeltaX();
		accumulatedDY = event.getDeltaY();
	}

	/**
	 * MC 1.21.2 renamed {@code Inventory.swapPaint(D)V} to
	 * {@code setSelectedHotbarSlot(I)V}, so the old
	 * {@code PlayerInventoryMixin} no longer had a valid target. Guard the call
	 * site here instead, which keeps the zoom feature from scrolling the
	 * hotbar.
	 */
	@WrapWithCondition(method = "onScroll(JDD)V",
		at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/player/Inventory;setSelectedHotbarSlot(I)V"))
	private boolean wrapOnMouseScroll(Inventory inventory, int slot)
	{
		return !WurstClient.INSTANCE.getOtfs().zoomOtf
			.shouldPreventHotbarScrolling();
	}
}
