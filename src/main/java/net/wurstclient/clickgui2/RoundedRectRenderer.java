package net.wurstclient.clickgui2;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.gui.GuiGraphicsExtractor;

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

		graphics.fill(left, top + safeRadius, right, bottom - safeRadius, color);

		CornerProfile profile = getProfile(safeRadius);
		for(int y = 0; y < safeRadius; y++)
		{
			int inset = profile.insets[y];
			int edgeColor = withCoverage(color, profile.coverages[y]);
			fillRoundedRow(graphics, left, top + y, right, inset, color,
				edgeColor);
			fillRoundedRow(graphics, left, bottom - y - 1, right, inset,
				color, edgeColor);
		}
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

		graphics.fill(left + outerRadius, top, right - outerRadius,
			top + 1, color);
		graphics.fill(left + outerRadius, bottom - 1,
			right - outerRadius, bottom, color);
		graphics.fill(left, top + outerRadius, left + 1,
			bottom - outerRadius, color);
		graphics.fill(right - 1, top + outerRadius, right,
			bottom - outerRadius, color);

		CornerProfile profile = getProfile(outerRadius);
		for(int y = 0; y < outerRadius; y++)
		{
			int inset = profile.insets[y];
			int edgeColor = withCoverage(color, profile.coverages[y]);
			fillOutlineRow(graphics, left, top + y, right, inset,
				profile.innerInsets[y], color, edgeColor);
			fillOutlineRow(graphics, left, bottom - y - 1, right, inset,
				profile.innerInsets[y], color, edgeColor);
		}
	}

	private static CornerProfile getProfile(int radius)
	{
		return CORNER_PROFILES.computeIfAbsent(radius, CornerProfile::new);
	}

	private static void fillRoundedRow(GuiGraphicsExtractor graphics, int left,
		int y, int right, int inset, int color, int edgeColor)
	{
		int start = left + inset;
		int end = right - inset;
		if(start + 1 < end - 1)
			graphics.fill(start + 1, y, end - 1, y + 1, color);
		if(edgeColor >>> 24 == 0 || start >= end)
			return;
		graphics.fill(start, y, start + 1, y + 1, edgeColor);
		if(end - 1 != start)
			graphics.fill(end - 1, y, end, y + 1, edgeColor);
	}

	private static void fillOutlineRow(GuiGraphicsExtractor graphics, int left,
		int y, int right, int outerInset, int innerInset, int color,
		int edgeColor)
	{
		int leftX = left + outerInset;
		int rightX = right - outerInset - 1;
		int leftInner = Math.min(rightX + 1, left + innerInset);
		int rightInner = Math.max(leftX, right - innerInset);
		if(leftX + 1 < leftInner)
			graphics.fill(leftX + 1, y, leftInner, y + 1, color);
		if(rightInner < rightX)
			graphics.fill(rightInner, y, rightX, y + 1, color);
		if(edgeColor >>> 24 == 0)
			return;
		graphics.fill(leftX, y, leftX + 1, y + 1, edgeColor);
		if(rightX != leftX)
			graphics.fill(rightX, y, rightX + 1, y + 1, edgeColor);
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
