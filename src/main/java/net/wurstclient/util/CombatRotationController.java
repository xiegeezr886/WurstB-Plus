/*
 * Copyright (c) 2015-2026 CCBlueX
 * Copyright (c) 2025-2026 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Owns the planned rotation, acceleration history and silent request queue.
 */
public final class CombatRotationController
{
	private static final Rotation ZERO = new Rotation(0, 0);

	private final RotationQueue queue;
	private Rotation planned;
	private Rotation delta = ZERO;

	public CombatRotationController(RotationQueue.Priority priority)
	{
		queue = new RotationQueue(Objects.requireNonNull(priority, "priority"));
	}

	public void start()
	{
		queue.start();
	}

	public void stop()
	{
		queue.stop();
		planned = null;
		delta = ZERO;
	}

	public void clear()
	{
		queue.clear();
		planned = null;
		delta = ZERO;
	}

	public void resetAcceleration()
	{
		delta = ZERO;
	}

	public Rotation plan(Rotation fallbackStart, Rotation target,
		float maxChange, float maxAcceleration, RotationSmoothing smoothing)
	{
		Rotation start = planned != null ? planned
			: Objects.requireNonNull(fallbackStart, "fallbackStart");
		RotationSmoothing.Step step = RotationSmoothing.smoothWithAcceleration(
			start, target, delta, maxChange, maxAcceleration, smoothing);
		planned = step.rotation();
		delta = step.delta();
		return planned;
	}

	public Rotation planExact(Rotation fallbackStart, Rotation target)
	{
		Rotation start = planned != null ? planned
			: Objects.requireNonNull(fallbackStart, "fallbackStart");
		Rotation sanitized = RotationSmoothing.smooth(start, target,
			Float.MAX_VALUE, RotationSmoothing.LINEAR);
		delta = new Rotation(
			net.minecraft.util.Mth.wrapDegrees(sanitized.yaw() - start.yaw()),
			sanitized.pitch() - start.pitch());
		planned = sanitized;
		return planned;
	}

	public void requestSilent()
	{
		if(planned != null)
			queue.setRotation(planned);
	}

	public void request(Rotation target)
	{
		Rotation fallback = planned != null ? planned : ZERO;
		planned = RotationSmoothing.smooth(fallback,
			Objects.requireNonNull(target, "target"), Float.MAX_VALUE,
			RotationSmoothing.LINEAR);
		delta = ZERO;
		queue.setRotation(planned);
	}

	public void applyClient(Consumer<Rotation> consumer)
	{
		Objects.requireNonNull(consumer, "consumer");
		queue.clear();
		if(planned != null)
			consumer.accept(planned);
	}

	public void clearRequest()
	{
		queue.clear();
	}

	public Rotation getPlanned()
	{
		return planned;
	}

	Rotation getDelta()
	{
		return delta;
	}
}
