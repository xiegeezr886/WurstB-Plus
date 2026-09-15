package net.wurstclient.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class JumpHeightSolverTest
{
	/**
	 * 自检：原版跳跃初速 0.42（{@code LivingEntity#getJumpPower()} 里的
	 * {@code 0.42F}）对应的最高点是众所周知的 1.2522 格。这条断言的意义在于
	 * 证明本类的逐 tick 积分与原版竖直运动一致，而不是我自己编了一套公式。
	 */
	@Test
	void vanillaJumpGetsTheKnownApex()
	{
		assertEquals(1.2522, JumpHeightSolver.apexHeight(0.42), 0.0001);
	}

	/** 滑块上每一个整数格都必须被精确反解（1..100 全查）。 */
	@Test
	void solvesEverySliderValueExactly()
	{
		for(int blocks = 1; blocks <= 100; blocks++)
			assertEquals(blocks,
				JumpHeightSolver.apexHeight(
					JumpHeightSolver.requiredVelocity(blocks)),
				1.0E-6, "Height=" + blocks);
	}

	/**
	 * 把旧实现（{@code v0 = 0.42 + Height * 0.1}）的实际最高点钉死，用来量化
	 * 「Height 设置不准」这件事：默认值偏 2%，20 格偏 39%，100 格偏到 2.7 倍。
	 */
	@Test
	void oldFormulaOvershootsAtHigherValues()
	{
		assertEquals(6.135,
			JumpHeightSolver.apexHeight(0.42 + 6 * 0.1), 0.001);
		assertEquals(27.718,
			JumpHeightSolver.apexHeight(0.42 + 20 * 0.1), 0.001);
		assertEquals(269.352,
			JumpHeightSolver.apexHeight(0.42 + 100 * 0.1), 0.001);
	}

	/**
	 * {@code requiredVelocity} 必须单调递增（反解用的二分若写错，这里会先炸）。
	 */
	@Test
	void requiredVelocityIsMonotonic()
	{
		double previous = 0;
		for(int blocks = 1; blocks <= 100; blocks++)
		{
			double velocity = JumpHeightSolver.requiredVelocity(blocks);
			assertTrue(velocity > previous, "Height=" + blocks);
			previous = velocity;
		}
	}

	@Test
	void nonPositiveHeightNeedsNoVelocity()
	{
		assertEquals(0, JumpHeightSolver.requiredVelocity(0), 0.0);
		assertEquals(0, JumpHeightSolver.requiredVelocity(-5), 0.0);
		assertEquals(0, JumpHeightSolver.apexHeight(0), 0.0);
		assertEquals(0, JumpHeightSolver.apexHeight(-1), 0.0);
	}
}
