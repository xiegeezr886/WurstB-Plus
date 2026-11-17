/*
 * This file contains a Forge/Mojmap adaptation of LiquidBounce's rolling
 * click array.
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

public final class RollingClickArray
{
	private final int cycleLength;
	private final int iterations;
	private final int[] array;
	private int head;

	public RollingClickArray(int cycleLength, int iterations)
	{
		if(cycleLength <= 0 || iterations <= 0)
			throw new IllegalArgumentException(
				"cycleLength and iterations must be positive");
		this.cycleLength = cycleLength;
		this.iterations = iterations;
		array = new int[cycleLength * iterations];
	}

	public int get(int relativeIndex)
	{
		return array[Math.floorMod(head + relativeIndex, array.length)];
	}

	public void set(int relativeIndex, int value)
	{
		array[Math.floorMod(head + relativeIndex, array.length)] = value;
	}

	public boolean advance()
	{
		return advance(1);
	}

	public boolean advance(int amount)
	{
		head = Math.floorMod(head + amount, array.length);
		return head % cycleLength == 0;
	}

	public void clear()
	{
		Arrays.fill(array, 0);
		head = 0;
	}

	public void push(int[] cycleArray)
	{
		if(cycleArray.length != cycleLength)
			throw new IllegalArgumentException(
				"Array size must match cycle length");

		if(head == 0)
			System.arraycopy(cycleArray, 0, array, cycleLength, cycleLength);
		else if(head == cycleLength)
			System.arraycopy(cycleArray, 0, array, 0, cycleLength);
		else
			throw new IllegalStateException(
				"Head must be at 0 or cycle length");
	}

	public int getCycleLength()
	{
		return cycleLength;
	}

	public int getIterations()
	{
		return iterations;
	}

	int[] copyArray()
	{
		return array.clone();
	}

	int getHead()
	{
		return head;
	}
}
