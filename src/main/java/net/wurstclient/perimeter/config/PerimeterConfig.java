/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.perimeter.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.wurstclient.perimeter.PerimeterLiquidPolicy;
import net.wurstclient.perimeter.PerimeterRecoveryMode;

/**
 * The per-server / per-save perimeter configuration.
 *
 * <p>
 * Field names, defaults and the schema version match the reference Perimeter
 * Digger mod, so an existing configuration file stays readable (aside from the
 * {@code schemaVersion} field, which is identical as well).
 */
public final class PerimeterConfig
{
	public static final String DEFAULT_SEALING_BLOCK = "minecraft:netherrack";
	public static final String DEFAULT_FOOD =
		"minecraft:enchanted_golden_apple";
	
	public int schemaVersion = PerimeterConfigMigration.CURRENT_SCHEMA_VERSION;
	public Integer diggingMinY;
	public Integer diggingMaxY;
	public PerimeterPosition consumableSupplyPoint;
	public PerimeterPosition durabilitySupplyPoint;
	public PerimeterPosition bedPoint;
	public PerimeterPosition perimeterPortalOverworld;
	public PerimeterPosition perimeterPortalNether;
	public PerimeterPosition repairPortalOverworld;
	public PerimeterPosition repairPortalNether;
	public PerimeterPosition furnaceRowStart;
	public PerimeterPosition furnaceRowEnd;
	public Map<String, PerimeterUnloadingPoint> unloadingPoints =
		new LinkedHashMap<>();
	public String liquidPolicy = PerimeterLiquidPolicy.DEFAULT.id();
	public String durabilityRecoveryMode = PerimeterRecoveryMode.DEFAULT.id();
	public List<String> sealingBlocks =
		new ArrayList<>(List.of(DEFAULT_SEALING_BLOCK));
	public List<String> foods = new ArrayList<>(List.of(DEFAULT_FOOD));
	public List<String> unloadingWhitelist = new ArrayList<>();
	public PerimeterDetectedArea detectedArea;
	public PerimeterFunctionConfig functions = new PerimeterFunctionConfig();
	public PerimeterAdvancedConfig advanced = new PerimeterAdvancedConfig();
	
	/**
	 * Repairs null fields that a hand-edited or partially written file may
	 * contain, and pins the schema version.
	 */
	public void normalize()
	{
		schemaVersion = PerimeterConfigMigration.CURRENT_SCHEMA_VERSION;
		
		if(unloadingPoints == null)
			unloadingPoints = new LinkedHashMap<>();
		
		if(detectedArea != null)
			detectedArea.normalize();
		
		if(liquidPolicy == null)
			liquidPolicy = PerimeterLiquidPolicy.DEFAULT.id();
		
		if(durabilityRecoveryMode == null)
			durabilityRecoveryMode = PerimeterRecoveryMode.DEFAULT.id();
		
		if(sealingBlocks == null)
			sealingBlocks = new ArrayList<>(List.of(DEFAULT_SEALING_BLOCK));
		
		if(foods == null)
			foods = new ArrayList<>(List.of(DEFAULT_FOOD));
		
		if(unloadingWhitelist == null)
			unloadingWhitelist = new ArrayList<>();
		
		if(functions == null)
			functions = new PerimeterFunctionConfig();
		
		if(advanced == null)
			advanced = new PerimeterAdvancedConfig();
	}
	
	public PerimeterLiquidPolicy liquidPolicyValue()
	{
		return PerimeterLiquidPolicy.fromId(liquidPolicy);
	}
	
	public PerimeterRecoveryMode recoveryModeValue()
	{
		return PerimeterRecoveryMode.fromId(durabilityRecoveryMode);
	}
	
	public boolean hasDetectedArea()
	{
		return detectedArea != null && !detectedArea.isEmpty();
	}
	
	public boolean hasDiggingRange()
	{
		return diggingMinY != null && diggingMaxY != null;
	}
	
	/**
	 * @return the inclusive mining Y range, or {@code null} when it is not fully
	 *         configured.
	 */
	public int[] diggingRange()
	{
		if(!hasDiggingRange())
			return null;
		
		return new int[]{diggingMinY, diggingMaxY};
	}
	
	public void putUnloadingPoint(String name, int x, int minY, int z)
	{
		unloadingPoints.put(name, new PerimeterUnloadingPoint(x, minY, z));
	}
	
	public void removeUnloadingPoint(String name)
	{
		unloadingPoints.remove(name);
	}
	
	/**
	 * Adds an identifier to one of the string lists, ignoring case, and returns
	 * whether the list changed.
	 */
	public static boolean addIdentifier(List<String> list, String identifier)
	{
		if(identifier == null || identifier.isBlank())
			return false;
		
		for(String existing : list)
			if(existing.equalsIgnoreCase(identifier))
				return false;
		
		return list.add(identifier);
	}
	
	/**
	 * Removes an identifier from one of the string lists, ignoring case, and
	 * returns whether the list changed.
	 */
	public static boolean removeIdentifier(List<String> list, String identifier)
	{
		if(identifier == null)
			return false;
		
		return list.removeIf(existing -> existing.equalsIgnoreCase(identifier));
	}
}
