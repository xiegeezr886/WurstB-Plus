package net.wurstclient.util;

import java.lang.reflect.Method;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.WurstClient;

/**
 * Baritone 集成工具（平台无关，反射调用）。
 * 若 Baritone 未安装或 API 不兼容，自动降级为不可用。
 */
public final class BaritoneUtils
{
	public static volatile boolean IS_AVAILABLE = detectAvailability();

	private static Object primaryBaritone;
	private static Object mineProcess;
	private static Object customGoalProcess;
	private static Object builderProcess;
	private static Object pathingBehavior;

	private BaritoneUtils()
	{
	}

	private static boolean ensureAvailable()
	{
		return IS_AVAILABLE || (IS_AVAILABLE = detectAvailability());
	}

	private static boolean detectAvailability()
	{
		try
		{
			Class<?> apiClass = Class.forName("baritone.api.BaritoneAPI");
			Method getProvider = apiClass.getMethod("getProvider");
			Object provider = getProvider.invoke(null);
			Method getPrimaryBaritone =
				provider.getClass().getMethod("getPrimaryBaritone");
			primaryBaritone = getPrimaryBaritone.invoke(provider);

			if(primaryBaritone == null)
				return false;

			Class<?> baritoneClass = primaryBaritone.getClass();
			mineProcess = invokeNoThrow(baritoneClass, primaryBaritone,
				"getMineProcess");
			customGoalProcess = invokeNoThrow(baritoneClass, primaryBaritone,
				"getCustomGoalProcess");
			builderProcess = invokeNoThrow(baritoneClass, primaryBaritone,
				"getBuilderProcess");
			pathingBehavior = invokeNoThrow(baritoneClass, primaryBaritone,
				"getPathingBehavior");

			return mineProcess != null && customGoalProcess != null;
		}catch(Throwable e)
		{
			return false;
		}
	}

	private static Object invokeNoThrow(Class<?> clazz, Object target,
		String methodName, Object... args)
	{
		try
		{
			Class<?>[] paramTypes = new Class<?>[args.length];
			for(int i = 0; i < args.length; i++)
				paramTypes[i] = args[i].getClass();
			Method method = clazz.getMethod(methodName, paramTypes);
			return method.invoke(target, args);
		}catch(Throwable e)
		{
			return null;
		}
	}

	private static Object invoke(String methodName, Object... args)
	{
		try
		{
			Class<?>[] paramTypes = new Class<?>[args.length];
			for(int i = 0; i < args.length; i++)
				paramTypes[i] = args[i].getClass();
			Method method =
				primaryBaritone.getClass().getMethod(methodName, paramTypes);
			return method.invoke(primaryBaritone, args);
		}catch(Throwable e)
		{
			markUnavailable(e);
			return null;
		}
	}

	private static void markUnavailable(Throwable error)
	{
		System.err.println("Baritone is incompatible: " + error);
	}

	public static boolean startMining(Block... blocks)
	{
		if(!ensureAvailable() || mineProcess == null)
			return false;
		try
		{
			Method mine = mineProcess.getClass().getMethod("mine",
				Block[].class);
			mine.invoke(mineProcess, (Object)blocks);
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
			if(!ensureAvailable() || customGoalProcess == null)
				return;
			Class<?> goalInterface =
				Class.forName("baritone.api.pathing.goals.Goal");
			Class<?> goalClass =
				Class.forName("baritone.api.pathing.goals.GoalBlock");
			Object goal = goalClass.getConstructor(BlockPos.class)
				.newInstance(pos);
			customGoalProcess.getClass().getMethod("setGoalAndPath",
				goalInterface).invoke(customGoalProcess, goal);
		}catch(Throwable e)
		{
			markUnavailable(e);
		}
	}

	public static void clearArea(BlockPos corner1, BlockPos corner2)
	{
		try
		{
			if(!ensureAvailable() || builderProcess == null)
				return;
			builderProcess.getClass().getMethod("clearArea", BlockPos.class,
				BlockPos.class).invoke(builderProcess, corner1, corner2);
		}catch(Throwable e)
		{
			markUnavailable(e);
		}
	}

	public static void walkDirection(float yaw, double distance)
	{
		try
		{
			if(!ensureAvailable() || customGoalProcess == null)
				return;
			if(WurstClient.MC.player == null)
				return;

			Vec3 origin = WurstClient.MC.player.getEyePosition();
			Class<?> goalXZClass =
				Class.forName("baritone.api.pathing.goals.GoalXZ");
			Class<?> goalInterface =
				Class.forName("baritone.api.pathing.goals.Goal");
			Object goal = goalXZClass.getMethod("fromDirection", Vec3.class,
				float.class, double.class).invoke(null, origin, yaw, distance);
			customGoalProcess.getClass().getMethod("setGoalAndPath",
				goalInterface).invoke(customGoalProcess, goal);
		}catch(Throwable e)
		{
			markUnavailable(e);
		}
	}

	public static void stop()
	{
		try
		{
			if(!ensureAvailable() || pathingBehavior == null)
				return;
			pathingBehavior.getClass().getMethod("cancelEverything")
				.invoke(pathingBehavior);
		}catch(Throwable e)
		{
			markUnavailable(e);
		}
	}

	public static boolean isPathing()
	{
		try
		{
			if(!ensureAvailable() || pathingBehavior == null)
				return false;
			return (boolean)pathingBehavior.getClass()
				.getMethod("isPathing").invoke(pathingBehavior);
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

			BlockPos homePos = WurstClient.MC.level.getSharedSpawnPos();

			if(homePos == null)
				return;

			walkTo(homePos);
		}catch(Throwable e)
		{
			markUnavailable(e);
		}
	}
}
