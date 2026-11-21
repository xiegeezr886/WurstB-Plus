package net.wurstclient.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import baritone.api.BaritoneAPI;

public final class BaritoneCacheWorkers
{
	private static final List<Future<?>> workers = new ArrayList<>();
	private static ExecutorService pool;

	private BaritoneCacheWorkers() {}

	public static synchronized void submit(ExecutorService executor, Runnable task)
	{
		pool = executor;
		workers.add(executor.submit(task));
	}

	public static synchronized void close()
	{
		if(pool == null)
			return;

		// FastQuit can disconnect without a final Baritone tick. Let its world
		// provider notice the unload and submit its final cache save first.
		for(var baritone : BaritoneAPI.getProvider().getAllBaritones())
			baritone.getWorldProvider().getCurrentWorld();

		// Only the packer and periodic saver run forever. One-shot saves on
		// the same executor must be allowed to finish without interruption.
		for(Future<?> worker : workers)
			worker.cancel(true);
		workers.clear();
		pool.shutdown();
		pool = null;
	}
}
