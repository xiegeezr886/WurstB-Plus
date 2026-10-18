package net.wurstclient.util;

public final class KeepSprintPolicy
{
	private KeepSprintPolicy()
	{
	}

	public static double attackMotionMultiplier(double vanillaMultiplier,
		boolean keepSprint)
	{
		return keepSprint ? 1 : vanillaMultiplier;
	}

	public static boolean shouldApplySprintChange(boolean keepSprint,
		boolean requestedState)
	{
		return !keepSprint || requestedState;
	}

	public static boolean shouldPreserveSprint(boolean enabled,
		boolean localPlayer, boolean sprinting)
	{
		return enabled && localPlayer && sprinting;
	}
}
