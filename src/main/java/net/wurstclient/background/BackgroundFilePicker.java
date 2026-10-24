/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Consumer;
import java.util.stream.Stream;

import net.minecraft.client.Minecraft;
import net.wurstclient.WurstClient;

/**
 * Opens {@link BackgroundFileChooser} without blocking the game.
 *
 * <p>
 * Minecraft sets {@code java.awt.headless=true}, so a Swing dialog cannot be
 * created inside the game JVM ({@link java.awt.HeadlessException}). The
 * chooser therefore runs in its own JVM and reports the picked path on
 * stdout. The classpath is the mod jar from {@code <gameDir>/mods}; the
 * protection-domain path is a union filesystem in production and cannot be
 * handed to {@link ProcessBuilder}.
 */
public final class BackgroundFilePicker
{
	private BackgroundFilePicker()
	{
	}

	/**
	 * @param onPicked
	 *            called on the client thread with the chosen file, or null when
	 *            the dialog was cancelled or failed.
	 */
	public static void pickImage(Consumer<Path> onPicked)
	{
		Thread thread = new Thread(() -> {
			Path picked = null;

			try
			{
				Process process = new ProcessBuilder(
					Paths.get(System.getProperty("java.home"), "bin", "java")
						.toString(),
					"-cp", findChooserClasspath(),
					BackgroundFileChooser.class.getName(),
					WurstClient.INSTANCE.getWurstFolder().toString())
						.start();
				picked = readPath(process);
				process.waitFor();

			}catch(Throwable t)
			{
				System.err.println("[Wurst] Background picker failed: " + t);
				t.printStackTrace();
			}

			Path result = picked;
			Minecraft.getInstance().execute(() -> onPicked.accept(result));
		}, "WurstB-BackgroundPicker");

		thread.setDaemon(true);
		thread.start();
	}

	private static String findChooserClasspath() throws IOException
	{
		Path mods = Minecraft.getInstance().gameDirectory.toPath()
			.resolve("mods");

		if(Files.isDirectory(mods))
		{
			try(Stream<Path> entries = Files.list(mods))
			{
				Path jar = entries
					.filter(path -> path.getFileName().toString()
						.startsWith("WurstB+"))
					.filter(path -> path.getFileName().toString()
						.endsWith(".jar"))
					.findFirst().orElse(null);

				if(jar != null)
					return jar.toAbsolutePath().toString();
			}
		}

		// Development runs keep the classes in a build directory.
		try
		{
			return Paths.get(BackgroundFileChooser.class.getProtectionDomain()
				.getCodeSource().getLocation().toURI()).toString();

		}catch(Exception e)
		{
			return System.getProperty("java.class.path");
		}
	}

	private static Path readPath(Process process) throws IOException
	{
		try(BufferedReader reader = new BufferedReader(
			new InputStreamReader(process.getInputStream(),
				StandardCharsets.UTF_8)))
		{
			String response = reader.readLine();

			if(response == null || response.isBlank())
				return null;

			try
			{
				return Path.of(response.trim());
			}catch(InvalidPathException e)
			{
				return null;
			}
		}
	}
}
