package net.wurstclient.util;

import java.util.List;

import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLLoader;

public enum PlatformUtils
{
	;

	public static boolean isModLoaded(String modId)
	{
		return ModList.isLoaded(modId);
	}

	public static String getModVersion(String modId)
	{
		return ModList.getModContainerById(modId)
			.map(container -> container.getModInfo().getVersion().toString())
			.orElse(null);
	}

	public static List<String> getLoadedModIds()
	{
		return ModList.getMods().stream().map(info -> info.getModId())
			.toList();
	}

	public static boolean isDevelopmentEnvironment()
	{
		return !FMLLoader.isProduction();
	}
}
