package net.wurstclient.twilight;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

/**
 * 用参考图里那一天（2026-09-27，周日）校验日期文案。
 */
final class TwilightHomeCopyTest
{
	private static final LocalDate REFERENCE_DAY =
		LocalDate.of(2026, 9, 27);

	@Test
	void formatsTheReferenceDay()
	{
		assertEquals("27", TwilightHomeCopy.dayBadge(REFERENCE_DAY));
		assertEquals("9月27日 · 周日",
			TwilightHomeCopy.dateLine(REFERENCE_DAY));
	}

	@Test
	void coversEveryWeekday()
	{
		// 2026-09-21 是周一，依次到周日
		String[] expected = {"周一", "周二", "周三", "周四", "周五", "周六",
			"周日"};

		for(int day = 0; day < 7; day++)
			assertEquals("9月" + (21 + day) + "日 · " + expected[day],
				TwilightHomeCopy.dateLine(REFERENCE_DAY.minusDays(6 - day)));
	}

	@Test
	void handlesSingleDigitDaysAndOtherMonths()
	{
		assertEquals("1月5日 · 周一",
			TwilightHomeCopy.dateLine(LocalDate.of(2026, 1, 5)));
		assertEquals("5", TwilightHomeCopy.dayBadge(LocalDate.of(2026, 1, 5)));
	}
}
