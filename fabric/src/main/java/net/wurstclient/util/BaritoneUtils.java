package net.wurstclient.util;

import java.lang.reflect.Method;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.WurstClient;

/** Optional Baritone bridge. Baritone is resolved after the client has loaded. */
public final class BaritoneUtils
{
	public static volatile boolean IS_AVAILABLE = ensureInitialized();

	private static Object primaryBaritone;
	private static Object mineProcess;
	private static Object customGoalProcess;
	private static Object builderProcess;
	private static Object pathingBehavior;

	private BaritoneUtils() {}

	private static synchronized boolean ensureInitialized()
	{
		if(primaryBaritone != null)
			return true;
		try
		{
			Class<?> api = Class.forName("baritone.api.BaritoneAPI");
			Object provider = api.getMethod("getProvider").invoke(null);
			primaryBaritone = provider.getClass().getMethod("getPrimaryBaritone").invoke(provider);
			if(primaryBaritone == null)
				return false;
			Object baritone = primaryBaritone;
			mineProcess = namedMethod(baritone, "getMineProcess");
			customGoalProcess = namedMethod(baritone, "getCustomGoalProcess");
			builderProcess = namedMethod(baritone, "getBuilderProcess");
			pathingBehavior = namedMethod(baritone, "getPathingBehavior");
			IS_AVAILABLE = mineProcess != null && customGoalProcess != null;
			return IS_AVAILABLE;
		}catch(Throwable ignored)
		{
			primaryBaritone = null;
			IS_AVAILABLE = false;
			return false;
		}
	}

	private static Object namedMethod(Object target, String name)
	{
		try { return target.getClass().getMethod(name).invoke(target); }
		catch(Throwable ignored) { return null; }
	}

	private static Method compatibleMethod(Object target, String name, Object... args)
	{
		for(Method method : target.getClass().getMethods())
		{
			Class<?>[] parameters = method.getParameterTypes();
			if(!method.getName().equals(name) || parameters.length != args.length)
				continue;
			boolean compatible = true;
			for(int i = 0; i < args.length; i++)
				if(args[i] == null || !parameters[i].isAssignableFrom(args[i].getClass()))
					compatible = false;
			if(compatible)
				return method;
		}
		return null;
	}

	private static boolean invoke(Object target, String name, Object... args)
	{
		try
		{
			Method method = compatibleMethod(target, name, args);
			if(method == null)
				return false;
			method.invoke(target, args);
			return true;
		}catch(Throwable ignored) { return false; }
	}

	public static boolean startMining(Block... blocks)
	{
		return ensureInitialized() && invoke(mineProcess, "mine", (Object)blocks);
	}

	public static void walkTo(BlockPos pos)
	{
		if(ensureInitialized())
			invoke(customGoalProcess, "setGoalAndPath", newGoal("GoalBlock", BlockPos.class, pos));
	}

	public static void clearArea(BlockPos corner1, BlockPos corner2)
	{
		if(ensureInitialized() && builderProcess != null)
			invoke(builderProcess, "clearArea", corner1, corner2);
	}

	public static void walkDirection(float yaw, double distance)
	{
		if(!ensureInitialized() || WurstClient.MC.player == null)
			return;
		try
		{
			Class<?> goal = Class.forName("baritone.api.pathing.goals.GoalXZ");
			Object origin = WurstClient.MC.player.getEyePosition();
			Object target = goal.getMethod("fromDirection", Vec3.class, float.class, double.class)
				.invoke(null, origin, yaw, distance);
			invoke(customGoalProcess, "setGoalAndPath", target);
		}catch(Throwable ignored) {}
	}

	private static Object newGoal(String name, Class<?> argumentType, Object argument)
	{
		try { return Class.forName("baritone.api.pathing.goals." + name).getConstructor(argumentType).newInstance(argument); }
		catch(Throwable ignored) { return null; }
	}

	public static void stop()
	{
		if(ensureInitialized() && pathingBehavior != null)
			invoke(pathingBehavior, "cancelEverything");
	}

	public static boolean isPathing()
	{
		if(!ensureInitialized() || pathingBehavior == null)
			return false;
		try { return (boolean)pathingBehavior.getClass().getMethod("isPathing").invoke(pathingBehavior); }
		catch(Throwable ignored) { return false; }
	}

	public static void walkHome()
	{
		if(WurstClient.MC.level != null)
			walkTo(WurstClient.MC.level.getSharedSpawnPos());
	}
}
