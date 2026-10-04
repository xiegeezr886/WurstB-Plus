/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

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

	/** The Wallpaper Engine scene that is playing, or null. */
	private WeSceneWallpaper scene;

	private CompletableFuture<WeSceneWallpaper.Decoded> scenePending;

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

	/**
	 * 仓库里带的那张默认主界面壁纸：Wallpaper Engine 工坊 2359043440「Persica」
	 * 的原图（5712x3214）。
	 *
	 * <p>
	 * 这件作品是 Scene 类型，本机会优先渲染它的 {@code scene.pkg}（见
	 * {@link #BUNDLED_SCENE_ID}）；内置的这张高清底图是没有装那件作品时的
	 * 退路，也是场景卡片的缩略图。</p>
	 *
	 * <p>
	 * 命名空间是 {@code "wurst"} 而不是 {@link WurstClient#MOD_ID}：资源真的放在
	 * {@code assets/wurst/} 下（全仓库一致，见 {@code WurstTitleMenu.LOGO}），
	 * 用 mod id（{@code wurstpenguin}）去找会 FileNotFoundException。这个异常
	 * 原来被静默吞掉，所以内置壁纸一次都没导入成功过——直到在真实客户端里查
	 * 才暴露出来。</p>
	 */
	private static final ResourceLocation BUNDLED_DEFAULT =
		new ResourceLocation("wurst", "background/default.jpg");
	private static final String BUNDLED_TITLE = "Persica";

	/**
	 * 「Persica」在 Steam 工坊里的 id。
	 *
	 * <p>
	 * 包里没有这件作品的许可，所以场景本体不进仓库：装了就读本地那份
	 * {@code scene.pkg} 并按图层渲染，没装就退回内置底图。</p>
	 */
	private static final String BUNDLED_SCENE_ID = "2359043440";

	private boolean bundledDefaultStarted;

	/** 场景那一次导入单独走一遍，见 {@link #importBundledScene()}。 */
	private boolean bundledSceneStarted;

	/**
	 * 首次启动时把内置壁纸导入到背景库并选中它，之后动效、运镜、选择屏的卡片
	 * 全都直接复用既有管线。只做一次：{@code GuiPreferences} 里记了
	 * {@code bundledDefaultImported}，用户自己删掉之后不会再自动重建。
	 *
	 * <p>
	 * 本机装了「Persica」的 {@code scene.pkg} 时这次导入会被跳过，改由
	 * {@link #importBundledScene()} 导入场景本身；等哪天那件作品被卸掉，内置
	 * 底图会在这条路径上补进来。</p>
	 */
	private void importBundledDefault()
	{
		if(bundledDefaultStarted)
			return;

		bundledDefaultStarted = true;

		if(WurstClient.INSTANCE.getGuiPreferences()
			.isBundledDefaultImported())
			return;

		Thread worker = new Thread(() -> {
			// 本机装着场景就交给 importBundledScene：两者只会有一个真的导入，
			// 免得选择屏上出现两张同名卡片
			if(bundledScenePackage() != null)
				return;

			Path temp = null;

			try
			{
				byte[] bytes = Minecraft.getInstance().getResourceManager()
					.open(BUNDLED_DEFAULT).readAllBytes();
				temp = Files.createTempFile("wurstb-default-", ".jpg");
				Files.write(temp, bytes);

				byte[] thumbnail = BackgroundThumbnail.create(temp,
					BackgroundThumbnail.MAX_SIZE);

				String id = storage().importFile(temp, BackgroundKind.IMAGE,
					BUNDLED_TITLE, "Wallpaper Engine", thumbnail);

				Minecraft.getInstance().execute(() -> {
					WurstClient.INSTANCE.getGuiPreferences()
						.setBundledDefaultImported(true);

					if(id != null && isDefaultSelected())
						select(id);

					forget();
				});
			}catch(IOException | RuntimeException e)
			{
				// 导入失败就维持内置网格背景，但要在日志里留下痕迹：这里曾经
				// 静默吞掉过一次资源路径写错，代价是内置壁纸一个版本没生效
				System.out.println("[Background] 内置壁纸导入失败：" + e);
			}finally
			{
				if(temp != null)
					try
					{
						Files.deleteIfExists(temp);
					}catch(IOException e)
					{
					}
			}
		}, "WurstB-BundledDefault");

		worker.setDaemon(true);
		worker.start();
	}

	/** 本机装着的「Persica」场景包，没装返回 null。 */
	private static Path bundledScenePackage()
	{
		Path folder = SteamLocator.workshopFolder(BUNDLED_SCENE_ID);

		if(folder == null)
			return null;

		Path pkg = folder.resolve("scene.pkg");
		return Files.isRegularFile(pkg) ? pkg : null;
	}

	/**
	 * 本机装了「Persica」就把场景导进库并选中它。
	 *
	 * <p>
	 * 单独一个标记（{@code bundledSceneImported}）：内置底图那次导入早就标记
	 * 过了，共用标记的话老用户永远等不到这一次。而且只有真的导入成功才置位，
	 * 所以之后装了那件作品，下一次启动仍然会补上；用户自己选过别的壁纸则不会
	 * 被顶掉——只有当前选中项是内置默认、或还是那张旧的 Persica 底图时才切换。
	 * </p>
	 */
	private void importBundledScene()
	{
		if(bundledSceneStarted)
			return;

		bundledSceneStarted = true;

		if(WurstClient.INSTANCE.getGuiPreferences().isBundledSceneImported())
			return;

		Thread worker = new Thread(() -> {
			Path pkg = bundledScenePackage();

			if(pkg == null)
				return;

			Path temp = null;

			try
			{
				byte[] bytes = Minecraft.getInstance().getResourceManager()
					.open(BUNDLED_DEFAULT).readAllBytes();
				temp = Files.createTempFile("wurstb-scene-thumb-", ".jpg");
				Files.write(temp, bytes);

				byte[] thumbnail = BackgroundThumbnail.create(temp,
					BackgroundThumbnail.MAX_SIZE);

				String id = storage().importFile(pkg, BackgroundKind.SCENE,
					BUNDLED_TITLE, "Wallpaper Engine", thumbnail);

				if(id == null)
				{
					System.out.println(
						"[Background] 内置场景导入失败了，下次启动再试");
					return;
				}

				Minecraft.getInstance().execute(() -> {
					WurstClient.INSTANCE.getGuiPreferences()
						.setBundledSceneImported(true);

					if(shouldSwitchToBundledScene())
						select(id);

					forget();
				});

			}catch(IOException | RuntimeException e)
			{
				// 装不了就算了，下次启动再试；这里一定要留下痕迹，
				// 上一轮的静默失败就是被这个 catch 藏了整整一个版本
				System.out.println("[Background] 内置场景导入失败：" + e);
			}finally
			{
				if(temp != null)
					try
					{
						Files.deleteIfExists(temp);
					}catch(IOException e)
					{
					}
			}
		}, "WurstB-BundledScene");

		worker.setDaemon(true);
		worker.start();
	}

	/** 用户没自己挑过别的壁纸时，才把选中项换到内置场景上。 */
	private boolean shouldSwitchToBundledScene()
	{
		if(isDefaultSelected())
			return true;

		BackgroundEntry selected = storage().read(selectedId());
		return selected != null && BUNDLED_TITLE.equals(selected.title());
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

		if(scenePending != null)
		{
			// 解码结果可能已经好了但还没回来，交给回调自己关掉
			scenePending.cancel(true);
			scenePending = null;
		}

		if(scene != null)
		{
			scene.close();
			scene = null;
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

		// 这两件事必须在「选中项是内置默认」的早退之前做：全新配置下选中项
		// 就是默认背景，放在 ensureLoaded() 里等于永远不导入
		importBundledDefault();
		importBundledScene();

		if(isDefaultSelected())
			return false;

		if(!ensureLoaded())
			return false;

		// 场景自己管一组纹理，不共用单张纹理那条路
		WeSceneWallpaper playing = scene;

		if(playing != null)
			return playing.render(graphics, screenWidth, screenHeight, mouseX,
				mouseY);

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

		if(scene != null && id.equals(loadedId))
			return true;

		if(pending != null || clipPending != null || scenePending != null)
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
		else if(entry.kind() == BackgroundKind.SCENE)
			startScene(id, media);
		else
			startStill(id, media);

		return false;
	}

	/**
	 * 场景包在后台线程里读+解码，纹理上传与其它路径一样回到客户端线程做。
	 */
	private void startScene(String id, Path media)
	{
		CompletableFuture<WeSceneWallpaper.Decoded> future =
			CompletableFuture.supplyAsync(() -> {
				try
				{
					return WeSceneWallpaper.decode(media);
				}catch(IOException e)
				{
					throw new CompletionException(e);
				}
			});

		scenePending = future;

		future.whenComplete((decoded, error) -> Minecraft.getInstance()
			.execute(() -> finishScene(id, future, decoded, error)));
	}

	/** Runs on the client thread once the scene has been decoded. */
	private void finishScene(String id,
		CompletableFuture<WeSceneWallpaper.Decoded> future,
		WeSceneWallpaper.Decoded decoded, Throwable error)
	{
		if(scenePending != future)
		{
			// forgotten, or replaced by another selection, while decoding
			closeQuietly(decoded);
			return;
		}

		scenePending = null;

		if(!id.equals(selectedId()))
		{
			closeQuietly(decoded);
			return;
		}

		if(error != null || decoded == null)
		{
			closeQuietly(decoded);
			System.out.println("[Background] Cannot draw scene " + id + ": "
				+ (error == null ? "no layers" : error.getMessage()));
			failedId = id;
			return;
		}

		WeSceneWallpaper bound = WeSceneWallpaper.bind(decoded);

		// bind 已经把 NativeImage 交给了纹理，失败时纹理那边负责关
		if(bound == null)
		{
			closeQuietly(decoded);
			failedId = id;
			return;
		}

		scene = bound;
		loadedId = id;
		System.out.println("[Background] Scene " + id + ": "
			+ bound.layerCount() + " layers, canvas " + bound.scene().width()
			+ "x" + bound.scene().height());
	}

	private static void closeQuietly(WeSceneWallpaper.Decoded decoded)
	{
		if(decoded != null)
			decoded.close();
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
