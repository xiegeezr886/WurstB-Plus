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

	/**
	 * 正在播放的视频，或 null。
	 *
	 * <p>
	 * 视频与动图不一样，帧不是一次性解好的：{@link BackgroundVideo} 一边放一边解，
	 * 所以它同时是「解码器」与「当前帧的来源」。</p>
	 */
	private BackgroundVideo video;

	private CompletableFuture<BackgroundVideo.Opened> videoPending;

	private BackgroundManager()
	{
	}

	public static BackgroundManager get()
	{
		return INSTANCE;
	}

	/**
	 * Stops the decoders, called when the client shuts down.
	 *
	 * <p>
	 * 只停线程，不碰纹理：客户端退出时 GL 上下文可能已经没了。
	 * </p>
	 */
	public static void shutdown()
	{
		BackgroundClip.shutdown();
		INSTANCE.stopVideo();
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

		if(videoPending != null)
		{
			// 同上：视频线程可能在 open() 里已经起来了，回调会负责关掉
			videoPending.cancel(true);
			videoPending = null;
		}

		if(scene != null)
		{
			scene.close();
			scene = null;
		}

		stopVideo();

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

		BackgroundVideo videoPlaying = video;

		if(videoPlaying != null)
		{
			if(videoPlaying.failed())
			{
				// 放到一半坏了（文件被删、样本损坏、解码器挂了）：退回内置背景。
				// 视频每帧都可能再失败一次，所以这里必须记住不要再试
				String id = loadedId;
				System.out.println("[Background] 视频播放中断 " + id + "："
					+ videoPlaying.failure());
				forget();
				failedId = id;
				return false;
			}

			// 视频是按窗口尺寸解码的，窗口尺寸一变它就会换一套复用缓冲（见
			// BackgroundVideo.takeFrame）。纹理必须跟着换：NativeImage.copyFrom
			// 要求两张图尺寸一致，不换的话 advanceVideo 会一直返回 false，画面
			// 停在旧的那一帧上——看起来就是「缩放窗口之后视频冻住了」
			texture = matchVideoSize(texture, videoPlaying);

			if(texture == null)
				return false;

			advanceVideo(texture, videoPlaying);
		}

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

		if(pending != null || clipPending != null || scenePending != null
			|| videoPending != null)
			return false;

		BackgroundEntry entry = storage().read(id);

		if(entry == null || !entry.kind().canPlay())
		{
			// a folder that was deleted, a half-copied entry or a kind this build
			// cannot show: fall back to the built-in background and stop asking
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
		else if(entry.kind() == BackgroundKind.VIDEO)
		{
			// 加载遮罩还在时不要碰视频：那段时间 Forge 的类加载还处在早期阶段，
			// 外部依赖（原生解码器、资源管理器）可能还没就绪，抛出来的是
			// NoClassDefFoundError / UnsatisfiedLinkError 这类链接错误——那是
			// "还没到时候"，不是"这个文件放不了"。这里直接不下手也不记 failedId，
			// 等遮罩散了下一帧自然会再来一次；否则第一次的失败会被永久记住，
			// 视频再也起不来。
			if(Minecraft.getInstance().getOverlay() != null)
				return false;

			startVideo(id, media);
		}else
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

	// ------------------------------------------------------------------
	// 视频背景
	// ------------------------------------------------------------------

	/**
	 * 停掉视频解码线程（不碰纹理）。切换背景、删除、退出客户端时都会走到这里。
	 */
	private void stopVideo()
	{
		if(videoPending != null)
		{
			// 已经开出来的那个由回调负责关，见 closeQuietly(Opened)
			videoPending.cancel(true);
			videoPending = null;
		}

		if(video != null)
		{
			video.close();
			video = null;
		}
	}

	/**
	 * 视频在后台探测并起解码线程，纹理与其它路径一样回到客户端线程建。
	 *
	 * <p>
	 * 这里连 {@link Throwable} 一起接住，是有实测原因的：外部依赖不在时抛的是
	 * {@link NoClassDefFoundError}（当时是 JCodec 不在类路径上）或
	 * {@link UnsatisfiedLinkError}（原生解码器的 DLL 少一个），那是 {@link Error}
	 * 而不是 {@link Exception}，只 catch 异常会让它直接穿透渲染循环、把客户端崩在
	 * 标题界面（crash-2026-10-05_16.06.43，栈顶是 WurstTitleMenu.drawBackground）。
	 * 链接错误是"这个功能用不了"的另一种说法，按"放不了"降级即可，不该影响整个
	 * 游戏。</p>
	 */
	private void startVideo(String id, Path media)
	{
		CompletableFuture<BackgroundVideo.Opened> future;

		try
		{
			future = BackgroundVideo.openAsync(media);
		}catch(Throwable t)
		{
			videoFailed(id, media, t);
			return;
		}

		videoPending = future;

		future.whenComplete((opened, error) -> Minecraft.getInstance()
			.execute(() -> finishVideo(id, future, opened, error)));
	}

	/** 视频起不来时的统一出口：退回内置背景、记住这次失败、只留一行日志。 */
	private void videoFailed(String id, Path media, Throwable error)
	{
		System.out.println("[Background] 视频背景不可用（" + id + "）：" + error);
		failedId = id;
		videoPending = null;
		select(BackgroundStorage.DEFAULT_ID);
	}

	/** Runs on the client thread once the video has been opened. */
	private void finishVideo(String id,
		CompletableFuture<BackgroundVideo.Opened> future,
		BackgroundVideo.Opened opened, Throwable error)
	{
		if(videoPending != future)
		{
			// forgotten, or replaced by another selection, while opening
			closeQuietly(opened);
			return;
		}

		videoPending = null;

		if(!id.equals(selectedId()))
		{
			closeQuietly(opened);
			return;
		}

		BackgroundVideo playing = opened == null ? null : opened.video();
		BackgroundVideo.Probe probe = opened == null ? null : opened.probe();

		if(error != null || playing == null)
		{
			// 放不了（编码不是 H.264、文件坏了、被删了）：退回内置背景并记住这次
			// 选择，不要每帧重试。probe 里带的是具体原因，日志里留一行
			System.out.println("[Background] 视频背景 " + id + " 不能播放："
				+ (probe == null ? String.valueOf(error)
					: probe.reason() + " / " + probe.detail()));

			failedId = id;
			return;
		}

		try
		{
			// 纹理自己持有一张图：视频那边按目标尺寸也持有自己的复用缓冲，
			// 每帧用 copyFrom 拷过来（与动图那条路同一个理由）
			NativeImage pixels = new NativeImage(NativeImage.Format.RGBA,
				playing.width(), playing.height(), false);

			DynamicTexture texture = new DynamicTexture(pixels);
			texture.setFilter(true, false);
			Minecraft.getInstance().getTextureManager().register(LOCATION,
				texture);

			video = playing;
			loadedTexture = texture;
			loadedId = id;

			// 第一帧通常已经解好了，先贴上去，免得看起来像黑屏
			if(advanceVideo(texture, playing))
				System.out.println("[Background] Video " + id + ": "
					+ playing.width() + "x" + playing.height() + " (源 "
					+ probe.sourceWidth() + "x" + probe.sourceHeight() + "), "
					+ probe.frameCount() + " 帧 / " + probe.durationMs()
					+ "ms, 上限 " + VideoPacing.CAP_FPS + "fps");
			else
				System.out.println("[Background] Video " + id + ": 已打开（"
					+ playing.width() + "x" + playing.height() + "），第一帧还没解好");

		}catch(RuntimeException | Error e)
		{
			playing.close();
			failedId = id;
		}
	}

	/**
	 * 把解好的那一帧拷进纹理，只在真的有新帧时上传。
	 *
	 * <p>
	 * 上传次数由视频那边按帧率上限决定（30fps 上限下 60fps 的源会丢一半），这里
	 * 只负责搬。拷完必须把缓冲还给解码线程，否则三个缓冲很快就会被占满。
	 * </p>
	 *
	 * @return 是否真的上传了一帧
	 */
	private boolean advanceVideo(DynamicTexture texture,
		BackgroundVideo playing)
	{
		NativeImage frame = playing.takeFrame();

		if(frame == null)
			return false;

		try
		{
			NativeImage pixels = texture.getPixels();

			if(pixels == null || pixels.getWidth() != playing.width()
				|| pixels.getHeight() != playing.height())
				return false;

			pixels.copyFrom(frame);
			texture.upload();
			return true;

		}finally
		{
			playing.recycle(frame);
		}
	}

	/**
	 * 视频的解码尺寸变了就换一张同尺寸的纹理。
	 *
	 * <p>
	 * 视频按窗口的帧缓冲尺寸解码（窗口变大就解大一点，这正是"模糊"那个问题的
	 * 修法），所以拖动窗口、切全屏、改 GUI 缩放都会让它换尺寸。纹理是照着旧尺寸
	 * 建的，不换的话 {@link #advanceVideo} 里的尺寸检查会让画面永远停在那一帧。
	 * 同一个纹理槽位照旧复用：先让旧的走，再注册新的，与 {@link #forget()} 一样。
	 * </p>
	 *
	 * @return 尺寸已经对上的纹理，建不出新的时为 null
	 */
	private DynamicTexture matchVideoSize(DynamicTexture texture,
		BackgroundVideo playing)
	{
		NativeImage pixels = texture.getPixels();

		if(pixels != null && pixels.getWidth() == playing.width()
			&& pixels.getHeight() == playing.height())
			return texture;

		try
		{
			NativeImage image = new NativeImage(NativeImage.Format.RGBA,
				playing.width(), playing.height(), false);
			DynamicTexture replacement = new DynamicTexture(image);
			replacement.setFilter(true, false);

			Minecraft.getInstance().getTextureManager().release(LOCATION);
			Minecraft.getInstance().getTextureManager().register(LOCATION,
				replacement);

			loadedTexture = replacement;
			return replacement;

		}catch(RuntimeException | Error e)
		{
			// 显存不够之类：当作这一帧画不出来，下一次再试
			System.out.println("[Background] 视频纹理换尺寸失败（" + playing.width()
				+ "x" + playing.height() + "）：" + e);
			return null;
		}
	}

	private static void closeQuietly(BackgroundVideo.Opened opened)
	{
		if(opened != null && opened.video() != null)
			opened.video().close();
	}
}
