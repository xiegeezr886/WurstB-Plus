package net.wurstclient.util.esp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.wurstclient.util.esp.EspEquipmentLayout.Slot;

final class EspEquipmentLayoutTest
{
	private static final float CENTER = 100F;
	private static final float BOX_TOP = 50F;
	private static final float EPSILON = 0.0001F;

	@Test
	void emptyEquipmentProducesNoSlots()
	{
		assertTrue(EspEquipmentLayout.layout(0, CENTER, BOX_TOP, true).isEmpty());
		assertTrue(
			EspEquipmentLayout.layout(-3, CENTER, BOX_TOP, true).isEmpty());
	}

	@Test
	void aSingleIconIsCentredOnTheBox()
	{
		List<Slot> slots =
			EspEquipmentLayout.layout(1, CENTER, BOX_TOP, false);

		assertEquals(1, slots.size());
		assertEquals(CENTER - EspEquipmentLayout.STEP / 2F, slots.get(0).x(),
			EPSILON);
	}

	/**
	 * 整排必须关于方框中心对称——参考原式里那个「减 n*step/2」就是干这个的。
	 */
	@Test
	void theWholeRowIsCentredRegardlessOfCount()
	{
		for(int count = 1; count <= 6; count++)
		{
			List<Slot> slots =
				EspEquipmentLayout.layout(count, CENTER, BOX_TOP, false);

			float left = Float.POSITIVE_INFINITY;
			float right = Float.NEGATIVE_INFINITY;
			for(Slot slot : slots)
			{
				left = Math.min(left, slot.x());
				right = Math.max(right, slot.x() + EspEquipmentLayout.STEP);
			}

			assertEquals(CENTER, (left + right) / 2F, EPSILON);
		}
	}

	/**
	 * 参考的 {@code (n - i - 1)}：列表最后一项在最左边。调用方是按
	 * 头盔→胸甲→护腿→靴子→主手收集的，所以画出来主手最左。
	 */
	@Test
	void theLastListEntryIsDrawnLeftmost()
	{
		List<Slot> slots = EspEquipmentLayout.layout(3, CENTER, BOX_TOP, false);

		float x0 = slots.get(0).x();
		float x1 = slots.get(1).x();
		float x2 = slots.get(2).x();

		assertTrue(x2 < x1, "index 2 should be left of index 1");
		assertTrue(x1 < x0, "index 1 should be left of index 0");
		assertEquals(EspEquipmentLayout.STEP, x0 - x1, EPSILON);
		assertEquals(EspEquipmentLayout.STEP, x1 - x2, EPSILON);
	}

	@Test
	void neighboursAreExactlyOneStepApart()
	{
		List<Slot> slots = EspEquipmentLayout.layout(5, CENTER, BOX_TOP, false);

		for(int i = 0; i < slots.size() - 1; i++)
		{
			Slot current = slots.get(i);
			Slot next = slots.get(i + 1);
			assertEquals(EspEquipmentLayout.STEP, current.x() - next.x(),
				EPSILON);
			assertEquals(i, current.index());
		}
		assertEquals(4, slots.get(4).index());
	}

	@Test
	void stepIsTheScaledIconSize()
	{
		assertEquals(16F * 0.65F, EspEquipmentLayout.STEP, EPSILON);
	}

	@Test
	void theRowSitsHigherWhenNameTagsArePresent()
	{
		Slot plain =
			EspEquipmentLayout.layout(1, CENTER, BOX_TOP, false).get(0);
		Slot withTags =
			EspEquipmentLayout.layout(1, CENTER, BOX_TOP, true).get(0);

		assertEquals(BOX_TOP - 14F, plain.y(), EPSILON);
		assertEquals(BOX_TOP - 23.5F, withTags.y(), EPSILON);
		assertTrue(withTags.y() < plain.y());
	}

	@Test
	void everyIconInARowSharesTheSameHeight()
	{
		List<Slot> slots = EspEquipmentLayout.layout(4, CENTER, BOX_TOP, true);

		for(Slot slot : slots)
			assertEquals(BOX_TOP - 23.5F, slot.y(), EPSILON);
	}

	@Test
	void theRowDoesNotMoveUpAsItGetsWider()
	{
		assertEquals(EspEquipmentLayout.layout(1, CENTER, BOX_TOP, true).get(0)
			.y(),
			EspEquipmentLayout.layout(6, CENTER, BOX_TOP, true).get(0).y(),
			EPSILON);
	}

	@Test
	void theReturnedListCannotBeMutated()
	{
		List<Slot> slots = EspEquipmentLayout.layout(2, CENTER, BOX_TOP, true);

		try
		{
			slots.add(new Slot(9, 0, 0));
			throw new AssertionError("expected an UnsupportedOperationException");
		}catch(UnsupportedOperationException expected)
		{
			// 预期
		}
	}

	// ------------------------------------------------------------------
	// 抽出来的横向口径（目标信息面板复用）
	// ------------------------------------------------------------------

	@Test
	void rowStartXCentresTheRowOnTheGivenCentre()
	{
		for(int count = 0; count <= 6; count++)
		{
			float start = EspEquipmentLayout.rowStartX(count, CENTER,
				EspEquipmentLayout.STEP);
			float left = start
				+ EspEquipmentLayout.rowOffsetX(count, count - 1,
					EspEquipmentLayout.STEP);
			float right = start
				+ EspEquipmentLayout.rowOffsetX(count, 0,
					EspEquipmentLayout.STEP) + EspEquipmentLayout.STEP;

			if(count == 0)
				assertEquals(CENTER, start, EPSILON);
			else
				assertEquals(CENTER, (left + right) / 2F, EPSILON,
					"count=" + count);
		}
	}

	@Test
	void rowOffsetXPutsTheLastEntryAtZeroAndTheFirstAtTheFarEnd()
	{
		int count = 4;

		assertEquals(0F,
			EspEquipmentLayout.rowOffsetX(count, count - 1, 10F), EPSILON);
		assertEquals((count - 1) * 10F,
			EspEquipmentLayout.rowOffsetX(count, 0, 10F), EPSILON);
	}

	/**
	 * 两个入口必须给出同一套 x，否则 ESP 铭牌条与目标面板会各排各的。
	 */
	@Test
	void layoutUsesTheSameHorizontalConventionAsRowStartX()
	{
		for(int count = 1; count <= 5; count++)
		{
			List<Slot> slots =
				EspEquipmentLayout.layout(count, CENTER, BOX_TOP, true);
			float start = EspEquipmentLayout
				.rowStartX(count, CENTER, EspEquipmentLayout.STEP);

			for(Slot slot : slots)
				assertEquals(
					start + EspEquipmentLayout.rowOffsetX(count, slot.index(),
						EspEquipmentLayout.STEP),
					slot.x(), EPSILON);
		}
	}

	/**
	 * 目标面板用的步长比 ESP 铭牌条小（图标缩放到 0.5 而不是 0.65），
	 * 所以步长必须是参数。
	 */
	@Test
	void rowStepIsParameterisable()
	{
		float half = EspEquipmentLayout.STEP / 2F;

		float wideStart =
			EspEquipmentLayout.rowStartX(4, CENTER, EspEquipmentLayout.STEP);
		float narrowStart = EspEquipmentLayout.rowStartX(4, CENTER, half);

		assertEquals(CENTER - 4 * half / 2F, narrowStart, EPSILON);
		assertEquals(half,
			EspEquipmentLayout.rowOffsetX(4, 2, half)
				- EspEquipmentLayout.rowOffsetX(4, 3, half),
			EPSILON);
		assertTrue(narrowStart > wideStart,
			"a smaller step should start further right, not further left");
	}
}
