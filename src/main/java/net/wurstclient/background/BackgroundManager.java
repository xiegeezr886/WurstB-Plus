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

	private BackgroundManager()
	{
	}

	public static BackgroundManager get()
	{
		return INSTANCE;
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

		if(loadedTexture != null)
		{
			Minecraft.getInstance().getTextureManager().release(LOCATION);
			loadedTexture = null;
		}

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

		if(pending != null)
			return false;

		Path media = storage().mediaPath(id);

		if(media == null)
		{
			// a selection whose folder is gone: fall back and remember not to
			// keep retrying
			failedId = id;
			return false;
		}

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

		return id.equals(loadedId) && loadedTexture != null;
	}
}
