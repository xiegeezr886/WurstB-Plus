/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.mojang.blaze3d.platform.NativeImage;

/**
 * 粒子帧图集的判定（{@link WeParticleSheet}）。
 *
 * <p>
 * 三个"真实样本"的行为用合成图复刻，因为真贴图在用户的工坊目录里、不能当测试依赖：
 * 图集 = 每个帧格内画同样的图案（平移一帧完全重合）；无周期纹理 = 平滑渐变（残差大）；
 * 纯色 = 平移多少都一样（残差全 0，比值判据必须否掉它）。
 * </p>
 */
final class WeParticleSheetTest
{
	/**
	 * 4×4 帧、每帧 16px：每格在**同一相对位置**画一小块亮斑，所以帧宽就是 16。
	 *
	 * <p>
	 * 亮斑必须在每格里同一相对位置 —— 投影法看的就是"每格对剖面贡献同样的形状"。
	 * 早先那版用棋盘格当素材，结果列投影是常数（棋盘每列明暗各半），任何周期都"相等"，
	 * 判据正确地拒绝了它：**是素材不像图集，不是代码错**。</p>
	 */
	@Test
	void detectsRepeatingSheet()
	{
		NativeImage image = image(64, (x, y) ->
			x % 16 >= 4 && x % 16 < 12 && y % 16 >= 4 && y % 16 < 12 ? 240 : 10);

		WeParticleSheet sheet = WeParticleSheet.detect(image);

		assertTrue(sheet.isSheet());
		assertEquals(4, sheet.columns());
		assertEquals(4, sheet.rows());
		assertEquals(16, sheet.frames());
	}

	/** 平滑渐变：没有周期，必须是单帧（否则会把整张图切碎）。 */
	@Test
	void treatsSmoothGradientAsSingleFrame()
	{
		NativeImage image = image(64, (x, y) -> (x + y) * 2);

		assertFalse(WeParticleSheet.detect(image).isSheet());
	}

	/** 纯色：平移多少残差都是 0，比值判据不成立 —— 不能被当成图集。 */
	@Test
	void treatsUniformImageAsSingleFrame()
	{
		assertFalse(WeParticleSheet.detect(image(64, (x, y) -> 128)).isSheet());
	}

	/** 非正方形、太小、null 都按单帧处理。 */
	@Test
	void handlesOddInput()
	{
		assertFalse(WeParticleSheet.detect(null).isSheet());
		assertFalse(WeParticleSheet.detect(image(16, (x, y) -> x)).isSheet());

		NativeImage wide =
			new NativeImage(NativeImage.Format.RGBA, 64, 32, false);
		assertFalse(WeParticleSheet.detect(wide).isSheet());
	}

	/** 寿命进度 → 帧号：floor(进度 × 帧数)，两端夹住。 */
	@Test
	void mapsLifeFractionToFrame()
	{
		WeParticleSheet sheet = new WeParticleSheet(4, 4);

		assertEquals(0, sheet.frameOf(0));
		assertEquals(0, sheet.frameOf(0.06F));
		assertEquals(1, sheet.frameOf(0.0625F));
		assertEquals(8, sheet.frameOf(0.5F));
		assertEquals(15, sheet.frameOf(0.99F));
		assertEquals(15, sheet.frameOf(1));
		assertEquals(15, sheet.frameOf(1.5F));
		assertEquals(0, sheet.frameOf(-1));
	}

	/** 帧号 → 行列：按行优先，和 WE 的 {@code currentFrame} 一致。 */
	@Test
	void splitsFrameIntoRowAndColumn()
	{
		WeParticleSheet sheet = new WeParticleSheet(8, 8);

		assertEquals(0, sheet.columnOf(0));
		assertEquals(0, sheet.rowOf(0));
		assertEquals(7, sheet.columnOf(7));
		assertEquals(0, sheet.rowOf(7));
		assertEquals(0, sheet.columnOf(8));
		assertEquals(1, sheet.rowOf(8));
		assertEquals(7, sheet.columnOf(63));
		assertEquals(7, sheet.rowOf(63));
	}

	private static NativeImage image(int size, Pixel pixel)
	{
		NativeImage image =
			new NativeImage(NativeImage.Format.RGBA, size, size, false);

		for(int y = 0; y < size; y++)
			for(int x = 0; x < size; x++)
			{
				int value = Math.max(0, Math.min(255, pixel.at(x, y)));
				image.setPixelRGBA(x, y,
					0xFF000000 | value << 16 | value << 8 | value);
			}

		return image;
	}

	private interface Pixel
	{
		int at(int x, int y);
	}
}
