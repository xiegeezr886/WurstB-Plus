/*
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

public enum CrystalAuraPlanner
{
	;

	public static <T> Candidate<T> selectBest(
		Iterable<Candidate<T>> candidates, float minDamage, float maxSelfDamage,
		float minDamageAdvantage, float availableHealth, boolean antiSuicide,
		int minAge)
	{
		Candidate<T> best = null;
		float bestScore = Float.NEGATIVE_INFINITY;

		for(Candidate<T> candidate : candidates)
		{
			if(!isAllowed(candidate, minDamage, maxSelfDamage,
				minDamageAdvantage, availableHealth, antiSuicide, minAge))
				continue;

			float score = candidate.targetDamage() - candidate.selfDamage() * 0.25F;
			if(best == null || score > bestScore
				|| score == bestScore
					&& isBetterTie(candidate, best))
			{
				best = candidate;
				bestScore = score;
			}
		}
		return best;
	}

	public static boolean isAllowed(Candidate<?> candidate, float minDamage,
		float maxSelfDamage, float minDamageAdvantage, float availableHealth,
		boolean antiSuicide, int minAge)
	{
		if(candidate == null || candidate.age() < minAge
			|| candidate.targetDamage() < minDamage
			|| candidate.selfDamage() > maxSelfDamage
			|| candidate.targetDamage() - candidate.selfDamage()
				< minDamageAdvantage)
			return false;

		return !antiSuicide || candidate.selfDamage() < availableHealth;
	}

	private static boolean isBetterTie(Candidate<?> candidate,
		Candidate<?> current)
	{
		if(candidate.targetDamage() != current.targetDamage())
			return candidate.targetDamage() > current.targetDamage();
		if(candidate.selfDamage() != current.selfDamage())
			return candidate.selfDamage() < current.selfDamage();
		return candidate.distanceSq() < current.distanceSq();
	}

	public record Candidate<T>(T value, float targetDamage, float selfDamage,
		double distanceSq, int age)
	{
	}
}
