/*
 * Copyright (c) 2025-2026 Penguin
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.goxr3plus.streamplayer.enums.Status;
import com.goxr3plus.streamplayer.stream.StreamPlayer;
import com.goxr3plus.streamplayer.stream.StreamPlayerEvent;
import com.goxr3plus.streamplayer.stream.StreamPlayerListener;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.wurstclient.WurstClient;
import net.wurstclient.gui.title.BackgroundSelectScreen;

/**
 * Plays the audio objects of a Wallpaper Engine scene.
 *
 * <p>
 * The MP3/OGG/FLAC/WAV files are extracted from {@code scene.pkg} into
 * {@code <gameDir>/wurst/scene-audio/} and decoded by java-stream-player (the
 * same SPIs the music player uses). Playback runs on its own daemon thread so
 * the title screen never blocks on the audio device; closing the scene stops
 * every player and deletes the temporary files.</p>
 */
final class WeSceneAudioPlayer implements AutoCloseable
{
	private final List<Clip> clips = new ArrayList<>();
	private final List<StreamPlayer> players = new ArrayList<>();
	private final ExecutorService executor =
		Executors.newSingleThreadExecutor(runnable -> {
			Thread thread =
				new Thread(runnable, "WurstB-SceneAudio");
			thread.setDaemon(true);
			return thread;
		});

	private volatile boolean closed;
	private Thread watchdog;

	/**
	 * @param audio
	 *            decoded audio files, possibly empty
	 * @return a player, or null when there is nothing to play
	 */
	static WeSceneAudioPlayer start(List<Clip> audio)
	{
		if(audio == null || audio.isEmpty())
			return null;

		WeSceneAudioPlayer player = new WeSceneAudioPlayer();
		player.startAll(audio);
		return player;
	}

	private void startAll(List<Clip> audio)
	{
		Path folder = WurstClient.INSTANCE.getWurstFolder()
			.resolve("scene-audio");

		try
		{
			Files.createDirectories(folder);
		}catch(IOException e)
		{
			System.out.println("[Background] Cannot create scene audio folder: "
				+ e);
			return;
		}

		for(Clip clip : audio)
		{
			Path file = writeClip(folder, clip);

			if(file == null)
				continue;

			StreamPlayer player = new StreamPlayer();
			player.setGain(Math.max(0, Math.min(1, clip.volume())));
			player.addStreamPlayerListener(
				new LoopListener(player, file, clip.loop()));

			clips.add(clip.withFile(file));
			players.add(player);
			executor.execute(() -> play(player, file));
		}

		watchdog = new Thread(this::watchVisibility, "WurstB-SceneAudioWatchdog");
		watchdog.setDaemon(true);
		watchdog.start();
	}

	/**
	 * Scene audio should only be heard while the title background is on
	 * screen. Pause it when the player joins a world, resume on the title /
	 * picker screens.
	 */
	private void watchVisibility()
	{
		while(!closed)
		{
			boolean active = Minecraft.getInstance().screen instanceof TitleScreen
				|| Minecraft.getInstance().screen instanceof BackgroundSelectScreen;

			for(StreamPlayer player : players)
				try
				{
					if(active && player.isPaused())
						player.resume();
					else if(!active && player.isPlaying())
						player.pause();
				}catch(Throwable ignored)
				{
				}

			try
			{
				Thread.sleep(1000);
			}catch(InterruptedException e)
			{
				return;
			}
		}
	}

	private static Path writeClip(Path folder, Clip clip)
	{
		try
		{
			Path file = Files.createTempFile(folder, "scene-audio-",
				clip.extension());
			Files.write(file, clip.bytes());
			return file;
		}catch(IOException e)
		{
			System.out.println("[Background] Cannot extract scene audio '"
				+ clip.name() + "': " + e);
			return null;
		}
	}

	private void play(StreamPlayer player, Path file)
	{
		if(closed)
			return;

		try
		{
			player.open(file.toFile());
			player.play();
			System.out.println("[Background] Scene audio: " + file.getFileName());

		}catch(Throwable t)
		{
			System.out.println("[Background] Scene audio failed for " + file
				+ ": " + t);
		}
	}

	@Override
	public void close()
	{
		if(closed)
			return;

		closed = true;

		if(watchdog != null)
			watchdog.interrupt();

		for(StreamPlayer player : players)
			try
			{
				player.stop();
			}catch(Throwable ignored)
			{
			}

		executor.shutdownNow();

		for(Clip clip : clips)
			try
			{
				Files.deleteIfExists(clip.file());
			}catch(IOException ignored)
			{
			}
	}

	private final class LoopListener implements StreamPlayerListener
	{
		private final StreamPlayer player;
		private final Path file;
		private final boolean loop;

		private LoopListener(StreamPlayer player, Path file, boolean loop)
		{
			this.player = player;
			this.file = file;
			this.loop = loop;
		}

		@Override
		public void opened(Object dataSource,
			Map<String, Object> properties)
		{
		}

		@Override
		public void progress(int bytesread, long microseconds,
			byte[] pcmdata, Map<String, Object> properties)
		{
		}

		@Override
		public void statusUpdated(StreamPlayerEvent event)
		{
			if(!loop || closed || event.getPlayerStatus() != Status.STOPPED)
				return;

			executor.execute(() -> play(player, file));
		}
	}

	/** One sound file extracted from a scene package. */
	record Clip(String name, String extension, byte[] bytes, float volume,
		boolean loop, Path file)
	{
		Clip(String name, String extension, byte[] bytes, float volume,
			boolean loop)
		{
			this(name, extension, bytes, volume, loop, null);
		}

		Clip withFile(Path value)
		{
			return new Clip(name, extension, bytes, volume, loop, value);
		}
	}
}
