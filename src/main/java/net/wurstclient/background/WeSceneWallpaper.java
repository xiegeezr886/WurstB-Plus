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
import java.util.Arrays;
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
		int skippedForBudget = 0;

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
				int imageWidth = image.getWidth();
				int imageHeight = image.getHeight();

				// 塞不进剩余预算时**先别丢层**：按缺口把它再缩到刚好放得下。
				// 图层是按顺序排的、背景在前，所以走到这里的通常是叠加在上面的
				// UI 面板/文字框 —— 稍微软一点远好过整层消失。
				int[] fit = LayerResampler.fitInto(imageWidth, imageHeight,
					MAX_PIXELS - totalPixels);

				if(fit != null)
					try
					{
						NativeImage smaller = LayerResampler.downscale(image,
							fit[0], fit[1]);
						image.close();
						image = smaller;

						System.out.println("[Background] 「" + layer.name()
							+ "」塞不进剩余预算，再缩到 " + fit[0] + "x" + fit[1]
							+ "（原 " + imageWidth + "x" + imageHeight + "）");

						imageWidth = fit[0];
						imageHeight = fit[1];

					}catch(RuntimeException e)
					{
						// 缩不了就照旧走下面的跳过分支
					}

				long pixels = (long)imageWidth * imageHeight;

				if(totalPixels + pixels > MAX_PIXELS)
				{
					// 到预算就别再往里加了，但**别把整个场景扔掉**：图层是按顺序
					// 排的，背景通常在最前面，丢掉后面几层远好过整幅退回内置背景
					// （实测「绪山真寻」就是这个 64M 像素的硬上限把整个场景判死的，
					// 而它前面几层本来完全能画）。
					//
					// 用 continue 而不是 break：放不下的这一层跳过，**后面更小、放得下
					// 的层仍然有机会画出来**。直接 break 会把后续所有层一并丢掉，
					// 包括那些本来装得下的。
					//
					// 这里**必须留日志**：不留的话，被砍掉的层看起来就像"压根没处理"，
					// 排查时会往贴图格式那边找 —— 实测「流萤」的 mp4 贴图就因此被误判
					// 过一轮（那一层本身解得出来，是被预算挡掉的）。
					image.close();
					skippedForBudget++;

					// 逐层记下来：只说"跳过了 4 层"没法判断跳掉的是不是要紧的内容
					System.out.println("[Background]   预算不够，跳过「"
						+ layer.name() + "」：" + imageWidth + "x" + imageHeight
						+ "（累计已 " + totalPixels / 1_000_000 + "M 像素）");
					continue;
				}

				totalPixels += pixels;
				decoded.add(new DecodedLayer(layer, image));
			}
		}catch(RuntimeException e)
		{
			for(DecodedLayer layer : decoded)
				layer.image().close();

			throw e;
		}

		if(decoded.isEmpty())
			throw new IOException("场景里没有可画的图层");

		if(skippedForBudget > 0)
			System.out.println("[Background] 图层像素总量超过预算（"
				+ MAX_PIXELS / 1_000_000 + "M），跳过 " + skippedForBudget
				+ " 层；已画出 " + decoded.size() + " 层");

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
			int width = texture.imageWidth();
			int height = texture.imageHeight();

			if(texture.isStandardImage())
				return new DecodedLayer(layer, shrink(layer,
					NativeImage.read(
						new ByteArrayInputStream(texture.payload()))));

			Raw raw = decodeUncompressed(texture, bytes);

			if(raw != null)
				return new DecodedLayer(layer, shrink(layer,
					toImage(raw.rgba(), raw.width(), raw.height())));

			// 视频贴图：载荷就是一整段 MP4，解出第一帧当静态画面用
			if(texture.isMp4())
			{
				NativeImage frame = decodeMp4Frame(texture.payload());

				if(frame != null)
					return new DecodedLayer(layer, shrink(layer, frame));

				System.out.println("[Background] 跳过 " + layer.name()
					+ "：mp4 视频贴图解不出帧（" + width + "x" + height + "，"
					+ texture.payload().length + " 字节）");
				return null;
			}

			System.out.println("[Background] 跳过 " + layer.name()
				+ "：暂不支持的贴图载荷（format=" + texture.format() + "，"
				+ width + "x" + height + "，载荷 " + texture.payload().length
				+ " 字节）");
			return null;

		}catch(IOException | RuntimeException e)
		{
			System.out.println(
				"[Background] 跳过 " + layer.name() + "：" + e.getMessage());
			return null;
		}
	}

	/**
	 * 按图层的**显示尺寸**把贴图缩小再上传。
	 *
	 * <p>
	 * 起因是实测到的浪费：某场景给一根钟表指针用了 2000×2000 的贴图，屏幕上只有几根细针
	 * 那么细，四层合计 20M 像素（约 80 MB 显存），把 64M 像素的预算吃光、后面的层全被挤掉。
	 * 官方文档也承认这类浪费（建议作者导入时把图层裁剪到最小），但已发布的素材改不了，
	 * 只能在渲染端补。
	 * </p>
	 *
	 * <p>
	 * <b>安全的依据</b>：{@code WeSceneLayout.rect} 算图层矩形时用的是
	 * {@code layer.sizeX() > 0 ? layer.sizeX() : textureWidth} —— 只要场景声明了尺寸，
	 * 布局就与贴图像素尺寸无关，缩小贴图不会改变图层的位置和大小；反过来，
	 * **没声明尺寸时绝不动**（那时布局拿贴图尺寸当尺寸，缩了会把图层画小）。
	 * 这条判断在 {@link LayerResampler#target} 里，并有单测钉住。
	 * </p>
	 *
	 * <p>
	 * 降采样失败不致命：打日志、用原图继续，别为这点优化丢一整层。
	 * </p>
	 */
	private static NativeImage shrink(WeScene.Layer layer, NativeImage image)
	{
		int[] target = LayerResampler.target(image.getWidth(),
			image.getHeight(), layer.sizeX(), layer.sizeY(), layer.scaleX(),
			layer.scaleY());

		if(target == null)
			return image;

		try
		{
			NativeImage small =
				LayerResampler.downscale(image, target[0], target[1]);

			// 记的是**有效显示尺寸**（size × scale），不是 size —— 只打 size 的话
			// 「2000 -> 625」看起来对不上，得让人一眼看懂为什么该缩
			long displayWidth = Math
				.round(layer.sizeX() * (layer.scaleX() > 0 ? layer.scaleX() : 1));
			long displayHeight = Math
				.round(layer.sizeY() * (layer.scaleY() > 0 ? layer.scaleY() : 1));
			long before = (long)image.getWidth() * image.getHeight();
			long after = (long)target[0] * target[1];

			System.out.println("[Background] 「" + layer.name() + "」贴图 "
				+ image.getWidth() + "x" + image.getHeight() + " -> " + target[0]
				+ "x" + target[1] + "（显示 " + displayWidth + "x"
				+ displayHeight + "，省 "
				+ (before > 0 ? (100 - after * 100 / before) : 0) + "%）");

			image.close();
			return small;

		}catch(RuntimeException e)
		{
			System.out.println("[Background] 「" + layer.name()
				+ "」降采样失败，改用原图：" + e.getMessage());
			return image;
		}
	}

	/**
	 * 非 PNG/JPEG 的载荷：按 format 解成 RGBA。
	 *
	 * <p>
	 * <b>两道保险。</b>① 长度必须与该格式的固定字节数**精确吻合** —— 不吻合就跳过，
	 * 少画一层远好过把错位的数据当像素糊上去。② 尺寸要试两套：**图像尺寸**
	 * （{@code imageWidth/Height}）与 **GPU 填充尺寸**（{@code textureWidth/Height}）。
	 * 实测有 {@code img=1920x1080} 而 {@code uncompressedSize = 2048×2048×4} 的情况，
	 * 那是 2 的幂填充，只按图像尺寸算会全部对不上。
	 * </p>
	 *
	 * <p>
	 * 载荷本身可能有两种来源：未压缩（{@code compression == 0}，数据原样存放）或
	 * LZ4 解压后的结果 —— 两者在 {@link WeTexture#parse} 里已经统一成"真正的像素
	 * 字节"，所以这里只管按 format 解。
	 * </p>
	 *
	 * @return 解出来的 RGBA 与它用的尺寸；不认识这个 format 或长度对不上返回 null
	 */
	private static Raw decodeUncompressed(WeTexture texture, byte[] bytes)
	{
		byte[] payload = texture.payload();
		int offset = texture.dataOffset();
		int remaining = bytes.length - offset;
		int[][] sizes = {{texture.imageWidth(), texture.imageHeight()},
			{texture.textureWidth(), texture.textureHeight()}};

		for(int[] size : sizes)
		{
			if(size[0] <= 0 || size[1] <= 0)
				continue;

			byte[] fromPayload = decodePayload(payload, 0, payload.length,
				texture.format(), size[0], size[1]);

			if(fromPayload != null)
				return new Raw(fromPayload, size[0], size[1]);

			if(remaining <= payload.length)
				continue;

			byte[] fromFile = decodePayload(bytes, offset, remaining,
				texture.format(), size[0], size[1]);

			if(fromFile != null)
				return new Raw(fromFile, size[0], size[1]);
		}

		return null;
	}

	/** 解出来的裸像素与它对应的尺寸。 */
	private record Raw(byte[] rgba, int width, int height)
	{
	}

	/**
	 * 把一整段 MP4 解成第一帧。
	 *
	 * <p>
	 * FFmpeg 那条 native 路只吃**文件路径**（没有内存入口），所以这里落一个临时文件、
	 * 解完就删。贴图里的视频段能到十几 MB，落盘一次是可以接受的代价 —— 换来的是这类
	 * 图层不再整层消失（实测「流萤」那个场景里 60 帧的两层就是这种情况）。
	 * </p>
	 *
	 * <p>
	 * <b>只取第一帧，所以它是静态的。</b>Wallpaper Engine 那边会把它当视频播，本项目还
	 * 没做到那一步 —— 有画面远好过整层没有，但它不会动，这一点不含糊。
	 * </p>
	 *
	 * @return 第一帧；解不出来返回 null（调用方照常跳过这一层并打日志）
	 */
	private static NativeImage decodeMp4Frame(byte[] mp4)
	{
		if(!FfmpegVideoDecoder.isAvailable())
			return null;

		Path temp = null;

		try
		{
			temp = Files.createTempFile("wurst-we-tex-", ".mp4");
			Files.write(temp, mp4);

			FfmpegVideoDecoder decoder =
				FfmpegVideoDecoder.open(temp.toString(), true);

			try
			{
				if(!decoder.isOpen() || decoder.nextFrame() <= 0)
					return null;

				return toImage(decoder.frameBuffer(), decoder.width(),
					decoder.height());

			}finally
			{
				decoder.close();
			}

		}catch(IOException | RuntimeException e)
		{
			System.out.println("[Background] mp4 贴图解帧失败：" + e);
			return null;

		}finally
		{
			if(temp != null)
				try
				{
					Files.deleteIfExists(temp);
				}catch(IOException ignored)
				{
					// 临时文件删不掉不影响画面，不值得让整幅场景失败
				}
		}
	}

	private static byte[] decodePayload(byte[] data, int offset, int length,
		int format, int width, int height)
	{
		long pixels = (long)width * height;

		// 长度必须**精确等于**该格式的固定字节数。别放宽成"至少"：实测这些载荷里
		// 有很大一部分是**压缩**的（例：512x512 的一张只有 6105 字节；1024x885 的
		// 真实数据 447615 字节是奇数，而任何 DXT 数据的长度都必须是 8 的倍数），
		// 放宽只会让"长度恰好够长"的压缩数据被当成裸像素画上去 —— 那样会得到垃圾
		// 画面，比少画一层糟得多。不吻合就跳过并打日志。
		switch(format)
		{
			case 0: // ARGB8888
			if(length != pixels * 4)
				return null;

			return Arrays.copyOfRange(data, offset, offset + length);

			case 9: // R8
			if(length != pixels)
				return null;

			return grey(data, offset, width, height, 1);

			case 8: // RG88：拿 R 那一路当灰度
			if(length != pixels * 2)
				return null;

			return grey(data, offset, width, height, 2);

			case 4: // DXT5
			case 6: // DXT3
			case 7: // DXT1
			return dxt(data, offset, length, width, height, format);

			default:
			return null;
		}
	}

	private static byte[] dxt(byte[] data, int offset, int length, int width,
		int height, int format)
	{
		long needed = format == 7 ? DxtCodec.dxt1Size(width, height)
			: DxtCodec.dxt35Size(width, height);

		if(length != needed)
			return null;

		byte[] block = Arrays.copyOfRange(data, offset, offset + (int)needed);

		return switch(format)
		{
			case 4 -> DxtCodec.decodeDxt5(block, width, height);
			case 6 -> DxtCodec.decodeDxt3(block, width, height);
			default -> DxtCodec.decodeDxt1(block, width, height);
		};
	}

	/** 单通道/双通道的裸像素按灰度铺开。 */
	private static byte[] grey(byte[] data, int offset, int width, int height,
		int stride)
	{
		byte[] rgba = new byte[width * height * 4];

		for(int i = 0; i < width * height; i++)
		{
			int value = data[offset + i * stride] & 0xFF;
			rgba[i * 4] = (byte)value;
			rgba[i * 4 + 1] = (byte)value;
			rgba[i * 4 + 2] = (byte)value;
			rgba[i * 4 + 3] = (byte)0xFF;
		}

		return rgba;
	}

	/**
	 * RGBA8888 字节 -> {@link NativeImage}。
	 *
	 * <p>
	 * 逐像素写：{@code NativeImage} 没有公开底层指针，而它的整数像素接口是 **ABGR**
	 * （低字节是红），所以这里要把 R,G,B,A 重新打包。这套约定与
	 * {@code BackgroundVideo.copyInto} 完全一致（那边是实测核对过的）。
	 * </p>
	 */
	private static NativeImage toImage(byte[] rgba, int width, int height)
	{
		NativeImage image =
			new NativeImage(NativeImage.Format.RGBA, width, height, false);

		for(int y = 0; y < height; y++)
			for(int x = 0; x < width; x++)
			{
				int i = (y * width + x) * 4;
				int r = rgba[i] & 0xFF;
				int g = rgba[i + 1] & 0xFF;
				int b = rgba[i + 2] & 0xFF;
				int a = rgba[i + 3] & 0xFF;
				image.setPixelRGBA(x, y, a << 24 | b << 16 | g << 8 | r);
			}

		return image;
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
