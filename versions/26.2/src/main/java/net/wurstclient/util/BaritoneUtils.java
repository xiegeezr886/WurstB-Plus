package net.wurstclient.util;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalXZ;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.storage.LevelData;
import net.wurstclient.WurstClient;

public final class BaritoneUtils
{
	public static volatile boolean IS_AVAILABLE = detectAvailability();

	private static boolean detectAvailability()
	{
		try
		{
			Class.forName("baritone.api.BaritoneAPI", false,
				BaritoneUtils.class.getClassLoader());
			return true;
		}catch(Throwable ignored)
		{
			return false;
		}
	}

	private static void markUnavailable(Throwable error)
	{
		IS_AVAILABLE = false;
		System.err.println("Baritone is incompatible with this Minecraft version: "
			+ error);
	}

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
		}catch(Throwable e)
		{
			markUnavailable(e);
			return false;
		}
	}

	public static void walkTo(BlockPos pos)
	{
		try
		{
			getBaritone().getCustomGoalProcess()
				.setGoalAndPath(new GoalBlock(pos));
		}catch(Throwable e)
		{
			markUnavailable(e);
		}
	}

	public static void clearArea(BlockPos corner1, BlockPos corner2)
	{
		try
		{
			getBaritone().getBuilderProcess().clearArea(corner1, corner2);
		}catch(Throwable e)
		{
			markUnavailable(e);
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
		}catch(Throwable e)
		{
			markUnavailable(e);
		}
	}

	public static void stop()
	{
		try
		{
			getBaritone().getPathingBehavior().cancelEverything();
		}catch(Throwable e)
		{
			markUnavailable(e);
		}
	}

	public static boolean isPathing()
	{
		try
		{
			return getBaritone().getPathingBehavior().isPathing();
		}catch(Throwable e)
		{
			markUnavailable(e);
			return false;
		}
	}

	public static void walkHome()
	{
		try
		{
			if(WurstClient.MC.level == null)
				return;

			LevelData.RespawnData respawnData =
				WurstClient.MC.level.getLevelData().getRespawnData();
			BlockPos homePos = respawnData == null ? null : respawnData.pos();

			if(homePos == null)
				return;

			walkTo(homePos);
		}catch(Throwable e)
		{
			markUnavailable(e);
		}
	}
}
