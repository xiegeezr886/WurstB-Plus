/*
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Queue;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.wurstclient.WurstClient;
import net.wurstclient.events.PreMotionListener;

/**
 * Queue-based rotation system for silent, smooth aim over multiple ticks.
 * Supplies a separate server rotation without changing the client camera.
 */
public final class RotationQueue
{
	private static final Minecraft MC = WurstClient.MC;
	private static final Object LOCK = new Object();
	private static final Set<RotationQueue> ACTIVE = new LinkedHashSet<>();
	private static final RotationDispatcher DISPATCHER = new RotationDispatcher();
	private static boolean dispatcherRegistered;
	private static long requestSequence;

	private final Queue<RotationTarget> targets = new ArrayDeque<>();
	private RotationTarget current;
	private Rotation currentStart;
	private int step;
	private Rotation lastSent;
	private Rotation nextRotation;
	private boolean registered;
	private long requestOrder;
	private Priority priority;

	public RotationQueue()
	{
		this(Priority.NORMAL);
	}

	public RotationQueue(Priority priority)
	{
		this.priority = priority;
	}

	public void setPriority(Priority priority)
	{
		synchronized(LOCK)
		{
			this.priority = priority;
		}
	}

	public void setRotation(Rotation target)
	{
		if(target == null)
			return;
		synchronized(LOCK)
		{
			nextRotation = target;
			requestOrder = ++requestSequence;
			start();
		}
	}

	public void add(Rotation target, int ticks)
	{
		if(target == null)
			return;
		synchronized(LOCK)
		{
			targets.add(new RotationTarget(target, Math.max(1, ticks)));
			requestOrder = ++requestSequence;
			start();
		}
	}

	public void add(Rotation target, float speed)
	{
		if(target == null || MC.player == null)
			return;

		Rotation start;
		synchronized(LOCK)
		{
			start = lastSent != null ? lastSent
				: new Rotation(MC.player.getYRot(), MC.player.getXRot());
		}
		float angle = (float)start.getAngleTo(target);
		int ticks = Math.max(1, (int)Math.ceil(angle / Math.max(1, speed)));
		add(target, ticks);
	}

	public void start()
	{
		synchronized(LOCK)
		{
			if(registered)
				return;
			ACTIVE.add(this);
			registered = true;
			if(!dispatcherRegistered)
			{
				WurstClient.INSTANCE.getEventManager()
					.add(PreMotionListener.class, DISPATCHER);
				dispatcherRegistered = true;
			}
		}
	}

	public void stop()
	{
		synchronized(LOCK)
		{
			if(!registered)
				return;
			ACTIVE.remove(this);
			registered = false;
			clear();
		}
	}

	public void clear()
	{
		synchronized(LOCK)
		{
			targets.clear();
			current = null;
			currentStart = null;
			step = 0;
			lastSent = null;
			nextRotation = null;
			requestOrder = 0;
		}
	}

	public boolean isEmpty()
	{
		synchronized(LOCK)
		{
			return !hasWork();
		}
	}

	public Rotation getLastSent()
	{
		synchronized(LOCK)
		{
			return lastSent;
		}
	}

	private boolean hasWork()
	{
		return current != null || !targets.isEmpty() || nextRotation != null;
	}

	private Rotation pollRotation()
	{
		if(MC.player == null)
			return null;
		if(nextRotation != null)
		{
			Rotation result = nextRotation;
			lastSent = result;
			nextRotation = null;
			return result;
		}

		if(current == null)
		{
			current = targets.poll();
			if(current == null)
				return null;

			step = 0;
			if(lastSent == null)
				lastSent = new Rotation(MC.player.getYRot(),
					MC.player.getXRot());
			currentStart = lastSent;
		}

		step++;
		float progress = (float)step / current.ticks;
		Rotation next = interpolate(currentStart, current.target, progress);

		if(step >= current.ticks)
		{
			lastSent = current.target;
			current = null;
			currentStart = null;
		}else
			lastSent = next;
		return next;
	}

	private static final class RotationDispatcher
		implements PreMotionListener
	{
		@Override
		public void onPreMotion()
		{
			if(MC.player == null)
				return;

			Rotation next;
			synchronized(LOCK)
			{
				RotationQueue owner = ACTIVE.stream().filter(RotationQueue::hasWork)
					.max((left, right) -> {
						int priorityComparison = Integer.compare(
							left.priority.weight, right.priority.weight);
						if(priorityComparison != 0)
							return priorityComparison;
						return Long.compare(left.requestOrder,
							right.requestOrder);
					}).orElse(null);
				next = owner == null ? null : owner.pollRotation();
			}
			if(next == null)
				return;

			WurstClient.INSTANCE.getRotationFaker().setRotationPacket(next);
		}
	}
	
	private Rotation interpolate(Rotation start, Rotation end, float t)
	{
		float yaw = start.yaw()
			+ Mth.wrapDegrees(end.yaw() - start.yaw()) * t;
		float pitch = Mth.lerp(t, start.pitch(), end.pitch());
		return new Rotation(yaw, pitch);
	}
	
	private record RotationTarget(Rotation target, int ticks)
	{
	}

	public enum Priority
	{
		BACKGROUND(0),
		NORMAL(100),
		MOVEMENT(200),
		BLOCK_PLACEMENT(300),
		COMBAT(400),
		EMERGENCY(500);

		private final int weight;

		Priority(int weight)
		{
			this.weight = weight;
		}
	}
}
