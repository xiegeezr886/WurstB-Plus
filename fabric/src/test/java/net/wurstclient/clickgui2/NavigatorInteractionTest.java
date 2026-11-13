package net.wurstclient.clickgui2;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

final class NavigatorInteractionTest
{
	@Test
	void closingUsesLinearFadeAndReversesAnOpeningWithoutJumping()
	{
		NavigatorAnimation opacity = new NavigatorAnimation(0);
		opacity.animate(1, 300, 1000);
		float start = opacity.value(1120);
		opacity.animate(0, 100, 1120, NavigatorAnimation.Easing.LINEAR);
		assertEquals(start, opacity.value(1120));
		assertEquals(start / 2, opacity.value(1170), 0.0001);
		assertEquals(0, opacity.value(1220));
	}

	@Test
	void numericEditorFiltersInputAndEditsAtTheCursor()
	{
		NavigatorTextBox editor = new NavigatorTextBox("1234567890.-", null);
		editor.focus(true);
		for(char c : "-12a.5".toCharArray()) editor.character(c);
		assertEquals("-12.5", editor.value());
		editor.key(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT, 0);
		editor.key(org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE, 0);
		assertEquals("-125", editor.value());
		editor.character('.');
		assertEquals("-12.5", editor.value());
		editor.focus(false);
		assertFalse(editor.character('9'));
		assertEquals("-12.5", editor.value());
	}

	@Test
	void originalNavigatorCategoriesKeepTheirOrder()
	{
		assertEquals(List.of("搜索", "方块", "移动", "战斗", "渲染", "聊天",
			"娱乐", "物品", "其他", "HUD", "界面"),
			java.util.Arrays.stream(NavigatorCategory.values()).map(NavigatorCategory::label).toList());
	}

	@Test
	void searchUsesAliasesWordsAndJoinedWordsWithoutFuzzyDescriptionMatches()
	{
		assertTrue(NavigatorSearch.matches(List.of("KillAura", "自动攻击"), "kill aura"));
		assertTrue(NavigatorSearch.matches(List.of("Auto Sprint"), "sprint unknown"));
		assertTrue(NavigatorSearch.matches(List.of("自动攻击"), "自动"));
		assertFalse(NavigatorSearch.matches(List.of("KillAura"), "klara"));
		assertTrue(NavigatorSearch.matches(List.of("Anything"), "  "));
	}

	@Test
	void searchDoesNotChangeWithTheOperatingSystemLocale()
	{
		Locale previous = Locale.getDefault();
		try
		{
			Locale.setDefault(Locale.forLanguageTag("tr-TR"));
			assertTrue(NavigatorSearch.matches(List.of("INVENTORY"), "inventory"));
		}finally
		{
			Locale.setDefault(previous);
		}
	}

	@Test
	void wheelSupportsFractionalDeltasAndReturnsFromBothOverscrollEdges()
	{
		NavigatorScroll scrolling = new NavigatorScroll();
		scrolling.bounds(1000, 200);
		scrolling.update(1000);
		scrolling.wheel(-0.5, 1001);
		assertTrue(scrolling.update(1050) < 0);
		assertTrue(scrolling.current() > -30);
		scrolling.wheel(-100, 1060);
		scrolling.update(1100);
		assertEquals(-800, scrolling.update(10000), 0.02);
		scrolling.wheel(100, 10001);
		scrolling.update(10040);
		assertEquals(0, scrolling.update(20000), 0.02);
	}

	@Test
	void collapsingCardsReboundsAnOldScrollPositionIntoTheNewContent()
	{
		NavigatorScroll scrolling = new NavigatorScroll();
		scrolling.bounds(2000, 200);
		scrolling.wheel(-100, 1000);
		scrolling.update(10000);
		assertEquals(-1800, scrolling.update(20000), 0.02);
		scrolling.bounds(100, 200);
		assertEquals(0, scrolling.update(30000), 0.02);
	}

	@Test
	void wheelSmoothingIsIndependentOfFrameRate()
	{
		NavigatorScroll fast = new NavigatorScroll();
		NavigatorScroll slow = new NavigatorScroll();
		for(NavigatorScroll scrolling : List.of(fast, slow))
		{
			scrolling.bounds(1000, 200);
			scrolling.update(1000);
			scrolling.wheel(-1, 1001);
		}
		for(int now = 1010; now <= 1500; now += 10)
			fast.update(now);
		for(int now = 1100; now <= 1500; now += 100)
			slow.update(now);
		assertEquals(fast.current(), slow.current(), 0.01);
	}

	@Test
	void expansionCanReverseMidAnimationWithoutAJumpAndFinishesExactly()
	{
		NavigatorAnimation animation = new NavigatorAnimation(38);
		animation.animate(200, 300, 1000);
		float midway = animation.value(1100);
		assertTrue(midway > 38 && midway < 200);
		animation.animate(38, 200, 1100);
		assertEquals(midway, animation.value(1100));
		assertEquals(38, animation.value(1300));
	}
}
