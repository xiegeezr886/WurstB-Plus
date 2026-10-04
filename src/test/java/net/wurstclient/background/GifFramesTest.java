/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.wurstclient.background.GifFrames.Clip;
import net.wurstclient.background.GifFrames.Frame;

/**
 * Tests the GIF decode and the frame compositing it depends on.
 *
 * <p>
 * GIF frames are rectangles that have to be drawn onto a canvas, and the frame
 * before them decides whether that rectangle gets cleared or the canvas gets
 * rewound. Getting this wrong is not a subtle bug: every partial frame of an
 * animation would show up as a torn image, which is why the disposal rules are
 * tested directly instead of only through a file.
 */
final class GifFramesTest
{
	private static final int RED = 0xFFFF0000;
	private static final int GREEN = 0xFF00FF00;
	private static final int BLUE = 0xFF0000FF;

	// ------------------------------------------------------------------
	// 合成
	// ------------------------------------------------------------------

	@Test
	void drawsFramesAtTheirOffset()
	{
		GifFrames.Canvas canvas = new GifFrames.Canvas(4, 4);
		canvas.draw(new int[]{RED}, 1, 1, 1, 1);

		assertEquals(RED, canvas.pixels()[1 * 4 + 1]);
		assertEquals(0, canvas.pixels()[0]);
	}

	@Test
	void skipsTransparentPixels()
	{
		GifFrames.Canvas canvas = new GifFrames.Canvas(2, 1);
		canvas.draw(new int[]{RED, RED}, 2, 1, 0, 0);
		canvas.draw(new int[]{0x00000000, GREEN}, 2, 1, 0, 0);

		assertEquals(RED, canvas.pixels()[0]);
		assertEquals(GREEN, canvas.pixels()[1]);
	}

	/** A half transparent pixel is blended, not replaced. */
	@Test
	void blendsPartialAlpha()
	{
		GifFrames.Canvas canvas = new GifFrames.Canvas(1, 1);
		canvas.draw(new int[]{BLUE}, 1, 1, 0, 0);
		canvas.draw(new int[]{0x80FF0000}, 1, 1, 0, 0);

		int mixed = canvas.pixels()[0];
		int red = mixed >> 16 & 0xFF;
		int blue = mixed & 0xFF;

		assertTrue(red > 100, "red should have come through: " + red);
		assertTrue(blue > 0 && blue < 155, "blue should be dimmed: " + blue);
	}

	/** Clipping: a frame that hangs over the edge must not throw or wrap. */
	@Test
	void clipsFramesToTheCanvas()
	{
		GifFrames.Canvas canvas = new GifFrames.Canvas(2, 2);
		canvas.draw(new int[]{RED, RED, RED, RED}, 2, 2, 1, 1);

		assertEquals(RED, canvas.pixels()[3]);
		assertEquals(0, canvas.pixels()[0]);
	}

	@Test
	void clearsOnlyTheGivenRectangle()
	{
		GifFrames.Canvas canvas = new GifFrames.Canvas(3, 1);
		canvas.draw(new int[]{RED, RED, RED}, 3, 1, 0, 0);
		canvas.clear(1, 0, 1, 1);

		assertEquals(RED, canvas.pixels()[0]);
		assertEquals(0, canvas.pixels()[1]);
		assertEquals(RED, canvas.pixels()[2]);
	}

	@Test
	void restoreToBackgroundClearsTheFrame()
	{
		GifFrames.Canvas canvas = new GifFrames.Canvas(2, 1);
		canvas.draw(new int[]{RED, RED}, 2, 1, 0, 0);
		canvas.applyDisposal(GifFrames.DISPOSAL_BACKGROUND,
			new int[]{0, 0, 2, 1});

		assertEquals(0, canvas.pixels()[0]);
		assertEquals(0, canvas.pixels()[1]);
	}

	@Test
	void restoreToPreviousRewindsTheCanvas()
	{
		GifFrames.Canvas canvas = new GifFrames.Canvas(2, 1);
		canvas.draw(new int[]{RED, RED}, 2, 1, 0, 0);

		// the frame that declares restoreToPrevious remembers the canvas first
		canvas.save();
		canvas.draw(new int[]{GREEN, GREEN}, 2, 1, 0, 0);
		canvas.applyDisposal(GifFrames.DISPOSAL_PREVIOUS,
			new int[]{0, 0, 2, 1});

		assertEquals(RED, canvas.pixels()[0]);
		assertEquals(RED, canvas.pixels()[1]);
	}

	@Test
	void disposalWithoutAFrameDoesNothing()
	{
		GifFrames.Canvas canvas = new GifFrames.Canvas(1, 1);
		canvas.draw(new int[]{RED}, 1, 1, 0, 0);
		canvas.applyDisposal(GifFrames.DISPOSAL_BACKGROUND, null);
		canvas.applyDisposal(null, new int[]{0, 0, 1, 1});

		assertEquals(RED, canvas.pixels()[0]);
	}

	// ------------------------------------------------------------------
	// 缩放预算
	// ------------------------------------------------------------------

	@Test
	void smallAnimationsAreNotScaled()
	{
		assertEquals(1F, GifFrames.scaleFor(320, 180, 20), 0.0001F);
		assertEquals(1F, GifFrames.scaleFor(0, 0, 0), 0.0001F);
	}

	@Test
	void hugeAnimationsAreScaledToTheBudget()
	{
		// 1920x1080 x 60 frames is 124 million pixels, far past the budget
		float scale = GifFrames.scaleFor(1920, 1080, 60);

		assertTrue(scale < 0.4F, "expected a heavy reduction, got " + scale);
		assertTrue(scale > 0.1F, "expected a usable size, got " + scale);

		long pixels = Math.round(1920 * scale) * Math.round(1080 * scale) * 60L;
		assertTrue(pixels <= GifFrames.MAX_TOTAL_PIXELS,
			"scaled animation still exceeds the budget: " + pixels);
	}

	@Test
	void noAxisExceedsTheMaximumDimension()
	{
		float scale = GifFrames.scaleFor(8000, 1000, 1);

		assertEquals(GifFrames.MAX_DIMENSION / 8000F, scale, 0.0001F);
	}

	@Test
	void scalingKeepsTheFrameShape()
	{
		int[] source = new int[4 * 2];

		for(int i = 0; i < source.length; i++)
			source[i] = RED;

		int[] scaled = GifFrames.scale(source, 4, 2, 2, 1);

		assertEquals(2, scaled.length);
		assertEquals(0xFFFF0000, scaled[0] & 0xFFFFFFFF);
	}

	// ------------------------------------------------------------------
	// 端到端解码
	// ------------------------------------------------------------------

	@Test
	void decodesAnAnimatedGif(@TempDir Path folder) throws IOException
	{
		Path file = folder.resolve("animation.gif");
		writeGif(file, List.of(image(2, 2, RED), image(2, 2, GREEN)),
			new int[]{5, 7}, 0, "none");

		Clip clip = GifFrames.decode(file);

		assertEquals(2, clip.frames().size());
		assertEquals(2, clip.width());
		assertEquals(2, clip.height());
		assertEquals(2, clip.sourceWidth());
		assertFalse(clip.wasScaled());
		assertFalse(clip.wasTruncated());

		// the delay is stored in hundredths of a second
		assertEquals(50, clip.frames().get(0).delayMs());
		assertEquals(70, clip.frames().get(1).delayMs());

		assertEquals(RED, clip.frames().get(0).argb()[0]);
		assertEquals(GREEN, clip.frames().get(1).argb()[0]);
	}

	@Test
	void readsTheNetscapeLoopCount(@TempDir Path folder) throws IOException
	{
		Path once = folder.resolve("once.gif");
		writeGif(once, List.of(image(1, 1, RED), image(1, 1, GREEN)),
			new int[]{10, 10}, 1, "none");

		assertEquals(1, GifFrames.decode(once).loopCount());

		Path forever = folder.resolve("forever.gif");
		writeGif(forever, List.of(image(1, 1, RED), image(1, 1, GREEN)),
			new int[]{10, 10}, 0, "none");

		Clip clip = GifFrames.decode(forever);

		assertEquals(0, clip.loopCount());
		assertTrue(new BackgroundAnimation(
			new int[]{clip.frames().get(0).delayMs(),
				clip.frames().get(1).delayMs()}, clip.loopCount())
					.repeatsForever());
	}

	@Test
	void rejectsFilesThatAreNotGifs(@TempDir Path folder) throws IOException
	{
		Path text = folder.resolve("not-an-image.gif");
		Files.writeString(text, "definitely not a GIF");

		assertThrows(IOException.class, () -> GifFrames.decode(text));
		assertThrows(IOException.class,
			() -> GifFrames.decode(folder.resolve("missing.gif")));
		assertThrows(IOException.class, () -> GifFrames.decode(null));
	}

	@Test
	void buildsAClipFromDecodedFrames(@TempDir Path folder) throws IOException
	{
		Path file = folder.resolve("loop.gif");
		writeGif(file, List.of(image(2, 2, RED), image(2, 2, GREEN)),
			new int[]{25, 25}, 3, "none");

		Clip clip = GifFrames.decode(file);
		Frame first = clip.frames().get(0);

		BackgroundAnimation animation = new BackgroundAnimation(
			new int[]{first.delayMs(), clip.frames().get(1).delayMs()},
			clip.loopCount());

		assertEquals(2, animation.frameCount());
		assertEquals(0, animation.frameIndexAt(0));
		assertEquals(1, animation.frameIndexAt(250));
		assertNotNull(clip.frames().get(1).argb());
	}

	// ------------------------------------------------------------------
	// 测试用的 GIF 写入
	// ------------------------------------------------------------------

	private static BufferedImage image(int width, int height, int argb)
	{
		BufferedImage image =
			new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

		for(int y = 0; y < height; y++)
			for(int x = 0; x < width; x++)
				image.setRGB(x, y, argb);

		return image;
	}

	/**
	 * Writes an animated GIF, including the Netscape loop extension.
	 *
	 * <p>
	 * The JDK's GIF plugin keeps application extensions in the image metadata,
	 * which is also where its reader looks - so the extension goes on the first
	 * frame, exactly where a looping GIF carries it.
	 */
	private static void writeGif(Path file, List<BufferedImage> frames,
		int[] delaysHundredths, int loopCount, String disposal)
		throws IOException
	{
		Iterator<ImageWriter> writers =
			ImageIO.getImageWritersByFormatName("gif");

		if(!writers.hasNext())
			throw new IOException("no GIF writer is available");

		ImageWriter writer = writers.next();

		try(ImageOutputStream output =
			ImageIO.createImageOutputStream(file.toFile()))
		{
			writer.setOutput(output);
			writer.prepareWriteSequence(null);

			for(int index = 0; index < frames.size(); index++)
			{
				BufferedImage frame = frames.get(index);
				ImageTypeSpecifier type =
					ImageTypeSpecifier.createFromRenderedImage(frame);
				IIOMetadata metadata =
					writer.getDefaultImageMetadata(type, null);
				applyFrameMetadata(metadata, delaysHundredths[index], disposal);

				if(index == 0)
					applyLoopExtension(metadata, loopCount);

				writer.writeToSequence(new IIOImage(frame, null, metadata),
					null);
			}

			writer.endWriteSequence();
		}finally
		{
			writer.dispose();
		}
	}

	private static void applyFrameMetadata(IIOMetadata metadata,
		int delayHundredths, String disposal) throws IOException
	{
		String format = metadata.getNativeMetadataFormatName();
		IIOMetadataNode root = (IIOMetadataNode)metadata.getAsTree(format);
		IIOMetadataNode control = child(root, "GraphicControlExtension");

		control.setAttribute("disposalMethod", disposal);
		control.setAttribute("userInputFlag", "FALSE");
		control.setAttribute("transparentColorFlag", "FALSE");
		control.setAttribute("delayTime", Integer.toString(delayHundredths));
		control.setAttribute("transparentColorIndex", "0");
		metadata.setFromTree(format, root);
	}

	/** NETSCAPE/2.0 with the sub-block ID and a little-endian loop count, which
	 * is what an encoder writes and what the decoder has to read back. */
	private static void applyLoopExtension(IIOMetadata metadata, int loopCount)
		throws IOException
	{
		String format = metadata.getNativeMetadataFormatName();
		IIOMetadataNode root = (IIOMetadataNode)metadata.getAsTree(format);
		IIOMetadataNode extensions = child(root, "ApplicationExtensions");
		IIOMetadataNode netscape = new IIOMetadataNode("ApplicationExtension");

		netscape.setAttribute("applicationID", "NETSCAPE");
		netscape.setAttribute("authenticationCode", "2.0");
		netscape.setUserObject(new byte[]{0x1, (byte)(loopCount & 0xFF),
			(byte)(loopCount >> 8 & 0xFF)});

		extensions.appendChild(netscape);
		metadata.setFromTree(format, root);
	}

	private static IIOMetadataNode child(IIOMetadataNode parent, String name)
	{
		for(int i = 0; i < parent.getLength(); i++)
			if(parent.item(i) instanceof IIOMetadataNode node
				&& name.equalsIgnoreCase(node.getNodeName()))
				return node;

		IIOMetadataNode node = new IIOMetadataNode(name);
		parent.appendChild(node);
		return node;
	}
}
