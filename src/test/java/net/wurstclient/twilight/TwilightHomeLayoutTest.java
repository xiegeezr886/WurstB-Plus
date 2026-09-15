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
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.wurstclient.twilight.TwilightHomeLayout.Home;
import net.wurstclient.twilight.TwilightShellLayout.Frame;
import net.wurstclient.twilight.TwilightShellLayout.Rect;

/**
 * Tests the geometry of the streaming home page.
 *
 * <p>
 * The values come from {@code StreamingHome.vue}: the hero height follows
 * {@code clamp(280px, 30vw, 340px)}, the copy takes 52% of the inner width, the
 * sections are 44px apart and the three floating covers keep their reference
 * offsets. The test checks those proportions for several canvas sizes instead of
 * pinning one layout.
 */
final class TwilightHomeLayoutTest
{
	private static final int WIDTH = 1500;
	private static final int HEIGHT = 880;
	
	@Test
	void theHeroHeightFollowsTheReferenceClamp()
	{
		Frame frame = TwilightShellLayout.layout(WIDTH, HEIGHT);
		Home home = TwilightHomeLayout.layout(frame);
		
		// 30% of the content width is 385px, so the 340px maximum wins
		assertEquals(340, home.hero.height());
		
		// below 880px the reference stacks the hero, so the stage adds its
		// own 240px band to the copy
		Frame narrow = TwilightShellLayout.layout(800, HEIGHT);
		Home narrowHome = TwilightHomeLayout.layout(narrow);
		
		assertTrue(narrowHome.heroStage.y() >= narrowHome.heroCopy.bottom());
		assertTrue(narrowHome.hero.height() >= 240F * narrow.scale);
		assertEquals(240, Math.round(narrowHome.heroStage.height()
			/ narrow.scale));
	}
	
	@Test
	void theHeroIsSplitIntoCopyAndStage()
	{
		Frame frame = TwilightShellLayout.layout(WIDTH, HEIGHT);
		Home home = TwilightHomeLayout.layout(frame);
		int inner = home.hero.width() - frame.px(TwilightHomeLayout.HERO_GAP);
		
		assertEquals(home.hero.x(), home.heroCopy.x());
		assertEquals(Math.round(inner * TwilightHomeLayout.HERO_COPY_SHARE),
			home.heroCopy.width());
		assertEquals(home.heroCopy.right() + frame.px(TwilightHomeLayout.HERO_GAP),
			home.heroStage.x());
		assertEquals(home.hero.right(), home.heroStage.right());
		assertEquals(home.hero.height(), home.heroStage.height());
		assertFalse(home.heroCopy.intersects(home.heroStage));
	}
	
	@Test
	void theCopyPaddingFollowsTheReferenceClamp()
	{
		Frame frame = TwilightShellLayout.layout(WIDTH, HEIGHT);
		Home home = TwilightHomeLayout.layout(frame);
		float expected = Math.min(TwilightHomeLayout.COPY_PADDING_MAX,
			TwilightHomeLayout.COPY_PADDING_MIN);
		
		// a 1140px content area is wide enough for the 48px maximum
		assertEquals(TwilightHomeLayout.COPY_PADDING_MAX,
			home.copyPadding / frame.scale, 0.01F);
		assertTrue(home.copyPadding >= expected);
	}
	
	@Test
	void theCopyColumnStaysInsideTheHero()
	{
		Frame frame = TwilightShellLayout.layout(WIDTH, HEIGHT);
		Home home = TwilightHomeLayout.layout(frame);
		
		assertTrue(home.dayBadge.x() >= home.hero.x());
		assertTrue(home.dayBadge.y() >= home.hero.y());
		assertTrue(home.primaryCta.x() >= home.hero.x());
		assertTrue(home.secondaryCta.right() <= home.heroCopy.right());
		assertTrue(home.primaryCta.bottom() <= home.hero.bottom());
		assertTrue(home.primaryCta.right() < home.secondaryCta.x());
		assertEquals(TwilightHomeLayout.CTA_HEIGHT,
			home.primaryCta.height() / frame.scale, 0.6F);
		assertEquals(TwilightHomeLayout.CTA_HEIGHT,
			home.secondaryCta.height() / frame.scale, 0.6F);
	}
	
	@Test
	void theThreeCoversKeepTheirReferenceOffsets()
	{
		Frame frame = TwilightShellLayout.layout(WIDTH, HEIGHT);
		Home home = TwilightHomeLayout.layout(frame);
		Rect[] covers = home.collage(frame);
		
		assertEquals(3, covers.length);
		
		// the first cover is the biggest; by x the reference puts cover 1
		// closest to the right edge (right: 7%), then cover 2 (12%) and the big
		// one last (24%), so it ends up furthest left
		assertTrue(covers[0].width() > covers[1].width());
		assertTrue(covers[1].width() > covers[2].width());
		assertTrue(covers[0].x() < covers[2].x());
		assertTrue(covers[2].x() < covers[1].x());
		
		// the first is vertically centred, the second near the top, the third
		// near the bottom
		assertTrue(Math.abs(covers[0].centerY()
			- home.collageArea.centerY()) <= frame.px(20));
		assertTrue(covers[1].y() < covers[0].y());
		assertTrue(covers[2].y() > covers[0].y());
		
		// every card has its own clamp, and none of them leaves the stage
		for(int i = 0; i < covers.length; i++)
		{
			assertTrue(covers[i].width() >= frame.px(
				TwilightHomeLayout.COLLAGE_MIN[i]) * 0.9F,
				"cover " + i + " is " + covers[i].width());
			assertTrue(covers[i].right() <= home.collageArea.right() + 1);
			assertTrue(covers[i].y() >= home.collageArea.y() - 1);
		}
	}
	
	@Test
	void theDuoRowSplitsTheContentInHalf()
	{
		Frame frame = TwilightShellLayout.layout(WIDTH, HEIGHT);
		Home home = TwilightHomeLayout.layout(frame);
		
		assertEquals(home.hero.right(), home.duoRow.right());
		assertEquals(home.hero.x(), home.duoRow.x());
		assertEquals(frame.px(TwilightHomeLayout.DUO_GAP),
			home.duoCardRight.x() - home.duoCardLeft.right());
		assertEquals(home.duoCardLeft.width(), home.duoCardRight.width());
		assertTrue(home.duoCardLeft.height() >= frame.px(112));
		
		// the sections are 44px apart
		assertEquals(frame.px(TwilightHomeLayout.SECTION_GAP),
			home.duoRow.y() - home.hero.bottom());
		assertEquals(frame.px(TwilightHomeLayout.SECTION_GAP),
			home.sectionHead.y() - home.duoRow.bottom());
	}
	
	@Test
	void aDuoCardHasThreeStackedCoversAndAnArrow()
	{
		Frame frame = TwilightShellLayout.layout(WIDTH, HEIGHT);
		Home home = TwilightHomeLayout.layout(frame);
		Rect[] covers = TwilightHomeLayout.duoCovers(frame, home.duoCardLeft);
		Rect arrow = TwilightHomeLayout.duoArrow(frame, home.duoCardLeft);
		
		assertEquals(3, covers.length);
		assertEquals(frame.px(TwilightHomeLayout.DUO_COVER), covers[0].width());
		
		for(int i = 1; i < covers.length; i++)
			assertTrue(covers[i].x() > covers[i - 1].x());
		
		assertEquals(frame.px(TwilightHomeLayout.DUO_ARROW), arrow.width());
		assertTrue(arrow.right() <= home.duoCardLeft.right());
		assertTrue(arrow.x() > covers[2].right());
	}
	
	@Test
	void theSectionHeadHasAMoreLinkOnTheRight()
	{
		Frame frame = TwilightShellLayout.layout(WIDTH, HEIGHT);
		Home home = TwilightHomeLayout.layout(frame);
		
		assertEquals(home.sectionHead.right(), home.sectionMore.right());
		assertEquals(TwilightHomeLayout.SECTION_MORE_HEIGHT,
			home.sectionMore.height() / frame.scale, 0.6F);
		assertTrue(home.sectionMore.x() > home.sectionHead.centerX());
	}
	
	@Test
	void everythingStaysInsideTheContentArea()
	{
		for(int[] size : new int[][]{{1500, 880}, {1100, 700}, {800, 600},
			{1920, 1080}})
		{
			Frame frame = TwilightShellLayout.layout(size[0], size[1]);
			Home home = TwilightHomeLayout.layout(frame);
			Rect body = frame.contentBody;
			
			for(Rect rect : new Rect[]{home.hero, home.duoRow,
				home.sectionHead})
			{
				assertTrue(rect.x() >= body.x() - 1, rect + " vs " + body);
				assertTrue(rect.right() <= body.right() + 1);
			}
		}
	}
	
	@Test
	void theHeroDoesNotOverlapThePlayerBar()
	{
		Frame frame = TwilightShellLayout.layout(WIDTH, HEIGHT);
		Home home = TwilightHomeLayout.layout(frame);
		
		assertTrue(home.hero.y() >= frame.contentBody.y());
		assertTrue(home.sectionHead.y() < frame.playerBar.y());
	}
}
