/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hud;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.wurstclient.WurstClient;
import net.wurstclient.clickgui2.FlatRenderer;
import net.wurstclient.util.RenderUtils;

/**
 * 把 {@link RadialMenuState} 的纯几何画到屏幕上。
 *
 * <p>
 * 只使用 {@link FlatRenderer} 的公开圆角矩形接口：圆盘、每一片、以及缝隙都用
 * 一层层很薄的圆角矩形叠出来，所以扇区边缘是**圆角**的，而不是尖角楔形。这样
 * 无需自己碰缓冲区和着色器，也不必改动 {@code clickgui2} 里的包私有绘制器。
 *
 * <p>
 * 全部尺寸都由屏幕尺寸推导：外半径由调用方按可用高度与宽度算好传进来，这里不再
 * 写死任何像素值。角度约定与 {@link RadialMenuState} 一致：0° 朝上，顺时针为正。
 */
public final class RadialMenuRenderer
{
	/** 圆盘底色的不透明度。 */
	private static final float DISC_OPACITY = 0.78F;
	
	/** 扇区之间的深色缝隙，用来把相邻扇区分开。 */
	private static final int GAP_COLOR = 0xB306090D;
	
	/** 未被指向的扇区颜色。 */
	private static final int SLICE_COLOR = 0xE6121722;
	
	/** 文字颜色（ARGB 的 alpha 由动画统一衰减）。 */
	private static final int LABEL_COLOR = 0xFFEFF3F8;
	private static final int HINT_COLOR = 0xFF9AA4B2;
	
	private RadialMenuRenderer()
	{
	}
	
	/** 指针相对圆心的坐标，同时用于命中判定和绘制。 */
	public static double[] pointerOffset(double mouseX, double mouseY,
		int centreX, int centreY)
	{
		return new double[]{mouseX - centreX, mouseY - centreY};
	}
	
	/**
	 * 画出整个圆盘。progress 为 0 时什么都不画。
	 *
	 * @param outerRadius 完全展开时的外半径（像素），由调用方按屏幕尺寸算好
	 */
	public static void draw(GuiGraphics context, Font font,
		RadialMenuState state, int centreX, int centreY, float outerRadius,
		float progress)
	{
		float eased = Math.max(0F, Math.min(1F, progress));
		
		if(eased <= 0.001F)
			return;
		
		float outer = outerRadius * eased;
		float inner = outer * RadialMenuState.DEAD_ZONE_RATIO;
		
		if(outer < 8F)
			return;
		
		int discColor = fade(0xB006090D, DISC_OPACITY * eased);
		int corner = Math.max(6, Math.round(outer * 0.22F));
		
		// 底色圆盘：一个整圆的实心圆角矩形
		disc(context, centreX, centreY, outer, corner, discColor);
		
		int accent = withAlpha(
			RenderUtils.toIntColor(
				WurstClient.INSTANCE.getGui().getAcColor(), 1F),
			Math.round(230 * eased));
		
		int slices = state.sliceCount();
		int selected = state.selected();
		
		// 缝隙先画，随后扇区会盖住除缝隙以外的部分
		for(int i = 0; i < slices; i++)
		{
			float gapStart = state.sliceStart(i) + state.sliceSweep();
			float gapEnd = state.sliceStart(i + 1);
			drawRoundedWedge(context, centreX, centreY, inner, outer,
				gapStart, gapEnd, fade(GAP_COLOR, eased));
		}
		
		// 先画普通扇区，选中的最后画，保证高亮不被压住
		for(int i = 0; i < slices; i++)
			if(i != selected)
				drawSlice(context, state, i, centreX, centreY, inner, outer,
					SLICE_COLOR, eased);
		
		if(selected >= 0)
			drawSlice(context, state, selected, centreX, centreY, inner, outer,
				accent, eased);
		
		// 圆盘的圆角外轮廓，让它看起来是一个整块面板
		FlatRenderer.drawRoundedOutline(context, centreX - Math.round(outer),
			centreY - Math.round(outer), centreX + Math.round(outer),
			centreY + Math.round(outer), corner, fade(0x33FFFFFF, eased));
		
		drawLabels(context, font, state, centreX, centreY, inner, outer, eased);
	}
	
	private static void drawSlice(GuiGraphics context, RadialMenuState state,
		int index, int centreX, int centreY, float inner, float outer,
		int fill, float eased)
	{
		boolean pointed = index == state.selected();
		float start = state.sliceStart(index);
		float sweep = state.sliceSweep();
		
		// 被指向的扇区用主题强调色，其余用暗底色
		drawRoundedWedge(context, centreX, centreY, inner, outer, start,
			start + sweep, pointed ? fill : fade(fill, eased));
		
		if(!pointed)
			return;
		
		// 高亮再叠一层细边框，边缘更清楚
		FlatRenderer.drawRoundedOutline(context,
			Math.round(centreX - outer), Math.round(centreY - outer),
			Math.round(centreX + outer), Math.round(centreY + outer),
			Math.max(6, Math.round(outer * 0.22F)),
			fade(0x66FFFFFF, eased));
	}
	
	/**
	 * 用一排很薄的圆角矩形拼出一片扇区：每一层横向从内半径延伸到外半径，
	 * 于是左右两端自然带 {@code radius} 的圆角，扇区看起来是圆润的。
	 */
	private static void drawRoundedWedge(GuiGraphics context, int centreX,
		int centreY, float inner, float outer, float startDegrees,
		float endDegrees, int color)
	{
		if(color >>> 24 == 0 || endDegrees <= startDegrees || outer <= inner)
			return;
		
		int radius = Math.max(2, Math.round((outer - inner) * 0.28F));
		float step = Math.max(1F, (outer - inner) / 12F);
		
		float y = centreY - outer;
		float bottom = centreY + outer;
		
		while(y < bottom)
		{
			float next = Math.min(y + step, bottom);
			float topHalf = quantise(y + 0.5F, centreY);
			float bottomHalf = quantise(next + 0.5F, centreY);
			
			if(bottomHalf > topHalf)
				drawWedgeBand(context, centreX, centreY, inner, outer,
					startDegrees, endDegrees, topHalf, bottomHalf, radius,
					color);
			
			y = next;
		}
	}
	
	private static void drawWedgeBand(GuiGraphics context, int centreX,
		int centreY, float inner, float outer, float startDegrees,
		float endDegrees, float topHalf, float bottomHalf, int radius,
		int color)
	{
		float span = bottomHalf - topHalf;
		
		if(span <= 0F)
			return;
		
		int radiusForBand = Math.min(radius, Math.max(1, Math.round(span)));
		float minX = Float.MAX_VALUE;
		float maxX = -Float.MAX_VALUE;
		
		for(float cursor = topHalf; cursor < bottomHalf; cursor += 0.5F)
		{
			float sample = Math.max(topHalf,
				Math.min(cursor, bottomHalf - 0.0001F));
			
			float left = edge(centreX, centreY, outer, startDegrees, sample);
			float right = edge(centreX, centreY, outer, endDegrees, sample);
			
			// 两角恰好跨越 0°/360° 接缝时两边会退化成同一条射线，此时不算错误
			if(right < left)
				return;
			
			minX = Math.min(minX, left);
			maxX = Math.max(maxX, right);
		}
		
		if(maxX < minX)
			return;
		
		if(maxX - minX < 0.5F)
		{
			// 这一层窄到不足半个像素：画一个圆点，接缝处才不会断
			FlatRenderer.fillRoundedRect(context, Math.round(centreX) - 1,
				Math.round(topHalf), Math.round(centreX) + 1,
				Math.round(bottomHalf), 1, color);
			return;
		}
		
		FlatRenderer.fillRoundedRect(context, Math.round(minX),
			Math.round(topHalf), Math.round(maxX), Math.round(bottomHalf),
			radiusForBand, color);
	}
	
	/**
	 * 扇区在某一行上的水平边界。取该行上与射线的交点中被外圆截断后**离圆心最远**
	 * 的那个：外圆以内它就在外圆上，外圆以外取外圆交点。于是扇区的外缘跟着圆弧
	 * 走，而不是变成直边。
	 */
	private static float edge(float centreX, float centreY, float outer,
		float degrees, float halfY)
	{
		double angle = Math.toRadians(degrees);
		double sin = Math.abs(Math.sin(angle));
		double cos = Math.cos(angle);
		double dy = Math.abs(halfY);
		double distance = outer;
		
		if(sin > 1.0E-6D)
		{
			double t = dy / sin;
			distance = Math.min(t, outer / sin);
		}
		
		return centreX + (float)(cos * distance);
	}
	
	/** 整圆实心圆角矩形。 */
	private static void disc(GuiGraphics context, int centreX, int centreY,
		float radius, int corner, int color)
	{
		int r = Math.round(radius);
		FlatRenderer.fillRoundedRect(context, centreX - r, centreY - r,
			centreX + r, centreY + r, corner, color);
	}
	
	private static void drawLabels(GuiGraphics context, Font font,
		RadialMenuState state, int centreX, int centreY, float inner,
		float outer, float eased)
	{
		int slices = state.sliceCount();
		int textColor = fade(LABEL_COLOR, eased);
		
		for(int i = 0; i < slices; i++)
		{
			String label = state.items().get(i);
			int textWidth = font.width(label);
			double centre = Math.toRadians(state.sliceCentre(i));
			float distance = (inner + outer) * 0.5F;
			int x = centreX + (int)Math.round(Math.sin(centre) * distance)
				- textWidth / 2;
			int y = centreY - (int)Math.round(Math.cos(centre) * distance)
				- font.lineHeight / 2;
			
			context.drawString(font, label, x, y, textColor, false);
		}
		
		int selected = state.selected();
		String hint = selected >= 0 ? state.items().get(selected) : "\u677E\u5F00\u53D6\u6D88";
		int hintColor = fade(selected >= 0 ? LABEL_COLOR : HINT_COLOR, eased);
		
		context.drawString(font, hint, centreX - font.width(hint) / 2,
			centreY - font.lineHeight / 2, hintColor, false);
	}
	
	/** 相对圆心的 y 取整数，保证相邻两层严丝合缝。 */
	private static float quantise(float absoluteY, int centreY)
	{
		return Math.round(absoluteY) - centreY;
	}
	
	private static int fade(int color, float factor)
	{
		int alpha = Math.round((color >>> 24) * Math.max(0F,
			Math.min(1F, factor)));
		return alpha << 24 | color & 0xFFFFFF;
	}
	
	private static int withAlpha(int color, int alpha)
	{
		return Math.max(0, Math.min(255, alpha)) << 24 | color & 0xFFFFFF;
	}
}
