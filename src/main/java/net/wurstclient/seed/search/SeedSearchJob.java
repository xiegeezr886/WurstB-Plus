/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.seed.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Runs a {@link SeedSearch} on a background thread so that the game keeps
 * ticking, and lets the command report progress by polling.
 */
public final class SeedSearchJob
{
	private static SeedSearchJob current;
	
	private final long from;
	private final long to;
	private final List<SeedSearch.Target> targets;
	private final int threads;
	private final int maxResults;
	
	private final AtomicLong progress = new AtomicLong();
	private final AtomicBoolean cancelled = new AtomicBoolean();
	private volatile List<Long> results = Collections.emptyList();
	private volatile boolean finished;
	private Thread thread;
	
	private SeedSearchJob(long from, long to, List<SeedSearch.Target> targets,
		int threads, int maxResults)
	{
		this.from = from;
		this.to = to;
		this.targets = targets;
		this.threads = threads;
		this.maxResults = maxResults;
	}
	
	/**
	 * Starts a search and remembers it as the current one. Any previous search
	 * is cancelled first.
	 */
	public static SeedSearchJob start(long from, long to,
		List<SeedSearch.Target> targets, int threads, int maxResults)
	{
		cancelCurrent();
		
		SeedSearchJob job = new SeedSearchJob(from, to, targets, threads,
			maxResults);
		current = job;
		job.run();
		return job;
	}
	
	public static SeedSearchJob current()
	{
		return current;
	}
	
	public static boolean isRunning()
	{
		SeedSearchJob job = current;
		return job != null && !job.finished;
	}
	
	public static void cancelCurrent()
	{
		SeedSearchJob job = current;
		
		if(job != null)
			job.cancel();
	}
	
	private void run()
	{
		thread = new Thread(() -> {
			try
			{
				results = SeedSearch.search(from, to, targets, threads,
					maxResults, progress, cancelled::get);
			}catch(Exception e)
			{
				e.printStackTrace();
			}finally
			{
				finished = true;
			}
		}, "Wurst seed search job");
		
		thread.setDaemon(true);
		thread.start();
	}
	
	public void cancel()
	{
		cancelled.set(true);
	}
	
	public long from()
	{
		return from;
	}
	
	public long to()
	{
		return to;
	}
	
	public long span()
	{
		return to - from + 1;
	}
	
	public long progress()
	{
		return Math.min(progress.get(), span());
	}
	
	public int percent()
	{
		long span = span();
		
		if(span <= 0)
			return 100;
		
		return (int)Math.min(100, progress() * 100 / span);
	}
	
	public boolean isFinished()
	{
		return finished;
	}
	
	public boolean isCancelled()
	{
		return cancelled.get();
	}
	
	public List<Long> results()
	{
		return new ArrayList<>(results);
	}
	
	public int targetCount()
	{
		return targets.size();
	}
	
	public String describeProgress()
	{
		return percent() + "% (" + progress() + "/" + span() + " seeds, "
			+ targets.size() + " observations)";
	}
	
	public String describe()
	{
		if(!finished)
			return "searching " + describeProgress();
		
		if(isCancelled())
			return "cancelled after " + progress() + " seeds";
		
		List<Long> found = results();
		
		if(found.isEmpty())
			return "no seed found in " + span() + " candidates";
		
		String suffix = found.size() >= maxResults
			? " (result limit reached, the range was not searched completely)"
			: "";
		
		return "found " + found.size() + " seed(s) in " + span()
			+ " candidates" + suffix;
	}
}
