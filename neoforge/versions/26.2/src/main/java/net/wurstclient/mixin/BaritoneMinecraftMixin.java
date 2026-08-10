/*
 * Copyright (c) 2026 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixin;

import java.util.function.BiFunction;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.event.events.PlayerUpdateEvent;
import baritone.api.event.events.TickEvent;
import baritone.api.event.events.WorldEvent;
import baritone.api.event.events.type.EventState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;

@Mixin(Minecraft.class)
public abstract class BaritoneMinecraftMixin
{
	@Shadow
	public LocalPlayer player;

	@Shadow
	public ClientLevel level;

	@Unique
	private BiFunction<EventState, TickEvent.Type, TickEvent> wurst$tickProvider;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void wurst$initializeBaritone(CallbackInfo ci)
	{
		BaritoneAPI.getProvider().getPrimaryBaritone();
	}

	@Inject(method = "tick", at = @At("HEAD"))
	private void wurst$preBaritoneTick(CallbackInfo ci)
	{
		wurst$tickProvider = TickEvent.createNextProvider();
		for(IBaritone baritone : BaritoneAPI.getProvider().getAllBaritones())
		{
			TickEvent.Type type = baritone.getPlayerContext().player() != null
				&& baritone.getPlayerContext().world() != null ? TickEvent.Type.IN
					: TickEvent.Type.OUT;
			baritone.getGameEventHandler().onTick(
				wurst$tickProvider.apply(EventState.PRE, type));
		}
	}

	@Inject(method = "tick", at = @At("RETURN"))
	private void wurst$postBaritoneTick(CallbackInfo ci)
	{
		if(wurst$tickProvider == null)
			return;

		for(IBaritone baritone : BaritoneAPI.getProvider().getAllBaritones())
		{
			TickEvent.Type type = baritone.getPlayerContext().player() != null
				&& baritone.getPlayerContext().world() != null ? TickEvent.Type.IN
					: TickEvent.Type.OUT;
			baritone.getGameEventHandler().onPostTick(
				wurst$tickProvider.apply(EventState.POST, type));
		}
		wurst$tickProvider = null;
	}

	@Inject(method = "tick",
		at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/multiplayer/ClientLevel;tickEntities()V",
			shift = At.Shift.AFTER))
	private void wurst$postBaritoneEntityTick(CallbackInfo ci)
	{
		if(player == null)
			return;

		IBaritone baritone =
			BaritoneAPI.getProvider().getBaritoneForPlayer(player);
		if(baritone != null)
			baritone.getGameEventHandler()
				.onPlayerUpdate(new PlayerUpdateEvent(EventState.POST));
	}

	@Inject(method = "setLevel", at = @At("HEAD"))
	private void wurst$preBaritoneWorldChange(ClientLevel world,
		CallbackInfo ci)
	{
		if(level == null && world == null)
			return;

		BaritoneAPI.getProvider().getPrimaryBaritone().getGameEventHandler()
			.onWorldEvent(new WorldEvent(world, EventState.PRE));
	}

	@Inject(method = "setLevel", at = @At("RETURN"))
	private void wurst$postBaritoneWorldChange(ClientLevel world,
		CallbackInfo ci)
	{
		BaritoneAPI.getProvider().getPrimaryBaritone().getGameEventHandler()
			.onWorldEvent(new WorldEvent(world, EventState.POST));
	}
}
