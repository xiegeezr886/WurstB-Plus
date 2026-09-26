/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.perimeter;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.Goal;
import baritone.api.process.IBaritoneProcess;
import baritone.api.process.ICustomGoalProcess;
import net.minecraft.core.BlockPos;

/**
 * Ported from the reference mod: walking and Elytra travel through Baritone,
 * including the destination settings discipline that stops Baritone from
 * breaking or placing blocks while the automation travels through terrain it
 * does not own.
 */
public final class PerimeterNavigation
{
	private ICustomGoalProcess walking;
	private IBaritoneProcess flight;
	private Boolean savedAllowBreak;
	private Boolean savedAllowPlace;
	private Boolean savedPlaceInFluidSource;
	private Boolean savedPlaceInFluidFlow;
	private Boolean savedElytraTermsAccepted;
	private boolean available;
	
	/**
	 * @return whether Baritone could be reached.
	 */
	public boolean bind()
	{
		unbind();
		
		try
		{
			IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
			walking = baritone.getCustomGoalProcess();
			Object candidate = baritone.getClass().getMethod("getElytraProcess")
				.invoke(baritone);
			flight = candidate instanceof IBaritoneProcess process ? process : null;
			available = walking != null && flight != null;
		}catch(Exception | LinkageError e)
		{
			walking = null;
			flight = null;
			available = false;
		}
		
		return available;
	}
	
	public void unbind()
	{
		stop();
		restoreDestinationSettings();
		walking = null;
		flight = null;
		available = false;
	}
	
	public boolean isAvailable()
	{
		return available;
	}
	
	public void walk(Goal goal)
	{
		if(walking == null)
			throw new IllegalStateException(
				"Navigation is not bound to Baritone.");
		
		walking.setGoalAndPath(goal);
	}
	
	public void fly(BlockPos target)
	{
		if(flight == null)
			throw new IllegalStateException(
				"Navigation is not bound to Baritone.");
		
		acceptElytraTerms();
		invokeFlight("pathTo", new Class<?>[]{BlockPos.class}, target);
	}
	
	public boolean isWalking()
	{
		return walking != null && walking.isActive();
	}
	
	public boolean isFlying()
	{
		return flight != null && flight.isActive();
	}
	
	public boolean isFlightLoaded()
	{
		return flight != null && Boolean.TRUE.equals(invokeFlight("isLoaded"));
	}
	
	public boolean isFlightSafeToCancel()
	{
		return flight != null && Boolean.TRUE.equals(invokeFlight("isSafeToCancel"));
	}
	
	public BlockPos flightDestination()
	{
		return flight == null ? null : (BlockPos)invokeFlight("currentDestination");
	}
	
	public void resetFlight()
	{
		if(flight != null)
			try
			{
				invokeFlight("resetState");
			}catch(Exception e)
			{
				e.printStackTrace();
			}
	}
	
	public void stopWalking()
	{
		if(walking != null)
			walking.onLostControl();
	}
	
	public void stopFlying()
	{
		if(flight != null && flight.isActive())
			flight.onLostControl();
	}
	
	public void stop()
	{
		stopWalking();
		stopFlying();
	}
	
	public void disablePlacement()
	{
		saveSettings();
		BaritoneAPI.getSettings().allowPlace.value = false;
	}
	
	public void disableBreakingAndPlacement()
	{
		saveSettings();
		BaritoneAPI.getSettings().allowBreak.value = false;
		BaritoneAPI.getSettings().allowPlace.value = false;
	}
	
	public void enableBreakingWithoutPlacement()
	{
		saveSettings();
		BaritoneAPI.getSettings().allowBreak.value = true;
		BaritoneAPI.getSettings().allowPlace.value = false;
	}
	
	/**
	 * Area mining needs to place sealing blocks into fluids, which Baritone
	 * refuses by default.
	 */
	public void enablePlacementInFluids()
	{
		saveSettings();
		if(savedPlaceInFluidSource == null || savedPlaceInFluidFlow == null)
			throw new IllegalStateException(
				"This Baritone version cannot place blocks in fluids.");
		setOptionalBooleanSetting("allowPlaceInFluidsSource", true);
		setOptionalBooleanSetting("allowPlaceInFluidsFlow", true);
	}
	
	private void saveSettings()
	{
		if(savedAllowBreak == null)
			savedAllowBreak = BaritoneAPI.getSettings().allowBreak.value;
		
		if(savedAllowPlace == null)
			savedAllowPlace = BaritoneAPI.getSettings().allowPlace.value;
		
		if(savedPlaceInFluidSource == null)
			savedPlaceInFluidSource =
				getOptionalBooleanSetting("allowPlaceInFluidsSource");
		
		if(savedPlaceInFluidFlow == null)
			savedPlaceInFluidFlow =
				getOptionalBooleanSetting("allowPlaceInFluidsFlow");
	}
	
	public void restoreDestinationSettings()
	{
		try
		{
			if(savedAllowBreak != null)
				BaritoneAPI.getSettings().allowBreak.value = savedAllowBreak;
			
			if(savedAllowPlace != null)
				BaritoneAPI.getSettings().allowPlace.value = savedAllowPlace;
			
			if(savedPlaceInFluidSource != null)
				setOptionalBooleanSetting("allowPlaceInFluidsSource",
					savedPlaceInFluidSource);
			
			if(savedPlaceInFluidFlow != null)
				setOptionalBooleanSetting("allowPlaceInFluidsFlow",
					savedPlaceInFluidFlow);
			
			if(savedElytraTermsAccepted != null)
				setOptionalBooleanSetting("elytraTermsAccepted",
					savedElytraTermsAccepted);
		}catch(Exception | LinkageError e)
		{
			// Baritone went away; nothing left to restore
		}
		
		savedAllowBreak = null;
		savedAllowPlace = null;
		savedPlaceInFluidSource = null;
		savedPlaceInFluidFlow = null;
		savedElytraTermsAccepted = null;
	}
	
	private void acceptElytraTerms()
	{
		if(savedElytraTermsAccepted == null)
			savedElytraTermsAccepted =
				getOptionalBooleanSetting("elytraTermsAccepted");
		
		setOptionalBooleanSetting("elytraTermsAccepted", true);
	}

	private Boolean getOptionalBooleanSetting(String name)
	{
		try
		{
			Object setting = BaritoneAPI.getSettings().getClass().getField(name)
				.get(BaritoneAPI.getSettings());
			return (Boolean)setting.getClass().getField("value").get(setting);
		}catch(NoSuchFieldException e)
		{
			return null;
		}catch(ReflectiveOperationException e)
		{
			throw new IllegalStateException("Cannot read Baritone setting: " + name,
				e);
		}
	}

	private void setOptionalBooleanSetting(String name, boolean value)
	{
		try
		{
			Field field = BaritoneAPI.getSettings().getClass().getField(name);
			Object setting = field.get(BaritoneAPI.getSettings());
			setting.getClass().getField("value").set(setting, value);
		}catch(ReflectiveOperationException e)
		{
			throw new IllegalStateException("Cannot set Baritone setting: " + name,
				e);
		}
	}

	private Object invokeFlight(String method)
	{
		return invokeFlight(method, new Class<?>[0]);
	}

	private Object invokeFlight(String method, Class<?>[] parameterTypes,
		Object... arguments)
	{
		try
		{
			Method target = flight.getClass().getMethod(method, parameterTypes);
			return target.invoke(flight, arguments);
		}catch(ReflectiveOperationException e)
		{
			Throwable cause = e instanceof InvocationTargetException
				? ((InvocationTargetException)e).getCause() : e;
			throw new IllegalStateException("Baritone Elytra process failed: " + method,
				cause);
		}
	}
}
