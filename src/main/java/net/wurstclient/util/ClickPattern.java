/*
 * This file contains Forge/Mojmap adaptations of LiquidBounce's click
 * patterns.
 *
 * Copyright (c) 2015-2026 CCBlueX
 * Copyright (c) 2025-2026 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

import java.util.Random;
import java.util.Objects;

public enum ClickPattern
{
	STABILIZED
	{
		@Override
		public void fill(int[] clicks, int minimumCps, int maximumCps,
			Random random)
		{
			if(!canFill(clicks, random))
				return;
			int amount = randomCps(minimumCps, maximumCps, random);
			fillStabilized(clicks, amount);
		}
	},
	EFFICIENT
	{
		@Override
		public void fill(int[] clicks, int minimumCps, int maximumCps,
			Random random)
		{
			if(!canFill(clicks, random))
				return;
			int amount = randomCps(minimumCps, maximumCps, random);
			if(amount < 10)
			{
				fillStabilized(clicks, amount);
				return;
			}

			for(int i = 0; i < amount; i++)
				clicks[i * 2 % clicks.length]++;
		}
	},
	SPAMMING
	{
		@Override
		public void fill(int[] clicks, int minimumCps, int maximumCps,
			Random random)
		{
			if(!canFill(clicks, random))
				return;
			int amount = randomCps(minimumCps, maximumCps, random);
			for(int i = 0; i < amount; i++)
				clicks[random.nextInt(clicks.length)]++;
		}
	},
	DOUBLE_CLICK
	{
		@Override
		public void fill(int[] clicks, int minimumCps, int maximumCps,
			Random random)
		{
			if(!canFill(clicks, random))
				return;
			int amount = randomCps(minimumCps, maximumCps, random);
			for(int i = 0; i < amount; i++)
				clicks[random.nextInt(clicks.length)] += 2;
		}
	},
	DRAG
	{
		@Override
		public void fill(int[] clicks, int minimumCps, int maximumCps,
			Random random)
		{
			if(!canFill(clicks, random))
				return;
			int amount = randomCps(minimumCps, maximumCps, random);
			int travelTime = Math.min(clicks.length, random.nextInt(17, 19));
			while(sum(clicks) < amount)
			{
				int lowestIndex = 0;
				for(int i = 1; i < travelTime; i++)
					if(clicks[i] < clicks[lowestIndex])
						lowestIndex = i;
				clicks[lowestIndex]++;
			}
		}
	},
	BUTTERFLY
	{
		@Override
		public void fill(int[] clicks, int minimumCps, int maximumCps,
			Random random)
		{
			if(!canFill(clicks, random))
				return;
			int amount = randomCps(minimumCps, maximumCps, random);
			while(sum(clicks) < amount)
			{
				int zeroCount = 0;
				for(int click : clicks)
					if(click == 0)
						zeroCount++;

				if(zeroCount == 0)
				{
					clicks[random.nextInt(clicks.length)]++;
					continue;
				}

				int selectedZero = random.nextInt(zeroCount);
				for(int i = 0; i < clicks.length; i++)
					if(clicks[i] == 0 && selectedZero-- == 0)
					{
						clicks[i] = Math.min(random.nextInt(1, 3),
							amount - sum(clicks));
						break;
					}
			}
		}
	},
	NORMAL_DISTRIBUTION
	{
		@Override
		public void fill(int[] clicks, int minimumCps, int maximumCps,
			Random random)
		{
			if(!canFill(clicks, random))
				return;
			int amount = randomCps(minimumCps, maximumCps, random);
			if(amount <= 0)
				return;

			double[] intervals = new double[amount];
			double total = 0;
			for(int i = 0; i < amount; i++)
			{
				intervals[i] = Math.max(0.1, 1 + random.nextGaussian() * 0.2);
				total += intervals[i];
			}

			double time = random.nextDouble() * total / amount;
			for(double interval : intervals)
			{
				int index = Math.min(clicks.length - 1,
					(int)(time / total * clicks.length));
				clicks[index]++;
				time += interval;
			}
		}
	};

	public abstract void fill(int[] clicks, int minimumCps, int maximumCps,
		Random random);

	private static int randomCps(int minimumCps, int maximumCps,
		Random random)
	{
		int minimum = Math.max(0, Math.min(minimumCps, maximumCps));
		int maximum = Math.max(minimum,
			Math.max(minimumCps, maximumCps));
		return minimum == maximum ? minimum
			: (int)random.nextLong(minimum, (long)maximum + 1);
	}

	private static boolean canFill(int[] clicks, Random random)
	{
		Objects.requireNonNull(clicks, "clicks");
		Objects.requireNonNull(random, "random");
		return clicks.length > 0;
	}

	private static void fillStabilized(int[] clicks, int amount)
	{
		int interval = amount > 0 ? clicks.length / amount : 0;
		int remainder = amount > 0 ? clicks.length % amount : 0;
		int currentIndex = 0;
		for(int i = 0; i < amount; i++)
		{
			clicks[currentIndex % clicks.length]++;
			currentIndex += Math.max(interval, 1);
			if(remainder > 0)
			{
				currentIndex++;
				remainder--;
			}
		}
	}

	private static int sum(int[] clicks)
	{
		int result = 0;
		for(int click : clicks)
			result += click;
		return result;
	}

	@Override
	public String toString()
	{
		return switch(this)
		{
			case STABILIZED -> "Stabilized";
			case EFFICIENT -> "Efficient";
			case SPAMMING -> "Spamming";
			case DOUBLE_CLICK -> "Double click";
			case DRAG -> "Drag";
			case BUTTERFLY -> "Butterfly";
			case NORMAL_DISTRIBUTION -> "Normal distribution";
		};
	}
}
