package net.wurstclient.clickgui2.supersoft;

import net.minecraft.client.gui.GuiGraphics;
import net.wurstclient.clickgui2.FlatRenderer;
import net.wurstclient.gui.visual.VisualTheme;

public final class SuperSoftRenderer
{
	private SuperSoftRenderer()
	{}

	public static void backdrop(GuiGraphics graphics, int width, int height,
		float progress)
	{
		int alpha = Math.round(0x80 * Math.max(0, Math.min(1, progress)));
		graphics.fill(0, 0, width, height, alpha << 24);
	}

	public static void window(GuiGraphics graphics, int left, int top,
		int right, int bottom, int radius, int borderColor)
	{
		FlatRenderer.fillRoundedRect(graphics, left - 2, top + 2, right + 2,
			bottom + 5, radius + 3, 0x24000000);
		FlatRenderer.fillRoundedRect(graphics, left - 1, top + 2, right + 1,
			bottom + 4, radius + 2, 0x38000000);
		FlatRenderer.fillRoundedRect(graphics, left, top + 2, right,
			bottom + 3, radius + 1, SuperSoftTheme.SHADOW);
		FlatRenderer.fillRoundedRect(graphics, left, top, right, bottom, radius,
			SuperSoftTheme.WINDOW);
		FlatRenderer.drawRoundedOutline(graphics, left, top, right, bottom,
			radius, borderColor);
	}

	public static void header(GuiGraphics graphics, int left, int top,
		int right, int bottom, int radius, int color)
	{
		FlatRenderer.fillRoundedRect(graphics, left, top, right, bottom, radius,
			color);
		graphics.fill(left, (top + bottom) / 2, right, bottom, color);
		graphics.fill(left, bottom - 1, right, bottom,
			VisualTheme.ACCENT);
	}

	public static void row(GuiGraphics graphics, int left, int top, int right,
		int bottom, int radius, int accentColor, float hover, float enabled)
	{
		int idle = SuperSoftTheme.mix(SuperSoftTheme.ROW,
			SuperSoftTheme.SETTING_HOVER, hover * 0.45F);
		int color = SuperSoftTheme.mix(idle, accentColor, enabled);
		FlatRenderer.fillRoundedRect(graphics, left, top, right, bottom, radius,
			color);
	}

	public static void switchControl(GuiGraphics graphics, int x, int y,
		int accentColor, float progress)
	{
		int track = SuperSoftTheme.mix(0xFF40434A,
			VisualTheme.mix(VisualTheme.BACKGROUND, accentColor, 0.4F), progress);
		FlatRenderer.fillRoundedRect(graphics, x, y, x + 14, y + 8, 4, track);
		int knobX = x + 1 + Math.round(6 * progress);
		FlatRenderer.fillRoundedRect(graphics, knobX, y + 1, knobX + 6, y + 7,
			3, SuperSoftTheme.mix(VisualTheme.TEXT, accentColor, progress));
	}

	public static void settingSwitch(GuiGraphics graphics, int x, int y,
		int accentColor, float progress)
	{
		int track = SuperSoftTheme.mix(0xFF40434A,
			VisualTheme.mix(VisualTheme.BACKGROUND, accentColor, 0.4F), progress);
		FlatRenderer.fillRoundedRect(graphics, x, y, x + 24, y + 12, 6, track);
		int knobX = x + 2 + Math.round(12 * progress);
		FlatRenderer.fillRoundedRect(graphics, knobX, y + 2, knobX + 8, y + 10,
			4, SuperSoftTheme.mix(VisualTheme.TEXT, accentColor, progress));
	}

	public static void slider(GuiGraphics graphics, int left, int right, int y,
		int accentColor, float progress)
	{
		slider(graphics, left, right, y, accentColor, progress, 1);
	}

	public static void slider(GuiGraphics graphics, int left, int right, int y,
		int accentColor, float progress, float thumbScale)
	{
		FlatRenderer.fillRoundedRect(graphics, left, y - 1, right, y + 2, 2,
			VisualTheme.BORDER_STRONG);
		int valueX = left + Math.round((right - left) * progress);
		if(valueX > left)
			FlatRenderer.fillRoundedRect(graphics, left, y - 1, valueX, y + 2,
				2, accentColor);
		int radius = Math.max(4, Math.round(4 * thumbScale));
		FlatRenderer.fillRoundedRect(graphics, valueX - radius, y - radius,
			valueX + radius, y + radius, radius, accentColor);
	}
}
