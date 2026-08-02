/*
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.waypoints;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

public final class Waypoint
{
	private final String name;
	private final Identifier dimension;
	private final BlockPos pos;
	private int color;

	public Waypoint(String name, Identifier dimension, BlockPos pos,
		int color)
	{
		this.name = name;
		this.dimension = dimension;
		this.pos = pos;
		this.color = color;
	}

	public String getName()
	{
		return name;
	}

	public Identifier getDimension()
	{
		return dimension;
	}

	public BlockPos getPos()
	{
		return pos;
	}

	public int getColor()
	{
		return color;
	}

	public void setColor(int color)
	{
		this.color = color;
	}

	@Override
	public boolean equals(Object obj)
	{
		if(obj instanceof Waypoint other)
			return name.equals(other.name);
		return false;
	}

	@Override
	public int hashCode()
	{
		return name.hashCode();
	}
}
