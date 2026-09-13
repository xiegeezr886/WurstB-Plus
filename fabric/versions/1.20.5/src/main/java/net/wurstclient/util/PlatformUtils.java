package net.wurstclient.util;

import java.util.List;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

public enum PlatformUtils
{
	;

	public static boolean isModLoaded(String modId)
	{
		return FabricLoader.getInstance().isModLoaded(modId);
	}

	public static String getModVersion(String modId)
	{
		return FabricLoader.getInstance().getModContainer(modId)
			.map(container -> container.getMetadata().getVersion().toString())
			.orElse(null);
	}

	public static List<String> getLoadedModIds()
	{
		return FabricLoader.getInstance().getAllMods().stream()
			.map(ModContainer::getMetadata)
			.map(metadata -> metadata.getId()).toList();
	}

	public static boolean isDevelopmentEnvironment()
	{
		return FabricLoader.getInstance().isDevelopmentEnvironment();
	}
}
