package net.wurstclient.hud2.island;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

final class IslandContainerViewTest
{
	@Test void mapsScaledGridAndExcludesPaddingAndOutsideEdges()
	{
		var view = new IslandContainerView(new Object(), 100, 40, 380, 144, 2, 3, 0);
		assertEquals(0, view.slotAt(110, 52));
		assertEquals(1, view.slotAt(150, 52));
		assertEquals(26, view.slotAt(469.9, 171.9));
		assertEquals(-1, view.slotAt(109.9, 52));
		assertEquals(-1, view.slotAt(470, 52));
		assertEquals(-1, view.slotAt(110, 172));
		assertTrue(view.contains(105, 45));
		assertFalse(view.contains(480, 45));
	}

	@Test void expandingShellOnlyAcceptsVisibleSlots()
	{
		var view = new IslandContainerView(new Object(), 100, 20, 150, 40, 1, 6, 0);
		assertEquals(0, view.slotAt(102, 26));
		assertEquals(1, view.slotAt(105, 26));
		assertEquals(-1, view.slotAt(101, 26));
		assertEquals(-1, view.slotAt(248, 26));
		assertEquals(-1, view.slotAt(110, 59));
		assertEquals(-1, view.slotAt(110, 66));
	}

	@Test void staleOrReplacedMenuCannotReceiveClicks()
	{
		Object menu = new Object();
		var view = new IslandContainerView(menu, 0, 0, 190, 72, 1, 3, 100);
		assertTrue(view.accepts(menu, 350));
		assertFalse(view.accepts(menu, 351));
		assertFalse(view.accepts(menu, 99));
		assertFalse(view.accepts(new Object(), 100));
		assertFalse(view.accepts(null, 100));
	}
}
