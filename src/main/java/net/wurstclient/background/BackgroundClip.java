/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.mojang.blaze3d.platform.NativeImage;

/**
 * A decoded animated background: the GIF's composited frames as
 * {@link NativeImage}s, plus the timing rules that decide which one is on
 * screen.
 *
 * <p>
 * The frames are owned here, not by the texture. A {@code DynamicTexture} closes
 * whatever image it is handed, so the renderer copies the current frame into the
 * texture's own image instead of giving the texture a frame to keep.
 *
 * <p>
 * Decoding happens on a background thread - a few hundred frames take long
 * enough to be visible as a freeze - so this class also owns that thread.
 */
final class BackgroundClip implements AutoCloseable
{
	private static final ExecutorService DECODER =
		Executors.newSingleThreadExecutor(runnable -> {
			Thread thread = new Thread(runnable, "WurstB-BackgroundClip");
			thread.setDaemon(true);
			return thread;
		});

	private final NativeImage[] frames;
	private final BackgroundAnimation animation;
	private final int width;
	private final int height;
	private final boolean scaled;
	private final boolean truncated;

	private BackgroundClip(NativeImage[] frames,
		BackgroundAnimation animation, int width, int height, boolean scaled,
		boolean truncated)
	{
		this.frames = frames;
		this.animation = animation;
		this.width = width;
		this.height = height;
		this.scaled = scaled;
		this.truncated = truncated;
	}

	/**
	 * @return a clip that is ready to upload, or a failed future when the file
	 *         cannot be decoded
	 */
	static CompletableFuture<BackgroundClip> decode(Path file)
	{
		return CompletableFuture.supplyAsync(() -> {
			try
			{
				return decodeNow(file);
			}catch(IOException e)
			{
				throw new CompletionException(e);
			}
		}, DECODER);
	}

	private static BackgroundClip decodeNow(Path file) throws IOException
	{
		GifFrames.Clip clip = GifFrames.decode(file);
		List<GifFrames.Frame> decoded = clip.frames();
		NativeImage[] frames = new NativeImage[decoded.size()];
		int[] delays = new int[decoded.size()];

		try
		{
			for(int i = 0; i < frames.length; i++)
			{
				GifFrames.Frame frame = decoded.get(i);
				frames[i] = toImage(frame.argb(), clip.width(), clip.height());
				delays[i] = frame.delayMs();
			}
		}catch(RuntimeException | Error e)
		{
			closeAll(frames);
			throw e;
		}

		return new BackgroundClip(frames,
			new BackgroundAnimation(delays, clip.loopCount()), clip.width(),
			clip.height(), clip.wasScaled(), clip.wasTruncated());
	}

	/**
	 * Converts an ARGB frame into the image a texture can take.
	 *
	 * <p>
	 * {@code NativeImage}'s integer accessors are ABGR, so red and blue have to
	 * be swapped on the way in - getting this wrong shows up as a wallpaper with
	 * its colours inverted, which is exactly the kind of mistake that only a
	 * screenshot catches.
	 */
	static NativeImage toImage(int[] argb, int width, int height)
	{
		NativeImage image =
			new NativeImage(NativeImage.Format.RGBA, width, height, false);

		for(int y = 0; y < height; y++)
			for(int x = 0; x < width; x++)
			{
				int pixel = argb[y * width + x];
				int abgr = (pixel & 0xFF00FF00) | (pixel & 0xFF) << 16
					| (pixel >> 16 & 0xFF);
				image.setPixelRGBA(x, y, abgr);
			}

		return image;
	}

	int width()
	{
		return width;
	}

	int height()
	{
		return height;
	}

	int frameCount()
	{
		return frames.length;
	}

	BackgroundAnimation animation()
	{
		return animation;
	}

	NativeImage frame(int index)
	{
		if(index < 0 || index >= frames.length)
			return frames[0];

		return frames[index];
	}

	/** Whether the animation had to be shrunk to fit the decode budget. */
	boolean wasScaled()
	{
		return scaled;
	}

	/** Whether the file had more frames than {@link GifFrames#MAX_FRAMES}. */
	boolean wasTruncated()
	{
		return truncated;
	}

	@Override
	public void close()
	{
		closeAll(frames);
	}

	private static void closeAll(NativeImage[] frames)
	{
		for(NativeImage image : frames)
			if(image != null)
				image.close();
	}

	static void shutdown()
	{
		DECODER.shutdownNow();
	}
}
