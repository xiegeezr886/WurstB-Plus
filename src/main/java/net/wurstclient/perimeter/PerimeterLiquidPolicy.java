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
 * How the digger treats fluids it meets while excavating.
 */
public enum PerimeterLiquidPolicy
{
	/** Never target fluids (they have no clickable outline anyway). */
	AVOID("avoid"),
	
	/** Replace fluids inside the area with the configured sealing blocks. */
	REPLACE("replace"),
	
	/** Seal fluids along the boundary and handle fluids inside the area. */
	SEAL_BOUNDARY("seal_boundary");
	
	public static final PerimeterLiquidPolicy DEFAULT = SEAL_BOUNDARY;
	
	private final String id;
	
	PerimeterLiquidPolicy(String id)
	{
		this.id = id;
	}
	
	public String id()
	{
		return id;
	}
	
	/**
	 * @return the policy with this id, or {@link #DEFAULT} when the id is
	 *         unknown or null.
	 */
	public static PerimeterLiquidPolicy fromId(String id)
	{
		if(id != null)
			for(PerimeterLiquidPolicy policy : values())
				if(policy.id.equals(id.toLowerCase(Locale.ROOT)))
					return policy;
		
		return DEFAULT;
	}
	
	@Override
	public String toString()
	{
		return id;
	}
}
