package net.wurstclient.gui;

import net.wurstclient.hud2.HUDMod;
import net.wurstclient.hud2.nanovg.NanoVGUI;

/** Shared translucent button shell, using the Dynamic Island corner rule. */
public final class IslandButtonStyle
{
	private IslandButtonStyle() {}

	public static void draw(float x, float y, float width, float height,
		boolean enabled, float hover, boolean selected, int accent)
	{
		if(width <= 0 || height <= 0) return;
		float amount = enabled ? Math.max(0, Math.min(1, hover)) : 0;
		float radius = HUDMod.backgroundRadius(width, height);
		int alpha = enabled ? 168 + Math.round(28 * amount) : 100;
		NanoVGUI.fillRounded(x, y, x + width, y + height, radius, alpha << 24);
		int border = enabled && (selected || amount > 0)
			? (Math.round(selected ? 175 : 30 + amount * 115) << 24)
				| (accent & 0xFFFFFF)
			: (enabled ? 0x24FFFFFF : 0x14FFFFFF);
		NanoVGUI.outlineRounded(x, y, x + width, y + height, radius, border);
	}
}
