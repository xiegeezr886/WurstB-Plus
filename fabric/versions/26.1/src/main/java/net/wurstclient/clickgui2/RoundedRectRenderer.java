package net.wurstclient.clickgui2;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.wurstclient.util.RenderUtils;

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
	private static final Map<Integer, CornerProfile> CORNER_PROFILES =
		new ConcurrentHashMap<>();

	private RoundedRectRenderer()
	{
	}

	public static void fill(GuiGraphicsExtractor graphics, float x1, float y1,
		float x2, float y2, float radius, int color)
	{
		int left = Math.round(x1);
		int top = Math.round(y1);
		int right = Math.round(x2);
		int bottom = Math.round(y2);
		if(right <= left || bottom <= top || color >>> 24 == 0)
			return;

		int safeRadius = clampRadius(left, top, right, bottom,
			Math.round(radius));
		if(safeRadius == 0)
		{
			graphics.fill(left, top, right, bottom, color);
			return;
		}

		// Worst case: 1 body quad + 2 rows * 2 corners * 3 segments per
		// radius unit.
		int quadCount = 1 + safeRadius * 12;
		float[][] mesh = new float[quadCount * 4][2];
		int[] quadColors = new int[quadCount];
		int quad = 0;

		// body
		quadColors[quad] = color;
		addQuad(mesh, quad++, left, top + safeRadius, left, bottom - safeRadius,
			right, bottom - safeRadius, right, top + safeRadius);

		CornerProfile profile = getProfile(safeRadius);
		for(int y = 0; y < safeRadius; y++)
		{
			int inset = profile.insets[y];
			int edgeColor = withCoverage(color, profile.coverages[y]);
			addFillRow(mesh, quadColors, quad, left, top + y, right, inset,
				color, edgeColor);
			quad += 3;
			addFillRow(mesh, quadColors, quad, left, bottom - y - 1, right,
				inset, color, edgeColor);
			quad += 3;
		}

		RenderUtils.submitQuadMesh2D(graphics, mesh, quadColors);
	}

	public static void outline(GuiGraphicsExtractor graphics, float x1,
		float y1, float x2, float y2, float radius, int color)
	{
		int left = Math.round(x1);
		int top = Math.round(y1);
		int right = Math.round(x2);
		int bottom = Math.round(y2);
		if(right <= left || bottom <= top || color >>> 24 == 0)
			return;

		int outerRadius = clampRadius(left, top, right, bottom,
			Math.round(radius));
		if(outerRadius == 0)
		{
			graphics.fill(left, top, right, top + 1, color);
			graphics.fill(left, bottom - 1, right, bottom, color);
			graphics.fill(left, top + 1, left + 1, bottom - 1, color);
			graphics.fill(right - 1, top + 1, right, bottom - 1, color);
			return;
		}

		// Worst case: 4 straight edges + 2 rows * 2 corners * 4 segments
		// per radius unit.
		int quadCount = 4 + outerRadius * 16;
		float[][] mesh = new float[quadCount * 4][2];
		int[] quadColors = new int[quadCount];
		int quad = 0;

		// straight edges
		quadColors[quad] = color;
		addQuad(mesh, quad++, left + outerRadius, top, left + outerRadius,
			top + 1, right - outerRadius, top + 1, right - outerRadius, top);
		quadColors[quad] = color;
		addQuad(mesh, quad++, left + outerRadius, bottom - 1,
			left + outerRadius, bottom, right - outerRadius, bottom,
			right - outerRadius, bottom - 1);
		quadColors[quad] = color;
		addQuad(mesh, quad++, left, top + outerRadius, left,
			bottom - outerRadius, left + 1, bottom - outerRadius, left + 1,
			top + outerRadius);
		quadColors[quad] = color;
		addQuad(mesh, quad++, right - 1, top + outerRadius, right - 1,
			bottom - outerRadius, right, bottom - outerRadius, right,
			top + outerRadius);

		CornerProfile profile = getProfile(outerRadius);
		for(int y = 0; y < outerRadius; y++)
		{
			int inset = profile.insets[y];
			int innerInset = profile.innerInsets[y];
			int edgeColor = withCoverage(color, profile.coverages[y]);
			addOutlineRow(mesh, quadColors, quad, left, top + y, right, inset,
				innerInset, color, edgeColor);
			quad += 4;
			addOutlineRow(mesh, quadColors, quad, left, bottom - y - 1, right,
				inset, innerInset, color, edgeColor);
			quad += 4;
		}

		RenderUtils.submitQuadMesh2D(graphics, mesh, quadColors);
	}

	private static void addFillRow(float[][] mesh, int[] quadColors, int quad,
		int left, int y, int right, int inset, int color, int edgeColor)
	{
		int start = left + inset;
		int end = right - inset;
		// Slot quad: middle segment. Unused slots keep alpha 0 and are
		// invisible (the vertex array defaults to 0,0).
		if(start + 1 < end - 1)
		{
			quadColors[quad] = color;
			addQuad(mesh, quad, start + 1, y, start + 1, y + 1, end - 1, y + 1,
				end - 1, y);
		}
		if(edgeColor >>> 24 == 0 || start >= end)
			return;
		// Slot quad + 1: left edge pixel.
		quadColors[quad + 1] = edgeColor;
		addQuad(mesh, quad + 1, start, y, start, y + 1, start + 1, y + 1,
			start + 1, y);
		// Slot quad + 2: right edge pixel.
		if(end - 1 != start)
		{
			quadColors[quad + 2] = edgeColor;
			addQuad(mesh, quad + 2, end - 1, y, end - 1, y + 1, end, y + 1,
				end, y);
		}
	}

	private static void addOutlineRow(float[][] mesh, int[] quadColors,
		int quad, int left, int y, int right, int outerInset, int innerInset,
		int color, int edgeColor)
	{
		int leftX = left + outerInset;
		int rightX = right - outerInset - 1;
		int leftInner = Math.min(rightX + 1, left + innerInset);
		int rightInner = Math.max(leftX, right - innerInset);
		// Slot quad: left inner segment; slot quad + 1: right inner segment.
		if(leftX + 1 < leftInner)
		{
			quadColors[quad] = color;
			addQuad(mesh, quad, leftX + 1, y, leftX + 1, y + 1, leftInner,
				y + 1, leftInner, y);
		}
		if(rightInner < rightX)
		{
			quadColors[quad + 1] = color;
			addQuad(mesh, quad + 1, rightInner, y, rightInner, y + 1, rightX,
				y + 1, rightX, y);
		}
		if(edgeColor >>> 24 == 0)
			return;
		// Slot quad + 2: left edge pixel; slot quad + 3: right edge pixel.
		quadColors[quad + 2] = edgeColor;
		addQuad(mesh, quad + 2, leftX, y, leftX, y + 1, leftX + 1, y + 1,
			leftX + 1, y);
		if(rightX != leftX)
		{
			quadColors[quad + 3] = edgeColor;
			addQuad(mesh, quad + 3, rightX, y, rightX, y + 1, rightX + 1,
				y + 1, rightX + 1, y);
		}
	}

	/**
	 * Writes one quad into the mesh in vanilla CCW order: left-top,
	 * left-bottom, right-bottom, right-top.
	 */
	private static void addQuad(float[][] mesh, int quad, float ltX, float ltY,
		float lbX, float lbY, float rbX, float rbY, float rtX, float rtY)
	{
		int base = quad * 4;
		mesh[base][0] = ltX;
		mesh[base][1] = ltY;
		mesh[base + 1][0] = lbX;
		mesh[base + 1][1] = lbY;
		mesh[base + 2][0] = rbX;
		mesh[base + 2][1] = rbY;
		mesh[base + 3][0] = rtX;
		mesh[base + 3][1] = rtY;
	}

	private static CornerProfile getProfile(int radius)
	{
		return CORNER_PROFILES.computeIfAbsent(radius, CornerProfile::new);
	}

	private static int withCoverage(int color, float coverage)
	{
		int alpha = color >>> 24;
		int coveredAlpha = Math.round(alpha * Math.max(0, Math.min(1, coverage)));
		return coveredAlpha << 24 | color & 0xFFFFFF;
	}

	private static int clampRadius(int left, int top, int right, int bottom,
		int radius)
	{
		return Math.max(0,
			Math.min(radius, Math.min((right - left) / 2, (bottom - top) / 2)));
	}

	private static final class CornerProfile
	{
		private final int[] insets;
		private final int[] innerInsets;
		private final float[] coverages;

		private CornerProfile(int radius)
		{
			insets = new int[radius];
			innerInsets = new int[radius];
			coverages = new float[radius];
			for(int y = 0; y < radius; y++)
			{
				double deltaY = radius - y - 0.5;
				double exactInset = radius
					- Math.sqrt(radius * radius - deltaY * deltaY);
				int inset = Math.max(0, (int)Math.floor(exactInset));
				insets[y] = inset;
				coverages[y] = (float)(1 - (exactInset - inset));
				double innerRadius = radius - 1;
				innerInsets[y] = deltaY >= innerRadius ? radius
					: Math.min(radius, (int)Math.ceil(radius - Math.sqrt(
						innerRadius * innerRadius - deltaY * deltaY)));
			}
		}
	}
}
