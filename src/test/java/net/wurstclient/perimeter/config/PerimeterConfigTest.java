/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.perimeter.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.wurstclient.perimeter.PerimeterLiquidPolicy;
import net.wurstclient.perimeter.PerimeterRecoveryMode;

final class PerimeterConfigTest
{
	@Test
	void defaultsMatchTheReferenceMod()
	{
		PerimeterConfig config = new PerimeterConfig();
		PerimeterAdvancedConfig advanced = config.advanced;
		PerimeterFunctionConfig functions = config.functions;
		
		assertEquals(PerimeterConfigMigration.CURRENT_SCHEMA_VERSION,
			config.schemaVersion);
		assertNull(config.diggingMinY);
		assertNull(config.diggingMaxY);
		assertFalse(config.hasDiggingRange());
		assertEquals(PerimeterLiquidPolicy.SEAL_BOUNDARY,
			config.liquidPolicyValue());
		assertEquals(PerimeterRecoveryMode.REPAIR_PORTAL,
			config.recoveryModeValue());
		assertEquals(List.of("minecraft:netherrack"), config.sealingBlocks);
		assertEquals(List.of("minecraft:enchanted_golden_apple"), config.foods);
		assertTrue(config.unloadingWhitelist.isEmpty());
		assertFalse(config.hasDetectedArea());
		
		assertEquals(32, advanced.toolDurabilityThreshold);
		assertEquals(32, advanced.elytraDurabilityThreshold);
		assertEquals(5, advanced.emergencyFlightDurabilityThreshold);
		assertEquals(14, advanced.foodLevelThreshold);
		assertEquals(16.0, advanced.healthEatingThreshold);
		assertEquals(1, advanced.foodResupplyTrigger);
		assertEquals(64, advanced.foodResupplyTarget);
		assertEquals(10, advanced.fireworkResupplyTrigger);
		assertEquals(128, advanced.fireworkResupplyTarget);
		assertEquals(8, advanced.dropCollectionRadius);
		assertEquals(1.5, advanced.dropCollectionStableSeconds);
		assertEquals(1, advanced.inventoryReservedSlots);
		assertEquals(64, advanced.miningBlocksPerEmptySlot);
		assertEquals(32, advanced.elytraNavigationMinDistance);
		assertEquals(16, advanced.unloadLandingSearchRadius);
		assertEquals(0.2, advanced.unloadEdgeInset);
		assertEquals(10.0, advanced.navigationStallTimeoutSeconds);
		assertEquals(2, advanced.navigationRetryCount);
		assertEquals(2, advanced.flightRetryCount);
		assertEquals(16.0, advanced.portalTransitionCost);
		assertEquals(20.0, advanced.portalTransitionTimeoutSeconds);
		assertEquals(20.0, advanced.portalExitTimeoutSeconds);
		assertEquals(3, advanced.portalExitMinRadius);
		assertEquals(8, advanced.portalExitMaxRadius);
		assertEquals(4, advanced.portalExitVerticalRadius);
		assertEquals(1.5, advanced.repairExperienceStableSeconds);
		assertEquals(2.0, advanced.supplyInteractionTimeoutSeconds);
		assertEquals(2.0, advanced.furnaceInteractionTimeoutSeconds);
		
		assertTrue(functions.collectDrops);
		assertTrue(functions.unload);
		assertTrue(functions.eat);
		assertTrue(functions.durabilityRecovery);
		assertTrue(functions.crossDimensionRepair);
		assertTrue(functions.resupply);
		assertTrue(functions.elytraNavigation);
		assertFalse(functions.sleep);
	}
	
	@Test
	void normalizeRepairsMissingSections()
	{
		PerimeterConfig config = new PerimeterConfig();
		config.unloadingPoints = null;
		config.liquidPolicy = null;
		config.durabilityRecoveryMode = null;
		config.sealingBlocks = null;
		config.foods = null;
		config.unloadingWhitelist = null;
		config.functions = null;
		config.advanced = null;
		config.detectedArea = new PerimeterDetectedArea();
		config.detectedArea.scanlines = null;
		
		config.normalize();
		
		assertNotNull(config.unloadingPoints);
		assertEquals(PerimeterLiquidPolicy.SEAL_BOUNDARY.id(), config.liquidPolicy);
		assertEquals(PerimeterRecoveryMode.REPAIR_PORTAL.id(),
			config.durabilityRecoveryMode);
		assertEquals(List.of("minecraft:netherrack"), config.sealingBlocks);
		assertEquals(List.of("minecraft:enchanted_golden_apple"), config.foods);
		assertNotNull(config.unloadingWhitelist);
		assertNotNull(config.functions);
		assertNotNull(config.advanced);
		assertNotNull(config.detectedArea.scanlines);
	}
	
	@Test
	void unknownPolicyIdsFallBackToTheDefaults()
	{
		PerimeterConfig config = new PerimeterConfig();
		config.liquidPolicy = "nonsense";
		config.durabilityRecoveryMode = "nonsense";
		
		assertEquals(PerimeterLiquidPolicy.SEAL_BOUNDARY,
			config.liquidPolicyValue());
		assertEquals(PerimeterRecoveryMode.REPAIR_PORTAL,
			config.recoveryModeValue());
	}
	
	@Test
	void diggingRangeNeedsBothLimits()
	{
		PerimeterConfig config = new PerimeterConfig();
		config.diggingMinY = -59;
		
		assertFalse(config.hasDiggingRange());
		assertNull(config.diggingRange());
		
		config.diggingMaxY = 79;
		
		assertTrue(config.hasDiggingRange());
		assertEquals(-59, config.diggingRange()[0]);
		assertEquals(79, config.diggingRange()[1]);
	}
	
	@Test
	void namedUnloadingPointsCanBeAddedAndRemoved()
	{
		PerimeterConfig config = new PerimeterConfig();
		config.putUnloadingPoint("main", 10, -59, 20);
		config.putUnloadingPoint("main", 11, -58, 21);
		config.putUnloadingPoint("north", 0, -59, 0);
		
		assertEquals(2, config.unloadingPoints.size());
		assertEquals(11, config.unloadingPoints.get("main").x);
		
		config.removeUnloadingPoint("main");
		
		assertFalse(config.unloadingPoints.containsKey("main"));
		assertTrue(config.unloadingPoints.containsKey("north"));
	}
	
	@Test
	void identifierListsIgnoreCaseAndRejectDuplicates()
	{
		PerimeterConfig config = new PerimeterConfig();
		
		assertTrue(PerimeterConfig.addIdentifier(config.sealingBlocks,
			"minecraft:obsidian"));
		assertFalse(PerimeterConfig.addIdentifier(config.sealingBlocks,
			"MINECRAFT:OBSIDIAN"));
		assertEquals(2, config.sealingBlocks.size());
		
		assertTrue(PerimeterConfig.removeIdentifier(config.sealingBlocks,
			"Minecraft:Obsidian"));
		assertFalse(PerimeterConfig.removeIdentifier(config.sealingBlocks,
			"minecraft:obsidian"));
		assertEquals(List.of("minecraft:netherrack"), config.sealingBlocks);
	}
	
	@Test
	void unversionedFilesAreMigratedToTheCurrentSchema()
	{
		JsonObject source =
			JsonParser.parseString("{\"liquidPolicy\":\"avoid\"}")
				.getAsJsonObject();
		
		PerimeterConfigMigration.Result result =
			PerimeterConfigMigration.migrate(source);
		
		assertTrue(result.changed());
		assertEquals(1,
			result.config().get("schemaVersion").getAsInt());
		assertEquals("avoid", result.config().get("liquidPolicy").getAsString());
		assertFalse(source.has("schemaVersion"));
	}
	
	@Test
	void currentSchemaFilesAreUnchanged()
	{
		JsonObject source = new JsonObject();
		source.addProperty("schemaVersion", 1);
		
		PerimeterConfigMigration.Result result =
			PerimeterConfigMigration.migrate(source);
		
		assertFalse(result.changed());
		assertEquals(1, result.config().get("schemaVersion").getAsInt());
	}
	
	@Test
	void newerSchemasAreRejected()
	{
		JsonObject source = new JsonObject();
		source.addProperty("schemaVersion", 2);
		
		IllegalStateException exception = assertThrows(
			IllegalStateException.class,
			() -> PerimeterConfigMigration.migrate(source));
		
		assertTrue(exception.getMessage().contains("newer"));
	}
	
	@Test
	void invalidSchemaVersionsAreRejected()
	{
		JsonObject negative = new JsonObject();
		negative.addProperty("schemaVersion", -1);
		
		assertThrows(IllegalStateException.class,
			() -> PerimeterConfigMigration.migrate(negative));
		
		JsonObject fractional = new JsonObject();
		fractional.addProperty("schemaVersion", 1.5);
		
		assertThrows(IllegalStateException.class,
			() -> PerimeterConfigMigration.migrate(fractional));
	}
	
	@Test
	void advancedOptionsExposeTheDocumentedKeys()
	{
		List<String> keys = PerimeterAdvancedOptions.keys();
		
		assertEquals(28, keys.size());
		assertTrue(keys.contains("tool_durability_threshold"));
		assertTrue(keys.contains("portal_exit_vertical_radius"));
		assertTrue(keys.contains("furnace_interaction_timeout_seconds"));
		assertTrue(PerimeterAdvancedOptions.has("unload_edge_inset"));
		assertFalse(PerimeterAdvancedOptions.has("nonsense"));
		assertThrows(IllegalArgumentException.class,
			() -> PerimeterAdvancedOptions.get("nonsense"));
		assertEquals(6, PerimeterAdvancedOptions.groups().size());
	}
	
	@Test
	void advancedOptionsRejectOutOfRangeValues()
	{
		PerimeterAdvancedConfig config = new PerimeterAdvancedConfig();
		
		assertThrows(IllegalArgumentException.class, () -> PerimeterAdvancedOptions
			.get("tool_durability_threshold").set(config, -1));
		assertThrows(IllegalArgumentException.class, () -> PerimeterAdvancedOptions
			.get("tool_durability_threshold").set(config, 32.5));
		assertThrows(IllegalArgumentException.class,
			() -> PerimeterAdvancedOptions.get("food_level_threshold")
				.set(config, 21));
		assertThrows(IllegalArgumentException.class,
			() -> PerimeterAdvancedOptions.get("unload_edge_inset")
				.set(config, 0.4));
		assertThrows(IllegalArgumentException.class,
			() -> PerimeterAdvancedOptions.get("navigation_retry_count")
				.set(config, -1));
		
		PerimeterAdvancedOptions.get("food_level_threshold").set(config, 20);
		assertEquals(20, config.foodLevelThreshold);
	}
	
	@Test
	void advancedOptionsEnforceCrossFieldRules()
	{
		PerimeterAdvancedConfig config = new PerimeterAdvancedConfig();
		
		assertThrows(IllegalArgumentException.class,
			() -> PerimeterAdvancedOptions.get("food_resupply_trigger")
				.set(config, 65));
		assertThrows(IllegalArgumentException.class,
			() -> PerimeterAdvancedOptions.get("portal_exit_min_radius")
				.set(config, 9));
		
		PerimeterAdvancedOptions.get("food_resupply_target").set(config, 128);
		PerimeterAdvancedOptions.get("food_resupply_trigger").set(config, 64);
		
		assertEquals(64, config.foodResupplyTrigger);
		assertEquals(128, config.foodResupplyTarget);
	}
	
	@Test
	void advancedOptionsFormatWholeNumbersWithoutADecimalPoint()
	{
		PerimeterAdvancedConfig config = new PerimeterAdvancedConfig();
		
		assertEquals("32", PerimeterAdvancedOptions
			.get("tool_durability_threshold").format(config));
		assertEquals("1.5", PerimeterAdvancedOptions
			.get("drop_collection_stable_seconds").format(config));
		assertEquals("0.2", PerimeterAdvancedOptions.get("unload_edge_inset")
			.format(config));
	}
}
