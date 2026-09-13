/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.perimeter.config;

import java.util.ArrayList;
import java.util.List;

/**
 * The stored result of a boundary detection run: the boundary block and Y that
 * were used, the bounding box, and one inclusive X run per Z row.
 */
public final class PerimeterDetectedArea
{
	public String boundaryBlock;
	public int boundaryY;
	public long columnCount;
	public int minX;
	public int maxX;
	public int minZ;
	public int maxZ;
	public List<PerimeterScanline> scanlines = new ArrayList<>();
	
	public void normalize()
	{
		if(scanlines == null)
			scanlines = new ArrayList<>();
	}
	
	public boolean isEmpty()
	{
		return scanlines == null || scanlines.isEmpty();
	}
}
