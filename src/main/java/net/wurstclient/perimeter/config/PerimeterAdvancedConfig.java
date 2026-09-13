/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.perimeter.config;

/**
 * Tuning values. Field names and defaults match the reference mod, including
 * the mining-stall fields that are not exposed through the advanced config
 * command.
 */
public final class PerimeterAdvancedConfig
{
	public int toolDurabilityThreshold = 32;
	public int elytraDurabilityThreshold = 32;
	public int emergencyFlightDurabilityThreshold = 5;
	public int foodLevelThreshold = 14;
	public double healthEatingThreshold = 16.0;
	public int foodResupplyTrigger = 1;
	public int foodResupplyTarget = 64;
	public int fireworkResupplyTrigger = 10;
	public int fireworkResupplyTarget = 128;
	public int dropCollectionRadius = 8;
	public double dropCollectionStableSeconds = 1.5;
	public int inventoryReservedSlots = 1;
	public int miningBlocksPerEmptySlot = 64;
	public int elytraNavigationMinDistance = 32;
	public int unloadLandingSearchRadius = 16;
	public double unloadEdgeInset = 0.2;
	public double navigationStallTimeoutSeconds = 10.0;
	public int navigationRetryCount = 2;
	public double portalTransitionCost = 16.0;
	public double portalTransitionTimeoutSeconds = 20.0;
	public double portalExitTimeoutSeconds = 20.0;
	public int portalExitMinRadius = 3;
	public int portalExitMaxRadius = 8;
	public int portalExitVerticalRadius = 4;
	public double repairExperienceStableSeconds = 1.5;
	public double supplyInteractionTimeoutSeconds = 2.0;
	public double furnaceInteractionTimeoutSeconds = 2.0;
	public int flightRetryCount = 2;
	public boolean miningStallDetectionEnabled = true;
	public double miningStallTimeoutSeconds = 8.0;
	public int miningStallMovementDistance = 3;
	public double miningStallCooldownSeconds = 15.0;
}
