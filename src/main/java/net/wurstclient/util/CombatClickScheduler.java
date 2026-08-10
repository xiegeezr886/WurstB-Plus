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
import java.util.Objects;
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
		applyConfiguration(minimumCps, maximumCps, pattern, minimumCooldown,
			maximumCooldown);
		resetTiming(nowMillis);
		newCooldown();
		fill();
	}

	public void configure(int minimumCps, int maximumCps,
		ClickPattern pattern, float minimumCooldown, float maximumCooldown)
	{
		if(applyConfiguration(minimumCps, maximumCps, pattern,
			minimumCooldown, maximumCooldown))
			fill();
	}

	private boolean applyConfiguration(int minimumCps, int maximumCps,
		ClickPattern pattern, float minimumCooldown, float maximumCooldown)
	{
		int newMinimumCps = Math.max(1,
			Math.min(minimumCps, maximumCps));
		int newMaximumCps = Math.max(newMinimumCps,
			Math.max(minimumCps, maximumCps));
		float firstCooldown = sanitizeCooldown(minimumCooldown);
		float secondCooldown = sanitizeCooldown(maximumCooldown);
		float newMinimumCooldown = Math.min(firstCooldown, secondCooldown);
		float newMaximumCooldown = Math.max(newMinimumCooldown,
			Math.max(firstCooldown, secondCooldown));
		ClickPattern newPattern = Objects.requireNonNull(pattern, "pattern");
		boolean refill = this.minimumCps != newMinimumCps
			|| this.maximumCps != newMaximumCps || this.pattern != newPattern;

		this.minimumCps = newMinimumCps;
		this.maximumCps = newMaximumCps;
		this.minimumCooldown = newMinimumCooldown;
		this.maximumCooldown = newMaximumCooldown;
		this.pattern = newPattern;
		return refill;
	}

	private float sanitizeCooldown(float cooldown)
	{
		return Float.isFinite(cooldown) ? Math.max(0, cooldown) : 0;
	}

	public void advanceTick()
	{
		if(ticksSinceLastClick < Integer.MAX_VALUE)
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
		if(tick < 0)
			throw new IllegalArgumentException("tick must not be negative");
		Objects.requireNonNull(cooldownPassed, "cooldownPassed");
		if(cooldownPassed.test(tick)
			|| elapsedMillis(nowMillis, tick) >= 1000L)
			return 1;
		return clickArray.get(tick);
	}

	private long elapsedMillis(long nowMillis, int tick)
	{
		long elapsed = nowMillis >= lastClickTime
			? nowMillis - lastClickTime : 0;
		long prediction = tick * 50L;
		return elapsed > Long.MAX_VALUE - prediction ? Long.MAX_VALUE
			: elapsed + prediction;
	}

	public boolean willClickAt(IntPredicate cooldownPassed, long nowMillis,
		int tick)
	{
		return getClickAmount(cooldownPassed, nowMillis, tick) > 0;
	}

	public int getTicksUntilClick(IntPredicate cooldownPassed, long nowMillis)
	{
		Objects.requireNonNull(cooldownPassed, "cooldownPassed");
		for(int i = 0; i < clickArray.length(); i++)
			if(willClickAt(cooldownPassed, nowMillis, i))
				return i;
		return clickArray.length();
	}

	public boolean isCooldownPassed(float cooldownProgress)
	{
		return cooldownProgress >= nextCooldown;
	}

	public void beginClickTick()
	{
		clickAmount = 0;
	}

	public void resetTiming(long nowMillis)
	{
		lastClickTime = nowMillis;
		ticksSinceLastClick = 0;
		clickAmount = null;
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
