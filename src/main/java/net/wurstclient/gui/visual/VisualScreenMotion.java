package net.wurstclient.gui.visual;

import java.util.Set;

import net.minecraft.client.gui.screens.Screen;

/** Shared motion values for screens that do not own a transition. */
public final class VisualScreenMotion
{
	private static final Set<String> SELF_ANIMATED_SCREENS = Set.of(
		"net.wurstclient.clickgui2.component.SuperSoftClickGuiScreen",
		"net.wurstclient.clickgui2.NavigatorScreen",
		"net.wurstclient.clickgui2.screens.NeteaseLoginScreen",
		"net.wurstclient.clickgui2.screens.NeteaseMusicScreen");

	public static final int DURATION_MS = 220;
	private static final float START_SCALE = 0.965F;

	private VisualScreenMotion()
	{}

	public static boolean shouldAnimate(Screen screen)
	{
		return screen != null
			&& !SELF_ANIMATED_SCREENS.contains(screen.getClass().getName());
	}

	public static float progress(long openedAtNanos, long nowNanos)
	{
		if(openedAtNanos <= 0 || nowNanos <= openedAtNanos)
			return 0;

		float elapsed = (nowNanos - openedAtNanos)
			/ (DURATION_MS * 1_000_000F);
		return clamp01(elapsed);
	}

	public static float easedProgress(float progress)
	{
		float value = clamp01(progress);
		float inverse = 1 - value;
		return 1 - inverse * inverse * inverse;
	}

	public static float scale(float progress)
	{
		return START_SCALE + (1 - START_SCALE) * easedProgress(progress);
	}

	public static int veilColor(float progress)
	{
		float alpha = (1 - easedProgress(progress)) * 0.52F;
		return VisualTheme.withAlpha(VisualTheme.BACKGROUND, alpha);
	}

	private static float clamp01(float value)
	{
		return Math.max(0, Math.min(1, value));
	}
}
