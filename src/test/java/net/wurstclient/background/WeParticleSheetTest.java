/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 粒子帧格的取值与帧号语义（{@link WeParticleSheet}）。
 *
 * <p>
 * 帧格本身来自 {@code .tex-json} 的声明，那些用例在 {@link WeTextureSheetTest}；
 * 这里只管两件事：声明出来的数字有没有被正确接住，以及"寿命 → 帧号"是否与 WE 的
 * {@code ComputeSpriteFrame()} 同语义（{@code floor(lifetime * numFrames)}，上界夹住）。
 * </p>
 */
final class WeParticleSheetTest
{
	/** 声明了 8×8 共 64 帧的贴图（雾那种），帧格就照抄声明。 */
	@Test
	void takesFrameGridFromDeclaredFrames()
	{
		WeParticleSheet sheet = WeParticleSheet
			.of(new WeTextureSheet(64, 128, 128, 8, 8, 1));

		assertTrue(sheet.isSheet());
		assertEquals(8, sheet.columns());
		assertEquals(8, sheet.rows());
		assertEquals(64, sheet.frames());
	}

	/** 单帧的三种来路：SINGLE 常量、空声明、null。 */
	@Test
	void fallsBackToSingleFrame()
	{
		assertFalse(WeParticleSheet.SINGLE.isSheet());
		assertFalse(WeParticleSheet.of(null).isSheet());
		assertFalse(WeParticleSheet.of(WeTextureSheet.SINGLE).isSheet());
		assertEquals(WeParticleSheet.SINGLE, WeParticleSheet.of(null));
	}

	/** 声明的帧数不能超过格子数，否则行优先取值会越界。 */
	@Test
	void rejectsFrameCountThatDoesNotFitTheGrid()
	{
		assertThrows(IllegalArgumentException.class,
			() -> new WeParticleSheet(2, 2, 5));
		assertThrows(IllegalArgumentException.class,
			() -> new WeParticleSheet(0, 2, 1));
		assertThrows(IllegalArgumentException.class,
			() -> new WeParticleSheet(2, 2, 0));
	}

	/** 寿命进度 → 帧号：floor(进度 × 帧数)，两端夹住（与 WE 的 currentFrame 一致）。 */
	@Test
	void mapsLifeFractionToFrame()
	{
		WeParticleSheet sheet = new WeParticleSheet(8, 8, 64);

		assertEquals(0, sheet.frameOf(0));
		assertEquals(0, sheet.frameOf(0.015F));
		assertEquals(1, sheet.frameOf(0.0157F));
		assertEquals(32, sheet.frameOf(0.5F));
		assertEquals(63, sheet.frameOf(0.99F));
		assertEquals(63, sheet.frameOf(1));
		assertEquals(63, sheet.frameOf(1.5F));
		assertEquals(0, sheet.frameOf(-1));
	}

	/** 帧数少于格子数时（WE 里有 frames:3 的贴图），最后一帧仍是 frames-1。 */
	@Test
	void clampsToTheDeclaredFrameCountNotTheGridSize()
	{
		WeParticleSheet sheet = new WeParticleSheet(8, 8, 3);

		assertEquals(2, sheet.frameOf(0.99F));
		assertEquals(2, sheet.frameOf(1));
	}

	/** 帧号 → 行列：按行优先，和 WE 的 {@code floor/frac(currentFrame * frameWidth)} 一致。 */
	@Test
	void splitsFrameIntoRowAndColumn()
	{
		WeParticleSheet sheet = new WeParticleSheet(8, 8, 64);

		assertEquals(0, sheet.columnOf(0));
		assertEquals(0, sheet.rowOf(0));
		assertEquals(7, sheet.columnOf(7));
		assertEquals(0, sheet.rowOf(7));
		assertEquals(0, sheet.columnOf(8));
		assertEquals(1, sheet.rowOf(8));
		assertEquals(7, sheet.columnOf(63));
		assertEquals(7, sheet.rowOf(63));
	}
}
