/*
 * This file contains a Forge/Mojmap adaptation of LiquidBounce's click
 * scheduler and item cooldown behavior.
 *
 * Copyright (c) 2015-2026 CCBlueX
 * Copyright (c) 2025-2026 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

import java.util.Arrays;
import java.util.Random;
import java.util.function.IntPredicate;

public final class CombatClickScheduler
{
	private static final int CYCLE_LENGTH = 20;
	private static final int ITERATIONS = 2;

	private final Random random;
	private final RollingClickArray clickArray =
		new RollingClickArray(CYCLE_LENGTH, ITERATIONS);
	private int minimumCps;
	private int maximumCps;
	private float minimumCooldown;
	private float maximumCooldown;
	private float nextCooldown;
	private ClickPattern pattern;
	private long lastClickTime;
	private int ticksSinceLastClick;
	private Integer clickAmount;

	public CombatClickScheduler()
	{
		this(new Random());
	}

	CombatClickScheduler(Random random)
	{
		this.random = random;
	}

	public void reset(int minimumCps, int maximumCps, ClickPattern pattern,
		float minimumCooldown, float maximumCooldown, long nowMillis)
	{
		configure(minimumCps, maximumCps, pattern, minimumCooldown,
			maximumCooldown);
		lastClickTime = nowMillis;
		ticksSinceLastClick = 0;
		clickAmount = null;
		newCooldown();
		fill();
	}

	public void configure(int minimumCps, int maximumCps,
		ClickPattern pattern, float minimumCooldown, float maximumCooldown)
	{
		int newMinimumCps = Math.max(1,
			Math.min(minimumCps, maximumCps));
		int newMaximumCps = Math.max(newMinimumCps,
			Math.max(minimumCps, maximumCps));
		float newMinimumCooldown = Math.max(0,
			Math.min(minimumCooldown, maximumCooldown));
		float newMaximumCooldown = Math.max(newMinimumCooldown,
			Math.max(minimumCooldown, maximumCooldown));
		boolean refill = this.minimumCps != newMinimumCps
			|| this.maximumCps != newMaximumCps || this.pattern != pattern;

		this.minimumCps = newMinimumCps;
		this.maximumCps = newMaximumCps;
		this.minimumCooldown = newMinimumCooldown;
		this.maximumCooldown = newMaximumCooldown;
		this.pattern = pattern;
		if(refill)
			fill();
	}

	public void advanceTick()
	{
		ticksSinceLastClick++;
		clickAmount = null;
		if(clickArray.advance())
		{
			int[] cycle = new int[CYCLE_LENGTH];
			pattern.fill(cycle, minimumCps, maximumCps, random);
			clickArray.push(cycle);
		}
	}

	public int getClickAmount(IntPredicate cooldownPassed, long nowMillis)
	{
		return getClickAmount(cooldownPassed, nowMillis, 0);
	}

	public int getClickAmount(IntPredicate cooldownPassed, long nowMillis,
		int tick)
	{
		if(cooldownPassed.test(tick)
			|| nowMillis - lastClickTime + tick * 50L >= 1000L)
			return 1;
		return clickArray.get(tick);
	}

	public boolean willClickAt(IntPredicate cooldownPassed, long nowMillis,
		int tick)
	{
		return getClickAmount(cooldownPassed, nowMillis, tick) > 0;
	}

	public int getTicksUntilClick(IntPredicate cooldownPassed, long nowMillis)
	{
		for(int i = 0; i < clickArray.getIterations(); i++)
			if(willClickAt(cooldownPassed, nowMillis, i))
				return i;
		return clickArray.getIterations();
	}

	public boolean isCooldownPassed(float cooldownProgress)
	{
		return cooldownProgress >= nextCooldown;
	}

	public void beginClickTick()
	{
		clickAmount = 0;
	}

	public void recordSuccessfulClick(long nowMillis)
	{
		clickAmount = clickAmount == null ? 1 : clickAmount + 1;
		lastClickTime = nowMillis;
		ticksSinceLastClick = 0;
		newCooldown();
	}

	public Integer getExecutedClickAmount()
	{
		return clickAmount;
	}

	public int getTicksSinceLastClick()
	{
		return ticksSinceLastClick;
	}

	float getNextCooldown()
	{
		return nextCooldown;
	}

	private void newCooldown()
	{
		nextCooldown = minimumCooldown == maximumCooldown ? minimumCooldown
			: minimumCooldown
				+ random.nextFloat() * (maximumCooldown - minimumCooldown);
	}

	private void fill()
	{
		if(pattern == null)
			return;

		clickArray.clear();
		int[] cycle = new int[CYCLE_LENGTH];
		for(int i = 0; i < clickArray.getIterations(); i++)
		{
			Arrays.fill(cycle, 0);
			pattern.fill(cycle, minimumCps, maximumCps, random);
			clickArray.push(cycle);
			clickArray.advance(CYCLE_LENGTH);
		}
	}
}
