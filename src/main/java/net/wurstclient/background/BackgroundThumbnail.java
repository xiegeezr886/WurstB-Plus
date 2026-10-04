/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import com.mojang.blaze3d.platform.NativeImage;

/**
 * Makes the small preview a background card shows.
 *
 * <p>
 * Generating one at import time is what keeps the picker cheap: the grid can
 * load a handful of 256px thumbnails instead of decoding every full size
 * wallpaper, which for a 4K image would be tens of megabytes each.
 *
 * <p>
 * Runs off the client thread - decoding and encoding are both slow.
 */
public final class BackgroundThumbnail
{
	/** Wide enough for a card, small enough to always upload cheaply. */
	public static final int MAX_SIZE = 256;

	private BackgroundThumbnail()
	{
	}

	/**
	 * @return the preview as PNG bytes, or null when the file cannot be read or
	 *         is not an image - which is the honest outcome for a video, whose
	 *         preview is generated from a decoded frame elsewhere.
	 */
	public static byte[] create(Path media, int maxSize)
	{
		if(media == null || !Files.isRegularFile(media))
			return null;

		try(InputStream in = Files.newInputStream(media);
			NativeImage source = NativeImage.read(in))
		{
			return create(source, maxSize);

		}catch(IOException | RuntimeException e)
		{
			return null;
		}
	}

	/**
	 * The preview of an animated file, taken from its first composited frame.
	 *
	 * <p>
	 * {@code NativeImage} cannot read a GIF at all, so without this every
	 * imported GIF would sit in the picker as a blank card - which reads as a
	 * broken import rather than as a moving wallpaper.
	 */
	public static byte[] createAnimated(Path media, int maxSize)
	{
		if(media == null || !Files.isRegularFile(media))
			return null;

		try
		{
			GifFrames.Clip clip = GifFrames.decode(media);

			if(clip.frames().isEmpty())
				return null;

			try(NativeImage source = BackgroundClip.toImage(
				clip.frames().get(0).argb(), clip.width(), clip.height()))
			{
				return create(source, maxSize);
			}
		}catch(IOException | RuntimeException e)
		{
			return null;
		}
	}

	/** Rescales and encodes an image that is already decoded. */
	private static byte[] create(NativeImage source, int maxSize)
		throws IOException
	{
		int width = source.getWidth();
		int height = source.getHeight();

		if(width <= 0 || height <= 0)
			return null;

		int limit = Math.max(16, maxSize);
		float scale = Math.min(1F, limit / (float)Math.max(width, height));
		int targetWidth = Math.max(1, Math.round(width * scale));
		int targetHeight = Math.max(1, Math.round(height * scale));

		if(targetWidth == width && targetHeight == height)
			return encode(source);

		try(NativeImage thumbnail =
			new NativeImage(targetWidth, targetHeight, false))
		{
			// the vanilla bitmap font atlas uses this same call to rescale
			source.resizeSubRectTo(0, 0, width, height, thumbnail);
			return encode(thumbnail);
		}
	}

	/** NativeImage can only encode by writing, so this writes to a temp file. */
	private static byte[] encode(NativeImage image) throws IOException
	{
		Path temp = Files.createTempFile("wurstb-thumbnail", ".png");

		try
		{
			image.writeToFile(temp);
			return Files.readAllBytes(temp);
		}finally
		{
			Files.deleteIfExists(temp);
		}
	}
}
