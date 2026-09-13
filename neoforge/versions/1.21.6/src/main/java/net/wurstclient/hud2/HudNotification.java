package net.wurstclient.hud2;

public final class HudNotification
{
	private final String title;
	private final String message;
	private final NotificationSeverity severity;
	private final long createdAt;

	public HudNotification(String title, String message,
		NotificationSeverity severity)
	{
		this.title = title;
		this.message = message;
		this.severity = severity;
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

	public long getCreatedAt()
	{
		return createdAt;
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
		return title.hashCode() ^ message.hashCode()
			^ severity.hashCode();
	}

	@Override
	public String toString()
	{
		return "[" + severity + "] " + title + ": " + message;
	}
}
