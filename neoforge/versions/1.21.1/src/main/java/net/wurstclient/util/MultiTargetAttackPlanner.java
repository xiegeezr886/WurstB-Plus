/*
 * Copyright (c) 2015-2026 CCBlueX
 * Copyright (c) 2025-2026 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

public enum MultiTargetAttackPlanner
{
	;

	public static <T> List<T> plan(List<T> candidates,
		Predicate<T> validator, ToIntFunction<T> hurtTime, int maxHurtTime,
		int maxTargets)
	{
		if(candidates == null || candidates.isEmpty() || maxTargets < 0)
			return List.of();

		int hurtTimeLimit = Math.max(0, maxHurtTime);
		var stream = candidates.stream().filter(validator).filter(
			candidate -> hurtTime.applyAsInt(candidate) <= hurtTimeLimit);
		return maxTargets == 0 ? stream.toList()
			: stream.limit(maxTargets).toList();
	}
}
