package net.wurstclient.util;

import java.util.List;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;

public enum PlatformUtils
{
	;

	public static boolean isModLoaded(String modId)
	{
		return ModList.get().isLoaded(modId);
	}

	public static String getModVersion(String modId)
	{
		return ModList.get().getModContainerById(modId)
			.map(container -> container.getModInfo().getVersion().toString())
			.orElse(null);
	}

	public static List<String> getLoadedModIds()
	{
		return ModList.get().getMods().stream().map(info -> info.getModId())
			.toList();
	}

	public static boolean isDevelopmentEnvironment()
	{
		return !FMLLoader.isProduction();
	}
}
