/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.perimeter.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The advanced configuration table, ported one-to-one from the reference mod:
 * the same keys, groups, labels, ranges and cross-field rules.
 */
public final class PerimeterAdvancedOptions
{
	private static final List<PerimeterAdvancedOption> OPTIONS = List.of(
		option("tool_durability_threshold", "Durability", "tool threshold",
			c -> c.toolDurabilityThreshold,
			(c, k, v) -> c.toolDurabilityThreshold = nonNegativeInteger(k, v)),
		option("elytra_durability_threshold", "Durability", "elytra threshold",
			c -> c.elytraDurabilityThreshold,
			(c, k, v) -> c.elytraDurabilityThreshold =
				nonNegativeInteger(k, v)),
		option("emergency_flight_durability_threshold", "Durability",
			"emergency flight threshold",
			c -> c.emergencyFlightDurabilityThreshold,
			(c, k, v) -> c.emergencyFlightDurabilityThreshold =
				nonNegativeInteger(k, v)),
		option("food_level_threshold", "Consumables", "food level threshold",
			c -> c.foodLevelThreshold,
			(c, k, v) -> c.foodLevelThreshold = rangedInteger(k, v, 0, 20)),
		option("health_eating_threshold", "Consumables",
			"health eating threshold", c -> c.healthEatingThreshold,
			(c, k, v) -> c.healthEatingThreshold = ranged(k, v, 0.0, 20.0)),
		option("food_resupply_trigger", "Consumables", "food trigger",
			c -> c.foodResupplyTrigger, (c, k, v) -> {
				int result = nonNegativeInteger(k, v);
				if(result > c.foodResupplyTarget)
					throw new IllegalArgumentException(
						k + " cannot exceed food_resupply_target.");
				c.foodResupplyTrigger = result;
			}),
		option("food_resupply_target", "Consumables", "food target",
			c -> c.foodResupplyTarget, (c, k, v) -> {
				int result = nonNegativeInteger(k, v);
				if(result < c.foodResupplyTrigger)
					throw new IllegalArgumentException(k
						+ " cannot be lower than food_resupply_trigger.");
				c.foodResupplyTarget = result;
			}),
		option("firework_resupply_trigger", "Consumables", "firework trigger",
			c -> c.fireworkResupplyTrigger, (c, k, v) -> {
				int result = nonNegativeInteger(k, v);
				if(result > c.fireworkResupplyTarget)
					throw new IllegalArgumentException(
						k + " cannot exceed firework_resupply_target.");
				c.fireworkResupplyTrigger = result;
			}),
		option("firework_resupply_target", "Consumables", "firework target",
			c -> c.fireworkResupplyTarget, (c, k, v) -> {
				int result = nonNegativeInteger(k, v);
				if(result < c.fireworkResupplyTrigger)
					throw new IllegalArgumentException(k
						+ " cannot be lower than firework_resupply_trigger.");
				c.fireworkResupplyTarget = result;
			}),
		option("drop_collection_radius", "Mining and unloading", "drop radius",
			c -> c.dropCollectionRadius,
			(c, k, v) -> c.dropCollectionRadius = positiveInteger(k, v)),
		option("drop_collection_stable_seconds", "Mining and unloading",
			"drop stable seconds", c -> c.dropCollectionStableSeconds,
			(c, k, v) -> c.dropCollectionStableSeconds = positive(k, v)),
		option("inventory_reserved_slots", "Mining and unloading",
			"reserved slots", c -> c.inventoryReservedSlots,
			(c, k, v) -> c.inventoryReservedSlots = rangedInteger(k, v, 0, 35)),
		option("mining_blocks_per_empty_slot", "Mining and unloading",
			"blocks per empty slot", c -> c.miningBlocksPerEmptySlot,
			(c, k, v) -> c.miningBlocksPerEmptySlot = positiveInteger(k, v)),
		option("unload_landing_search_radius", "Mining and unloading",
			"unload search radius", c -> c.unloadLandingSearchRadius,
			(c, k, v) -> c.unloadLandingSearchRadius = positiveInteger(k, v)),
		option("unload_edge_inset", "Mining and unloading", "unload edge inset",
			c -> c.unloadEdgeInset,
			(c, k, v) -> c.unloadEdgeInset = ranged(k, v, 0.0, 0.3)),
		option("elytra_navigation_min_distance", "Navigation",
			"elytra minimum distance", c -> c.elytraNavigationMinDistance,
			(c, k, v) -> c.elytraNavigationMinDistance = nonNegativeInteger(k,
				v)),
		option("navigation_stall_timeout_seconds", "Navigation",
			"stall timeout seconds", c -> c.navigationStallTimeoutSeconds,
			(c, k, v) -> c.navigationStallTimeoutSeconds = positive(k, v)),
		option("navigation_retry_count", "Navigation", "retry count",
			c -> c.navigationRetryCount,
			(c, k, v) -> c.navigationRetryCount = nonNegativeInteger(k, v)),
		option("flight_retry_count", "Navigation", "flight retry count",
			c -> c.flightRetryCount,
			(c, k, v) -> c.flightRetryCount = positiveInteger(k, v)),
		option("portal_transition_cost", "Navigation",
			"portal transition cost", c -> c.portalTransitionCost,
			(c, k, v) -> c.portalTransitionCost = nonNegative(k, v)),
		option("portal_transition_timeout_seconds", "Navigation",
			"portal transition timeout seconds",
			c -> c.portalTransitionTimeoutSeconds,
			(c, k, v) -> c.portalTransitionTimeoutSeconds = positive(k, v)),
		option("portal_exit_timeout_seconds", "Portal exit",
			"timeout seconds", c -> c.portalExitTimeoutSeconds,
			(c, k, v) -> c.portalExitTimeoutSeconds = positive(k, v)),
		option("portal_exit_min_radius", "Portal exit", "minimum radius",
			c -> c.portalExitMinRadius, (c, k, v) -> {
				int result = positiveInteger(k, v);
				if(result > c.portalExitMaxRadius)
					throw new IllegalArgumentException(
						k + " cannot exceed portal_exit_max_radius.");
				c.portalExitMinRadius = result;
			}),
		option("portal_exit_max_radius", "Portal exit", "maximum radius",
			c -> c.portalExitMaxRadius, (c, k, v) -> {
				int result = positiveInteger(k, v);
				if(result < c.portalExitMinRadius)
					throw new IllegalArgumentException(k
						+ " cannot be lower than portal_exit_min_radius.");
				c.portalExitMaxRadius = result;
			}),
		option("portal_exit_vertical_radius", "Portal exit", "vertical radius",
			c -> c.portalExitVerticalRadius,
			(c, k, v) -> c.portalExitVerticalRadius = nonNegativeInteger(k, v)),
		option("repair_experience_stable_seconds", "Interaction",
			"repair experience stable seconds",
			c -> c.repairExperienceStableSeconds,
			(c, k, v) -> c.repairExperienceStableSeconds = positive(k, v)),
		option("supply_interaction_timeout_seconds", "Interaction",
			"supply timeout seconds", c -> c.supplyInteractionTimeoutSeconds,
			(c, k, v) -> c.supplyInteractionTimeoutSeconds = positive(k, v)),
		option("furnace_interaction_timeout_seconds", "Interaction",
			"furnace timeout seconds", c -> c.furnaceInteractionTimeoutSeconds,
			(c, k, v) -> c.furnaceInteractionTimeoutSeconds = positive(k, v)));
	
	private static final Map<String, PerimeterAdvancedOption> BY_KEY =
		index();
	
	private PerimeterAdvancedOptions()
	{
		
	}
	
	public static List<PerimeterAdvancedOption> all()
	{
		return OPTIONS;
	}
	
	public static List<String> keys()
	{
		return OPTIONS.stream().map(PerimeterAdvancedOption::key).toList();
	}
	
	public static List<String> groups()
	{
		return OPTIONS.stream().map(PerimeterAdvancedOption::group).distinct()
			.toList();
	}
	
	public static boolean has(String key)
	{
		return BY_KEY.containsKey(key);
	}
	
	public static PerimeterAdvancedOption get(String key)
	{
		PerimeterAdvancedOption option = BY_KEY.get(key);
		
		if(option == null)
			throw new IllegalArgumentException(
				"Unknown advanced configuration key: " + key + ".");
		
		return option;
	}
	
	private static PerimeterAdvancedOption option(String key, String group,
		String label,
		java.util.function.Function<PerimeterAdvancedConfig, Number> getter,
		ConfigSetter setter)
	{
		return new PerimeterAdvancedOption(key, group, label, getter,
			(config, value) -> setter.set(config, key, value));
	}
	
	@FunctionalInterface
	private interface ConfigSetter
	{
		void set(PerimeterAdvancedConfig config, String key, double value);
	}
	
	private static Map<String, PerimeterAdvancedOption> index()
	{
		Map<String, PerimeterAdvancedOption> result = new LinkedHashMap<>();
		
		for(PerimeterAdvancedOption option : OPTIONS)
			if(result.put(option.key(), option) != null)
				throw new IllegalStateException(
					"Duplicate advanced configuration key: " + option.key());
		
		return Map.copyOf(result);
	}
	
	private static int nonNegativeInteger(String key, double value)
	{
		return rangedInteger(key, value, 0, Integer.MAX_VALUE);
	}
	
	private static int positiveInteger(String key, double value)
	{
		return rangedInteger(key, value, 1, Integer.MAX_VALUE);
	}
	
	private static int rangedInteger(String key, double value, int minimum,
		int maximum)
	{
		if(!Double.isFinite(value) || value != Math.rint(value) || value < minimum
			|| value > maximum)
			throw new IllegalArgumentException(key + " must be an integer from "
				+ minimum + " to " + maximum + ".");
		
		return (int)value;
	}
	
	private static double positive(String key, double value)
	{
		if(!Double.isFinite(value) || value <= 0.0)
			throw new IllegalArgumentException(
				key + " must be a positive finite number.");
		
		return value;
	}
	
	private static double nonNegative(String key, double value)
	{
		if(!Double.isFinite(value) || value < 0.0)
			throw new IllegalArgumentException(
				key + " must be a non-negative finite number.");
		
		return value;
	}
	
	private static double ranged(String key, double value, double minimum,
		double maximum)
	{
		if(!Double.isFinite(value) || value < minimum || value > maximum)
			throw new IllegalArgumentException(
				key + " must be from " + minimum + " to " + maximum + ".");
		
		return value;
	}
}
