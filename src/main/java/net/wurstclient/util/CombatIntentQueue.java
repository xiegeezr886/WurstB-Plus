/*
 * Copyright (c) 2025-2026 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

import java.util.Objects;

/**
 * Transfers one combat decision from the planning phase to the input phase.
 */
public final class CombatIntentQueue<T>
{
	private Intent<T> pending = Intent.none();

	public void beginTick()
	{
		pending = Intent.none();
	}

	public void scheduleAttack(T target)
	{
		pending = Intent.attack(Objects.requireNonNull(target, "target"));
	}

	public void scheduleMiss()
	{
		if(pending.kind() == Kind.NONE)
			pending = Intent.miss();
	}

	public Intent<T> consume()
	{
		Intent<T> result = pending;
		pending = Intent.none();
		return result;
	}

	public void clear()
	{
		pending = Intent.none();
	}

	public enum Kind
	{
		NONE,
		ATTACK,
		MISS
	}

	public record Intent<T>(Kind kind, T target)
	{
		public Intent
		{
			Objects.requireNonNull(kind, "kind");
			if(kind == Kind.ATTACK && target == null)
				throw new IllegalArgumentException(
					"attack intent requires a target");
			if(kind != Kind.ATTACK && target != null)
				throw new IllegalArgumentException(
					"only attack intents may have a target");
		}

		public static <T> Intent<T> none()
		{
			return new Intent<>(Kind.NONE, null);
		}

		public static <T> Intent<T> attack(T target)
		{
			return new Intent<>(Kind.ATTACK,
				Objects.requireNonNull(target, "target"));
		}

		public static <T> Intent<T> miss()
		{
			return new Intent<>(Kind.MISS, null);
		}
	}
}
