/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinReg;

import net.wurstclient.background.VdfParser.VdfParseException;

/**
 * Finds where Steam and its libraries live, so Wallpaper Engine wallpapers can
 * be imported without the user hunting for a folder.
 *
 * <p>
 * Steam itself is located through the registry, with the usual install paths as
 * a fallback. Everything after that - libraries, workshop content, local
 * projects - is derived by path arithmetic and by reading {@code
 * libraryfolders.vdf}, and takes the roots as arguments so it can be unit
 * tested against a temp folder.
 */
public final class SteamLocator
{
	/** Steam's app id for Wallpaper Engine. */
	public static final int WALLPAPER_ENGINE_APP_ID = 431960;

	private static final String[] STEAM_SUBKEYS =
		{"Software\\Valve\\Steam", "Software\\Valve\\Steam\\ActiveProcess"};

	private SteamLocator()
	{
	}

	/**
	 * @return every Steam root we can find, de-duplicated and only those that
	 *         exist. Empty when Steam is not installed.
	 */
	public static List<Path> steamRoots()
	{
		LinkedHashSet<Path> roots = new LinkedHashSet<>();

		for(String subkey : STEAM_SUBKEYS)
		{
			addIfDirectory(roots, registryValue(WinReg.HKEY_CURRENT_USER, subkey,
				"SteamPath"));
			addIfDirectory(roots, registryValue(WinReg.HKEY_CURRENT_USER, subkey,
				"SteamExe"));
			addIfDirectory(roots, registryValue(WinReg.HKEY_LOCAL_MACHINE,
				"SOFTWARE\\WOW6432Node\\Valve\\Steam", "InstallPath"));
			addIfDirectory(roots, registryValue(WinReg.HKEY_LOCAL_MACHINE,
				"SOFTWARE\\Valve\\Steam", "InstallPath"));
		}

		addIfDirectory(roots, "C:\\Program Files (x86)\\Steam");
		addIfDirectory(roots, "C:\\Program Files\\Steam");
		return new ArrayList<>(roots);
	}

	/**
	 * @return the library roots of a Steam install, including the install
	 *         folder itself, which is always a library.
	 */
	public static List<Path> libraryRoots(Path steamRoot)
	{
		LinkedHashSet<Path> roots = new LinkedHashSet<>();
		Path steamApps = steamRoot.resolve("steamapps");

		if(Files.isDirectory(steamApps))
			roots.add(steamRoot);

		Path vdf = steamApps.resolve("libraryfolders.vdf");

		if(!Files.isRegularFile(vdf))
			return new ArrayList<>(roots);

		try
		{
			Map<String, Object> document =
				VdfParser.parse(Files.readString(vdf));
			collectLibraryPaths(document, roots);
		}catch(IOException | VdfParseException e)
		{
			// a library list we cannot read just means fewer candidates
		}

		return new ArrayList<>(roots);
	}

	/** Where a library keeps Wallpaper Engine's workshop content. */
	public static Path workshopDir(Path libraryRoot)
	{
		return libraryRoot.resolve("steamapps").resolve("workshop")
			.resolve("content").resolve(Integer
				.toString(WALLPAPER_ENGINE_APP_ID));
	}

	/** The folders Wallpaper Engine keeps hand-made wallpapers in. */
	public static List<Path> localProjectDirs(Path steamRoot)
	{
		List<Path> dirs = new ArrayList<>();
		Path projects = steamRoot.resolve("steamapps").resolve("common")
			.resolve("wallpaper_engine").resolve("projects");

		for(String name : new String[]{"myprojects", "defaultprojects"})
		{
			Path dir = projects.resolve(name);

			if(Files.isDirectory(dir))
				dirs.add(dir);
		}

		return dirs;
	}

	/**
	 * @return every folder that can hold wallpapers across all Steam libraries
	 *         of all Steam installs.
	 */
	public static List<Path> wallpaperFolders()
	{
		List<Path> folders = new ArrayList<>();

		for(Path steamRoot : steamRoots())
		{
			for(Path library : libraryRoots(steamRoot))
			{
				Path workshop = workshopDir(library);

				if(Files.isDirectory(workshop))
					folders.add(workshop);
			}

			folders.addAll(localProjectDirs(steamRoot));
		}

		return folders;
	}

	private static void collectLibraryPaths(Map<String, Object> document,
		LinkedHashSet<Path> roots)
	{
		for(Map.Entry<String, Object> entry : document.entrySet())
		{
			Object value = entry.getValue();
			String key = entry.getKey();

			if(value instanceof Map<?, ?> nested)
			{
				@SuppressWarnings("unchecked")
				Map<String, Object> child = (Map<String, Object>)nested;
				collectLibraryPaths(child, roots);
				continue;
			}

			if(value instanceof String path
				&& ("path".equals(key) || "1".equals(key)))
				addIfDirectory(roots, path);
		}
	}

	private static void addIfDirectory(LinkedHashSet<Path> roots, String value)
	{
		if(value == null || value.isBlank())
			return;

		try
		{
			Path path = Path.of(value.trim()).toAbsolutePath().normalize();

			if(Files.isDirectory(path))
				roots.add(path);
		}catch(RuntimeException e)
		{
			// an unusable registry value is simply skipped
		}
	}

	/**
	 * Reads one registry string. Anything at all may go wrong here - the value
	 * may be absent, the registry may be unavailable, JNA may not load - and a
	 * missing value only costs us a fallback path, never a crash.
	 */
	private static String registryValue(WinReg.HKEY root, String path,
		String key)
	{
		try
		{
			return Advapi32Util.registryGetStringValue(root, path, key);
		}catch(Throwable t)
		{
			return null;
		}
	}
}
