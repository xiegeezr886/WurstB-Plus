/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util.esp;

/**
 * 铭牌元素模型，移植自 OpenOpal 的
 * {@code client/feature/module/impl/visual/esp/NameTagElement.java} +
 * {@code NameTagIcon.java} + {@code NameTagIconPosition.java}（GPL-3.0）。
 *
 * <p>
 * 参考把「图标」「文本」拆成两个可空字段，所以一个元素可以是：只有图标
 * （状态指示）、只有文本（距离/血量）、或两者都有且图标分居文本左右。
 * 铭牌就是一串这样的元素按固定顺序排成一行。
 *
 * <p>
 * <b>与参考的差异</b>：参考的图标来自 {@code materialicons-regular} 图标字体，
 * 本工程没有该字体资源。图标字段因此退化为「用现有 PingFang 字体绘制的单个
 * 字形」，见 {@link EspIndicatorGlyphs}。
 *
 * <p>
 * 本类型是纯数据，不含任何 {@code net.minecraft.} 依赖，可直接单测。
 */
public record EspNameTagElement(Icon icon, String text, int color)
{
	public enum IconPosition
	{
		LEFT,
		RIGHT
	}

	/**
	 * @param glyph
	 *            要绘制的字形，来自与正文同一个字体。
	 * @param position
	 *            图标在文本的左侧还是右侧。
	 * @param horizontalOffset
	 *            图标相对其锚点的水平微调，参考默认 0.5。
	 */
	public record Icon(String glyph, IconPosition position,
		float horizontalOffset)
	{
		public Icon(String glyph)
		{
			this(glyph, IconPosition.RIGHT, 0.5F);
		}

		public Icon(String glyph, float horizontalOffset)
		{
			this(glyph, IconPosition.RIGHT, horizontalOffset);
		}

		public Icon(String glyph, IconPosition position)
		{
			this(glyph, position, 0.5F);
		}
	}

	/** 只有图标的元素（状态指示）。 */
	public EspNameTagElement(Icon icon, int color)
	{
		this(icon, null, color);
	}

	/** 只有文本的元素（名字/血量/距离）。 */
	public EspNameTagElement(String text, int color)
	{
		this(null, text, color);
	}

	public boolean hasText()
	{
		return text != null && !text.isEmpty();
	}

	public boolean hasIcon()
	{
		return icon != null;
	}
}
