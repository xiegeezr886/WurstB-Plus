package net.wurstclient.util;

public final class HitboxExpansionPolicy
{
	private HitboxExpansionPolicy() {}

	public static boolean shouldExpand(boolean clientSide, boolean living,
		boolean localPlayer, float extra)
	{
		return clientSide && living && !localPlayer && extra > 0;
	}
}
