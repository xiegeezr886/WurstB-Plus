/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.perimeter;

import java.util.Objects;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Adapts a Minecraft level to the lookups the perimeter engine needs.
 */
public final class PerimeterWorldView
	implements PerimeterFluidLookup, PerimeterBlockView
{
	private final Level level;
	
	public PerimeterWorldView(Level level)
	{
		this.level = Objects.requireNonNull(level, "level");
	}
	
	public Level level()
	{
		return level;
	}
	
	public boolean isLoaded(int x, int z)
	{
		return level.hasChunk(SectionPos.blockToSectionCoord(x),
			SectionPos.blockToSectionCoord(z));
	}
	
	public BlockState stateAt(int x, int y, int z)
	{
		return level.getBlockState(new BlockPos(x, y, z));
	}
	
	@Override
	public boolean isFluid(int x, int y, int z)
	{
		if(!isLoaded(x, z))
			return false;
		
		return !stateAt(x, y, z).getFluidState().isEmpty();
	}
	
	@Override
	public boolean isAir(int x, int y, int z)
	{
		if(!isLoaded(x, z))
			return false;
		
		return stateAt(x, y, z).isAir();
	}
	
	public boolean isSolid(int x, int y, int z)
	{
		if(!isLoaded(x, z))
			return false;
		
		BlockState state = stateAt(x, y, z);
		return !state.isAir() && state.getFluidState().isEmpty();
	}
}
