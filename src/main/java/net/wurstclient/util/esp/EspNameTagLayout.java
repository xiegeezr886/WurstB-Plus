/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util.esp;

import java.util.ArrayList;
import java.util.List;

/**
 * 铭牌元素排版，移植自 OpenOpal 的
 * {@code visual/esp/ESPModule.java} 的 {@code calculateStartingPosition()} 与
 * {@code renderNameTagElements()}（GPL-3.0）。
 *
 * <p>
 * 参考的这两段逻辑把「量宽度」和「画」混在一起：先遍历元素累加
 * {@code 文本宽 + 图标宽 + 间隔}，由总宽算出起点使整串在碰撞箱上方居中，
 * 然后再遍历一次、边推进游标边绘制。本类只保留其中的<b>几何</b>，字形宽度
 * 由调用方通过 {@link GlyphMeasurer} 注入，因此不依赖字体、不依赖 Minecraft，
 * 可以逐字节单测。
 *
 * <p>
 * 几何口径完全照搬参考，包含 {@link #BASELINE_OFFSET} 造成的那处「文字基线在
 * 碰撞箱上方 4.5px、而背景再往上 4.5px」的偏移——参考是在 NanoVG 下写的，
 * {@code nvgText} 的 y 是<b>基线</b>而不是顶边，所以那个额外的 4.5px 是刻意
 * 抵消字形上伸部分、让文字在背景里竖直居中的，不是笔误。
 */
public final class EspNameTagLayout
{
	/** 铭牌字号，参考写死 5。 */
	public static final float FONT_SIZE = 5;

	/** 元素之间的水平间隔，参考写死 5。 */
	public static final float GAP = 5;

	/** 每个元素背景的四边内边距，参考写死 2。 */
	public static final float BG_PADDING = 2;

	/** 背景圆角，参考写死 2。 */
	public static final float BG_RADIUS = 2;

	/**
	 * 文本基线相对碰撞箱顶边的偏移。参考在 `calculateStartingPosition` 里返回
	 * {@code y - 4.5F}，在 `renderNameTagElements` 里画背景时又减了一次。
	 */
	public static final float BASELINE_OFFSET = 4.5F;

	private EspNameTagLayout()
	{
	}

	/** 量一个字形串的宽度。实现方通常转发给字体对象。 */
	@FunctionalInterface
	public interface GlyphMeasurer
	{
		float width(String glyph);
	}

	/**
	 * 一个元素排版后的位置。{@code iconX} 在元素没有图标时是
	 * {@link Float#NaN}。
	 */
	public record Placed(EspNameTagElement element, float x, float textX,
		float iconX, float textWidth, float iconWidth, float bgX, float bgY,
		float bgWidth, float bgHeight)
	{
		public boolean hasIcon()
		{
			return element.hasIcon();
		}
	}

	/**
	 * 整串铭牌的排版结果。
	 *
	 * @param totalWidth
	 *            所有元素宽度加间隔之和。
	 * @param startX
	 *            第一个元素的左边缘。
	 * @param baselineY
	 *            文本基线；Skia 的 {@code drawString} 与 NanoVG 一样取基线。
	 */
	public record Layout(float totalWidth, float startX, float baselineY,
		List<Placed> placed)
	{
		public boolean isEmpty()
		{
			return placed.isEmpty();
		}
	}

	/**
	 * @param elements
	 *            按绘制顺序排列的元素。
	 * @param centerX
	 *            碰撞箱的水平中心，整串铭牌以此居中。
	 * @param boxTopY
	 *            碰撞箱顶边。
	 * @param measurer
	 *            字形宽度来源。
	 */
	public static Layout layout(List<EspNameTagElement> elements,
		float centerX, float boxTopY, GlyphMeasurer measurer)
	{
		// 参考对每个元素都算 (文本宽 + 图标宽)，相邻元素之间加一个 GAP。
		// 元素个数为 0 或 1 时不应有间隔，参考的 (n - 1) 在 n = 0 时会算出
		// 负数（-5），这里钳到 0；否则空列表会让 startX 偏移 +2.5。
		float totalWidth = 0;
		for(EspNameTagElement element : elements)
		{
			totalWidth += width(element.text(), measurer);
			totalWidth += iconWidth(element, measurer);
		}
		if(elements.size() > 1)
			totalWidth += GAP * (elements.size() - 1);

		float startX = centerX - totalWidth / 2F;
		float baselineY = boxTopY - BASELINE_OFFSET;

		ArrayList<Placed> placed = new ArrayList<>(elements.size());
		float currentX = startX;

		for(EspNameTagElement element : elements)
		{
			float textWidth = width(element.text(), measurer);
			float iconWidth = iconWidth(element, measurer);

			float bgX = currentX - BG_PADDING;
			float bgY = baselineY - BG_PADDING - BASELINE_OFFSET;
			float bgWidth = textWidth + iconWidth + BG_PADDING * 2F;
			float bgHeight = FONT_SIZE + BG_PADDING * 2F;

			boolean iconOnLeft = element.hasIcon()
				&& element.icon()
					.position() == EspNameTagElement.IconPosition.LEFT;
			float textX = currentX + (iconOnLeft ? iconWidth : 0);

			float iconX = Float.NaN;
			if(element.hasIcon())
				iconX = iconOnLeft
					? currentX + element.icon().horizontalOffset()
					: textX + textWidth + element.icon().horizontalOffset();

			placed.add(new Placed(element, currentX, textX, iconX, textWidth,
				iconWidth, bgX, bgY, bgWidth, bgHeight));

			currentX += textWidth + iconWidth + GAP;
		}

		return new Layout(totalWidth, startX, baselineY, List.copyOf(placed));
	}

	private static float width(String text, GlyphMeasurer measurer)
	{
		if(text == null || text.isEmpty())
			return 0;
		return measurer.width(text);
	}

	private static float iconWidth(EspNameTagElement element,
		GlyphMeasurer measurer)
	{
		if(!element.hasIcon())
			return 0;
		return measurer.width(element.icon().glyph());
	}
}
