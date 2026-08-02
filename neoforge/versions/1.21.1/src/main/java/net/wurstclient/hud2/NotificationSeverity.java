package net.wurstclient.hud2;

public enum NotificationSeverity
{
	INFO("#4677ff"),
	SUCCESS("#4dac68"),
	ERROR("#fc4130"),
	ENABLED("#4dac68"),
	DISABLED("#fc4130");

	private final String colorHex;

	NotificationSeverity(String colorHex)
	{
		this.colorHex = colorHex;
	}

	public String getColorHex()
	{
		return colorHex;
	}

	public static NotificationSeverity fromString(String name)
	{
		try
		{
			return valueOf(name.toUpperCase());
		}catch(IllegalArgumentException e)
		{
			return INFO;
		}
	}
}
