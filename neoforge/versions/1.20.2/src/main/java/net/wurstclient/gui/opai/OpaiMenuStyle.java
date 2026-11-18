package net.wurstclient.gui.opai;

import net.wurstclient.hud2.nanovg.NanoVGUI;
import net.wurstclient.gui.IslandButtonStyle;

public final class OpaiMenuStyle
{
	public static final int ACCENT = 0xFFB5BEF0;
	private OpaiMenuStyle() {}

	public static void flip(double angle, double scale, float cx, float cy)
	{
		float[] m = OpaiMenuMotion.projection(angle, scale);
		NanoVGUI.context().transform(m[0], 0, m[1], m[2],
			cx - m[0] * cx - m[1] * cy, cy - m[2] * cy);
	}

	public static void button(float x, float y, float w, String label,
		boolean enabled, boolean hovered)
	{
		IslandButtonStyle.draw(x, y, w, 20, enabled, hovered ? 1 : 0, false, ACCENT);
		NanoVGUI.centeredText(NanoVGUI.plainSubstrByWidth(label, w - 8, 9),
			x + w / 2, y + (20 - NanoVGUI.height(9)) / 2,
			enabled ? 0xFFF2F2F8 : 0xFF77798C, 9);
	}

	public static void row(float x, float y, float width, float height,
		float hover, float selection)
	{
		NanoVGUI.fillRounded(x, y, x + width, y + height, 8, 0x32000000);
		int leftAlpha = Math.round(84 * selection + 36 * hover * (1 - selection * .5F));
		int rightAlpha = Math.round(24 * selection + 8 * hover);
		if(leftAlpha == 0) return;
		NanoVGUI.context().drawHorizontalGradientRoundedRect(x, y, width, height, 8,
			(leftAlpha << 24) | (ACCENT & 0xFFFFFF),
			(rightAlpha << 24) | 0xDED5FF);
	}
}
