/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.perimeter;

import java.util.Locale;

/**
 * Every state of the automation, ported one to one from the reference mod.
 */
public enum PerimeterAutomationState
{
	IDLE,
	VALIDATING,
	READY,
	NAVIGATING_TO_MINE,
	MINING,
	COLLECTING_DROPS,
	EATING,
	COMPLETE,
	NAVIGATING_TO_UNLOAD,
	APPROACHING_UNLOAD,
	POSITIONING_FOR_UNLOAD,
	UNLOADING,
	NAVIGATING_TO_RESUPPLY,
	RESUPPLYING,
	NAVIGATING_TO_DURABILITY_SUPPLY,
	SWAPPING_DURABILITY_AT_SUPPLY,
	NAVIGATING_TO_BED,
	SLEEPING,
	NAVIGATING_TO_PERIMETER_PORTAL,
	ENTERING_PERIMETER_PORTAL,
	NAVIGATING_TO_REPAIR_PORTAL,
	ENTERING_REPAIR_PORTAL,
	CLEARING_REPAIR_PORTAL,
	NAVIGATING_TO_REPAIR_MACHINE,
	REPAIRING,
	RETURNING_TO_MINE,
	PAUSED,
	ERROR;
	
	/**
	 * The state name used in commands and status output, matching the
	 * reference's lower case ids.
	 */
	public String id()
	{
		return name().toLowerCase(Locale.ROOT);
	}
	
	public boolean isNavigationState()
	{
		return switch(this)
		{
			case NAVIGATING_TO_MINE, NAVIGATING_TO_UNLOAD,
				APPROACHING_UNLOAD, NAVIGATING_TO_RESUPPLY,
				NAVIGATING_TO_DURABILITY_SUPPLY, NAVIGATING_TO_BED,
				NAVIGATING_TO_PERIMETER_PORTAL, ENTERING_PERIMETER_PORTAL,
				NAVIGATING_TO_REPAIR_PORTAL, ENTERING_REPAIR_PORTAL,
				CLEARING_REPAIR_PORTAL, NAVIGATING_TO_REPAIR_MACHINE,
				RETURNING_TO_MINE ->
				true;
			default -> false;
		};
	}
	
	public boolean isActive()
	{
		return this != IDLE && this != COMPLETE && this != ERROR
			&& this != PAUSED;
	}
	
	public static PerimeterAutomationState fromId(String id)
	{
		if(id == null)
			return null;
		
		for(PerimeterAutomationState state : values())
			if(state.id().equalsIgnoreCase(id))
				return state;
		
		return null;
	}
}
