/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests the still-image motion, which is what makes a plain wallpaper feel
 * alive. The pose has to stay deterministic and stay inside the image, or the
 * background shows an edge.
 */
final class BackgroundMotionTest
{
	private static final long CYCLE = 40_000L;

	@Test
	void noneIsTheIdentity()
	{
		BackgroundPose pose =
			BackgroundMotion.NONE.compute(12_345L, 1F, 1920, 1080, 100, 100);

		assertEquals(1F, pose.scale(), 0.0001F);
		assertEquals(0F, pose.offsetX(), 0.0001F);
		assertEquals(0F, pose.offsetY(), 0.0001F);
	}

	/** Zero strength, a zero viewport and a zero-size canvas all mean still. */
	@Test
	void degenerateInputIsStill()
	{
		for(BackgroundMotion motion : BackgroundMotion.values())
		{
			assertEquals(1F, motion.compute(5_000L, 0F, 1920, 1080, 0, 0)
				.scale(), 0.0001F);
			assertEquals(1F, motion.compute(5_000L, 1F, 0, 0, 0, 0).scale(),
				0.0001F);
		}
	}

	/** The same clock and pointer always give the same pose. */
	@Test
	void isDeterministic()
	{
		for(BackgroundMotion motion : BackgroundMotion.values())
		{
			BackgroundPose first =
				motion.compute(7_777L, 0.8F, 1280, 720, 400, 300);
			BackgroundPose second =
				motion.compute(7_777L, 0.8F, 1280, 720, 400, 300);

			assertEquals(first.scale(), second.scale(), 0F);
			assertEquals(first.offsetX(), second.offsetX(), 0F);
			assertEquals(first.offsetY(), second.offsetY(), 0F);
		}
	}

	/**
	 * Every motion that pans is drawn larger than the viewport, so a pan can
	 * never reveal the edge of the image.
	 */
	@Test
	void panningMotionsStayOverscanned()
	{
		for(BackgroundMotion motion : new BackgroundMotion[]{
			BackgroundMotion.KEN_BURNS, BackgroundMotion.PARALLAX,
			BackgroundMotion.DRIFT})
		{
			for(int step = 0; step < 40; step++)
			{
				long now = step * (CYCLE / 40);
				BackgroundPose pose =
					motion.compute(now, 1F, 1280, 720, 0, 0);

				assertTrue(pose.scale() >= 1F,
					motion + " 不应缩到比视口还小");
				assertTrue(pose.scale() <= 1.15F,
					motion + " 放大过头了");

				float halfTravelX = 1280 * (pose.scale() - 1F) / 2F + 1F;
				float halfTravelY = 720 * (pose.scale() - 1F) / 2F + 1F;

				assertTrue(Math.abs(pose.offsetX()) <= halfTravelX,
					motion + " 横向偏移露出了边缘");
				assertTrue(Math.abs(pose.offsetY()) <= halfTravelY,
					motion + " 纵向偏移露出了边缘");
			}
		}
	}

	/** Parallax actually follows the pointer, and flips side with it. */
	@Test
	void parallaxFollowsThePointer()
	{
		BackgroundPose left = BackgroundMotion.PARALLAX.compute(0L, 1F, 1280, 720,
			0, 360);
		BackgroundPose right = BackgroundMotion.PARALLAX.compute(0L, 1F, 1280,
			720, 1280, 360);

		assertTrue(left.offsetX() > 0);
		assertTrue(right.offsetX() < 0);
		assertEquals(0F, left.offsetY(), 0.5F);
	}

	@Test
	void strengthScalesWithTheViewport()
	{
		assertEquals(0F, BackgroundMotion.strengthFor(1F, 0), 0.0001F);
		assertEquals(1F, BackgroundMotion.strengthFor(1F, 1280), 0.0001F);
		// clamped, so an absurd setting cannot throw the image off screen
		assertTrue(BackgroundMotion.strengthFor(5F, 1920) <= 1.5F);
		assertTrue(BackgroundMotion.strengthFor(-5F, 1280) == 0F);
	}
}
