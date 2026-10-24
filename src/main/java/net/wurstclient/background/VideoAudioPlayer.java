/*
 * Copyright (c) 2025-2026 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

import java.nio.file.Path;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.wurstclient.gui.title.BackgroundSelectScreen;

/**
 * Plays the audio track of a video wallpaper.
 *
 * <p>
 * A second {@link FfmpegVideoDecoder} is opened for the audio side so the
 * video decode thread keeps its own handle. The decoded PCM is streamed to a
 * {@link SourceDataLine}; playback pauses whenever the title background is not
 * on screen and loops when the track ends (the video loops independently).</p>
 */
final class VideoAudioPlayer implements AutoCloseable
{
	/** 0.7 keeps a wallpaper from being louder than the game itself. */
	private static final double GAIN = 0.7;

	private final FfmpegVideoDecoder decoder;
	private final SourceDataLine line;
	private final Thread thread;
	private volatile boolean closed;

	static VideoAudioPlayer start(Path file)
	{
		FfmpegVideoDecoder decoder = null;
		SourceDataLine line = null;

		try
		{
			decoder = FfmpegVideoDecoder.openSoftware(file.toString());

			if(decoder == null || !decoder.hasAudio())
			{
				if(decoder != null)
					decoder.close();
				return null;
			}

			int rate = decoder.audioSampleRate();
			int channels = decoder.audioChannels();

			if(rate <= 0 || channels <= 0)
			{
				decoder.close();
				return null;
			}

			AudioFormat format =
				new AudioFormat(rate, 16, channels, true, false);
			line = AudioSystem.getSourceDataLine(format);
			line.open(format);
			line.start();

			VideoAudioPlayer player = new VideoAudioPlayer(decoder, line);
			player.startThread();
			System.out.println("[Background] Video audio: " + rate + " Hz, "
				+ channels + " ch");
			return player;

		}catch(Throwable t)
		{
			System.out.println("[Background] Video audio unavailable: " + t);

			if(line != null)
				try
				{
					line.close();
				}catch(Throwable ignored)
				{
				}

			if(decoder != null)
				decoder.close();

			return null;
		}
	}

	private VideoAudioPlayer(FfmpegVideoDecoder decoder, SourceDataLine line)
	{
		this.decoder = decoder;
		this.line = line;
		this.thread = new Thread(this::run, "WurstB-VideoAudio");
		this.thread.setDaemon(true);
	}

	private void startThread()
	{
		thread.start();
	}

	private void run()
	{
		byte[] buffer = new byte[64 * 1024];

		while(!closed)
		{
			if(!isBackgroundOnScreen())
			{
				if(line.isRunning())
					line.stop();

				try
				{
					Thread.sleep(250);
				}catch(InterruptedException e)
				{
					return;
				}

				continue;
			}

			if(!line.isRunning())
				line.start();

			int bytes;

			try
			{
				bytes = decoder.nextAudio(buffer);
			}catch(Throwable t)
			{
				System.out.println("[Background] Video audio stopped: " + t);
				return;
			}

			if(bytes == 0)
			{
				if(decoder.seekAudioToStart() < 0)
					return;
				continue;
			}

			if(bytes < 0)
			{
				System.out.println("[Background] Video audio decode failed: "
					+ decoder.lastError());
				return;
			}

			applyGain(buffer, bytes);

			try
			{
				line.write(buffer, 0, bytes);
			}catch(Throwable t)
			{
				return;
			}
		}
	}

	private static void applyGain(byte[] buffer, int bytes)
	{
		for(int i = 0; i + 1 < bytes; i += 2)
		{
			int sample = (short)((buffer[i] & 0xFF) | (buffer[i + 1] << 8));
			sample = (int)(sample * GAIN);
			buffer[i] = (byte)sample;
			buffer[i + 1] = (byte)(sample >> 8);
		}
	}

	private static boolean isBackgroundOnScreen()
	{
		return Minecraft.getInstance().screen instanceof TitleScreen
			|| Minecraft.getInstance().screen instanceof BackgroundSelectScreen;
	}

	@Override
	public void close()
	{
		if(closed)
			return;

		closed = true;
		thread.interrupt();

		try
		{
			line.stop();
			line.flush();
			line.close();
		}catch(Throwable ignored)
		{
		}

		decoder.close();
	}
}
