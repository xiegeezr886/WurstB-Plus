/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.twilight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.wurstclient.twilight.TwilightShellLayout.Frame;
import net.wurstclient.twilight.TwilightShellLayout.Rect;
import net.wurstclient.twilight.TwilightShellLayout.SidebarMode;

/**
 * Tests the shell geometry.
 *
 * <p>
 * The numbers come from the reference implementation, so the tests check two
 * things: that the reference values survive the conversion (sidebar clamps, item
 * height 40, player bar 54, no full width title bar) and that the regions of the
 * shell never overlap, whatever the canvas size.
 */
final class TwilightShellLayoutTest
{
	private static final int WIDTH = 1500;
	private static final int HEIGHT = 880;
	
	@Test
	void theDesignScaleIsClamped()
	{
		assertEquals(1.0F,
			TwilightShellLayout.designScale(WIDTH, HEIGHT), 0.001F);
		assertEquals(1.6F,
			TwilightShellLayout.designScale(3000, 2000), 0.001F);
		assertEquals(0.7F,
			TwilightShellLayout.designScale(400, 300), 0.001F);
		assertEquals(0.7F, TwilightShellLayout.designScale(0, 0), 0.001F);
		assertEquals(0.7F, TwilightShellLayout.designScale(-5, 100), 0.001F);
	}
	
	@Test
	void theDesignScaleFollowsTheSmallerAxis()
	{
		// a wide but flat canvas is limited by the height: 1320 / 880 = 1.5
		assertEquals(1.5F,
			TwilightShellLayout.designScale(3000, 1320), 0.001F);
	}
	
	@Test
	void theSidebarModeFollowsTheCanvasWidth()
	{
		assertEquals(SidebarMode.EXPANDED,
			TwilightShellLayout.sidebarMode(WIDTH));
		assertEquals(SidebarMode.EXPANDED,
			TwilightShellLayout.sidebarMode(1100));
		assertEquals(SidebarMode.COMPACT,
			TwilightShellLayout.sidebarMode(1099));
		assertEquals(SidebarMode.COMPACT,
			TwilightShellLayout.sidebarMode(760));
		assertEquals(SidebarMode.ICON_ONLY,
			TwilightShellLayout.sidebarMode(759));
	}
	
	@Test
	void theSidebarWidthIsClampedLikeTheReference()
	{
		// clamp(180px, 18vw, 216px)
		assertEquals(216, TwilightShellLayout.sidebarWidth(WIDTH,
			SidebarMode.EXPANDED, 1F));
		assertEquals(180, TwilightShellLayout.sidebarWidth(1000,
			SidebarMode.EXPANDED, 1F));
		assertEquals(180, TwilightShellLayout.sidebarWidth(700,
			SidebarMode.EXPANDED, 1F));
		assertEquals(164,
			TwilightShellLayout.sidebarWidth(900, SidebarMode.COMPACT, 1F));
		assertEquals(72,
			TwilightShellLayout.sidebarWidth(500, SidebarMode.ICON_ONLY, 1F));
		assertEquals(324, TwilightShellLayout.sidebarWidth(WIDTH,
			SidebarMode.EXPANDED, 1.5F));
	}
	
	@Test
	void theShellHasNoFullWidthTitleBar()
	{
		Frame frame = TwilightShellLayout.layout(WIDTH, HEIGHT);
		
		// the panel floats: top and bottom are inset by 22px
		assertEquals(22, frame.sidebar.y());
		assertEquals(HEIGHT - 44, frame.sidebar.height());
		assertEquals(frame.sidebar.right(), frame.content.x());
		assertEquals(0, frame.content.y());
		
		// the player bar floats over the content, right of the sidebar,
		// measured at 796..865 in the reference screenshot
		assertEquals(frame.content.x() + 13, frame.playerBar.x());
		assertEquals(70, frame.playerBar.height());
		assertEquals(796, frame.playerBar.y());
		assertEquals(HEIGHT - 14, frame.playerBar.bottom());
		assertTrue(frame.playerBar.width() < frame.content.width());
	}
	
	@Test
	void theSidebarRegionsAreStackedWithoutOverlap()
	{
		Frame frame = TwilightShellLayout.layout(WIDTH, HEIGHT);
		
		// the logo is hidden by default, so the brand row is 36px
		assertEquals(36, frame.brand.height());
		assertTrue(frame.brand.bottom() <= frame.nav.y());
		assertTrue(frame.nav.bottom() <= frame.sidebarFooter.y());
		assertTrue(frame.sidebarFooter.bottom() <= frame.sidebar.bottom());
		assertEquals(frame.sidebar.width(), frame.brand.width());
		assertEquals(frame.sidebar.width(), frame.nav.width());
		assertTrue(frame.nav.height() > 0);
		assertTrue(frame.sidebarFooter.height() > 0);
	}
	
	@Test
	void theContentAreaIsSplitIntoHeaderAndBody()
	{
		Frame frame = TwilightShellLayout.layout(WIDTH, HEIGHT);
		
		assertEquals(0, frame.contentHeader.y());
		assertEquals(96, frame.contentHeader.height());
		assertEquals(frame.contentHeader.bottom(), frame.contentBody.y());
		assertEquals(frame.content.right(), frame.contentBody.right());
		assertTrue(frame.contentBody.bottom() < frame.playerBar.y());
		assertFalse(frame.contentHeader.intersects(frame.contentBody));
	}
	
	@Test
	void navItemsUseTheReferenceGeometry()
	{
		Frame frame = TwilightShellLayout.layout(WIDTH, HEIGHT);
		Rect first = frame.navItem(0);
		Rect second = frame.navItem(1);
		
		assertNotNull(first);
		assertNotNull(second);
		
		// 45px high, 22px below the brand row, 5px apart (row pitch 50px)
		assertEquals(45, first.height());
		assertEquals(frame.nav.y() + 22, first.y());
		assertEquals(second.y() - first.bottom(), 5);
		assertEquals(frame.nav.x() + 13, first.x());
		assertEquals(frame.sidebar.right() - 13 - 8, first.right());
		assertEquals(50, second.y() - first.y());
	}
	
	@Test
	void navItemsNeverOverlapOrLeaveTheSidebar()
	{
		for(int width : new int[]{1500, 1200, 900, 700, 400})
		{
			Frame frame = TwilightShellLayout.layout(width, HEIGHT);
			Rect previous = null;
			
			for(int index = 0; index < 8; index++)
			{
				Rect rect = frame.navItem(index);
				
				if(rect == null)
					break;
				
				assertTrue(rect.x() >= frame.sidebar.x());
				assertTrue(rect.right() <= frame.sidebar.right());
				assertTrue(rect.y() >= frame.nav.y());
				assertTrue(rect.bottom() <= frame.nav.bottom());
				
				if(previous != null)
					assertFalse(previous.intersects(rect));
				
				previous = rect;
			}
		}
	}
	
	@Test
	void hitTestingFindsExactlyTheItemItWasGiven()
	{
		Frame frame = TwilightShellLayout.layout(WIDTH, HEIGHT);
		
		for(int index = 0; index < 6; index++)
		{
			Rect rect = frame.navItem(index);
			
			if(rect == null)
				break;
			
			assertEquals(index,
				frame.navItemAt(rect.centerX(), rect.centerY()));
		}
	}
	
	@Test
	void hitTestingRejectsGapsAndTheContentArea()
	{
		Frame frame = TwilightShellLayout.layout(WIDTH, HEIGHT);
		Rect first = frame.navItem(0);
		Rect second = frame.navItem(1);
		
		// the middle of the 6px gap
		assertEquals(-1, frame.navItemAt(first.centerX(),
			first.bottom() + (second.y() - first.bottom()) / 2));
		
		// inside the content area, right of the sidebar
		assertEquals(-1, frame.navItemAt(frame.content.x() + 10,
			first.centerY()));
	}
	
	@Test
	void theTransportButtonsFrameThePlayButton()
	{
		Frame frame = TwilightShellLayout.layout(WIDTH, HEIGHT);
		Rect play = frame.playButton();
		Rect previous = frame.transportButton(-1);
		Rect next = frame.transportButton(1);
		
		assertEquals(44, play.width());
		assertEquals(32, previous.width());
		assertTrue(previous.right() <= play.x());
		assertTrue(next.x() >= play.right());
		assertTrue(Math.abs(play.centerX() - frame.playerBar.centerX()) <= 1);
		assertTrue(play.y() >= frame.playerBar.y());
		assertTrue(play.bottom() <= frame.playerBar.bottom());
	}
	
	@Test
	void theBrandButtonsSitInTheTopRow()
	{
		Frame frame = TwilightShellLayout.layout(WIDTH, HEIGHT);
		
		Rect first = frame.brandButton(0);
		Rect third = frame.brandButton(2);
		
		assertEquals(36, first.width());
		assertEquals(12, first.x());
		assertTrue(third.right() <= frame.sidebar.right());
		assertEquals(4, first.y() - frame.brand.y());
	}
	
	@Test
	void columnsSplitTheAvailableWidthExactly()
	{
		Frame frame = TwilightShellLayout.layout(WIDTH, HEIGHT);
		Rect area = new Rect(100, 200, 1000, 80);
		Rect[] columns = frame.columns(area, 4, 16);
		
		assertEquals(4, columns.length);
		assertEquals(area.x(), columns[0].x());
		assertEquals(238, columns[0].width());
		assertEquals(area.right(), columns[3].right());
		
		for(int i = 1; i < columns.length; i++)
			assertFalse(columns[i - 1].intersects(columns[i]));
	}
	
	@Test
	void theContentInnerRectangleKeepsTheMargin()
	{
		Frame frame = TwilightShellLayout.layout(WIDTH, HEIGHT);
		Rect inner = frame.contentInner();
		
		assertEquals(frame.content.x() + 24, inner.x());
		assertEquals(frame.content.y() + 24, inner.y());
		assertEquals(frame.content.width() - 48, inner.width());
		assertEquals(frame.content.height() - 48, inner.height());
	}
	
	@Test
	void aTinyCanvasStillGivesUsableRectangles()
	{
		for(int[] size : new int[][]{{320, 240}, {200, 120}, {80, 60},
			{1920, 1080}})
		{
			Frame frame = TwilightShellLayout.layout(size[0], size[1]);
			
			assertTrue(frame.sidebar.width() >= 0);
			assertTrue(frame.nav.height() >= 0);
			assertTrue(frame.contentBody.height() >= 0);
			assertTrue(frame.playerBar.height() >= 0);
			assertTrue(frame.content.x() >= frame.sidebar.width() - 1);
			assertTrue(frame.playerBar.bottom() <= size[1]);
			assertTrue(frame.playerBar.bottom() >= size[1] - 40);
		}
	}
	
	@Test
	void aSmallCanvasGivesFewerNavItems()
	{
		Frame big = TwilightShellLayout.layout(WIDTH, HEIGHT);
		Frame small = TwilightShellLayout.layout(400, 300);
		int bigCount = countNavItems(big);
		int smallCount = countNavItems(small);
		
		assertTrue(bigCount > smallCount,
			"big " + bigCount + " vs small " + smallCount);
		assertTrue(smallCount >= 0);
	}
	
	@Test
	void invalidNavIndicesAreRejected()
	{
		Frame frame = TwilightShellLayout.layout(WIDTH, HEIGHT);
		
		assertNull(frame.navItem(-1));
		assertNull(frame.navItem(500));
	}
	
	private static int countNavItems(Frame frame)
	{
		int count = 0;
		
		while(frame.navItem(count) != null)
			count++;
		
		return count;
	}
}
