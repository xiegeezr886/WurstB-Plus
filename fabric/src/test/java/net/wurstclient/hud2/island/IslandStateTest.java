package net.wurstclient.hud2.island;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import net.wurstclient.hud2.island.IslandState.Kind;
import net.wurstclient.hud2.island.IslandState.NoticeKind;

final class IslandStateTest
{
	@Test void noticesKeepLatestThreeAndRapidToggleReversesWithoutJump()
	{
		IslandState state = new IslandState();
		for(int i = 0; i < 4; i++)
			state.post("module" + i, "Module Toggled", "Test", NoticeKind.TOGGLE, true, 0);
		assertEquals(3, state.notices().size());
		assertEquals("module1", state.notices().get(0).key());
		float before = state.notices().get(1).toggle(80);
		state.post("module2", "Module Toggled", "Test", NoticeKind.TOGGLE, false, 80);
		assertEquals(3, state.notices().size());
		var reversed = state.notices().get(1);
		assertEquals("module2", reversed.key());
		assertEquals("module3", state.notices().get(2).key());
		assertEquals(before, reversed.toggle(80), .0001);
		assertEquals(0, reversed.toggle(280));
		state.expire(800);
		assertEquals(1, state.notices().size());
		state.expire(880);
		assertTrue(state.notices().isEmpty());
	}

	@Test void newRowsAnimateIndependentlyAndUpdatingOneKeepsItsAppearance()
	{
		IslandState state = new IslandState();
		state.post("first", "Info", "one", NoticeKind.INFO, true, 0);
		state.post("second", "Info", "two", NoticeKind.INFO, true, 200);
		assertEquals(1, state.notices().get(0).appearance(200));
		assertEquals(0, state.notices().get(1).appearance(200));
		assertEquals(.75F, state.notices().get(1).appearance(280));
		state.post("first", "Info", "updated", NoticeKind.INFO, true, 280);
		assertEquals("updated", state.notices().get(0).detail());
		assertEquals(1, state.notices().get(0).appearance(280));
		state.expire(3200);
		assertEquals(1, state.notices().size());
		assertEquals("first", state.notices().get(0).key());
	}

	@Test void activitiesPreemptNoticesWithoutExtendingTheirLifetime()
	{
		IslandState state = new IslandState();
		state.post("notice", "Title", "Message", NoticeKind.INFO, true, 0);
		assertEquals(Kind.CHEST, state.kind(true, true, true));
		assertEquals(Kind.BREAKING, state.kind(false, true, true));
		assertEquals(Kind.NOTICE, state.kind(false, false, true));
		state.expire(3000);
		assertEquals(Kind.SCAFFOLD, state.kind(false, false, true));
		assertEquals(Kind.IDLE, state.kind(false, false, false));
	}

	@Test void blockRefillsDoNotCountAsConsumptionAndRateExpires()
	{
		IslandState state = new IslandState();
		state.blocks(64, 0);
		state.blocks(60, 500);
		assertEquals(4, state.rate(500));
		state.blocks(128, 800);
		assertEquals(1, state.progress());
		state.blocks(64, 1000);
		assertEquals(.5, state.progress());
		assertEquals(34, state.rate(2000));
		assertEquals(0, state.rate(3000));
		state.post("test", "", "", NoticeKind.INFO, true, 3000);
		state.reset();
		assertEquals(0, state.count());
		assertEquals(0, state.progress());
		assertTrue(state.notices().isEmpty());
		state.blocks(16, 4000);
		assertEquals(1, state.progress());
		assertEquals(0, state.rate(4000));
	}

	@Test void springIsFrameRateIndependentAndCanReverseContinuously()
	{
		IslandMotion fast = new IslandMotion(), slow = new IslandMotion();
		fast.value(23, 0, 0);
		slow.value(23, 0, 0);
		fast.value(108, 0, 7.25);
		slow.value(108, 0, 7.25);
		for(int time = 5; time <= 200; time += 5) fast.value(108, time, 0);
		for(int time = 50; time <= 200; time += 50) slow.value(108, time, 0);
		float before = fast.value(108, 200, 0);
		assertEquals(before, slow.value(108, 200, 0), .0001);
		assertEquals(before, fast.value(23, 200, 5.5), .0001);
		assertEquals(23, fast.value(23, 10000, 0));
		assertEquals(23, fast.value(23, 20000, 0));
		fast.reset();
		assertEquals(50, fast.value(50, 30000, 0));
	}
}
