/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.wurstclient.util.TargetScore.Inputs;
import net.wurstclient.util.TargetScore.Weights;

/**
 * 锁住五个因子的饱和点、方向与权重。打分方向写反（例如"血多的优先"）不会编译报错，
 * 只会在实战里表现为"总是打错人"，所以每个因子都要有正反两面的断言。
 */
public final class TargetScoreTest
{
	private static final float DELTA = 0.0001F;
	private static final float W = TargetScore.DEFAULT_WEIGHT;
	
	@Test
	public void reverseAndClampFlipsAndClamps()
	{
		assertEquals(1F, TargetScore.reverseAndClamp(0F), DELTA);
		assertEquals(0F, TargetScore.reverseAndClamp(1F), DELTA);
		assertEquals(0.5F, TargetScore.reverseAndClamp(0.5F), DELTA);
		// 越界夹取：负数当 0，大于 1 当 1
		assertEquals(1F, TargetScore.reverseAndClamp(-3F), DELTA);
		assertEquals(0F, TargetScore.reverseAndClamp(9F), DELTA);
	}
	
	@Test
	public void distanceSaturatesAtEightBlocks()
	{
		assertEquals(W, TargetScore.distanceFactor(0F, W), DELTA);
		assertEquals(0.5F * W, TargetScore.distanceFactor(4F, W), DELTA);
		assertEquals(0F, TargetScore.distanceFactor(8F, W), DELTA);
		assertEquals(0F, TargetScore.distanceFactor(50F, W), DELTA);
	}
	
	@Test
	public void lowerHealthScoresHigher()
	{
		assertEquals(W, TargetScore.healthFactor(0F, W), DELTA);
		assertEquals(0.5F * W, TargetScore.healthFactor(10F, W), DELTA);
		assertEquals(0F, TargetScore.healthFactor(20F, W), DELTA);
		assertEquals(0F, TargetScore.healthFactor(40F, W), DELTA);
		
		// 方向断言：残血必须比满血分高
		assertTrue(
			TargetScore.healthFactor(4F, W) > TargetScore.healthFactor(18F, W));
	}
	
	@Test
	public void thinnerArmourScoresHigher()
	{
		// 一次 20 点爆炸全吃进去 = 满分
		assertEquals(W, TargetScore.armorFactor(20F, W), DELTA);
		assertEquals(0.5F * W, TargetScore.armorFactor(10F, W), DELTA);
		assertEquals(0F, TargetScore.armorFactor(0F, W), DELTA);
		// 外部输入越界时夹住，不会超过权重
		assertEquals(W, TargetScore.armorFactor(80F, W), DELTA);
		assertEquals(0F, TargetScore.armorFactor(-5F, W), DELTA);
	}
	
	@Test
	public void holeFactorCountsExposedSides()
	{
		assertEquals(0F, TargetScore.holeFactor(0, W), DELTA);
		assertEquals(0.5F * W, TargetScore.holeFactor(2, W), DELTA);
		assertEquals(W, TargetScore.holeFactor(4, W), DELTA);
		// 越界夹到 0..4
		assertEquals(0F, TargetScore.holeFactor(-2, W), DELTA);
		assertEquals(W, TargetScore.holeFactor(9, W), DELTA);
	}
	
	@Test
	public void crosshairFactorSaturatesAtThirtyDegrees()
	{
		assertEquals(W, TargetScore.crosshairFactor(0F, W), DELTA);
		assertEquals(0.5F * W, TargetScore.crosshairFactor(15F, W), DELTA);
		assertEquals(0F, TargetScore.crosshairFactor(30F, W), DELTA);
		assertEquals(0F, TargetScore.crosshairFactor(-90F, W), DELTA);
		
		// 左右对称：偏航取绝对值
		assertEquals(TargetScore.crosshairFactor(-12F, W),
			TargetScore.crosshairFactor(12F, W), DELTA);
	}
	
	@Test
	public void aPerfectTargetHitsTheMaximum()
	{
		Inputs perfect = new Inputs(0F, 0F, 20F, 4, 0F);
		
		assertEquals(TargetScore.maxScore(Weights.DEFAULT),
			TargetScore.score(perfect), DELTA);
		// 默认权重下满分是 5 * 0.5
		assertEquals(2.5F, TargetScore.maxScore(Weights.DEFAULT), DELTA);
	}
	
	@Test
	public void aHopelessTargetScoresZero()
	{
		Inputs hopeless = new Inputs(30F, 20F, 0F, 0, 90F);
		
		assertEquals(0F, TargetScore.score(hopeless), DELTA);
	}
	
	@Test
	public void scoreCanNeverExceedTheMaximum()
	{
		// 即便输入全部越界，总分也不该超过 maxScore
		Inputs absurd = new Inputs(-5F, -5F, 999F, 99, -999F);
		
		assertTrue(
			TargetScore.score(absurd) <= TargetScore
				.maxScore(Weights.DEFAULT) + DELTA,
			"总分越过了上限");
	}
	
	@Test
	public void aCloseWeakTargetBeatsAFarHealthyOne()
	{
		Inputs close = new Inputs(2F, 6F, 16F, 3, 5F);
		Inputs far = new Inputs(7F, 18F, 6F, 1, 25F);
		
		float closeScore = TargetScore.score(close);
		float farScore = TargetScore.score(far);
		
		assertTrue(closeScore > farScore,
			"近的残血目标应优先：" + closeScore + " vs " + farScore);
	}
	
	@Test
	public void scoreIsMonotonicInDistance()
	{
		float previous = Float.MAX_VALUE;
		
		for(float distance = 0F; distance <= 10F; distance += 1F)
		{
			float score =
				TargetScore.score(new Inputs(distance, 10F, 10F, 2, 10F));
			assertTrue(score <= previous, distance + " 格处分数反而升高了");
			previous = score;
		}
	}
	
	@Test
	public void weightsScaleTheirOwnFactorOnly()
	{
		Inputs inputs = new Inputs(0F, 10F, 10F, 2, 15F);
		
		// 只留距离权重：满分应是该权重本身
		Weights distanceOnly = new Weights(1F, 0F, 0F, 0F, 0F);
		assertEquals(1F, TargetScore.score(inputs, distanceOnly), DELTA);
		
		// 只留血量权重：血量 10/20 → 一半
		Weights healthOnly = new Weights(0F, 1F, 0F, 0F, 0F);
		assertEquals(0.5F, TargetScore.score(inputs, healthOnly), DELTA);
		
		// 全零权重 → 任何目标都是 0（等价于关掉打分）
		Weights none = new Weights(0F, 0F, 0F, 0F, 0F);
		assertEquals(0F, TargetScore.score(inputs, none), DELTA);
		assertEquals(0F, TargetScore.maxScore(none), DELTA);
	}
	
	@Test
	public void totalIsTheSumOfTheWeights()
	{
		assertEquals(2.5F, Weights.DEFAULT.total(), DELTA);
		assertEquals(1.7F, new Weights(1F, 0.3F, 0.2F, 0.1F, 0.1F).total(),
			DELTA);
	}
	
	@Test
	public void rankOrdersCandidatesBestFirst()
	{
		// 0 最优（近、残血），2 最差
		List<Inputs> candidates = List.of(new Inputs(1F, 4F, 18F, 3, 2F),
			new Inputs(5F, 12F, 10F, 2, 12F), new Inputs(8F, 20F, 2F, 0, 30F));
		
		assertArrayEquals(new int[]{0, 1, 2},
			TargetScore.rank(candidates, Weights.DEFAULT));
	}
	
	@Test
	public void rankDetectsAReorder()
	{
		// 把最优放在末尾，确认排的是下标而不是原样返回
		List<Inputs> candidates = List.of(new Inputs(8F, 20F, 2F, 0, 30F),
			new Inputs(5F, 12F, 10F, 2, 12F), new Inputs(1F, 4F, 18F, 3, 2F));
		
		assertArrayEquals(new int[]{2, 1, 0},
			TargetScore.rank(candidates, Weights.DEFAULT));
	}
	
	@Test
	public void rankIsStableOnTies()
	{
		Inputs same = new Inputs(3F, 10F, 10F, 2, 10F);
		List<Inputs> candidates = List.of(same, same, same);
		
		// 同分必须保持传入顺序，否则打分会随排序实现抖动
		assertArrayEquals(new int[]{0, 1, 2},
			TargetScore.rank(candidates, Weights.DEFAULT));
	}
	
	@Test
	public void rankHandlesEmptyAndSingle()
	{
		assertEquals(0, TargetScore.rank(List.of(), Weights.DEFAULT).length);
		assertEquals(0, TargetScore.rank(null, Weights.DEFAULT).length);
		assertArrayEquals(new int[]{0},
			TargetScore.rank(List.of(new Inputs(1F, 1F, 1F, 1, 1F)),
				Weights.DEFAULT));
	}
	
	@Test
	public void rankFollowsTheWeights()
	{
		List<Inputs> candidates = List.of(new Inputs(0F, 20F, 0F, 0, 90F),
			new Inputs(8F, 0F, 0F, 0, 90F));
		
		// 只看血量权重：第 1 个（0 血）应该赢
		assertArrayEquals(new int[]{1, 0}, TargetScore.rank(candidates,
			new Weights(0F, 1F, 0F, 0F, 0F)));
		
		// 只看距离权重：第 0 个（0 格）应该赢
		assertArrayEquals(new int[]{0, 1}, TargetScore.rank(candidates,
			new Weights(1F, 0F, 0F, 0F, 0F)));
	}
}
