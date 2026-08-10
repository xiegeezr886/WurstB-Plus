package net.wurstclient.util;

import java.util.List;

import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLLoader;

public enum PlatformUtils
{
	;

	public static boolean isModLoaded(String modId)
	{
		// TODO: 26.1.2 - ModList.get() not found
		return false;
	}

	public static String getModVersion(String modId)
	{
		// TODO: 26.1.2 - ModList.get() not found
		return "unknown";
	}

	public static List<String> getLoadedModIds()
	{
		// TODO: 26.1.2 - ModList.get() not found
		return List.of();
	}

	public static boolean isDevelopmentEnvironment()
	{
		return !FMLLoader.isProduction();
	}
}
