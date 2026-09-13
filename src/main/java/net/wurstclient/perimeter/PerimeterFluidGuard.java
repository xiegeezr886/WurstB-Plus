/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.perimeter;

/**
 * The liquid policy decisions of the mining schematic, kept free of Minecraft
 * types so they can be unit tested.
 *
 * <p>
 * The rules are ported from the reference mod's area mining schematic: inside
 * the area, fluids are skipped under {@code avoid} and replaced with a sealing
 * block under {@code replace} and {@code seal_boundary}. Under
 * {@code seal_boundary} the schematic is additionally expanded by one block to
 * the sides and above, and fluid blocks in that rim which touch the sides or
 * the top of the area are sealed as well.
 *
 * <p>
 * One deviation is deliberate: the reference prevents breaking solid blocks
 * whose removal would release a fluid from inside Baritone's own breaking
 * logic, which a stock Baritone does not offer a hook for. This port instead
 * refuses to process fluid-adjacent blocks under {@code avoid}, which leaves
 * the same blocks untouched.
 */
public final class PerimeterFluidGuard
{
	private PerimeterFluidGuard()
	{}
	
	/**
	 * @return whether a position inside the area should be processed by the
	 *         builder at all.
	 */
	public static boolean processInside(PerimeterLiquidPolicy policy,
		boolean hasSealingBlocks, boolean currentIsFluid,
		boolean fluidAdjacent)
	{
		if(policy == PerimeterLiquidPolicy.AVOID && fluidAdjacent)
			return false;
		
		return hasSealingBlocks && policy != PerimeterLiquidPolicy.AVOID
			|| !currentIsFluid;
	}
	
	/**
	 * @return whether a fluid block outside the area but inside the sealing rim
	 *         should be sealed.
	 */
	public static boolean processRim(PerimeterRegion area,
		PerimeterLiquidPolicy policy, boolean hasSealingBlocks, int x, int y,
		int z, boolean currentIsFluid)
	{
		return hasSealingBlocks && policy == PerimeterLiquidPolicy.SEAL_BOUNDARY
			&& currentIsFluid && touchesSideOrTopBoundary(area, x, y, z);
	}
	
	/**
	 * @return whether the desired state for a position inside the area is a
	 *         sealing block instead of air.
	 */
	public static boolean replaceFluidInside(PerimeterLiquidPolicy policy,
		boolean currentIsFluid)
	{
		return currentIsFluid && policy != PerimeterLiquidPolicy.AVOID;
	}
	
	/**
	 * Ported from the reference: the top rim layer above the area counts as
	 * touching, a rim layer below or above the mining range does not, and
	 * otherwise any cardinal neighbour inside the area counts.
	 */
	public static boolean touchesSideOrTopBoundary(PerimeterRegion area, int x,
		int y, int z)
	{
		if(y == area.maxY() + 1 && area.containsXZ(x, z))
			return true;
		
		if(y < area.minY() || y > area.maxY())
			return false;
		
		return area.containsXZ(x + 1, z) || area.containsXZ(x - 1, z)
			|| area.containsXZ(x, z + 1) || area.containsXZ(x, z - 1);
	}
}
