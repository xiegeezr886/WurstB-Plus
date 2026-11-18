package net.wurstclient.hud2.island;

/** Lifetimes for real game activity. Menu identity is deliberately not its recycled numeric ID. */
public final class IslandActivity
{
	public record Mining(long position, String name, float progress) {}
	private Mining mining;
	private long miningUntil;
	private Object menu;
	private int rows;
	private long closedAt = -1;

	public boolean mine(long position, String name, float progress, long now)
	{
		if(!Float.isFinite(progress) || name == null || name.isBlank()) return false;
		progress = Math.max(0, Math.min(1, progress));
		boolean changed = mining == null || mining.position() != position
			|| now >= miningUntil || progress < mining.progress();
		mining = new Mining(position, name, progress);
		// A short lease bridges the vanilla stop call between automatic mining ticks.
		miningUntil = now + (progress == 1 ? 450 : 150);
		return changed;
	}

	public Mining mining(long now)
	{
		return now < miningUntil ? mining : null;
	}

	/** Returns true only on a newly opened menu, so its initial contents never flash. */
	public boolean container(Object next, int nextRows, long now)
	{
		if(next != null && nextRows >= 1 && nextRows <= 6)
		{
			boolean changed = next != menu || closedAt >= 0;
			menu = next;
			rows = nextRows;
			closedAt = -1;
			return changed;
		}
		if(menu != null && closedAt < 0) closedAt = now;
		if(closedAt >= 0 && now - closedAt >= 450)
		{
			menu = null;
			rows = 0;
		}
		return false;
	}

	public int rows(long now)
	{
		return menu != null && (closedAt < 0 || now - closedAt < 450) ? rows : 0;
	}

	public void reset()
	{
		mining = null;
		miningUntil = 0;
		menu = null;
		rows = 0;
		closedAt = -1;
	}
}
