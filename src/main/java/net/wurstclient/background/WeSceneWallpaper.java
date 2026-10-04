/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.wurstclient.WurstClient;

/**
 * 把 Wallpaper Engine 的场景包画到屏幕上。
 *
 * <p>
 * 载入分两步，因为 GL 上传只能在渲染线程做：{@link #decode(byte[])} 在后台线程
 * 里读包、解析图层树、把每张贴图的 PNG/JPEG 载荷解成 {@link NativeImage}；
 * {@link #bind(Decoded)} 在客户端线程上把它们注册成纹理。这和
 * {@code BackgroundManager} 里 GIF 那条路径的做法一致。</p>
 *
 * <p>
 * 画的时候只用 {@code GuiGraphics.blit}，位置由 {@link WeSceneLayout} 算：
 * 画布按 cover 缩放铺满屏幕，每层再按 {@code parallaxDepth} 跟着鼠标轻微错动。
 * 视差本身做了两件让画面顺滑的事：目标位移按帧时间做指数阻尼跟随（时间常数来自
 * 场景的 {@code cameraparallaxdelay}），画的时候再把位置的小数部分交给模型矩阵，
 * 而不是取整——浅视差的图层每帧只该动零点零几像素，取整会变成台阶。</p>
 *
 * <p>
 * 有意没做的两件事：一是场景里记录的相机机位（Persica 里是 -213.6, -20.9），
 * 那是编辑器里的取景，照它平移会让画面偏出画布、边上露出 clearcolor；二是
 * godrays / blurprecise / filmgrain / waterwaves 这些 GLSL 后期效果，所以亮度
 * 与光晕会比 Wallpaper Engine 里淡一些。</p>
 */
public final class WeSceneWallpaper implements AutoCloseable
{
	/** 一层贴图的像素上限，防止坏文件把显存吃光。 */
	private static final long MAX_PIXELS = 64_000_000L;

	/** 图层数上限，正常场景个位数。 */
	private static final int MAX_LAYERS = 64;

	private final WeScene scene;
	private final List<Bound> layers;
	private boolean closed;

	/** 平滑后的鼠标偏移（屏幕像素），视差跟随它而不是直接跟鼠标。 */
	private float smoothOffsetX;
	private float smoothOffsetY;
	private boolean smoothStarted;

	/** 上一帧的时间戳，用来算与帧率无关的阻尼。 */
	private long lastFrameNanos;

	private WeSceneWallpaper(WeScene scene, List<Bound> layers)
	{
		this.scene = scene;
		this.layers = layers;
	}

	/** 一张已经上传的贴图，连同它属于哪一层。 */
	private record Bound(WeScene.Layer layer, ResourceLocation location,
		DynamicTexture texture, int width, int height)
	{}

	/**
	 * 解好、但贴图还在内存里等着上传的场景。
	 *
	 * <p>
	 * 里面的像素是 {@code NativeImage}，不能重复释放，所以这个类按「所有权
	 * 转移」来用：{@link #bind(Decoded)} 每把一张图包进纹理，就从待办列表里
	 * 摘掉一张。于是 bind 失败时列表里剩下的正好是还没人接手的那几张，
	 * {@link #close()} 无论何时调用都不会碰到已经交给纹理的像素。
	 * </p>
	 */
	public static final class Decoded implements AutoCloseable
	{
		private final WeScene scene;
		private final List<DecodedLayer> remaining;

		Decoded(WeScene scene, List<DecodedLayer> layers)
		{
			this.scene = scene;
			this.remaining = new ArrayList<>(layers);
		}

		public WeScene scene()
		{
			return scene;
		}

		/** 还没交给纹理的图层，上传完成后就是空的。 */
		public int pending()
		{
			return remaining.size();
		}

		@Override
		public void close()
		{
			for(DecodedLayer layer : remaining)
				layer.image().close();

			remaining.clear();
		}
	}

	/** 一层还没上传的贴图。 */
	public record DecodedLayer(WeScene.Layer layer, NativeImage image)
	{}

	/** 后台线程：读包、解析、解码所有图层贴图。 */
	public static Decoded decode(byte[] packageBytes) throws IOException
	{
		WePackage pkg = WePackage.parse(packageBytes);
		String sceneJson = text(pkg, WeScene.SCENE_JSON);

		WeScene scene = WeScene.parse(sceneJson, name -> text(pkg, name));
		List<DecodedLayer> decoded = new ArrayList<>();
		long totalPixels = 0;

		try
		{
			for(WeScene.Layer layer : scene.layers())
			{
				if(decoded.size() >= MAX_LAYERS)
					break;

				String entry =
					WeScene.textureEntryName(layer.texture(), pkg.names());

				if(entry == null)
					continue;

				byte[] bytes = pkg.read(entry);

				if(bytes == null)
					continue;

				DecodedLayer bound = decodeLayer(layer, bytes);

				if(bound == null)
					continue;

				NativeImage image = bound.image();
				totalPixels += (long)image.getWidth() * image.getHeight();

				if(totalPixels > MAX_PIXELS)
				{
					image.close();
					throw new IOException("场景贴图总量过大，放弃载入");
				}

				decoded.add(bound);
			}
		}catch(IOException | RuntimeException e)
		{
			for(DecodedLayer layer : decoded)
				layer.image().close();

			throw e;
		}

		if(decoded.isEmpty())
			throw new IOException("场景里没有可画的图层");

		return new Decoded(scene, List.copyOf(decoded));
	}

	public static Decoded decode(Path pkgFile) throws IOException
	{
		return decode(Files.readAllBytes(pkgFile));
	}

	private static DecodedLayer decodeLayer(WeScene.Layer layer, byte[] bytes)
	{
		try
		{
			WeTexture texture = WeTexture.parse(bytes);

			if(!texture.isStandardImage())
			{
				System.out.println("[Background] 跳过 " + layer.name()
					+ "：贴图载荷不是 PNG/JPEG（format=" + texture.format()
					+ "）");
				return null;
			}

			NativeImage image =
				NativeImage.read(new ByteArrayInputStream(texture.payload()));
			return new DecodedLayer(layer, image);

		}catch(IOException | RuntimeException e)
		{
			System.out.println(
				"[Background] 跳过 " + layer.name() + "：" + e.getMessage());
			return null;
		}
	}

	/**
	 * 客户端线程：把解码好的贴图注册成纹理，并从 {@code decoded} 手里接过
	 * 这些像素。
	 *
	 * @return 可以画的场景，失败时返回 null（调用方回退到默认背景）；失败时
	 *         {@code decoded} 里剩下的图会被关掉，调用方不需要再管
	 */
	public static WeSceneWallpaper bind(Decoded decoded)
	{
		List<Bound> bound = new ArrayList<>();
		Iterator<DecodedLayer> iterator = decoded.remaining.iterator();

		while(iterator.hasNext())
		{
			DecodedLayer layer = iterator.next();
			NativeImage image = layer.image();

			try
			{
				DynamicTexture texture = new DynamicTexture(image);
				texture.setFilter(true, false);

				ResourceLocation location = new ResourceLocation(
					WurstClient.MOD_ID, "we_scene_layer_" + bound.size());
				Minecraft.getInstance().getTextureManager().register(location,
					texture);

				// 像素交给纹理了，从待办里摘掉
				iterator.remove();
				bound.add(new Bound(layer.layer(), location, texture,
					image.getWidth(), image.getHeight()));

			}catch(RuntimeException | Error e)
			{
				System.out.println("[Background] 场景贴图上传失败：" + e);
				closeQuietly(bound);
				decoded.close();
				return null;
			}
		}

		return new WeSceneWallpaper(decoded.scene(), List.copyOf(bound));
	}

	/** 场景的画布宽高，日志与测试用。 */
	public WeScene scene()
	{
		return scene;
	}

	public int layerCount()
	{
		return layers.size();
	}

	/**
	 * 画一帧。
	 *
	 * @return 是否真的画了东西
	 */
	public boolean render(GuiGraphics graphics, int screenWidth,
		int screenHeight, int mouseX, int mouseY)
	{
		if(closed || layers.isEmpty() || screenWidth <= 0 || screenHeight <= 0)
			return false;

		float scale = WeSceneLayout.coverScale(scene.width(), scene.height(),
			scene.zoom(), screenWidth, screenHeight);

		if(scale <= 0)
			return false;

		// 视差：Wallpaper Engine 里 parallaxDepth=1 表示与鼠标 1:1，
		// cameraparallaxmouseinfluence 实测是 1.0
		float influence = scene.parallax() ? 1 : 0;
		float targetX = mouseX - screenWidth / 2F;
		float targetY = mouseY - screenHeight / 2F;
		advanceParallax(targetX, targetY);

		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();

		boolean drew = false;

		for(Bound layer : layers)
		{
			WeScene.Layer definition = layer.layer();

			WeSceneLayout.Rect rect = WeSceneLayout.rect(definition, scale,
				scene.width(), scene.height(), screenWidth, screenHeight,
				layer.width(), layer.height(),
				WeSceneLayout.parallaxOffset(definition.parallaxX(),
					scene.parallaxAmount(), influence, smoothOffsetX),
				WeSceneLayout.parallaxOffset(definition.parallaxY(),
					scene.parallaxAmount(), influence, smoothOffsetY));

			if(rect.isEmpty())
				continue;

			graphics.setColor(definition.colorR(), definition.colorG(),
				definition.colorB(), definition.alpha());

			// 亚像素定位：位置取整后把小数部分交给模型矩阵。直接 Math.round
			// 会让浅视差的图层变成「攒够一格才跳一次」，也就是一顿一顿的
			int x = (int)Math.floor(rect.x());
			int y = (int)Math.floor(rect.y());

			graphics.pose().pushPose();
			graphics.pose().translate(rect.x() - x, rect.y() - y, 0);

			graphics.blit(layer.location(), x, y,
				Math.round(rect.width()), Math.round(rect.height()), 0, 0,
				layer.width(), layer.height(), layer.width(), layer.height());

			graphics.pose().popPose();
			drew = true;
		}

		graphics.setColor(1, 1, 1, 1);
		return drew;
	}

	/**
	 * 一帧的视差阻尼：目标值是鼠标相对屏幕中心的偏移，实际用的是平滑后的值。
	 *
	 * <p>
	 * 时间常数取自场景的 {@code cameraparallaxdelay}（Persica 是 0.5）。第一帧
	 * 直接贴上去，免得刚进主界面时所有图层从中心滑出来。
	 * </p>
	 */
	private void advanceParallax(float targetX, float targetY)
	{
		long now = System.nanoTime();
		float delta = (now - lastFrameNanos) / 1_000_000_000F;
		lastFrameNanos = now;

		if(!smoothStarted)
		{
			smoothStarted = true;
			smoothOffsetX = targetX;
			smoothOffsetY = targetY;
			return;
		}

		smoothOffsetX = WeSceneLayout.approach(smoothOffsetX, targetX, delta,
			scene.parallaxDelay());
		smoothOffsetY = WeSceneLayout.approach(smoothOffsetY, targetY, delta,
			scene.parallaxDelay());
	}

	/** 资源重载或换壁纸时释放纹理。 */
	@Override
	public void close()
	{
		if(closed)
			return;

		closed = true;
		closeQuietly(layers);
	}

	private static void closeQuietly(List<Bound> bound)
	{
		for(Bound layer : bound)
		{
			Minecraft.getInstance().getTextureManager()
				.release(layer.location());
			layer.texture().close();
		}
	}

	private static String text(WePackage pkg, String name)
	{
		byte[] bytes = pkg.read(name);
		return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
	}
}
