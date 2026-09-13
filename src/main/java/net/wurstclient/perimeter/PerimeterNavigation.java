/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.perimeter;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.Goal;
import baritone.api.process.ICustomGoalProcess;
import baritone.api.process.IElytraProcess;
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
	private IElytraProcess flight;
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
			flight = baritone.getElytraProcess();
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
		flight.pathTo(target);
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
		return flight != null && flight.isLoaded();
	}
	
	public boolean isFlightSafeToCancel()
	{
		return flight != null && flight.isSafeToCancel();
	}
	
	public BlockPos flightDestination()
	{
		return flight == null ? null : flight.currentDestination();
	}
	
	public void resetFlight()
	{
		if(flight != null)
			try
			{
				flight.resetState();
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
		BaritoneAPI.getSettings().allowPlaceInFluidsSource.value = true;
		BaritoneAPI.getSettings().allowPlaceInFluidsFlow.value = true;
	}
	
	private void saveSettings()
	{
		if(savedAllowBreak == null)
			savedAllowBreak = BaritoneAPI.getSettings().allowBreak.value;
		
		if(savedAllowPlace == null)
			savedAllowPlace = BaritoneAPI.getSettings().allowPlace.value;
		
		if(savedPlaceInFluidSource == null)
			savedPlaceInFluidSource =
				BaritoneAPI.getSettings().allowPlaceInFluidsSource.value;
		
		if(savedPlaceInFluidFlow == null)
			savedPlaceInFluidFlow =
				BaritoneAPI.getSettings().allowPlaceInFluidsFlow.value;
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
				BaritoneAPI.getSettings().allowPlaceInFluidsSource.value =
					savedPlaceInFluidSource;
			
			if(savedPlaceInFluidFlow != null)
				BaritoneAPI.getSettings().allowPlaceInFluidsFlow.value =
					savedPlaceInFluidFlow;
			
			if(savedElytraTermsAccepted != null)
				BaritoneAPI.getSettings().elytraTermsAccepted.value =
					savedElytraTermsAccepted;
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
				BaritoneAPI.getSettings().elytraTermsAccepted.value;
		
		BaritoneAPI.getSettings().elytraTermsAccepted.value = true;
	}
}
