/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.perimeter.config;

/**
 * One inclusive run of minable X coordinates at a single Z.
 */
public final class PerimeterScanline
{
	public int z;
	public int minX;
	public int maxX;
	
	public PerimeterScanline()
	{
		
	}
	
	public PerimeterScanline(int z, int minX, int maxX)
	{
		this.z = z;
		this.minX = minX;
		this.maxX = maxX;
	}
	
	public int length()
	{
		return maxX - minX + 1;
	}
	
	@Override
	public String toString()
	{
		return "z=" + z + " x=" + minX + ".." + maxX;
	}
}
