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

import org.lwjgl.opengl.GL11;

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
 * 视差做了两件让画面顺滑的事：目标位移按帧时间做指数阻尼跟随（时间常数来自场景的
 * {@code cameraparallaxdelay}），画的时候再把位置的小数部分交给模型矩阵，而不是
 * 取整——浅视差的图层每帧只该动零点零几像素，取整会变成台阶。</p>
 *
 * <p>
 * 粒子层（雪）按场景顺序插在图像图层之间：Persica 有一层雪在树枝<b>后面</b>、
 * 一层在<b>前面</b>，顺序丢了近处那层就不会盖住树枝。粒子用加性混合画成柔光点，
 * 贴图是运行时生成的——预设引用的 {@code particle/chromaticdot} 是 Wallpaper
 * Engine 的内置资源，包里没有。</p>
 *
 * <p>
 * 有意没做的两件事：一是场景里记录的相机机位（Persica 里是 -213.6, -20.9），
 * 那是编辑器里的取景，照它平移会让画面偏出画布、边上露出 clearcolor；二是
 * godrays / blurprecise / filmgrain / waterwaves 这些 GLSL 后期效果，所以亮度
 * 与光晕会比 Wallpaper Engine 里淡一些。时钟与日期文字层同样不画。</p>
 */
public final class WeSceneWallpaper implements AutoCloseable
{
	/** 一层贴图的像素上限，防止坏文件把显存吃光。 */
	private static final long MAX_PIXELS = 64_000_000L;

	/** 图层数上限，正常场景个位数。 */
	private static final int MAX_LAYERS = 64;

	/** 粒子层数上限。 */
	private static final int MAX_PARTICLE_LAYERS = 8;

	/** 一帧最多按这么长时间推进，卡顿之后不会一次补出成百上千个粒子。 */
	private static final float MAX_FRAME_SECONDS = 0.1F;

	/** 运行时生成的粒子贴图边长（柔光点）。 */
	private static final int DOT_SIZE = 64;

	private static final ResourceLocation DOT =
		new ResourceLocation(WurstClient.MOD_ID, "we_particle_dot");

	private final WeScene scene;
	private final List<Bound> layers;
	private final List<BoundParticles> particleLayers;
	private boolean closed;

	/** 平滑后的鼠标偏移（屏幕像素），视差跟随它而不是直接跟鼠标。 */
	private float smoothOffsetX;
	private float smoothOffsetY;
	private boolean smoothStarted;

	/** 上一帧的时间戳，用来算与帧率无关的阻尼。 */
	private long lastFrameNanos;

	private DynamicTexture dot;

	private WeSceneWallpaper(WeScene scene, List<Bound> layers,
		List<BoundParticles> particleLayers)
	{
		this.scene = scene;
		this.layers = layers;
		this.particleLayers = particleLayers;
	}

	/** 一张已经上传的贴图，连同它属于哪一层。 */
	private record Bound(WeScene.Layer layer, ResourceLocation location,
		DynamicTexture texture, int width, int height)
	{}

	/** 一套跑起来的粒子，以及它插在第几个图层之前。 */
	private record BoundParticles(WeScene.ParticleLayer layer,
		WeParticles system, int layerIndex)
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
		private final List<DecodedParticles> particles;

		Decoded(WeScene scene, List<DecodedLayer> layers,
			List<DecodedParticles> particles)
		{
			this.scene = scene;
			this.remaining = new ArrayList<>(layers);
			this.particles = particles;
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

		public int particleLayers()
		{
			return particles.size();
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

	/** 一个已经解析、还没跑起来的粒子层。 */
	public record DecodedParticles(WeScene.ParticleLayer layer,
		WeParticlePreset preset)
	{}

	/** 后台线程：读包、解析、解码所有图层贴图与粒子预设。 */
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
					// 到预算就别再往里加了，但**别把整个场景扔掉**：图层是按顺序
					// 排的，背景通常在最前面，丢掉后面几层远好过整幅退回内置背景
					// （实测「绪山真寻」就是这个 64M 像素的硬上限把整个场景判死的，
					// 而它前面几层本来完全能画）。一层都没解出来时，下面那句
					// 「场景里没有可画的图层」仍然会照常报出来。
					image.close();
					break;
				}

				decoded.add(bound);
			}
		}catch(RuntimeException e)
		{
			for(DecodedLayer layer : decoded)
				layer.image().close();

			throw e;
		}

		if(decoded.isEmpty())
			throw new IOException("场景里没有可画的图层");

		return new Decoded(scene, List.copyOf(decoded),
			decodeParticles(pkg, scene));
	}

	public static Decoded decode(Path pkgFile) throws IOException
	{
		return decode(Files.readAllBytes(pkgFile));
	}

	/** 粒子预设读不出来不算致命：那一层跳过，剩下的照画。 */
	private static List<DecodedParticles> decodeParticles(WePackage pkg,
		WeScene scene)
	{
		List<DecodedParticles> out = new ArrayList<>();

		for(WeScene.ParticleLayer layer : scene.particles())
		{
			if(out.size() >= MAX_PARTICLE_LAYERS)
				break;

			String json = text(pkg, layer.preset());

			if(json == null)
			{
				System.out
					.println("[Background] 粒子预设不在包里：" + layer.preset());
				continue;
			}

			try
			{
				out.add(
					new DecodedParticles(layer, WeParticlePreset.parse(json)));
			}catch(IOException | RuntimeException e)
			{
				System.out.println("[Background] 跳过粒子层 " + layer.name()
					+ "：" + e.getMessage());
			}
		}

		return List.copyOf(out);
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

		List<BoundParticles> particles = new ArrayList<>();

		for(int i = 0; i < decoded.particles.size(); i++)
		{
			DecodedParticles layer = decoded.particles.get(i);

			// 每个粒子层用自己的种子：两层雪的分布不该一模一样；
			// spread 把发射半径放大到覆盖画布（预设写的半径只够中间一圈）
			float spread = WeSceneLayout.emitterSpread(decoded.scene.width(),
				decoded.scene.height(), layer.preset().distanceMax());
			particles.add(new BoundParticles(layer.layer(),
				new WeParticles(layer.preset(), 0x5EED0000L + i, spread),
				Math.min(layer.layer().layerIndex(), bound.size())));
		}

		return new WeSceneWallpaper(decoded.scene(), List.copyOf(bound),
			List.copyOf(particles));
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

	public int particleLayerCount()
	{
		return particleLayers.size();
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
		float delta = advanceParallax(targetX, targetY);

		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();

		boolean drew = false;
		int particleIndex = 0;

		for(int i = 0; i < layers.size(); i++)
		{
			// 场景顺序：插在第 i 层之前的粒子先画
			while(particleIndex < particleLayers.size()
				&& particleLayers.get(particleIndex).layerIndex() <= i)
				drew |= renderParticles(graphics,
					particleLayers.get(particleIndex++), scale, screenWidth,
					screenHeight, influence, delta);

			drew |= renderLayer(graphics, layers.get(i), scale, screenWidth,
				screenHeight, influence);
		}

		while(particleIndex < particleLayers.size())
			drew |= renderParticles(graphics,
				particleLayers.get(particleIndex++), scale, screenWidth,
				screenHeight, influence, delta);

		graphics.setColor(1, 1, 1, 1);
		return drew;
	}

	private boolean renderLayer(GuiGraphics graphics, Bound layer, float scale,
		int screenWidth, int screenHeight, float influence)
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
			return false;

		graphics.setColor(definition.colorR(), definition.colorG(),
			definition.colorB(), definition.alpha());

		// 亚像素定位：位置取整后把小数部分交给模型矩阵。直接 Math.round
		// 会让浅视差的图层变成「攒够一格才跳一次」，也就是一顿一顿的
		int x = (int)Math.floor(rect.x());
		int y = (int)Math.floor(rect.y());

		graphics.pose().pushPose();
		graphics.pose().translate(rect.x() - x, rect.y() - y, 0);

		graphics.blit(layer.location(), x, y, Math.round(rect.width()),
			Math.round(rect.height()), 0, 0, layer.width(), layer.height(),
			layer.width(), layer.height());

		graphics.pose().popPose();
		return true;
	}

	/** 一个粒子层：先按帧时间推进，再逐颗画成加性混合的柔光点。 */
	private boolean renderParticles(GuiGraphics graphics, BoundParticles group,
		float scale, int screenWidth, int screenHeight, float influence,
		float delta)
	{
		group.system().advance(delta);

		if(dotTexture() == null)
			return false;

		WeScene.ParticleLayer layer = group.layer();
		float offsetX = WeSceneLayout.parallaxOffset(layer.parallaxX(),
			scene.parallaxAmount(), influence, smoothOffsetX);
		float offsetY = WeSceneLayout.parallaxOffset(layer.parallaxY(),
			scene.parallaxAmount(), influence, smoothOffsetY);

		// 加性混合：雪花是发光的小点，不是半透明的贴片
		RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);

		for(WeParticles.Particle particle : group.system().particles())
		{
			// y 要翻符号：预设空间是 y 向上（camera.up = "0 1 0"），雪的初速度
			// 因此是负的＝往下落；画布坐标是 y 向下。不翻的话雪会往上飘。
			float canvasX = layer.originX() + particle.x() + particle.swayX();
			float canvasY =
				layer.originY() - particle.y() - particle.swayY();

			float screenX = screenWidth / 2F
				+ (canvasX - scene.width() / 2F) * scale + offsetX;
			float screenY = screenHeight / 2F
				+ (canvasY - scene.height() / 2F) * scale + offsetY;

			float size = Math.max(1, particle.size() * scale);

			if(screenX + size < 0 || screenY + size < 0
				|| screenX - size > screenWidth
				|| screenY - size > screenHeight)
				continue;

			float brightness = particle.brightness();
			graphics.setColor(brightness, brightness, brightness,
				particle.alpha());

			int left = Math.round(screenX - size / 2);
			int top = Math.round(screenY - size / 2);
			int side = Math.max(1, Math.round(size));

			graphics.blit(DOT, left, top, side, side, 0, 0, DOT_SIZE, DOT_SIZE,
				DOT_SIZE, DOT_SIZE);
		}

		graphics.setColor(1, 1, 1, 1);
		RenderSystem.defaultBlendFunc();
		return true;
	}

	/**
	 * 柔光点贴图：中心白、向外平滑衰减到透明。
	 *
	 * <p>
	 * 预设里写的是 {@code particle/chromaticdot}，那是 Wallpaper Engine 的内置
	 * 资源，包里没有，所以自己生成一个：alpha 用 {@code (1-r)^2}，加性混合下就是
	 * 一颗柔和的光点。</p>
	 */
	private DynamicTexture dotTexture()
	{
		if(dot != null && Minecraft.getInstance().getTextureManager()
			.getTexture(DOT) == dot)
			return dot;

		NativeImage image =
			new NativeImage(NativeImage.Format.RGBA, DOT_SIZE, DOT_SIZE, false);
		float centre = (DOT_SIZE - 1) / 2F;

		for(int y = 0; y < DOT_SIZE; y++)
			for(int x = 0; x < DOT_SIZE; x++)
			{
				float dx = (x - centre) / centre;
				float dy = (y - centre) / centre;
				float distance =
					Math.min(1, (float)Math.sqrt(dx * dx + dy * dy));
				int alpha = Math.round(255 * (1 - distance) * (1 - distance));

				// NativeImage 的整数像素接口是 ABGR，白色 + 这个 alpha
				image.setPixelRGBA(x, y, alpha << 24 | 0xFFFFFF);
			}

		DynamicTexture texture = new DynamicTexture(image);
		texture.setFilter(true, false);

		DynamicTexture previous = dot;

		if(previous != null)
		{
			Minecraft.getInstance().getTextureManager().release(DOT);
			previous.close();
		}

		Minecraft.getInstance().getTextureManager().register(DOT, texture);
		dot = texture;
		return texture;
	}

	/**
	 * 一帧的视差阻尼：目标值是鼠标相对屏幕中心的偏移，实际用的是平滑后的值。
	 *
	 * <p>
	 * 时间常数取自场景的 {@code cameraparallaxdelay}（Persica 是 0.5）。第一帧
	 * 直接贴上去，免得刚进主界面时所有图层从中心滑出来。
	 * </p>
	 *
	 * @return 这一帧的秒数，粒子推进也用同一个值
	 */
	private float advanceParallax(float targetX, float targetY)
	{
		long now = System.nanoTime();
		float delta = (now - lastFrameNanos) / 1_000_000_000F;
		lastFrameNanos = now;

		if(delta < 0)
			delta = 0;
		else if(delta > MAX_FRAME_SECONDS)
			delta = MAX_FRAME_SECONDS;

		if(!smoothStarted)
		{
			smoothStarted = true;
			smoothOffsetX = targetX;
			smoothOffsetY = targetY;
			return delta;
		}

		smoothOffsetX = WeSceneLayout.approach(smoothOffsetX, targetX, delta,
			scene.parallaxDelay());
		smoothOffsetY = WeSceneLayout.approach(smoothOffsetY, targetY, delta,
			scene.parallaxDelay());
		return delta;
	}

	/** 资源重载或换壁纸时释放纹理。 */
	@Override
	public void close()
	{
		if(closed)
			return;

		closed = true;
		closeQuietly(layers);

		if(dot != null)
		{
			Minecraft.getInstance().getTextureManager().release(DOT);
			dot.close();
			dot = null;
		}
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
