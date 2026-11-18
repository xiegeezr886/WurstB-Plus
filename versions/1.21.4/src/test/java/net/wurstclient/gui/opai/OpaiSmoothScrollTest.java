package net.wurstclient.gui.opai;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

final class OpaiSmoothScrollTest
{
	@Test void rapidWheelEventsAccumulateWithoutJumpingTheDrawnPosition()
	{
		var scroll = new OpaiSmoothScroll();
		scroll.limit(500);
		scroll.step(0);
		scroll.by(41);
		scroll.by(41);
		scroll.by(41);
		assertEquals(123, scroll.target());
		assertEquals(0, scroll.position());
		scroll.step(16);
		assertTrue(scroll.position() > 0 && scroll.position() < 123);
		scroll.step(1000);
		assertEquals(123, scroll.position(), .01);
	}

	@Test void motionIsIndependentOfFrameRate()
	{
		var slow = new OpaiSmoothScroll();
		var fast = new OpaiSmoothScroll();
		for(var scroll : new OpaiSmoothScroll[]{slow, fast})
		{
			scroll.limit(1000);
			scroll.step(0);
			scroll.by(400);
		}
		slow.step(100);
		for(int time = 10; time <= 100; time += 10) fast.step(time);
		assertEquals(slow.position(), fast.position(), .0001);
	}

	@Test void deletingRowsClampsBothThePositionAndPendingMotion()
	{
		var scroll = new OpaiSmoothScroll();
		scroll.limit(500);
		scroll.step(0);
		scroll.by(1000);
		scroll.step(500);
		scroll.limit(20);
		assertEquals(20, scroll.position());
		assertEquals(20, scroll.target());
		scroll.by(-1000);
		scroll.step(1500);
		assertEquals(0, scroll.position());
	}

	@Test void keyboardSelectionRevealsTheRowSmoothly()
	{
		var scroll = new OpaiSmoothScroll();
		scroll.limit(500);
		scroll.reveal(205, 241, 100);
		assertEquals(141, scroll.target());
		assertEquals(0, scroll.position());
		scroll.reveal(0, 36, 100);
		assertEquals(0, scroll.target());
	}
}
