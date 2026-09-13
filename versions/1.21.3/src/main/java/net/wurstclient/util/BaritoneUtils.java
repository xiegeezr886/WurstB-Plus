package net.wurstclient.util;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.WurstClient;

public final class BaritoneUtils
{
	public static volatile boolean IS_AVAILABLE = detectAvailability();

	private static Object primaryBaritone;

	private BaritoneUtils()
	{
	}

	private static boolean detectAvailability()
	{
		try
		{
			Class<?> apiClass = Class.forName("baritone.api.BaritoneAPI", false,
				BaritoneUtils.class.getClassLoader());
			Object provider = invokeStatic(apiClass, "getProvider");
			primaryBaritone = invoke(provider, "getPrimaryBaritone");
			return primaryBaritone != null;
		}catch(Throwable ignored)
		{
			primaryBaritone = null;
			return false;
		}
	}

	private static boolean ensureAvailable()
	{
		return IS_AVAILABLE || (IS_AVAILABLE = detectAvailability());
	}

	private static void markUnavailable(Throwable error)
	{
		IS_AVAILABLE = false;
		primaryBaritone = null;
		System.err.println("Baritone is incompatible with this Minecraft version: "
			+ error);
	}

	private static Object getBaritone()
	{
		if(!ensureAvailable())
			throw new IllegalStateException("Baritone is not available");
		return primaryBaritone;
	}

	public static boolean startMining(Block... blocks)
	{
		try
		{
			Object mineProcess = invoke(getBaritone(), "getMineProcess");
			invoke(mineProcess, "mine", (Object)blocks);
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
			Class<?> goalClass = Class.forName(
				"baritone.api.pathing.goals.GoalBlock", false,
				BaritoneUtils.class.getClassLoader());
			Object goal;
			try
			{
				goal = construct(goalClass, pos);
			}catch(ReflectiveOperationException ignored)
			{
				goal = construct(goalClass, pos.getX(), pos.getY(), pos.getZ());
			}
			Object process = invoke(getBaritone(), "getCustomGoalProcess");
			invoke(process, "setGoalAndPath", goal);
		}catch(Throwable e)
		{
			markUnavailable(e);
		}
	}

	public static void clearArea(BlockPos corner1, BlockPos corner2)
	{
		try
		{
			Object process = invoke(getBaritone(), "getBuilderProcess");
			invoke(process, "clearArea", corner1, corner2);
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
			Class<?> goalClass = Class.forName(
				"baritone.api.pathing.goals.GoalXZ", false,
				BaritoneUtils.class.getClassLoader());
			Object goal = invokeStatic(goalClass, "fromDirection", origin, yaw,
				distance);
			Object process = invoke(getBaritone(), "getCustomGoalProcess");
			invoke(process, "setGoalAndPath", goal);
		}catch(Throwable e)
		{
			markUnavailable(e);
		}
	}

	public static void stop()
	{
		try
		{
			Object pathing = invoke(getBaritone(), "getPathingBehavior");
			invoke(pathing, "cancelEverything");
		}catch(Throwable e)
		{
			markUnavailable(e);
		}
	}

	public static boolean isPathing()
	{
		try
		{
			Object pathing = invoke(getBaritone(), "getPathingBehavior");
			return (boolean)invoke(pathing, "isPathing");
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
			if(homePos != null)
				walkTo(homePos);
		}catch(Throwable e)
		{
			markUnavailable(e);
		}
	}

	private static Object invokeStatic(Class<?> type, String name,
		Object... args) throws ReflectiveOperationException
	{
		return findMethod(type, name, args).invoke(null, args);
	}

	private static Object invoke(Object target, String name, Object... args)
		throws ReflectiveOperationException
	{
		if(target == null)
			throw new IllegalStateException(name + " target is unavailable");
		return findMethod(target.getClass(), name, args).invoke(target, args);
	}

	private static Method findMethod(Class<?> type, String name, Object[] args)
		throws NoSuchMethodException
	{
		for(Method method : type.getMethods())
			if(method.getName().equals(name)
				&& parametersMatch(method.getParameterTypes(), args))
				return method;
		throw new NoSuchMethodException(type.getName() + "." + name);
	}

	private static Object construct(Class<?> type, Object... args)
		throws ReflectiveOperationException
	{
		for(Constructor<?> constructor : type.getConstructors())
			if(parametersMatch(constructor.getParameterTypes(), args))
				return constructor.newInstance(args);
		throw new NoSuchMethodException(type.getName() + " constructor");
	}

	private static boolean parametersMatch(Class<?>[] parameterTypes,
		Object[] args)
	{
		if(parameterTypes.length != args.length)
			return false;
		for(int i = 0; i < parameterTypes.length; i++)
		{
			if(args[i] == null)
			{
				if(parameterTypes[i].isPrimitive())
					return false;
				continue;
			}
			Class<?> parameterType = wrapPrimitive(parameterTypes[i]);
			if(!parameterType.isAssignableFrom(args[i].getClass()))
				return false;
		}
		return true;
	}

	private static Class<?> wrapPrimitive(Class<?> type)
	{
		if(type == boolean.class)
			return Boolean.class;
		if(type == byte.class)
			return Byte.class;
		if(type == short.class)
			return Short.class;
		if(type == int.class)
			return Integer.class;
		if(type == long.class)
			return Long.class;
		if(type == float.class)
			return Float.class;
		if(type == double.class)
			return Double.class;
		if(type == char.class)
			return Character.class;
		return type;
	}
}
