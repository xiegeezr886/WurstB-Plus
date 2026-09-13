package net.wurstclient.util;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalComposite;
import baritone.api.pathing.goals.GoalXZ;
import baritone.api.process.ICustomGoalProcess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.WurstClient;

public final class BaritoneUtils
{
	public static final boolean IS_AVAILABLE = true;

	public static IBaritone getBaritone()
	{
		return BaritoneAPI.getProvider().getPrimaryBaritone();
	}

	public static boolean startMining(Block... blocks)
	{
		try
		{
			getBaritone().getMineProcess().mine(blocks);
			return true;
		}catch(Exception e)
		{
			e.printStackTrace();
			return false;
		}
	}

	public static void walkTo(BlockPos pos)
	{
		try
		{
			getBaritone().getCustomGoalProcess()
				.setGoalAndPath(new GoalBlock(pos));
		}catch(Exception e)
		{
			e.printStackTrace();
		}
	}

	public static void clearArea(BlockPos corner1, BlockPos corner2)
	{
		try
		{
			getBaritone().getBuilderProcess().clearArea(corner1, corner2);
		}catch(Exception e)
		{
			e.printStackTrace();
		}
	}

	public static void walkDirection(float yaw, double distance)
	{
		try
		{
			if(WurstClient.MC.player == null)
				return;

			Vec3 origin = WurstClient.MC.player.getEyePosition();
			GoalXZ goal = GoalXZ.fromDirection(origin, yaw, distance);
			getBaritone().getCustomGoalProcess().setGoalAndPath(goal);
		}catch(Exception e)
		{
			e.printStackTrace();
		}
	}

	public static void stop()
	{
		try
		{
			getBaritone().getPathingBehavior().cancelEverything();
		}catch(Exception e)
		{
			e.printStackTrace();
		}
	}

	/**
	 * Sends a list of goals to Baritone, e.g. all predicted ore positions of the
	 * current batch. An empty list clears the goal instead, so callers do not
	 * have to special-case it.
	 *
	 * @return whether a goal was actually set.
	 */
	public static boolean setGoals(Goal... goals)
	{
		try
		{
			ICustomGoalProcess process = getBaritone().getCustomGoalProcess();

			if(goals == null || goals.length == 0)
			{
				clearGoal();
				return false;
			}

			Goal goal =
				goals.length == 1 ? goals[0] : new GoalComposite(goals);
			process.setGoalAndPath(goal);
			return true;
		}catch(Exception e)
		{
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * @return the goal Baritone is currently working on, or null.
	 */
	public static Goal getGoal()
	{
		try
		{
			return getBaritone().getCustomGoalProcess().getGoal();
		}catch(Exception e)
		{
			return null;
		}
	}

	public static void clearGoal()
	{
		try
		{
			ICustomGoalProcess process = getBaritone().getCustomGoalProcess();
			process.setGoal(null);
			getBaritone().getPathingBehavior().cancelEverything();
		}catch(Exception e)
		{
			e.printStackTrace();
		}
	}

	/**
	 * @return whether Baritone is currently mining.
	 */
	public static boolean isMining()
	{
		try
		{
			return getBaritone().getMineProcess().isActive();
		}catch(Exception e)
		{
			return false;
		}
	}

	/**
	 * @return whether Baritone is walking or mining right now.
	 */
	public static boolean isBusy()
	{
		return isPathing() || isMining();
	}

	public static boolean isPathing()
	{
		try
		{
			return getBaritone().getPathingBehavior().isPathing();
		}catch(Exception e)
		{
			return false;
		}
	}

	public static void walkHome()
	{
		try
		{
			if(WurstClient.MC.level == null)
				return;

			BlockPos homePos =
				WurstClient.MC.level.getSharedSpawnPos();

			if(homePos == null)
				return;

			walkTo(homePos);
		}catch(Exception e)
		{
			e.printStackTrace();
		}
	}
}
