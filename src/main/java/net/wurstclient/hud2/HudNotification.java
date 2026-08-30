package net.wurstclient.hud2;

/**
 * A single HUD notification. Ported from the SuperSoftClient Compose
 * {@code Notify} model: each notification carries its own lifetime
 * ({@code durationMillis}, where {@link #PERSISTENT} means it never expires
 * on its own) and an optional {@link NotificationContent} for custom
 * rendering.
 */
public final class HudNotification
{
	public static final long DEFAULT_DURATION = 2500;
	public static final long PERSISTENT = -1;

	private final String title;
	private final String message;
	private final NotificationSeverity severity;
	private final long durationMillis;
	private final NotificationContent content;
	private final long createdAt;

	public HudNotification(String title, String message,
		NotificationSeverity severity)
	{
		this(title, message, severity, DEFAULT_DURATION, null);
	}

	public HudNotification(String title, String message,
		NotificationSeverity severity, long durationMillis)
	{
		this(title, message, severity, durationMillis, null);
	}

	public HudNotification(String title, String message,
		NotificationSeverity severity, long durationMillis,
		NotificationContent content)
	{
		this.title = title;
		this.message = message;
		this.severity = severity;
		this.durationMillis = durationMillis;
		this.content = content;
		this.createdAt = System.currentTimeMillis();
	}

	public String getTitle()
	{
		return title;
	}

	public String getMessage()
	{
		return message;
	}

	public NotificationSeverity getSeverity()
	{
		return severity;
	}

	public long getDurationMillis()
	{
		return durationMillis;
	}

	public NotificationContent getContent()
	{
		return content;
	}

	public long getCreatedAt()
	{
		return createdAt;
	}

	public boolean isPersistent()
	{
		return durationMillis < 0;
	}

	public boolean isExpired()
	{
		return !isPersistent()
			&& System.currentTimeMillis() - createdAt >= durationMillis;
	}

	public float getLifetimeProgress()
	{
		return lifetimeProgress(createdAt, System.currentTimeMillis(),
			durationMillis);
	}

	public static float lifetimeProgress(long createdAt, long now,
		long durationMillis)
	{
		if(durationMillis < 0)
			return 0;
		return Math.max(0, Math.min(1,
			(float)(now - createdAt) / durationMillis));
	}

	@Override
	public boolean equals(Object obj)
	{
		if(!(obj instanceof HudNotification other))
			return false;
		return title.equals(other.title) && message.equals(other.message)
			&& severity == other.severity;
	}

	@Override
	public int hashCode()
	{
		return title.hashCode() ^ message.hashCode() ^ severity.hashCode();
	}

	@Override
	public String toString()
	{
		return "[" + severity + "] " + title + ": " + message;
	}
}
