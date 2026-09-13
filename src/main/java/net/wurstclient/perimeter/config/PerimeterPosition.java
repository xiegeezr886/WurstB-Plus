/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.perimeter.config;

/**
 * A block coordinate stored in the per-world perimeter configuration.
 *
 * <p>
 * Field names match the reference Perimeter Digger configuration schema so that
 * existing files remain readable.
 */
public final class PerimeterPosition
{
	public int x;
	public int y;
	public int z;
	
	public PerimeterPosition()
	{
		
	}
	
	public PerimeterPosition(int x, int y, int z)
	{
		this.x = x;
		this.y = y;
		this.z = z;
	}
	
	public long squaredDistanceTo(double otherX, double otherY, double otherZ)
	{
		double dx = x - otherX;
		double dy = y - otherY;
		double dz = z - otherZ;
		return (long)(dx * dx + dy * dy + dz * dz);
	}
	
	public void set(int x, int y, int z)
	{
		this.x = x;
		this.y = y;
		this.z = z;
	}
	
	/**
	 * @return whether a position was configured at all. The reference keeps
	 *         unset facilities as empty objects in the JSON file.
	 */
	public boolean isSet()
	{
		return x != 0 || y != 0 || z != 0;
	}
	
	@Override
	public String toString()
	{
		return x + " " + y + " " + z;
	}
	
	@Override
	public boolean equals(Object obj)
	{
		if(this == obj)
			return true;
		
		if(!(obj instanceof PerimeterPosition other))
			return false;
		
		return x == other.x && y == other.y && z == other.z;
	}
	
	@Override
	public int hashCode()
	{
		return (x * 31 + y) * 31 + z;
	}
}
