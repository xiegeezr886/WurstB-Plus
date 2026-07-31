package net.wurstclient.clickgui2;

import net.minecraft.client.gui.GuiGraphicsExtractor;

final class RoundedRectRenderer
{
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
		graphics.fill(left + safeRadius, top, right - safeRadius,
			top + safeRadius, color);
		graphics.fill(left + safeRadius, bottom - safeRadius,
			right - safeRadius, bottom, color);

		for(int y = 0; y < safeRadius; y++)
			for(int x = 0; x < safeRadius; x++)
			{
				float coverage = circleCoverage(safeRadius, x, y, 0);
				fillCorners(graphics, left, top, right, bottom, x, y,
					withCoverage(color, coverage));
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

		for(int y = 0; y < outerRadius; y++)
			for(int x = 0; x < outerRadius; x++)
			{
				float coverage = circleCoverage(outerRadius, x, y,
					Math.max(0, outerRadius - 1));
				fillCorners(graphics, left, top, right, bottom, x, y,
					withCoverage(color, coverage));
			}
	}

	private static float circleCoverage(int radius, int pixelX, int pixelY,
		int innerRadius)
	{
		int covered = 0;
		for(int sampleY = 0; sampleY < 4; sampleY++)
			for(int sampleX = 0; sampleX < 4; sampleX++)
			{
				double x = pixelX + (sampleX + 0.5) / 4.0 - radius;
				double y = pixelY + (sampleY + 0.5) / 4.0 - radius;
				double distanceSquared = x * x + y * y;
				if(distanceSquared <= radius * radius
					&& distanceSquared > innerRadius * innerRadius)
					covered++;
			}
		return covered / 16F;
	}

	private static void fillCorners(GuiGraphicsExtractor graphics, int left,
		int top, int right, int bottom, int x, int y, int color)
	{
		if(color >>> 24 == 0)
			return;
		graphics.fill(left + x, top + y, left + x + 1, top + y + 1, color);
		graphics.fill(right - x - 1, top + y, right - x, top + y + 1, color);
		graphics.fill(left + x, bottom - y - 1, left + x + 1, bottom - y, color);
		graphics.fill(right - x - 1, bottom - y - 1, right - x, bottom - y, color);
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
}
