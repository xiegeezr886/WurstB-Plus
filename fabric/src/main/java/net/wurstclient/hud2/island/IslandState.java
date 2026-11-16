/* Adapted from OpaiBounce Island.svelte / blockStats.ts; see the bundled Opai notice. */
package net.wurstclient.hud2.island;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.List;

/** Rendering-independent notice lifetime, activity priority and block telemetry. */
public final class IslandState
{
	public enum Kind {IDLE, NOTICE, SCAFFOLD, BREAKING, CHEST}
	public enum NoticeKind {TOGGLE, SUCCESS, WARNING, INFO}
	public record Notice(String key, String title, String detail, NoticeKind kind,
		boolean enabled, long started, long deadline, float fromToggle, long appeared)
	{
		public float appearance(long now)
		{
			float t = Math.min(1, Math.max(0, (now - appeared) / 160F));
			return 1 - (1 - t) * (1 - t);
		}

		public float toggle(long now)
		{
			float t = Math.min(1, Math.max(0, (now - started) / 200F));
			float eased = enabled && t < 1 ? 1 - (float)Math.pow(2, -10 * t) : t;
			return fromToggle + ((enabled ? 1 : 0) - fromToggle) * eased;
		}
	}
	private record Sample(long at, int used) {}
	private final ArrayList<Notice> notices = new ArrayList<>(3);
	private final ArrayDeque<Sample> samples = new ArrayDeque<>();
	private int count = -1, maximum;
	private long since;

	public void post(String key, String title, String detail, NoticeKind kind,
		boolean enabled, long now)
	{
		expire(now);
		float from = enabled ? 0 : 1;
		int existing = -1;
		long appeared = now;
		for(int i = 0; i < notices.size(); i++)
			if(notices.get(i).key().equals(key))
			{
				from = notices.get(i).toggle(now);
				appeared = notices.get(i).appeared();
				existing = i;
				break;
			}
		Notice next = new Notice(key, title, detail, kind, enabled, now,
			now + (kind == NoticeKind.TOGGLE ? 800 : 3000), from, appeared);
		if(existing >= 0) notices.set(existing, next);
		else notices.add(next);
		if(notices.size() > 3)
			notices.remove(0);
	}

	public void expire(long now)
	{
		notices.removeIf(n -> n.deadline() <= now);
	}

	public List<Notice> notices()
	{
		return notices;
	}

	public Kind kind(boolean chest, boolean breaking, boolean scaffold)
	{
		return chest ? Kind.CHEST : breaking ? Kind.BREAKING
			: !notices.isEmpty() ? Kind.NOTICE : scaffold ? Kind.SCAFFOLD : Kind.IDLE;
	}

	public void blocks(int remaining, long now)
	{
		if(remaining < 0) return;
		if(count < 0) since = now;
		else if(remaining < count) samples.add(new Sample(now, count - remaining));
		count = remaining;
		maximum = Math.max(maximum, count);
	}

	public int count() { return Math.max(0, count); }
	public float progress() { return maximum == 0 ? 0 : (float)count() / maximum; }
	public float rate(long now)
	{
		while(!samples.isEmpty() && now - samples.peek().at() >= 2000)
			samples.remove();
		int used = 0;
		for(Sample sample : samples) used += sample.used();
		return used / Math.max(1F, Math.min(2F, (now - since) / 1000F));
	}

	public void resetBlocks()
	{
		count = -1;
		maximum = 0;
		samples.clear();
	}

	public void reset()
	{
		notices.clear();
		resetBlocks();
	}
}
