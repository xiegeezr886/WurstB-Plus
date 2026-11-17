package net.wurstclient.util;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalXZ;
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

	private static final int PRELOAD_PENDING = 0;
	private static final int PRELOAD_STARTED = 1;
	private static final int PRELOAD_READY = 2;
	private static volatile int miningPreloadState;

	/**
	 * Older bundled Baritone builds initialize their registry snapshot through
	 * Minecraft's task queue, which deadlocks if that first happens on the
	 * client thread. Prepare it on a background thread before mining.
	 */
	public static boolean isMiningReady()
	{
		if(miningPreloadState == PRELOAD_READY)
			return true;
		if(miningPreloadState == PRELOAD_PENDING)
			synchronized(BaritoneUtils.class)
			{
				if(miningPreloadState == PRELOAD_PENDING)
				{
					miningPreloadState = PRELOAD_STARTED;
					Thread thread = new Thread(BaritoneUtils::preloadMining,
						"Wurst-BaritoneMiningPreload");
					thread.setDaemon(true);
					thread.start();
				}
			}
		return false;
	}

	private static void preloadMining()
	{
		try
		{
			Class<?> stub = Class.forName(
				"baritone.api.utils.BlockOptionalMeta$ServerLevelStub");
			try
			{
				stub.getMethod("registryAccess");
			}catch(NoSuchMethodException e)
			{
				return;
			}
			Object instance = stub.getMethod("fastCreate").invoke(null);
			stub.getMethod("registryAccess").invoke(instance);
		}catch(Throwable e)
		{
			System.err.println("Baritone preload failed: " + e);
		}finally
		{
			miningPreloadState = PRELOAD_READY;
		}
	}
	public static boolean startMining(Block... blocks)
	{
		if(!isMiningReady())
			return false;
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
