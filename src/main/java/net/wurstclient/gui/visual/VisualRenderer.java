package net.wurstclient.gui.visual;

import net.minecraft.client.gui.GuiGraphics;
import net.wurstclient.clickgui2.FlatRenderer;

public final class VisualRenderer
{
	private VisualRenderer()
	{}

	public static void backdrop(GuiGraphics graphics, int width, int height)
	{
		graphics.fill(0, 0, width, height, VisualTheme.OVERLAY);
	}

	public static void gridBackground(GuiGraphics graphics, int width,
		int height)
	{
		graphics.fill(0, 0, width, height, VisualTheme.BACKGROUND);
		int grid = VisualTheme.withAlpha(VisualTheme.GRID, 0.22F);
		for(int x = 0; x < width; x += 32)
			graphics.fill(x, 0, x + 1, height, grid);
		for(int y = 0; y < height; y += 32)
			graphics.fill(0, y, width, y + 1, grid);
		graphics.fill(0, 0, width, 2, VisualTheme.ACCENT);
	}

	public static void panel(GuiGraphics graphics, int left, int top,
		int right, int bottom, int radius, boolean focused)
	{
		FlatRenderer.fillRoundedRect(graphics, left - 2, top + 3, right + 2,
			bottom + 4, radius + 2, VisualTheme.SHADOW);
		FlatRenderer.fillRoundedRect(graphics, left, top, right, bottom, radius,
			VisualTheme.PANEL);
		FlatRenderer.drawRoundedOutline(graphics, left, top, right, bottom,
			radius, focused ? VisualTheme.ACCENT : VisualTheme.BORDER);
	}

	public static void button(GuiGraphics graphics, int left, int top,
		int right, int bottom, int radius, float hover, boolean active,
		boolean dangerous)
	{
		int accent = dangerous ? VisualTheme.ERROR : VisualTheme.ACCENT;
		int idle = active ? VisualTheme.ACCENT_SUBTLE_STRONG
			: VisualTheme.CONTROL;
		int fill = VisualTheme.mix(idle,
			active ? accent : VisualTheme.CONTROL_HOVER, hover);
		FlatRenderer.fillRoundedRect(graphics, left, top, right, bottom, radius,
			fill);
		FlatRenderer.drawRoundedOutline(graphics, left, top, right, bottom,
			radius, active ? accent : VisualTheme.mix(VisualTheme.BORDER,
				VisualTheme.BORDER_STRONG, hover));
	}

	public static void input(GuiGraphics graphics, int left, int top,
		int right, int bottom, float focus)
	{
		FlatRenderer.fillRoundedRect(graphics, left, top, right, bottom,
			VisualTheme.RADIUS_SMALL, VisualTheme.SURFACE_36);
		FlatRenderer.drawRoundedOutline(graphics, left, top, right, bottom,
			VisualTheme.RADIUS_SMALL, VisualTheme.mix(VisualTheme.BORDER,
				VisualTheme.ACCENT, focus));
	}

	public static void progress(GuiGraphics graphics, int left, int right,
		int y, float progress, int color)
	{
		FlatRenderer.fillRoundedRect(graphics, left, y, right, y + 2, 1,
			VisualTheme.BORDER);
		int valueX = left + Math.round((right - left)
			* Math.max(0, Math.min(1, progress)));
		if(valueX > left)
			FlatRenderer.fillRoundedRect(graphics, left, y, valueX, y + 2, 1,
				color);
	}
}
