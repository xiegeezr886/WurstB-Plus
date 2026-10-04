/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;

/**
 * Decodes an animated GIF into composited frames.
 *
 * <p>
 * GIF frames are not pictures: each one is a rectangle that has to be drawn onto
 * a canvas, and the frame before it may ask for that rectangle to be cleared or
 * for the whole canvas to be rewound afterwards. This class does that
 * compositing, which is the part that cannot be skipped - showing the decoded
 * rectangles as-is makes every partial frame look like a corrupted image.
 *
 * <p>
 * Deliberately free of Minecraft types, so all of it runs in a unit test. Frames
 * come out as ARGB ints; turning those into textures is the caller's job.
 */
public final class GifFrames
{
	/** Frames past this are dropped: a background is not worth gigabytes. */
	public static final int MAX_FRAMES = 150;

	/** No frame is kept larger than this on either axis. */
	public static final int MAX_DIMENSION = 1600;

	/**
	 * The decode budget, in pixels across every frame. A 1080p GIF with 60
	 * frames is 124 million pixels - half a gigabyte of ints - so large
	 * animations are scaled down rather than refused.
	 */
	public static final long MAX_TOTAL_PIXELS = 12_000_000L;

	static final String DISPOSAL_NONE = "none";
	static final String DISPOSAL_BACKGROUND = "restoreToBackgroundColor";
	static final String DISPOSAL_PREVIOUS = "restoreToPrevious";

	private GifFrames()
	{
	}

	/**
	 * One composited frame.
	 *
	 * @param argb
	 *            {@code width * height} pixels, top-left first
	 * @param delayMs
	 *            how long the frame is shown, straight from the file - a
	 *            degenerate {@code 0} is left alone so that
	 *            {@link BackgroundAnimation} can apply the browser rule
	 */
	public record Frame(int[] argb, int delayMs)
	{
	}

	/**
	 * @param width
	 *            the size the frames were scaled to
	 * @param height
	 *            the size the frames were scaled to
	 * @param sourceWidth
	 *            the GIF's own canvas size, for reporting
	 * @param sourceHeight
	 *            the GIF's own canvas size, for reporting
	 * @param loopCount
	 *            {@code 0} for forever
	 * @param availableFrames
	 *            how many frames the file holds, which can exceed
	 *            {@code frames.size()} when {@link #MAX_FRAMES} cut it short
	 */
	public record Clip(int width, int height, int sourceWidth, int sourceHeight,
		int loopCount, int availableFrames, List<Frame> frames)
	{
		public boolean wasScaled()
		{
			return width != sourceWidth || height != sourceHeight;
		}

		public boolean wasTruncated()
		{
			return availableFrames > frames.size();
		}
	}

	/**
	 * @return the composited frames, scaled down when the animation is too large
	 *         to hold at full size
	 * @throws IOException
	 *             when the file is not a readable GIF
	 */
	public static Clip decode(Path file) throws IOException
	{
		if(file == null || !Files.isRegularFile(file))
			throw new IOException("not a readable file: " + file);

		try(ImageInputStream input = ImageIO.createImageInputStream(
			file.toFile()))
		{
			if(input == null)
				throw new IOException("no image reader for " + file);

			ImageReader reader = findReader();

			try
			{
				reader.setInput(input, false, false);
				return read(reader);
			}finally
			{
				reader.dispose();
			}
		}
	}

	private static ImageReader findReader() throws IOException
	{
		Iterator<ImageReader> readers =
			ImageIO.getImageReadersByFormatName("gif");

		if(!readers.hasNext())
			throw new IOException("no GIF reader is available");

		return readers.next();
	}

	private static Clip read(ImageReader reader) throws IOException
	{
		int available = reader.getNumImages(true);

		if(available <= 0)
			throw new IOException("the GIF has no frames");

		int frameCount = Math.min(available, MAX_FRAMES);
		int loopCount = readLoopCount(reader);

		BufferedImage first = reader.read(0);
		int[] screen = logicalScreenSize(reader, first);
		int sourceWidth = screen[0];
		int sourceHeight = screen[1];

		float scale = scaleFor(sourceWidth, sourceHeight, frameCount);
		int width = Math.max(1, Math.round(sourceWidth * scale));
		int height = Math.max(1, Math.round(sourceHeight * scale));

		Canvas canvas = new Canvas(sourceWidth, sourceHeight);
		List<Frame> frames = new ArrayList<>(frameCount);

		// the disposal of the frame drawn last, which is what has to be undone
		// before the next one goes down
		String previousDisposal = DISPOSAL_NONE;
		int[] previousRect = null;

		for(int index = 0; index < frameCount; index++)
		{
			canvas.applyDisposal(previousDisposal, previousRect);

			BufferedImage image = index == 0 ? first : reader.read(index);
			IIOMetadata metadata = reader.getImageMetadata(index);
			IIOMetadataNode root = tree(metadata);
			int[] offset = frameOffset(root);
			String disposal = disposalMethod(root);
			int delayMs = frameDelayMs(root);

			// restoreToPrevious has to rewind to the moment before this frame
			if(DISPOSAL_PREVIOUS.equals(disposal))
				canvas.save();

			int frameWidth = image.getWidth();
			int frameHeight = image.getHeight();
			canvas.draw(argb(image), frameWidth, frameHeight, offset[0],
				offset[1]);

			int[] snapshot = canvas.snapshot();
			frames.add(new Frame(
				scale == 1F ? snapshot
					: scale(snapshot, sourceWidth, sourceHeight, width, height),
				delayMs));

			previousDisposal = disposal;
			previousRect =
				new int[]{offset[0], offset[1], frameWidth, frameHeight};
		}

		return new Clip(width, height, sourceWidth, sourceHeight, loopCount,
			available, List.copyOf(frames));
	}

	/**
	 * Scaled so the whole animation fits the decode budget and neither axis
	 * exceeds {@link #MAX_DIMENSION}.
	 */
	static float scaleFor(int width, int height, int frameCount)
	{
		if(width <= 0 || height <= 0 || frameCount <= 0)
			return 1F;

		float byDimension = MAX_DIMENSION / (float)Math.max(width, height);
		double budget =
			MAX_TOTAL_PIXELS / (double)((long)width * height * frameCount);
		float byBudget = (float)Math.sqrt(budget);

		return Math.min(1F, Math.min(byDimension, byBudget));
	}

	/** Scales a composited frame down, keeping its transparency. */
	static int[] scale(int[] argb, int width, int height, int targetWidth,
		int targetHeight)
	{
		BufferedImage source = new BufferedImage(width, height,
			BufferedImage.TYPE_INT_ARGB);
		source.setRGB(0, 0, width, height, argb, 0, width);

		BufferedImage target = new BufferedImage(targetWidth, targetHeight,
			BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = target.createGraphics();

		try
		{
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
				RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
		}finally
		{
			graphics.dispose();
		}

		return target.getRGB(0, 0, targetWidth, targetHeight, null, 0,
			targetWidth);
	}

	private static int[] argb(BufferedImage image)
	{
		int width = image.getWidth();
		int height = image.getHeight();
		return image.getRGB(0, 0, width, height, null, 0, width);
	}

	/**
	 * The canvas a GIF is composited on.
	 *
	 * <p>
	 * Package private so the disposal rules can be tested directly, without
	 * having to write GIFs that use them.
	 */
	static final class Canvas
	{
		private final int width;
		private final int height;
		private final int[] pixels;
		private int[] saved;

		Canvas(int width, int height)
		{
			this.width = Math.max(1, width);
			this.height = Math.max(1, height);
			pixels = new int[this.width * this.height];
		}

		int[] pixels()
		{
			return pixels;
		}

		/** Remembers the canvas for a later {@link #restore()}. */
		void save()
		{
			saved = pixels.clone();
		}

		void restore()
		{
			if(saved != null)
				System.arraycopy(saved, 0, pixels, 0, pixels.length);
		}

		/**
		 * Draws one frame rectangle over the canvas, source-over and skipping
		 * fully transparent pixels.
		 */
		void draw(int[] frame, int frameWidth, int frameHeight, int offsetX,
			int offsetY)
		{
			for(int y = 0; y < frameHeight; y++)
			{
				int targetY = offsetY + y;

				if(targetY < 0 || targetY >= height)
					continue;

				for(int x = 0; x < frameWidth; x++)
				{
					int targetX = offsetX + x;

					if(targetX < 0 || targetX >= width)
						continue;

					int source = frame[y * frameWidth + x];
					int alpha = source >>> 24;

					if(alpha == 0)
						continue;

					int target = targetY * width + targetX;

					if(alpha == 255)
						pixels[target] = source;
					else
						pixels[target] = blend(source, alpha, pixels[target]);
				}
			}
		}

		/** Clears a frame rectangle back to transparent. */
		void clear(int offsetX, int offsetY, int frameWidth, int frameHeight)
		{
			for(int y = 0; y < frameHeight; y++)
			{
				int targetY = offsetY + y;

				if(targetY < 0 || targetY >= height)
					continue;

				int from = targetY * width + Math.max(0, offsetX);
				int to =
					targetY * width + Math.min(width, offsetX + frameWidth);

				for(int x = from; x < to; x++)
					pixels[x] = 0;
			}
		}

		/**
		 * Undoes the frame that was drawn last, as its own disposal method asks.
		 *
		 * @param rect
		 *            that frame's rectangle, or null when there was none
		 */
		void applyDisposal(String disposal, int[] rect)
		{
			if(rect == null || disposal == null)
				return;

			if(DISPOSAL_BACKGROUND.equals(disposal))
				clear(rect[0], rect[1], rect[2], rect[3]);
			else if(DISPOSAL_PREVIOUS.equals(disposal))
				restore();
		}

		int[] snapshot()
		{
			return pixels.clone();
		}

		private static int blend(int source, int alpha, int target)
		{
			int inverse = 255 - alpha;
			int red = ((source >> 16 & 0xFF) * alpha
				+ (target >> 16 & 0xFF) * inverse) / 255;
			int green = ((source >> 8 & 0xFF) * alpha
				+ (target >> 8 & 0xFF) * inverse) / 255;
			int blue =
				((source & 0xFF) * alpha + (target & 0xFF) * inverse) / 255;
			int outAlpha = alpha + (target >>> 24) * inverse / 255;

			return Math.min(255, outAlpha) << 24 | red << 16 | green << 8 | blue;
		}
	}

	private static IIOMetadataNode tree(IIOMetadata metadata)
	{
		if(metadata == null)
			return null;

		try
		{
			return (IIOMetadataNode)metadata
				.getAsTree(metadata.getNativeMetadataFormatName());
		}catch(RuntimeException e)
		{
			return null;
		}
	}

	private static int[] logicalScreenSize(ImageReader reader,
		BufferedImage fallback)
	{
		IIOMetadataNode screen =
			node(streamTree(reader), "LogicalScreenDescriptor");

		if(screen != null)
		{
			int width = number(screen, "logicalScreenWidth", 0);
			int height = number(screen, "logicalScreenHeight", 0);

			if(width > 0 && height > 0)
				return new int[]{width, height};
		}

		return new int[]{fallback.getWidth(), fallback.getHeight()};
	}

	private static IIOMetadataNode streamTree(ImageReader reader)
	{
		try
		{
			return tree(reader.getStreamMetadata());
		}catch(IOException e)
		{
			return null;
		}
	}

	private static int[] frameOffset(IIOMetadataNode root)
	{
		IIOMetadataNode descriptor = node(root, "ImageDescriptor");

		if(descriptor == null)
			return new int[]{0, 0};

		return new int[]{number(descriptor, "imageLeftPosition", 0),
			number(descriptor, "imageTopPosition", 0)};
	}

	private static String disposalMethod(IIOMetadataNode root)
	{
		IIOMetadataNode control = node(root, "GraphicControlExtension");

		if(control == null)
			return DISPOSAL_NONE;

		String disposal = control.getAttribute("disposalMethod");

		return disposal == null || disposal.isBlank() ? DISPOSAL_NONE : disposal;
	}

	private static int frameDelayMs(IIOMetadataNode root)
	{
		IIOMetadataNode control = node(root, "GraphicControlExtension");

		// the GIF delay is in hundredths of a second
		return control == null ? 0 : number(control, "delayTime", 0) * 10;
	}

	/**
	 * The loop count of the Netscape application extension, {@code 0} when the
	 * file does not carry one - which means "forever".
	 *
	 * <p>
	 * The extension is image metadata in the JDK's GIF plugin, not stream
	 * metadata, so it is looked up there first and in the stream metadata second
	 * in case another writer put it there.
	 */
	private static int readLoopCount(ImageReader reader)
	{
		int loopCount = loopCountOf(
			node(tree(safeImageMetadata(reader)), "ApplicationExtensions"));

		if(loopCount >= 0)
			return loopCount;

		loopCount = loopCountOf(node(streamTree(reader),
			"ApplicationExtensions"));

		return Math.max(0, loopCount);
	}

	private static IIOMetadata safeImageMetadata(ImageReader reader)
	{
		try
		{
			return reader.getImageMetadata(0);
		}catch(IOException | RuntimeException e)
		{
			return null;
		}
	}

	/** @return the loop count, or {@code -1} when there is no such extension */
	private static int loopCountOf(IIOMetadataNode extensions)
	{
		if(extensions == null)
			return -1;

		for(int i = 0; i < extensions.getLength(); i++)
		{
			if(!(extensions.item(i) instanceof IIOMetadataNode extension))
				continue;

			if(!"NETSCAPE".equalsIgnoreCase(
				extension.getAttribute("applicationID")))
				continue;

			if(!(extension.getUserObject() instanceof byte[] bytes))
				continue;

			// the payload starts with the sub-block ID that the Netscape
			// extension is defined to carry, followed by a little-endian short;
			// a writer that omits the ID has the count at the front instead
			int offset = bytes.length >= 3 ? 1 : 0;

			if(bytes.length - offset < 2)
				continue;

			return (bytes[offset] & 0xFF) | (bytes[offset + 1] & 0xFF) << 8;
		}

		return -1;
	}

	/**
	 * Depth first search for a named node: the GIF metadata tree puts the
	 * interesting nodes at different depths, so the path cannot be hard coded.
	 */
	private static IIOMetadataNode node(IIOMetadataNode root, String name)
	{
		if(root == null)
			return null;

		if(name.equalsIgnoreCase(root.getNodeName()))
			return root;

		for(int i = 0; i < root.getLength(); i++)
		{
			if(!(root.item(i) instanceof IIOMetadataNode child))
				continue;

			IIOMetadataNode found = node(child, name);

			if(found != null)
				return found;
		}

		return null;
	}

	private static int number(IIOMetadataNode node, String attribute,
		int fallback)
	{
		String value = node.getAttribute(attribute);

		if(value == null || value.isBlank())
			return fallback;

		try
		{
			return Integer.parseInt(value.trim());
		}catch(NumberFormatException e)
		{
			return fallback;
		}
	}
}
