package net.wurstclient.clickgui2;

import net.wurstclient.util.render.GuiGraphicsExtractor;

import net.wurstclient.hud2.nanovg.NanoVGUI;

/**
 * Renders rounded rectangles with anti-aliased corners.
 *
 * <p>Each rounded rectangle is submitted as a single quad mesh element
 * (one {@code submitQuadMesh2D} call) instead of one {@code fill} call per
 * pixel row. The vanilla GUI render state runs an O(n) intersection check
 * per element, so collapsing ~20-40 fills into one element removes a large
 * part of the per-frame GUI cost while keeping the exact same rounded,
 * coverage-blended corner appearance.</p>
 */
final class RoundedRectRenderer
{
	private RoundedRectRenderer()
	{
	}

	public static void fill(GuiGraphicsExtractor graphics, float x1, float y1,
		float x2, float y2, float radius, int color)
	{
		NanoVGUI.fillRounded(x1, y1, x2, y2, radius, color);
	}

	public static void outline(GuiGraphicsExtractor graphics, float x1,
		float y1, float x2, float y2, float radius, int color)
	{
		NanoVGUI.outlineRounded(x1, y1, x2, y2, radius, color);
	}
}
