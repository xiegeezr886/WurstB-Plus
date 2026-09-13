/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.perimeter;

import java.util.Collection;
import java.util.List;

import baritone.api.IBaritone;
import baritone.api.process.IBuilderProcess;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;

/**
 * One running batch of area mining, driven through Baritone's builder process
 * the same way the reference mod drives its own area mine process.
 *
 * <p>
 * The block limit of the reference is reproduced by observing the blocks around
 * the player: a block that was seen solid and is now air counts as mined, and
 * once the batch limit is reached the builder is paused until the products have
 * been unloaded.
 */
public final class PerimeterMiningJob
{
	public static final String BUILD_NAME = "perimeter";
	private static final int SCAN_RADIUS = 5;
	private static final int PRUNE_INTERVAL_TICKS = 40;
	private static final int MAX_TRACKED_BLOCKS = 300_000;
	
	private final LongOpenHashSet tracked = new LongOpenHashSet();
	private IBuilderProcess builder;
	private PerimeterMiningSchematic schematic;
	private PerimeterRegion region;
	private long blockLimit = Long.MAX_VALUE;
	private long minedBlocks;
	private int ticks;
	private boolean active;
	private boolean paused;
	private boolean complete;
	
	/**
	 * @return whether the batch could be started.
	 */
	public boolean start(IBaritone baritone, PerimeterRegion region,
		PerimeterLiquidPolicy policy, Collection<Block> sealingBlocks,
		Collection<Block> disallowed, PerimeterFluidLookup fluids,
		long blockLimit)
	{
		cancel();
		
		if(baritone == null || region == null || region.columnCount() <= 0L)
			return false;
		
		this.builder = baritone.getBuilderProcess();
		this.region = region;
		this.blockLimit = Math.max(1L, blockLimit);
		this.schematic = new PerimeterMiningSchematic(region, policy,
			sealingBlocks, disallowed, fluids);
		this.minedBlocks = 0L;
		this.ticks = 0;
		this.complete = false;
		this.paused = false;
		this.tracked.clear();
		
		builder.build(BUILD_NAME, schematic, schematic.origin());
		active = true;
		return true;
	}
	
	/**
	 * Counts the blocks mined since the batch started and enforces the batch
	 * limit. Call once per client tick while the batch runs.
	 */
	public void tick(BlockPos feet, PerimeterBlockView view)
	{
		if(!active || builder == null)
			return;
		
		if(!builder.isActive())
		{
			// the builder finished the area or lost control
			active = false;
			complete = !paused;
			return;
		}
		
		if(paused || feet == null || view == null)
			return;
		
		scan(feet, view);
		
		if(++ticks % PRUNE_INTERVAL_TICKS == 0)
			prune(feet);
		
		if(minedBlocks >= blockLimit)
			pause();
	}
	
	private void scan(BlockPos feet, PerimeterBlockView view)
	{
		int centerX = feet.getX();
		int centerY = feet.getY();
		int centerZ = feet.getZ();
		
		for(int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++)
			for(int dy = -SCAN_RADIUS; dy <= SCAN_RADIUS; dy++)
				for(int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++)
				{
					int x = centerX + dx;
					int y = centerY + dy;
					int z = centerZ + dz;
					
					if(!region.contains(x, y, z))
						continue;
					
					long key = BlockPos.asLong(x, y, z);
					
					if(view.isAir(x, y, z))
					{
						if(tracked.remove(key))
							minedBlocks++;
					}else if(!tracked.contains(key)
						&& tracked.size() < MAX_TRACKED_BLOCKS)
						tracked.add(key);
				}
	}
	
	private void prune(BlockPos feet)
	{
		int limit = SCAN_RADIUS * 8;
		var iterator = tracked.iterator();
		
		while(iterator.hasNext())
		{
			long key = iterator.nextLong();
			
			if(Math.abs(BlockPos.getX(key) - feet.getX()) > limit
				|| Math.abs(BlockPos.getY(key) - feet.getY()) > limit
				|| Math.abs(BlockPos.getZ(key) - feet.getZ()) > limit)
				iterator.remove();
		}
	}
	
	public void pause()
	{
		if(!active || builder == null)
			return;
		
		builder.pause();
		paused = true;
	}
	
	/**
	 * Continues the area with a fresh batch budget.
	 */
	public void resume()
	{
		if(!active || builder == null)
			return;
		
		minedBlocks = 0L;
		paused = false;
		complete = false;
		builder.resume();
	}
	
	public void cancel()
	{
		if(builder != null)
			try
			{
				builder.onLostControl();
			}catch(Exception e)
			{
				e.printStackTrace();
			}
		
		builder = null;
		schematic = null;
		region = null;
		active = false;
		paused = false;
		complete = false;
		minedBlocks = 0L;
		ticks = 0;
		tracked.clear();
	}
	
	public boolean isActive()
	{
		return active;
	}
	
	public boolean isPaused()
	{
		return paused;
	}
	
	public boolean isComplete()
	{
		return complete;
	}
	
	public boolean isLimitReached()
	{
		return active && paused && minedBlocks >= blockLimit;
	}
	
	public long getMinedBlocks()
	{
		return minedBlocks;
	}
	
	public long getBlockLimit()
	{
		return blockLimit;
	}
	
	public void setBlockLimit(long blockLimit)
	{
		this.blockLimit = Math.max(1L, blockLimit);
	}
	
	public PerimeterRegion getRegion()
	{
		return region;
	}
	
	public PerimeterMiningSchematic getSchematic()
	{
		return schematic;
	}
	
	public List<String> describe()
	{
		if(!active && schematic == null)
			return List.of("no mining batch");
		
		return List.of("mined " + minedBlocks + "/" + blockLimit, paused
			? "paused" : "running", complete ? "complete" : "");
	}
}
