/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
package net.wurstclient.hacks;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.ResourceLocation;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.GUIRenderListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.FileSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.render.AsyncTextureLoader;
import net.wurstclient.util.render.GuiGraphicsExtractor;

/**
 * HUD overlay showing a configurable local image.
 *
 * <p>The image is picked from the {@code wurst/images} folder. A checkerboard
 * placeholder PNG is generated on first launch, and users can drop their own
 * PNG files into the same folder. Loading and GPU upload happen off the render
 * thread through {@link AsyncTextureLoader}.
 *
 * <p>Simplifications: Soar applied rounded corners, a drop shadow and an alpha
 * setting through NanoVG. This port draws a plain rectangular blit, caps the
 * image's longest side at 256 pixels (before the scale setting is applied) and
 * only guarantees PNG support, which is what Minecraft's native image decoder
 * accepts.
 */
@SearchTags({"image display", "image", "picture", "photo", "hud image",
	"png"})
public final class ImageDisplayHack extends Hack implements GUIRenderListener
{
	private static final int PADDING = 4;
	private static final double MAX_DISPLAY_SIZE = 256;
	private static final AtomicInteger NEXT_TEXTURE_ID = new AtomicInteger();

	private final FileSetting imageFile = new FileSetting("Image",
		"PNG image shown in the HUD. Drop your own PNG files into the Wurst"
			+ " 'images' folder and select one. A checkerboard placeholder is"
			+ " created on first launch.",
		"images", ImageDisplayHack::createPlaceholder);

	private final SliderSetting scale = new SliderSetting("Scale",
		"Scale of the displayed image.", 1, 0.1, 4, 0.1, ValueDisplay.DECIMAL);

	private final SliderSetting posX = new SliderSetting("Position X",
		"X position of the image.", 10, 0, 2500, 1, ValueDisplay.INTEGER);

	private final SliderSetting posY = new SliderSetting("Position Y",
		"Y position of the image.", 100, 0, 2500, 1, ValueDisplay.INTEGER);

	private final CheckboxSetting background = new CheckboxSetting(
		"Show background", "Draws a colored box behind the image.", false);

	private final ColorSetting bgColor = new ColorSetting("Background color",
		"Color of the background box.", new Color(0, 0, 0, 150));

	private final ColorSetting textColor = new ColorSetting("Text color",
		"Color of the placeholder/status text.", Color.WHITE);

	private ResourceLocation textureLocation;
	private int textureWidth;
	private int textureHeight;
	private Path requestedFile;
	private int loadGeneration;
	private String status = "No image file";

	public ImageDisplayHack()
	{
		super("ImageDisplay");
		setCategory(Category.RENDER);
		addSetting(imageFile);
		addSetting(scale);
		addSetting(posX);
		addSetting(posY);
		addSetting(background);
		addSetting(bgColor);
		addSetting(textColor);
		bgColor.visibleWhen(background::isChecked);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(GUIRenderListener.class, this);
		loadImage(imageFile.getSelectedFile());
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(GUIRenderListener.class, this);
		loadGeneration++;
		releaseCurrentTexture();
		requestedFile = null;
		status = "No image file";
	}

	@Override
	public void onRenderGUI(GuiGraphicsExtractor context, float partialTicks)
	{
		if(MC.player == null)
			return;

		Path selected = imageFile.getSelectedFile();
		if(!selected.equals(requestedFile))
			loadImage(selected);

		int x = (int)Math.round(posX.getValue());
		int y = (int)Math.round(posY.getValue());

		if(textureLocation == null)
		{
			drawStatusText(context, x, y, status);
			return;
		}

		double fit = Math.min(1,
			MAX_DISPLAY_SIZE / Math.max(textureWidth, textureHeight));
		double drawScale = fit * scale.getValue();
		int drawWidth = Math.max(1, (int)Math.round(textureWidth * drawScale));
		int drawHeight =
			Math.max(1, (int)Math.round(textureHeight * drawScale));

		if(background.isChecked())
			context.fill(x - PADDING, y - PADDING, x + drawWidth + PADDING,
				y + drawHeight + PADDING, bgColor.getColor().getRGB());

		context.blit(RenderPipelines.GUI_TEXTURED, textureLocation, x, y, 0, 0,
			drawWidth, drawHeight, textureWidth, textureHeight);
	}

	private void drawStatusText(GuiGraphicsExtractor context, int x, int y,
		String text)
	{
		if(text == null)
			text = "No image";

		int width = MC.font.width(text);
		if(background.isChecked())
			context.fill(x - PADDING, y - PADDING, x + width + PADDING,
				y + MC.font.lineHeight + PADDING, bgColor.getColor().getRGB());

		context.text(MC.font, text, x, y, textColor.getColor().getRGB(), true);
	}

	private void loadImage(Path file)
	{
		requestedFile = file;
		int generation = ++loadGeneration;
		releaseCurrentTexture();

		if(file == null || !Files.isRegularFile(file))
		{
			status = "No image file";
			return;
		}

		status = "Loading image...";
		ResourceLocation location = ResourceLocation.fromNamespaceAndPath("wurst",
			"dynamic/imagedisplay_" + NEXT_TEXTURE_ID.getAndIncrement());

		CompletableFuture.supplyAsync(() -> readImageSize(file))
			.thenCompose(size -> {
				if(size == null)
					return CompletableFuture.<LoadedImage>failedFuture(
						new IOException("Unsupported image file"));

				return AsyncTextureLoader.load(file, location).thenApply(
					texture -> new LoadedImage(texture, size[0], size[1]));
			}).whenComplete((loaded, error) -> MC.execute(() ->
			{
				boolean current = generation == loadGeneration && isEnabled();

				if(!current)
				{
					// The hack was disabled or another file was selected while
					// this image was uploading, so drop the stale texture.
					if(loaded != null)
						MC.getTextureManager().release(loaded.texture());
					return;
				}

				if(error != null || loaded == null)
				{
					status = "Could not load image";
					return;
				}

				releaseCurrentTexture();
				textureLocation = loaded.texture();
				textureWidth = loaded.width();
				textureHeight = loaded.height();
				status = null;
			}));
	}

	private void releaseCurrentTexture()
	{
		if(textureLocation != null)
			MC.getTextureManager().release(textureLocation);

		textureLocation = null;
		textureWidth = 0;
		textureHeight = 0;
	}

	/**
	 * Reads the image dimensions without decoding the whole file. Returns
	 * {@code null} if the file is not a readable image.
	 */
	private static int[] readImageSize(Path file)
	{
		try(ImageInputStream input =
			ImageIO.createImageInputStream(file.toFile()))
		{
			if(input == null)
				return null;

			Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
			if(!readers.hasNext())
				return null;

			ImageReader reader = readers.next();
			try
			{
				reader.setInput(input);
				int width = reader.getWidth(0);
				int height = reader.getHeight(0);
				return width > 0 && height > 0 ? new int[]{width, height} : null;

			}finally
			{
				reader.dispose();
			}

		}catch(IOException e)
		{
			return null;
		}
	}

	private static void createPlaceholder(Path folder)
	{
		Path file = folder.resolve("placeholder.png");
		if(Files.exists(file))
			return;

		try
		{
			BufferedImage image =
				new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
			for(int y = 0; y < 64; y++)
				for(int x = 0; x < 64; x++)
					image.setRGB(x, y,
						(x / 8 + y / 8) % 2 == 0 ? 0xFF2E3440 : 0xFF88C0D0);

			ImageIO.write(image, "png", file.toFile());

		}catch(IOException e)
		{
			throw new UncheckedIOException(e);
		}
	}

	private record LoadedImage(ResourceLocation texture, int width, int height)
	{}
}
