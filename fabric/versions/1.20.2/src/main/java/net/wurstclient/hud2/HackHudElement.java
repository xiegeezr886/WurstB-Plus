package net.wurstclient.hud2;

public final class HackHudElement
{
	private HackHudElement() {}

	public static boolean isPreviewRendering()
	{
		return false;
	}

	public static boolean isSuppressed(Object listener)
	{
		return false;
	}

	public static void clearManaged() {}
}
