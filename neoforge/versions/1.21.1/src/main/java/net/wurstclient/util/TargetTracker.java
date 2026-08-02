/*
 * This file contains a Forge/Mojmap adaptation of LiquidBounce's target
 * tracker switching behavior.
 *
 * Copyright (c) 2015-2026 CCBlueX
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

public final class TargetTracker<T>
{
	private T target;
	private int switchCooldown;

	public void tick()
	{
		if(switchCooldown > 0)
			switchCooldown--;
	}

	public T update(T candidate, Predicate<T> validator,
		ToDoubleFunction<T> score, boolean sticky, int switchDelay,
		double switchAdvantagePercent)
	{
		if(sticky && isValid(validator, target))
			return target;
		if(!isValid(validator, target))
			return replace(isValid(validator, candidate) ? candidate : null,
				switchDelay);
		if(candidate == null || candidate == target)
			return target;
		if(switchCooldown > 0)
			return target;

		double currentScore = score.applyAsDouble(target);
		double candidateScore = score.applyAsDouble(candidate);
		double requiredAdvantage = Math.max(0.05,
			Math.abs(currentScore) * Math.max(0, switchAdvantagePercent) / 100);
		if(candidateScore + requiredAdvantage < currentScore)
			return replace(candidate, switchDelay);
		return target;
	}

	private boolean isValid(Predicate<T> validator, T value)
	{
		return value != null && validator.test(value);
	}

	private T replace(T candidate, int switchDelay)
	{
		target = candidate;
		switchCooldown = Math.max(0, switchDelay);
		return target;
	}

	public T getTarget()
	{
		return target;
	}

	public void reset()
	{
		target = null;
		switchCooldown = 0;
	}
}
