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
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.wurstclient.WurstClient;
import net.wurstclient.util.MultiProcessingUtils;

/**
 * Opens {@link BackgroundFileChooser} without blocking the game.
 *
 * <p>
 * The alt list importer waits for its chooser inline, which freezes the client
 * for as long as the dialog is open. Here the whole exchange runs on a daemon
 * thread and only the result is handed back to the client thread.
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
				Process process = MultiProcessingUtils.startProcessWithIO(
					BackgroundFileChooser.class,
					WurstClient.INSTANCE.getWurstFolder().toString());
				picked = readPath(process);
				process.waitFor();

			}catch(IOException | InterruptedException | RuntimeException e)
			{
				// a chooser we cannot open simply means nothing was picked
			}

			Path result = picked;
			Minecraft.getInstance().execute(() -> onPicked.accept(result));
		}, "WurstB-BackgroundPicker");

		thread.setDaemon(true);
		thread.start();
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
