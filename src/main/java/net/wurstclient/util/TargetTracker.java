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

import java.util.Objects;
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
		Objects.requireNonNull(validator, "validator");
		Objects.requireNonNull(score, "score");
		boolean currentValid = isValid(validator, target);
		if(sticky && currentValid)
			return target;
		boolean candidateValid = !Objects.equals(candidate, target)
			&& isValid(validator, candidate);
		if(!currentValid)
			return replace(candidateValid ? candidate : null,
				switchDelay);
		if(!candidateValid)
			return target;
		if(switchCooldown > 0)
			return target;

		double currentScore = score.applyAsDouble(target);
		double candidateScore = score.applyAsDouble(candidate);
		if(!Double.isFinite(candidateScore))
			return target;
		if(!Double.isFinite(currentScore))
			return replace(candidate, switchDelay);
		double advantagePercent = Double.isFinite(switchAdvantagePercent)
			? Math.max(0, switchAdvantagePercent) : 0;
		double requiredAdvantage = Math.max(0.05,
			Math.abs(currentScore) * advantagePercent / 100);
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
