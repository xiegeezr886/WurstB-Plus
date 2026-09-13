/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.perimeter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.wurstclient.perimeter.config.PerimeterScanline;

final class PerimeterEngineTest
{
	// ------------------------------------------------------------------
	// liquid policies
	// ------------------------------------------------------------------
	
	@Test
	void avoidSkipsFluidsAndFluidAdjacentBlocks()
	{
		assertFalse(PerimeterFluidGuard.processInside(
			PerimeterLiquidPolicy.AVOID, true, true, false));
		assertFalse(PerimeterFluidGuard.processInside(
			PerimeterLiquidPolicy.AVOID, true, false, true));
		assertTrue(PerimeterFluidGuard.processInside(
			PerimeterLiquidPolicy.AVOID, true, false, false));
	}
	
	@Test
	void replaceAndSealProcessFluidsWhenSealingBlocksAreConfigured()
	{
		for(PerimeterLiquidPolicy policy : List.of(
			PerimeterLiquidPolicy.REPLACE,
			PerimeterLiquidPolicy.SEAL_BOUNDARY))
		{
			assertTrue(PerimeterFluidGuard.processInside(policy, true, true,
				false));
			assertTrue(PerimeterFluidGuard.processInside(policy, true, false,
				false));
		}
		
		// without sealing blocks there is nothing to place
		assertFalse(PerimeterFluidGuard.processInside(
			PerimeterLiquidPolicy.SEAL_BOUNDARY, false, true, false));
		assertTrue(PerimeterFluidGuard.processInside(
			PerimeterLiquidPolicy.SEAL_BOUNDARY, false, false, false));
	}
	
	@Test
	void onlySealBoundaryTouchesTheRim()
	{
		PerimeterArea area = PerimeterArea.fromXZ(-2, -2, 2, 2, 0, 4);
		
		// a fluid one block beside the area
		assertTrue(PerimeterFluidGuard.processRim(area,
			PerimeterLiquidPolicy.SEAL_BOUNDARY, true, 3, 2, 0, true));
		
		// the same position under replace or avoid is left alone
		assertFalse(PerimeterFluidGuard.processRim(area,
			PerimeterLiquidPolicy.REPLACE, true, 3, 2, 0, true));
		assertFalse(PerimeterFluidGuard.processRim(area,
			PerimeterLiquidPolicy.AVOID, true, 3, 2, 0, true));
		
		// far away, solid, or without sealing blocks it is not touched either
		assertFalse(PerimeterFluidGuard.processRim(area,
			PerimeterLiquidPolicy.SEAL_BOUNDARY, true, 9, 2, 0, true));
		assertFalse(PerimeterFluidGuard.processRim(area,
			PerimeterLiquidPolicy.SEAL_BOUNDARY, true, 3, 2, 0, false));
		assertFalse(PerimeterFluidGuard.processRim(area,
			PerimeterLiquidPolicy.SEAL_BOUNDARY, false, 3, 2, 0, true));
	}
	
	@Test
	void theTopRimLayerCountsAsTouching()
	{
		PerimeterArea area = PerimeterArea.fromXZ(-2, -2, 2, 2, 0, 4);
		
		// directly above the area
		assertTrue(PerimeterFluidGuard.touchesSideOrTopBoundary(area, 0, 5, 0));
		assertTrue(PerimeterFluidGuard.touchesSideOrTopBoundary(area, 2, 5, -2));
		
		// above the area but outside its columns
		assertFalse(PerimeterFluidGuard.touchesSideOrTopBoundary(area, 3, 5, 0));
		
		// inside the mining range next to a column of the area
		assertTrue(PerimeterFluidGuard.touchesSideOrTopBoundary(area, 3, 2, 0));
		assertFalse(PerimeterFluidGuard.touchesSideOrTopBoundary(area, 4, 2, 0));
		
		// below and above the mining range do not touch
		assertFalse(PerimeterFluidGuard.touchesSideOrTopBoundary(area, 3, -1, 0));
		assertFalse(PerimeterFluidGuard.touchesSideOrTopBoundary(area, 3, 9, 0));
	}
	
	@Test
	void fluidInsideIsReplacedOnlyWhenNotAvoiding()
	{
		assertTrue(PerimeterFluidGuard.replaceFluidInside(
			PerimeterLiquidPolicy.REPLACE, true));
		assertTrue(PerimeterFluidGuard.replaceFluidInside(
			PerimeterLiquidPolicy.SEAL_BOUNDARY, true));
		assertFalse(PerimeterFluidGuard.replaceFluidInside(
			PerimeterLiquidPolicy.AVOID, true));
		assertFalse(PerimeterFluidGuard.replaceFluidInside(
			PerimeterLiquidPolicy.REPLACE, false));
	}
	
	// ------------------------------------------------------------------
	// inventory rules
	// ------------------------------------------------------------------
	
	@Test
	void batchLimitFollowsTheEmptySlots()
	{
		assertEquals(64L, PerimeterInventoryPolicy.batchLimit(2, 1, 64));
		assertEquals(320L, PerimeterInventoryPolicy.batchLimit(6, 1, 64));
		
		// never less than one block, so a full inventory still gets unloaded
		assertEquals(1L, PerimeterInventoryPolicy.batchLimit(1, 1, 64));
		assertEquals(1L, PerimeterInventoryPolicy.batchLimit(0, 1, 64));
		assertEquals(1L, PerimeterInventoryPolicy.batchLimit(3, 5, 64));
	}
	
	@Test
	void unloadingStartsWhenTheReserveIsAllThatIsLeft()
	{
		assertTrue(PerimeterInventoryPolicy.needsUnloading(1, 1));
		assertTrue(PerimeterInventoryPolicy.needsUnloading(0, 1));
		assertFalse(PerimeterInventoryPolicy.needsUnloading(2, 1));
	}
	
	@Test
	void disposableSlotsSkipEverythingWorthKeeping()
	{
		List<PerimeterInventoryPolicy.ItemStackLike> slots = new ArrayList<>();
		slots.add(new FakeStack(false, true));
		slots.add(new FakeStack(true, true));
		slots.add(new FakeStack(false, false));
		slots.add(new FakeStack(false, false));
		
		assertEquals(2, PerimeterInventoryPolicy.firstDisposableSlot(slots));
		
		slots.set(2, new FakeStack(false, true));
		assertEquals(3, PerimeterInventoryPolicy.firstDisposableSlot(slots));
		
		for(int slot = 0; slot < slots.size(); slot++)
			slots.set(slot, new FakeStack(false, true));
		
		assertEquals(-1, PerimeterInventoryPolicy.firstDisposableSlot(slots));
	}
	
	// ------------------------------------------------------------------
	// state machine bookkeeping
	// ------------------------------------------------------------------
	
	@Test
	void everyStateHasAnIdThatRoundTrips()
	{
		for(PerimeterAutomationState state : PerimeterAutomationState
			.values())
		{
			assertEquals(state, PerimeterAutomationState.fromId(state.id()));
			assertEquals(state.id(), state.id().toLowerCase(java.util.Locale.ROOT));
		}
		
		assertEquals(28, PerimeterAutomationState.values().length);
		assertNull(PerimeterAutomationState.fromId("nonsense"));
		assertNull(PerimeterAutomationState.fromId(null));
	}
	
	@Test
	void navigationAndActiveStatesAreClassified()
	{
		assertTrue(PerimeterAutomationState.NAVIGATING_TO_UNLOAD
			.isNavigationState());
		assertTrue(PerimeterAutomationState.RETURNING_TO_MINE
			.isNavigationState());
		assertFalse(PerimeterAutomationState.MINING.isNavigationState());
		assertFalse(PerimeterAutomationState.REPAIRING.isNavigationState());
		
		assertTrue(PerimeterAutomationState.MINING.isActive());
		assertFalse(PerimeterAutomationState.IDLE.isActive());
		assertFalse(PerimeterAutomationState.COMPLETE.isActive());
		assertFalse(PerimeterAutomationState.ERROR.isActive());
	}
	
	@Test
	void historyKeepsTheNewestTransitionsUpToItsLimit()
	{
		PerimeterStateHistory history = new PerimeterStateHistory(3);
		
		assertEquals(0, history.size());
		assertEquals(List.of("no state changes recorded yet"),
			history.describe());
		
		for(int index = 0; index < 5; index++)
			history.record(PerimeterAutomationState.IDLE,
				PerimeterAutomationState.MINING, "batch " + index, "overworld",
				"0 64 0");
		
		assertEquals(3, history.size());
		
		List<String> lines = history.describe();
		assertEquals(3, lines.size());
		assertTrue(lines.get(0).contains("batch 4"), lines.get(0));
		assertTrue(lines.get(2).contains("batch 2"), lines.get(2));
		assertTrue(lines.get(0).contains("idle -> mining"), lines.get(0));
		assertTrue(lines.get(0).contains("overworld"), lines.get(0));
		
		history.clear();
		assertEquals(0, history.size());
	}
	
	@Test
	void aTransitionRecordsPreviousAndNextState()
	{
		PerimeterStateHistory history = new PerimeterStateHistory();
		history.record(PerimeterAutomationState.MINING,
			PerimeterAutomationState.EATING, "eating", "the_nether",
			"10 70 -4");
		
		var transition = history.all().get(0);
		assertEquals(PerimeterAutomationState.MINING, transition.previous());
		assertEquals(PerimeterAutomationState.EATING, transition.next());
		assertEquals("eating", transition.detail());
		assertNotNull(transition.at());
	}
	
	private static final class FakeStack
		implements PerimeterInventoryPolicy.ItemStackLike
	{
		private final boolean empty;
		private final boolean keep;
		
		private FakeStack(boolean empty, boolean keep)
		{
			this.empty = empty;
			this.keep = keep;
		}
		
		@Override
		public boolean isEmpty()
		{
			return empty;
		}
		
		@Override
		public boolean keepWhenUnloading()
		{
			return keep;
		}
	}
	
	// ------------------------------------------------------------------
	// column area helpers used by the engine
	// ------------------------------------------------------------------
	
	@Test
	void columnAreaDescribesItselfForStatusOutput()
	{
		var config = new net.wurstclient.perimeter.config.PerimeterDetectedArea();
		config.scanlines = new ArrayList<>();
		config.scanlines.add(new PerimeterScanline(0, 0, 4));
		config.scanlines.add(new PerimeterScanline(1, 0, 4));
		
		PerimeterColumnArea area = new PerimeterColumnArea(config, -59, -50);
		String description = area.describe();
		
		assertTrue(description.contains("10 columns"), description);
		assertTrue(description.contains("2 runs"), description);
		assertTrue(description.contains("100 blocks"), description);
		assertEquals(100L, area.estimatedBlockCount());
	}
}
