/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.seed;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import net.minecraft.core.BlockPos;
import net.wurstclient.util.BaritoneUtils;

/**
 * Mines a list of predicted ore positions through Baritone's custom goal
 * process.
 *
 * <p>
 * Baritone is only ever given the closest few targets, because a
 * {@link GoalComposite} that spans a whole ore field would make the path finder
 * spend far too much time on every recalculation. Targets that turned into air
 * are treated as mined, so the job notices the progress without needing to know
 * which block the player actually broke.
 */
public final class SeedMineJob
{
	/**
	 * Read-only block lookup used to notice which targets have been mined.
	 */
	public interface BlockView
	{
		boolean isAir(BlockPos pos);
	}
	
	private static final int BATCH_SIZE = 8;
	private static final int REPATH_INTERVAL_TICKS = 40;
	
	private final List<BlockPos> pending = new ArrayList<>();
	private int minedCount;
	private int totalCount;
	private int ticks;
	private BlockPos origin;
	private BlockPos anchor;
	private boolean active;
	private boolean paused;
	private boolean complete;
	
	/**
	 * Starts a new job, sorted by distance to the given origin. Any previous
	 * job is discarded.
	 */
	public void start(Collection<BlockPos> targets, BlockPos origin)
	{
		cancel();
		
		this.origin = origin;
		anchor = origin;
		
		if(targets != null)
			for(BlockPos pos : targets)
				if(pos != null)
					pending.add(pos);
		
		sortByDistance(pending, origin);
		
		totalCount = pending.size();
		active = totalCount > 0;
		complete = !active;
		
		if(active)
			dispatch();
	}
	
	/**
	 * Counts the targets that have been mined and re-sends the goal whenever the
	 * batch changed or the periodic re-path is due. Call once per client tick
	 * while the job runs.
	 */
	public void tick(BlockPos feet, BlockView view)
	{
		if(!active || paused)
			return;
		
		if(feet != null)
			anchor = feet;
		
		boolean removed = false;
		
		if(view != null)
			for(int i = pending.size() - 1; i >= 0; i--)
			{
				BlockPos pos = pending.get(i);
				
				if(!view.isAir(pos))
					continue;
				
				pending.remove(i);
				minedCount++;
				removed = true;
			}
		
		if(pending.isEmpty())
		{
			finish();
			return;
		}
		
		ticks++;
		
		if(removed || ticks >= REPATH_INTERVAL_TICKS)
		{
			ticks = 0;
			dispatch();
		}
	}
	
	/**
	 * Cancels the current goal but keeps the progress.
	 */
	public void pause()
	{
		if(!active || paused)
			return;
		
		paused = true;
		stopBaritone();
	}
	
	/**
	 * Re-sends the next batch after a pause.
	 */
	public void resume()
	{
		if(!active || !paused)
			return;
		
		paused = false;
		ticks = 0;
		dispatch();
	}
	
	/**
	 * Discards the job and everything it has counted so far.
	 */
	public void cancel()
	{
		pending.clear();
		minedCount = 0;
		totalCount = 0;
		ticks = 0;
		origin = null;
		anchor = null;
		active = false;
		paused = false;
		complete = false;
		stopBaritone();
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
	
	public int remaining()
	{
		return pending.size();
	}
	
	public int minedCount()
	{
		return minedCount;
	}
	
	public int totalCount()
	{
		return totalCount;
	}
	
	public List<BlockPos> pending()
	{
		return new ArrayList<>(pending);
	}
	
	public String describe()
	{
		String state = paused ? " (paused)" : complete ? " (complete)" : "";
		return "seed mining: " + minedCount + "/" + totalCount + " targets, "
			+ remaining() + " left" + state;
	}
	
	private void finish()
	{
		active = false;
		paused = false;
		complete = true;
		ticks = 0;
		stopBaritone();
	}
	
	/**
	 * Sends the closest {@link #BATCH_SIZE} targets to Baritone.
	 */
	private void dispatch()
	{
		if(pending.isEmpty())
		{
			stopBaritone();
			return;
		}
		
		BlockPos reference =
			anchor != null ? anchor : origin != null ? origin : BlockPos.ZERO;
		
		ArrayList<BlockPos> batch = new ArrayList<>(pending);
		sortByDistance(batch, reference);
		
		int size = Math.min(batch.size(), BATCH_SIZE);
		Goal[] goals = new Goal[size];
		
		for(int i = 0; i < size; i++)
			goals[i] = new GoalBlock(batch.get(i));
		
		BaritoneUtils.setGoals(goals);
	}
	
	private void sortByDistance(List<BlockPos> list, BlockPos reference)
	{
		if(reference == null)
			return;
		
		list.sort(Comparator
			.comparingDouble((BlockPos pos) -> pos.distSqr(reference)));
	}
	
	private void stopBaritone()
	{
		BaritoneUtils.clearGoal();
	}
}
