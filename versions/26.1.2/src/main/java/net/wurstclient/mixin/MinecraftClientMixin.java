/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixin;

import java.io.File;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.blaze3d.platform.WindowEventHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.thread.ReentrantBlockableEventLoop;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.wurstclient.WurstClient;
import net.wurstclient.event.EventManager;
import net.wurstclient.events.HandleBlockBreakingListener.HandleBlockBreakingEvent;
import net.wurstclient.events.HandleInputListener.HandleInputEvent;
import net.wurstclient.events.LeftClickListener.LeftClickEvent;
import net.wurstclient.events.RightClickListener.RightClickEvent;
import net.wurstclient.events.WorldChangeListener.WorldChangeEvent;
import net.wurstclient.mixinterface.ILocalPlayer;
import net.wurstclient.mixinterface.IMinecraftClient;
import net.wurstclient.mixinterface.IMultiPlayerGameMode;

@Mixin(Minecraft.class)
public abstract class MinecraftClientMixin
	extends ReentrantBlockableEventLoop<Runnable>
	implements WindowEventHandler, IMinecraftClient
{
	@Shadow
	@Final
	public File gameDirectory;
	@Shadow
	public MultiPlayerGameMode gameMode;
	@Shadow
	public LocalPlayer player;
	@Shadow
	@Final
	private User user;
	@Shadow
	@Final
	private UserApiService userApiService;
	@Shadow
	private int missTime;
	@Shadow
	private int rightClickDelay;
	
	private User wurstSession;
	private ProfileKeyPairManager wurstProfileKeys;
	
	private MinecraftClientMixin(WurstClient wurst, String name)
	{
		super(name, false);
	}
	
	/**
	 * Runs just before {@link Minecraft#handleKeybinds()}, bypassing
	 * the <code>overlay == null && currentScreen == null</code> check in
	 * {@link Minecraft#tick()}.
	 */
	@Inject(at = @At(value = "FIELD",
		target = "Lnet/minecraft/client/Minecraft;overlay:Lnet/minecraft/client/gui/screens/Overlay;",
		ordinal = 0), method = "tick()V")
	private void onHandleInputEvents(CallbackInfo ci)
	{
		// Make sure this event is not fired outside of gameplay
		if(player == null)
			return;

		EventManager.fire(HandleInputEvent.INSTANCE);
	}
	
	@Inject(at = @At(value = "FIELD",
		target = "Lnet/minecraft/client/Minecraft;hitResult:Lnet/minecraft/world/phys/HitResult;",
		ordinal = 0), method = "startAttack()Z", cancellable = true)
	private void onDoAttack(CallbackInfoReturnable<Boolean> cir)
	{
		LeftClickEvent event = new LeftClickEvent();
		EventManager.fire(event);
		
		if(event.isCancelled())
			cir.setReturnValue(false);
	}
	
	@Inject(
		at = @At(value = "FIELD",
			target = "Lnet/minecraft/client/Minecraft;rightClickDelay:I",
			ordinal = 0),
		method = "startUseItem()V",
		cancellable = true)
	private void onDoItemUse(CallbackInfo ci)
	{
		RightClickEvent event = new RightClickEvent();
		EventManager.fire(event);
		
		if(event.isCancelled())
			ci.cancel();
	}
	
	@Inject(at = @At("HEAD"), method = "pickBlockOrEntity()V")
	private void onDoItemPick(CallbackInfo ci)
	{
		if(!WurstClient.INSTANCE.isEnabled())
			return;
		
		HitResult hitResult = WurstClient.MC.hitResult;
		if(!(hitResult instanceof EntityHitResult eHitResult))
			return;
		
		WurstClient.INSTANCE.getFriends().middleClick(eHitResult.getEntity());
	}
	
	/**
	 * Allows hacks to cancel vanilla block breaking and replace it with their
	 * own. Useful for Nuker-like hacks.
	 */
	@Inject(at = @At("HEAD"),
		method = "continueAttack(Z)V",
		cancellable = true)
	private void onHandleBlockBreaking(boolean breaking, CallbackInfo ci)
	{
		HandleBlockBreakingEvent event = new HandleBlockBreakingEvent();
		EventManager.fire(event);
		
		if(event.isCancelled())
			ci.cancel();
	}
	
	@Inject(at = @At("HEAD"),
		method = "getUser()Lnet/minecraft/client/User;",
		cancellable = true)
	private void onGetSession(CallbackInfoReturnable<User> cir)
	{
		if(wurstSession != null)
			cir.setReturnValue(wurstSession);
	}
	
	@Inject(at = @At("HEAD"),
		method = "getProfileKeyPairManager()Lnet/minecraft/client/multiplayer/ProfileKeyPairManager;",
		cancellable = true)
	private void onGetProfileKeys(CallbackInfoReturnable<ProfileKeyPairManager> cir)
	{
		if(WurstClient.INSTANCE.getOtfs().noChatReportsOtf.isActive())
			cir.setReturnValue(ProfileKeyPairManager.EMPTY_KEY_MANAGER);
		
		if(wurstProfileKeys == null)
			return;
		
		cir.setReturnValue(wurstProfileKeys);
	}
	
	@Inject(at = @At("HEAD"),
		method = "allowsTelemetry()Z",
		cancellable = true)
	private void onIsTelemetryEnabledByApi(CallbackInfoReturnable<Boolean> cir)
	{
		cir.setReturnValue(
			!WurstClient.INSTANCE.getOtfs().noTelemetryOtf.isEnabled());
	}
	
	@Inject(at = @At("HEAD"),
		method = "extraTelemetryAvailable()Z",
		cancellable = true)
	private void onIsOptionalTelemetryEnabledByApi(
		CallbackInfoReturnable<Boolean> cir)
	{
		cir.setReturnValue(
			!WurstClient.INSTANCE.getOtfs().noTelemetryOtf.isEnabled());
	}
	
	@Override
	public ILocalPlayer getPlayer()
	{
		return (ILocalPlayer)player;
	}
	
	@Override
	public IMultiPlayerGameMode getInteractionManager()
	{
		return (IMultiPlayerGameMode)gameMode;
	}

	@Override
	public int getRightClickDelay()
	{
		return rightClickDelay;
	}

	@Override
	public void setRightClickDelay(int delay)
	{
		rightClickDelay = delay;
	}
	
	@Override
	public User getWurstSession()
	{
		return wurstSession;
	}
	
	@Override
	public void setWurstSession(User session)
	{
		wurstSession = session;
		if(session == null)
		{
			wurstProfileKeys = null;
			return;
		}
		
		String accessToken = session.getAccessToken();
		boolean isOffline = accessToken == null || accessToken.isBlank()
			|| accessToken.equals("0") || accessToken.equals("null");
		UserApiService userApiService = isOffline ? UserApiService.OFFLINE
			: wurst_createUserApiService(accessToken);
		wurstProfileKeys =
			ProfileKeyPairManager.create(userApiService, session,
				gameDirectory.toPath());
	}
	
	/** Bridges world enter and leave events into Wurst's event system. */
	@Inject(at = @At("HEAD"),
		method = "updateLevelInEngines(Lnet/minecraft/client/multiplayer/ClientLevel;)V")
	private void beforeSetWorld(ClientLevel world, CallbackInfo ci)
	{
		if(world == null)
			EventManager.fire(new WorldChangeEvent(null));
	}

	public int getMissTime()
	{
		return missTime;
	}

	public void setMissTime(int missTime)
	{
		this.missTime = missTime;
	}
	
	@Inject(at = @At("TAIL"),
		method = "updateLevelInEngines(Lnet/minecraft/client/multiplayer/ClientLevel;)V")
	private void onSetWorld(ClientLevel world, CallbackInfo ci)
	{
		if(world == null)
			return;
		
		EventManager.fire(new WorldChangeEvent(world));
	}
	
	private UserApiService wurst_createUserApiService(String accessToken)
	{
		return userApiService;
	}
}
