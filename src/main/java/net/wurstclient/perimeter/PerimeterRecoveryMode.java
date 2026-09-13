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
 * How low-durability tools and Elytra are recovered.
 */
public enum PerimeterRecoveryMode
{
	/** Travel through the portal pairs and repair at XP furnaces. */
	REPAIR_PORTAL("repair_portal"),
	
	/** Swap the item for a better one at the durability supply chest. */
	SUPPLY_POINT("supply_point");
	
	public static final PerimeterRecoveryMode DEFAULT = REPAIR_PORTAL;
	
	private final String id;
	
	PerimeterRecoveryMode(String id)
	{
		this.id = id;
	}
	
	public String id()
	{
		return id;
	}
	
	public static PerimeterRecoveryMode fromId(String id)
	{
		if(id != null)
			for(PerimeterRecoveryMode mode : values())
				if(mode.id.equals(id.toLowerCase(Locale.ROOT)))
					return mode;
		
		return DEFAULT;
	}
	
	@Override
	public String toString()
	{
		return id;
	}
}
