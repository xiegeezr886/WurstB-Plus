package net.wurstclient.hud2;

/**
 * Compatibility shim for the old hack-based HUD. The new NanoVG HUD owns its
 * modules directly; the old hacks still call the preview guard, which is now a
 * no-op.
 */
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
