package net.wurstclient.clickgui2;

import net.minecraft.client.gui.GuiGraphics;
import net.wurstclient.clickgui2.theme.FlatTheme;
import net.wurstclient.util.RenderUtils;

public final class FlatRenderer
{
	private FlatRenderer()
	{
	}

	public static void drawBackdrop(GuiGraphics context, int width, int height,
		FlatTheme theme)
	{
		context.fill(0, 0, width, height, 0x18000000);
	}

	public static void drawWindowPanel(GuiGraphics context, int x1, int y1,
		int x2, int y2, int radius, FlatTheme theme, boolean focused)
	{
		fillRoundedRect(context, x1 - 2, y1 + 2, x2 + 2, y2 + 3,
			Math.min(3, radius), 0x48000000);
		fillRoundedRect(context, x1, y1, x2, y2, Math.min(2, radius),
			theme.windowFill(focused));
		drawRoundedOutline(context, x1, y1, x2, y2, Math.min(2, radius),
			theme.border(focused));
	}

	public static void drawPopup(GuiGraphics context, int x1, int y1, int x2,
		int y2, int radius, FlatTheme theme)
	{
		fillRoundedRect(context, x1 - 2, y1 + 2, x2 + 2, y2 + 3, 2,
			0x50000000);
		fillRoundedRect(context, x1, y1, x2, y2, 2, theme.popupFill());
		drawRoundedOutline(context, x1, y1, x2, y2, 2, theme.border(true));
	}

	public static void drawControl(GuiGraphics context, int x1, int y1,
		int x2, int y2, int radius, FlatTheme theme, float hover,
		boolean active)
	{
		fillRoundedRect(context, x1, y1, x2, y2, Math.min(2, radius),
			theme.controlFill(hover, active));
	}

	public static void drawSliderTrack(GuiGraphics context, int x1, int y1,
		int x2, int y2, float percentage, FlatTheme theme, float hover)
	{
		fillRoundedRect(context, x1, y1, x2, y2, 2, theme.railFill());
		int progressX = x1 + Math.round((x2 - x1) * Math.max(0,
			Math.min(1, percentage)));
		if(progressX > x1)
			fillRoundedRect(context, x1, y1, progressX, y2, 2,
				theme.progressFill(hover));
	}

	public static void drawPanel(GuiGraphics context, int x1, int y1, int x2,
		int y2, int radius, int fillColor, int borderColor)
	{
		fillRoundedRect(context, x1 - 2, y1 + 2, x2 + 2, y2 + 3, 2,
			0x50000000);
		fillRoundedRect(context, x1, y1, x2, y2, Math.min(2, radius),
			borderColor);
		fillRoundedRect(context, x1 + 1, y1 + 1, x2 - 1, y2 - 1,
			1, fillColor);
	}

	public static void fillRoundedRect(GuiGraphics context, int x1, int y1,
		int x2, int y2, int radius, int color)
	{
		RoundedRectRenderer.fill(context, x1, y1, x2, y2, radius, color);
	}

	public static void drawRoundedOutline(GuiGraphics context, int x1, int y1,
		int x2, int y2, int radius, int color)
	{
		RoundedRectRenderer.outline(context, x1, y1, x2, y2, radius, color);
	}

	/**
	 * 每角独立半径的填充。
	 *
	 * @param radii
	 *            按 CSS {@code border-radius} 的顺序：{@code {左上, 右上, 右下,
	 *            左下}}；{@code null} 表示四角皆方。
	 */
	public static void fillRoundedRectCorners(GuiGraphics context, float x1,
		float y1, float x2, float y2, float[] radii, int color)
	{
		RoundedRectRenderer.fillCorners(context, x1, y1, x2, y2, radii, color);
	}

	/**
	 * 每角独立半径、且从上到下渐变的填充。渐变由顶点色插值完成，所以在圆角上
	 * 也成立，可以替代 Skia 的「圆角 + 渐变」填充。
	 */
	public static void fillRoundedRectCornersGradient(GuiGraphics context,
		float x1, float y1, float x2, float y2, float[] radii, int topColor,
		int bottomColor)
	{
		RoundedRectRenderer.fillCornersVerticalGradient(context, x1, y1, x2,
			y2, radii, topColor, bottomColor);
	}

	/**
	 * 单一圆角、且沿 x 从左到右渐变的填充。参考实现的播放进度条用的是
	 * {@code linear-gradient(90deg, accent, #0d9488)}，原版只有纵向渐变，所以
	 * 顶点色得自己插。
	 */
	public static void fillRoundedRectHorizontalGradient(GuiGraphics context,
		float x1, float y1, float x2, float y2, float radius, int leftColor,
		int rightColor)
	{
		fillRoundedRectCornersHorizontalGradient(context, x1, y1, x2, y2,
			new float[]{radius, radius, radius, radius}, leftColor,
			rightColor);
	}

	/**
	 * 每角独立半径、且沿 x 渐变的填充。渐变由顶点色插值完成，所以在圆角上
	 * 同样成立，可以替代 Skia 的「圆角 + 横向渐变」填充。
	 */
	public static void fillRoundedRectCornersHorizontalGradient(
		GuiGraphics context, float x1, float y1, float x2, float y2,
		float[] radii, int leftColor, int rightColor)
	{
		RoundedRectRenderer.fillCornersHorizontalGradient(context, x1, y1, x2,
			y2, radii, leftColor, rightColor);
	}

	public static int mixColor(float[] base, float[] accent, float weight,
		float opacity)
	{
		float inverseWeight = 1 - weight;
		float[] mixed = {base[0] * inverseWeight + accent[0] * weight,
			base[1] * inverseWeight + accent[1] * weight,
			base[2] * inverseWeight + accent[2] * weight};
		return RenderUtils.toIntColor(mixed, opacity);
	}

	@FunctionalInterface
	public interface GradientColorFn
	{
		int colorAt(float x);
	}

	public static void drawGradientOutline(GuiGraphics context, int x1, int y1,
		int x2, int y2, int radius, GradientColorFn colorFn)
	{
		RoundedRectRenderer.outlineGradient(context, x1, y1, x2, y2, radius,
			colorFn);
	}
}
