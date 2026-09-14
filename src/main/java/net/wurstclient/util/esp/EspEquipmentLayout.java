/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util.esp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 参考 ESPModule 装备栏的几何：把 n 枚物品图标横向居中排在方框正上方。
 *
 * <p>
 * 参考的原式是
 * {@code stackX = x + w / 2 - (n * scale * 8) + ((n - i - 1) * scale * 16)}。
 * 因为 {@code scale * 8} 正好是 {@code scale * 16} 的一半，整排是以方框中心
 * 对称的；而 {@code (n - i - 1)} 这一项意味着<b>列表最后一项画在最左边</b>
 * ——调用方按「头盔/胸甲/护腿/靴子/主手」的顺序收集，画出来就是从右往左
 * 头盔在前、主手最左。本类把这个口径原样固定下来。
 *
 * <p>
 * 纯几何，不含 {@code net.minecraft.} 依赖。
 */
public final class EspEquipmentLayout
{
	/** 物品图标的缩放（参考 {@code scale = 0.65F}）。 */
	public static final float ICON_SCALE = 0.65F;

	/** 原版物品图标的原始边长。 */
	public static final float ICON_SIZE = 16F;

	/** 方框顶边到图标顶边的距离：方框上方有铭牌元素时（参考 23.5）。 */
	public static final float OFFSET_WITH_NAME_TAGS = 23.5F;

	/** 方框顶边到图标顶边的距离：没有铭牌元素时（参考 14）。 */
	public static final float OFFSET_WITHOUT_NAME_TAGS = 14F;

	/** 相邻两枚图标的中心距。 */
	public static final float STEP = ICON_SCALE * ICON_SIZE;

	/**
	 * 一枚图标在屏幕上的左上角。
	 *
	 * @param index
	 *            调用方传入列表里的下标，不是从左往右的序号。
	 */
	public record Slot(int index, float x, float y)
	{
	}

	private EspEquipmentLayout()
	{
	}

	/**
	 * @param count
	 *            图标数量，&le; 0 时返回空列表。
	 * @param boxCenterX
	 *            方框水平中心。
	 * @param boxTopY
	 *            方框顶边。
	 * @param hasNameTags
	 *            方框上方是否已经排了铭牌条，决定竖直偏移。
	 */
	public static List<Slot> layout(int count, float boxCenterX, float boxTopY,
		boolean hasNameTags)
	{
		if(count <= 0)
			return List.of();

		float y = boxTopY
			- (hasNameTags ? OFFSET_WITH_NAME_TAGS : OFFSET_WITHOUT_NAME_TAGS);
		float startX = boxCenterX - count * STEP / 2F;

		List<Slot> slots = new ArrayList<>(count);
		for(int i = 0; i < count; i++)
			slots.add(new Slot(i, startX + (count - i - 1) * STEP, y));

		return Collections.unmodifiableList(slots);
	}
}
