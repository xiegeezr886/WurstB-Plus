/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
package net.wurstclient.hud2.nanovg;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import org.lwjgl.system.Configuration;

/**
 * Extracts the LWJGL NanoVG native library from the mod jar (or Gradle's
 * classpath) before NanoVG is initialised.
 *
 * <p>LWJGL's own {@code SharedLibraryLoader} usually finds the native resources
 * when the natives classifier jar is on the classpath. Fabric/Forge nested
 * jars are not always visible to it, so this loader additionally checks the
 * mod jar's {@code META-INF/jars} directory and sets
 * {@code org.lwjgl.librarypath} to a writable extraction directory.
 */
public final class NanoVGNativeLoader
{
	/** Replaced by the propagation script for each Minecraft version. */
	private static final String LIB_VERSION = "3.3.3";
	private static final String LIBRARY_NAME = "lwjgl_nanovg";

	private static boolean attempted;
	private static boolean loaded;

	private NanoVGNativeLoader() {}

	public static synchronized boolean ensureLoaded()
	{
		if(attempted)
			return loaded;
		attempted = true;

		try
		{
			String classifier = classifier();
			if(classifier == null)
			{
				System.out.println("[HUD] Unsupported OS/arch for NanoVG: "
					+ System.getProperty("os.name") + " / "
					+ System.getProperty("os.arch"));
				return loaded = false;
			}

			// The natives bundled in the mod are desktop builds (glibc /
			// macOS / Windows). Android needs the launcher's bionic build,
			// which is already on LWJGL's library path; extracting ours
			// would shadow it with an unloadable glibc .so.
			if(isAndroid())
				return loaded = true;

			Path directory = extractNative(classifier);
			if(directory != null)
			{
				configureLibraryPath(directory);
				System.out.println("[HUD] NanoVG native extracted to 	"
					+ directory);
			}

			// If extraction failed, give LWJGL's classpath loader a chance.
			return loaded = true;
		}catch(Throwable t)
		{
			System.err.println("[HUD] Could not prepare the NanoVG native");
			t.printStackTrace();
			return loaded = false;
		}
	}

	private static boolean isAndroid()
	{
		return System.getProperty("os.version", "").startsWith("Android")
			|| System.getenv("POJAV_NATIVEDIR") != null
			|| System.getenv("ANDROID_ROOT") != null;
	}

	private static String classifier()
	{
		String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
		String arch =
			System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
		boolean arm = arch.contains("aarch64") || arch.contains("arm64");

		if(os.contains("win"))
			return arm ? "natives-windows-arm64" : "natives-windows";
		if(os.contains("mac") || os.contains("darwin"))
			return arm ? "natives-macos-arm64" : "natives-macos";
		if(os.contains("linux"))
			return arm ? "natives-linux-arm64" : "natives-linux";
		return null;
	}

	private static String osDirectory()
	{
		String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
		if(os.contains("win"))
			return "windows";
		if(os.contains("mac") || os.contains("darwin"))
			return "macos";
		return "linux";
	}

	private static String archDirectory()
	{
		String arch =
			System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
		return arch.contains("aarch64") || arch.contains("arm64") ? "arm64"
			: "x64";
	}

	private static String nativeName()
	{
		boolean windows = System.getProperty("os.name", "")
			.toLowerCase(Locale.ROOT).contains("win");
		boolean mac = System.getProperty("os.name", "")
			.toLowerCase(Locale.ROOT).contains("mac");
		if(windows)
			return LIBRARY_NAME + ".dll";
		return "lib" + LIBRARY_NAME + (mac ? ".dylib" : ".so");
	}

	private static Path extractNative(String classifier) throws Exception
	{
		String os = osDirectory();
		String arch = archDirectory();
		String name = nativeName();
		String base = "assets/wurstpenguin/nanovg/natives/";
		String resource = base + os + "/" + arch + "/org/lwjgl/nanovg/"
			+ name;

		// 1. Natives unpacked into the mod jar by processResources.
		InputStream stream = resourceStream(resource);
		if(stream == null)
			stream = resourceStream(base + os + "/" + arch + "/" + name);
		if(stream == null)
			stream = resourceStream(os + "/" + arch + "/org/lwjgl/nanovg/"
				+ name);
		if(stream == null)
			stream = resourceStream(os + "/" + arch + "/" + name);
		if(stream == null)
			stream = resourceStream(name);
		if(stream != null)
			try(InputStream in = stream)
			{
				return writeNative(in, name);
			}

		// 2. Native jar directly on the classpath (Gradle dev runs).
		for(Path jar : classpathJars())
		{
			String fileName = jar.getFileName().toString();
			if(!fileName.contains("lwjgl-nanovg")
				|| !fileName.contains(classifier))
				continue;

			try(ZipFile zip = new ZipFile(jar.toFile()))
			{
				ZipEntry entry = findNativeEntry(zip, name);
				if(entry == null)
					continue;
				try(InputStream in = zip.getInputStream(entry))
				{
					return writeNative(in, name);
				}
			}
		}

		// 3. Our own mod jar with nested META-INF/jars.
		Path self = ownJar();
		if(self != null)
		{
			try(ZipFile zip = new ZipFile(self.toFile()))
			{
				Enumeration<? extends ZipEntry> entries = zip.entries();
				while(entries.hasMoreElements())
				{
					ZipEntry entry = entries.nextElement();
					String entryName = entry.getName();
					if(!entryName.startsWith("META-INF/jars/")
						|| !entryName.toLowerCase(Locale.ROOT)
							.contains("nanovg")
						|| !entryName.toLowerCase(Locale.ROOT)
							.contains(classifier))
						continue;

					try(InputStream nested = zip.getInputStream(entry);
						ZipInputStream nestedZip = new ZipInputStream(nested))
					{
						ZipEntry inner;
						while((inner = nestedZip.getNextEntry()) != null)
						{
							String innerName = inner.getName()
								.replace('\\', '/');
							if(!innerName.endsWith("/" + name)
								&& !innerName.equals(name))
								continue;
							return writeNative(nestedZip, name);
						}
					}
				}
			}
		}

		return null;
	}

	private static InputStream resourceStream(String path)
	{
		ClassLoader loader = NanoVGNativeLoader.class.getClassLoader();
		InputStream in = loader.getResourceAsStream(path);
		if(in != null)
			return in;
		return loader.getResourceAsStream("/" + path);
	}

	private static List<Path> classpathJars()
	{
		List<Path> paths = new ArrayList<>();
		String classpath = System.getProperty("java.class.path", "");
		for(String entry : classpath.split(File.pathSeparator))
		{
			if(entry.isBlank())
				continue;
			try
			{
				Path path = Path.of(entry);
				if(Files.isRegularFile(path) && entry.endsWith(".jar"))
					paths.add(path);
			}catch(Exception ignored)
			{}
		}
		return paths;
	}

	private static ZipEntry findNativeEntry(ZipFile zip, String name)
	{
		Enumeration<? extends ZipEntry> entries = zip.entries();
		while(entries.hasMoreElements())
		{
			ZipEntry entry = entries.nextElement();
			String entryName = entry.getName().replace('\\', '/');
			if(entryName.endsWith("/" + name) || entryName.equals(name))
				return entry;
		}
		return null;
	}

	private static Path ownJar()
	{
		try
		{
			URI uri = NanoVGNativeLoader.class.getProtectionDomain()
				.getCodeSource().getLocation().toURI();
			Path path = Path.of(uri);
			if(Files.isRegularFile(path) && path.toString().endsWith(".jar"))
				return path;
		}catch(Exception ignored)
		{}
		return null;
	}

	private static Path writeNative(InputStream in, String name)
		throws Exception
	{
		Path directory = Path.of(System.getProperty("java.io.tmpdir"),
			"wurst-nanovg", LIB_VERSION);
		Files.createDirectories(directory);
		Path destination = directory.resolve(name);

		if(Files.isRegularFile(destination) && Files.size(destination) > 0)
			return directory;

		Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
		try
		{
			destination.toFile().setExecutable(true);
		}catch(SecurityException ignored)
		{}
		return directory;
	}

	private static void configureLibraryPath(Path directory)
	{
		String path = directory.toAbsolutePath().toString();

		String current = System.getProperty("org.lwjgl.librarypath");
		System.setProperty("org.lwjgl.librarypath",
			current == null || current.isBlank() ? path
				: current + File.pathSeparator + path);

		String configured = Configuration.LIBRARY_PATH.get();
		Configuration.LIBRARY_PATH.set(configured == null
			|| configured.isBlank() ? path
				: configured + File.pathSeparator + path);
	}
}
