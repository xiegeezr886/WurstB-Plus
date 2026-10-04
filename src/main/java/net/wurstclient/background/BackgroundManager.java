/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.wurstclient.WurstClient;
import net.wurstclient.twilight.TwilightCoverFit;
import net.wurstclient.util.render.AsyncTextureLoader;

/**
 * Owns the title screen background: which one is selected, the texture behind
 * it, and the motion it is drawn with.
 *
 * <p>
 * A client wide singleton, because the selection outlives any one screen and
 * has to keep showing behind the background picker.
 *
 * <p>
 * The background is drawn with vanilla {@code GuiGraphics.blit} only. It runs
 * before any Skia pass, and mixing the two here would fight over the stateful
 * Skia GL backend for no gain.
 */
public final class BackgroundManager
{
	private static final BackgroundManager INSTANCE = new BackgroundManager();

	/**
	 * One fixed texture id: only one background is ever shown, so reusing the
	 * slot means switching never leaks a registration.
	 */
	private static final ResourceLocation LOCATION =
		new ResourceLocation(WurstClient.MOD_ID, "title_background");

	private BackgroundStorage storage;

	/** The id whose texture is currently registered, or null when none is. */
	private String loadedId;

	/** The texture we registered, to notice a resource reload dropping it. */
	private DynamicTexture loadedTexture;

	/** Set when the selected background could not be read, so we stop retrying
	 * every frame. */
	private String failedId;

	private CompletableFuture<ResourceLocation> pending;

	/** The animated background that is playing, or null for a still image. */
	private BackgroundClip clip;

	/** Which of the clip's frames the texture currently holds. */
	private int clipFrame = -1;

	/** When the clip started playing, which is what its clock is measured
	 * from. */
	private long clipStartedAt;

	private CompletableFuture<BackgroundClip> clipPending;

	private BackgroundManager()
	{
	}

	public static BackgroundManager get()
	{
		return INSTANCE;
	}

	/** Stops the clip decoder, called when the client shuts down. */
	public static void shutdown()
	{
		BackgroundClip.shutdown();
	}

	public BackgroundStorage storage()
	{
		if(storage == null)
			storage = new BackgroundStorage(WurstClient.INSTANCE
				.getWurstFolder().resolve("backgrounds"));

		return storage;
	}

	/** Every stored background, newest first. The built-in default is not part
	 * of this list. */
	public List<BackgroundEntry> entries()
	{
		return storage().list();
	}

	public String selectedId()
	{
		return WurstClient.INSTANCE.getGuiPreferences().getSelectedBackground();
	}

	public boolean isDefaultSelected()
	{
		return BackgroundStorage.DEFAULT_ID.equals(selectedId());
	}

	public BackgroundMotion motion()
	{
		return WurstClient.INSTANCE.getGuiPreferences().getBackgroundMotion();
	}

	/** Switches the selection and remembers it. */
	public void select(String id)
	{
		WurstClient.INSTANCE.getGuiPreferences().setSelectedBackground(id);
		forget();
	}

	/**
	 * Drops the loaded texture so the next frame rereads the selection. Called
	 * after an import or a delete, and after a resource reload.
	 */
	public void forget()
	{
		if(pending != null)
		{
			pending.cancel(true);
			pending = null;
		}

		if(clipPending != null)
		{
			clipPending.cancel(true);
			clipPending = null;
		}

		if(loadedTexture != null)
		{
			Minecraft.getInstance().getTextureManager().release(LOCATION);
			loadedTexture = null;
		}

		if(clip != null)
		{
			clip.close();
			clip = null;
		}

		clipFrame = -1;
		loadedId = null;
		failedId = null;
	}

	/**
	 * Draws the background over the whole screen.
	 *
	 * @return false when the caller should draw the built-in default instead,
	 *         which is the case before the image has decoded and whenever the
	 *         selection is broken.
	 */
	public boolean render(GuiGraphics graphics, int screenWidth,
		int screenHeight, int mouseX, int mouseY)
	{
		if(screenWidth <= 0 || screenHeight <= 0)
			return false;

		if(isDefaultSelected())
			return false;

		if(!ensureLoaded())
			return false;

		DynamicTexture texture = currentTexture();

		if(texture == null)
			return false;

		advanceClip(texture);

		NativeImage pixels = texture.getPixels();

		if(pixels == null)
			return false;

		int textureWidth = pixels.getWidth();
		int textureHeight = pixels.getHeight();

		if(textureWidth <= 0 || textureHeight <= 0)
			return false;

		// cover: crop the source to the screen's aspect so nothing is distorted
		int[] crop = TwilightCoverFit.sourceRect(textureWidth, textureHeight,
			screenWidth, screenHeight);

		if(crop[2] <= 0 || crop[3] <= 0)
			return false;

		BackgroundPose pose = motion().compute(System.currentTimeMillis(),
			BackgroundMotion.strengthFor(WurstClient.INSTANCE
				.getGuiPreferences().getBackgroundMotionStrength(),
				screenWidth),
			screenWidth, screenHeight, mouseX, mouseY);

		// the pose is an extra zoom, so the motion can pan without exposing an
		// edge; the crop already fills the screen's aspect
		float width = screenWidth * pose.scale();
		float height = screenHeight * pose.scale();
		float x = (screenWidth - width) / 2F + pose.offsetX();
		float y = (screenHeight - height) / 2F + pose.offsetY();

		graphics.blit(LOCATION, Math.round(x), Math.round(y),
			Math.round(width), Math.round(height), crop[0], crop[1], crop[2],
			crop[3], textureWidth, textureHeight);
		return true;
	}

	/** The texture of the current selection, re-requesting it when a resource
	 * reload has dropped our registration. */
	private DynamicTexture currentTexture()
	{
		DynamicTexture texture = loadedTexture;

		if(texture == null)
			return null;

		if(Minecraft.getInstance().getTextureManager()
			.getTexture(LOCATION) != texture)
		{
			// a resource reload released it; load it again
			forget();
			return null;
		}

		return texture;
	}

	/**
	 * @return whether a texture for the selection is registered, kicking off
	 *         the load when it is not. Never blocks: an image that has not
	 *         decoded yet just means the default is drawn for a frame or two.
	 */
	private boolean ensureLoaded()
	{
		String id = selectedId();

		if(id.equals(failedId))
			return false;

		if(id.equals(loadedId) && loadedTexture != null)
			return true;

		if(pending != null || clipPending != null)
			return false;

		BackgroundEntry entry = storage().read(id);

		if(entry == null || !entry.kind().canPlay())
		{
			// a folder that was deleted, a half-copied entry or a video
			// wallpaper: fall back to the built-in background and stop asking
			if(entry != null)
				System.out.println("[Background] Cannot play " + id + " ("
					+ entry.kind() + "), using the default instead");

			failedId = id;
			return false;
		}

		Path media = storage().mediaPath(id);

		if(media == null)
		{
			// a selection whose folder is gone: fall back and remember not to
			// keep retrying
			failedId = id;
			return false;
		}

		if(entry.kind() == BackgroundKind.GIF)
			startClip(id, media);
		else
			startStill(id, media);

		return false;
	}

	private void startStill(String id, Path media)
	{
		pending = AsyncTextureLoader.load(media, LOCATION);

		pending.whenComplete((location, error) -> {
			pending = null;

			if(error != null)
			{
				failedId = id;
				return;
			}

			// the completion already runs on the client thread
			if(Minecraft.getInstance().getTextureManager()
				.getTexture(location) instanceof DynamicTexture uploaded)
			{
				// smooth scaling: a wallpaper is almost never drawn at its
				// native size
				uploaded.setFilter(true, false);
				loadedTexture = uploaded;
				loadedId = id;
			}else
				failedId = id;
		});
	}

	private void startClip(String id, Path media)
	{
		CompletableFuture<BackgroundClip> future = BackgroundClip.decode(media);
		clipPending = future;

		future.whenComplete((decoded, error) -> Minecraft.getInstance()
			.execute(() -> finishClip(id, future, decoded, error)));
	}

	/** Runs on the client thread once the frames have been decoded. */
	private void finishClip(String id, CompletableFuture<BackgroundClip> future,
		BackgroundClip decoded, Throwable error)
	{
		if(clipPending != future)
		{
			// forgotten, or replaced by another selection, while decoding
			closeQuietly(decoded);
			return;
		}

		clipPending = null;

		if(!id.equals(selectedId()))
		{
			// the selection moved on: not a failure of this background
			closeQuietly(decoded);
			return;
		}

		if(error != null || decoded == null)
		{
			closeQuietly(decoded);
			failedId = id;
			return;
		}

		try
		{
			// the texture owns its own image: a DynamicTexture closes whatever
			// it is handed, and the frames have to stay with the clip
			NativeImage pixels = new NativeImage(NativeImage.Format.RGBA,
				decoded.width(), decoded.height(), false);
			pixels.copyFrom(decoded.frame(0));

			DynamicTexture texture = new DynamicTexture(pixels);
			texture.setFilter(true, false);
			Minecraft.getInstance().getTextureManager().register(LOCATION,
				texture);

			clip = decoded;
			clipFrame = 0;
			clipStartedAt = System.currentTimeMillis();
			loadedTexture = texture;
			loadedId = id;

			if(decoded.wasScaled() || decoded.wasTruncated())
				System.out.println("[Background] " + id + " decoded to "
					+ decoded.width() + "x" + decoded.height() + ", "
					+ decoded.frameCount() + " frames"
					+ (decoded.wasScaled() ? " (scaled to fit)" : "")
					+ (decoded.wasTruncated() ? " (frame limit reached)" : ""));

		}catch(RuntimeException | Error e)
		{
			closeQuietly(decoded);
			failedId = id;
		}
	}

	/**
	 * Uploads the frame the clock has reached, and only then: a GIF with a 20 ms
	 * delay would otherwise ask for an upload every frame it is drawn.
	 */
	private void advanceClip(DynamicTexture texture)
	{
		BackgroundClip playing = clip;

		if(playing == null)
			return;

		int index = playing.animation()
			.frameIndexAt(System.currentTimeMillis() - clipStartedAt);

		if(index == clipFrame)
			return;

		NativeImage pixels = texture.getPixels();

		if(pixels == null || pixels.getWidth() != playing.width()
			|| pixels.getHeight() != playing.height())
			return;

		clipFrame = index;
		pixels.copyFrom(playing.frame(index));
		texture.upload();
	}

	private static void closeQuietly(BackgroundClip clip)
	{
		if(clip != null)
			clip.close();
	}
}
