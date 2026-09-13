/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.perimeter.config;

/**
 * A named vertical unloading shaft. The XZ coordinate is the shaft centre and
 * {@code minY} is the shaft's lowest Y.
 */
public final class PerimeterUnloadingPoint
{
	public int x;
	public int minY;
	public int z;
	
	public PerimeterUnloadingPoint()
	{
		
	}
	
	public PerimeterUnloadingPoint(int x, int minY, int z)
	{
		this.x = x;
		this.minY = minY;
		this.z = z;
	}
	
	@Override
	public String toString()
	{
		return x + " " + minY + " " + z;
	}
}
