/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.gui.title;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * 标题字标：<b>矢量的</b>，按实际绘制尺寸光栅化。
 *
 * <p>
 * 为什么要走矢量：字标在屏幕上只占 337（小窗）～1000（4K）物理像素，而素材是
 * 2101 宽。把位图交给 GPU 去缩，双线性每次只取 2×2 纹素、四分之三的纹素采不到
 * ——1～2 像素宽的连笔会整根消失（实机放大是一堆缺口，对比见
 * {@code docs/title-logo-native-vs-baked.png}）；烘死一个尺寸又会换个窗口就失配。
 * 改成矢量之后两个问题一起消失：任何尺寸都是<b>等尺寸</b>光栅化，边缘现算，
 * 曲线永远平滑。</p>
 *
 * <p>
 * 资源里是描摹出来的单条 {@code <path>}（{@code fill-rule="evenodd"}，字母内孔因此
 * 自动成洞；实测与原图 alpha 的 IoU 0.982，差的 1.8% 是 1.2 像素简化容差在轮廓上
 * 留下的那条带）。解析只支持 {@code M/L/Z}——本仓库生成的文件只用这三个命令，
 * 与其写通用 SVG 解析器，不如把范围写清楚。</p>
 *
 * <p>
 * 光栅化按 2 倍超采样再盒平均：Java2D 的抗锯齿本身不错，超采样之后细笔画的过渡
 * 更干净。整个过程只碰 CPU 位图、没有 GL 调用，跑在渲染线程上（尺寸不变就命中
 * 缓存）。</p>
 */
final class TitleLogoVector
{
	/** 描摹出来的矢量字标（单条 path，evenodd）。 */
	static final ResourceLocation SVG =
		new ResourceLocation("wurst", "logo/wurstb.svg");

	/** 读不到 SVG 时退回的位图（仓库里那份 768×229 烘焙版）。 */
	private static final ResourceLocation FALLBACK =
		new ResourceLocation("wurst", "textures/gui/wurstb_logo_white.png");

	/** 光栅化的超采样倍率。 */
	private static final int SUPERSAMPLE = 2;

	/**
	 * 缩小核的半径，单位是<b>目标像素</b>。
	 *
	 * <p>
	 * 1.0 让边缘过渡横跨约两个像素：与烘过的位图版观感一致（过渡像素占比 20%
	 * 以上），又不会把笔画糊掉。实测 0.5（近似盒子平均）只有 7%，看着是硬边。
	 * </p>
	 */
	private static final float FILTER_RADIUS = 1F;

	/** 按绘制尺寸生成的那张，复用一个槽位。 */
	private static final ResourceLocation GENERATED =
		new ResourceLocation("wurst", "title_logo");

	/** 解析结果：路径 + 它自己的坐标系（viewBox）。 */
	record Outline(Path2D.Float path, float width, float height)
	{}

	/** 真的要 blit 的那张贴图，以及它自己的像素尺寸（UV 要用）。 */
	record Bound(ResourceLocation location, int width, int height)
	{}

	private Outline outline;
	private boolean unreadable;

	private DynamicTexture generated;
	private int generatedWidth;
	private int generatedHeight;

	/**
	 * 选出这次该 blit 的贴图。
	 *
	 * @param drawnWidth
	 *            屏幕上真实占用的像素宽（渲染目标像素 = GUI 逻辑宽 × guiScale）
	 */
	Bound bind(int drawnWidth)
	{
		if(drawnWidth <= 0)
			return fallback();

		Minecraft minecraft = Minecraft.getInstance();

		if(!loadOutline(minecraft))
			return fallback();

		int targetHeight = Math.max(1,
			Math.round(outline.height() * drawnWidth / outline.width()));

		if(generated != null && generatedWidth == drawnWidth
			&& generatedHeight == targetHeight
			&& minecraft.getTextureManager().getTexture(GENERATED) == generated)
			return new Bound(GENERATED, generatedWidth, generatedHeight);

		try
		{
			upload(minecraft, drawnWidth, targetHeight);
		}catch(RuntimeException | Error e)
		{
			System.out.println("[Title] 字标光栅化失败，改用位图：" + e);
			unreadable = true;
			return fallback();
		}

		return new Bound(GENERATED, generatedWidth, generatedHeight);
	}

	private Bound fallback()
	{
		return new Bound(FALLBACK, 768, 229);
	}

	/**
	 * 逻辑像素换算成渲染目标像素（纯函数，可单测）。
	 *
	 * @param pixelsPerGui
	 *            guiScale：一个 GUI 逻辑像素占几个渲染目标像素
	 */
	static int physicalWidth(int guiWidth, double pixelsPerGui)
	{
		if(guiWidth <= 0 || pixelsPerGui <= 0)
			return 0;

		return Math.max(1, (int)Math.round(guiWidth * pixelsPerGui));
	}

	/** 光栅化到绘制尺寸并注册。 */
	private void upload(Minecraft minecraft, int width, int height)
	{
		int[] alpha = rasterize(outline, width, height);

		NativeImage image =
			new NativeImage(NativeImage.Format.RGBA, width, height, false);

		for(int y = 0; y < height; y++)
			for(int x = 0; x < width; x++)
				// NativeImage 的整数像素接口是 ABGR，alpha 在高字节
				image.setPixelRGBA(x, y, alpha[y * width + x] << 24 | 0xFFFFFF);

		DynamicTexture texture = new DynamicTexture(image);
		texture.setFilter(true, false);

		// 先放掉旧的再注册新的：release 是按名字找纹理的
		DynamicTexture previous = generated;

		if(previous != null)
		{
			minecraft.getTextureManager().release(GENERATED);
			previous.close();
			generated = null;
		}

		minecraft.getTextureManager().register(GENERATED, texture);
		generated = texture;
		generatedWidth = width;
		generatedHeight = height;
	}

	private boolean loadOutline(Minecraft minecraft)
	{
		if(outline != null)
			return true;

		if(unreadable)
			return false;

		try(InputStream stream = minecraft.getResourceManager().open(SVG))
		{
			outline = parse(
				new String(stream.readAllBytes(), StandardCharsets.UTF_8));
			return true;
		}catch(IOException | RuntimeException e)
		{
			System.out.println("[Title] 读不到矢量字标：" + e);
			unreadable = true;
			return false;
		}
	}

	/**
	 * 解析本仓库生成的那种子集：{@code viewBox} 加一条 {@code <path d="...">}，
	 * 路径里只有 {@code M}、{@code L} 与 {@code Z}。
	 */
	static Outline parse(String svg) throws IOException
	{
		if(svg == null || svg.isBlank())
			throw new IOException("矢量字标是空的");

		float[] viewBox = parseViewBox(svg);
		String data = attribute(svg, "d");

		if(data == null || data.isBlank())
			throw new IOException("矢量字标里没有 <path d=\"...\">");

		Path2D.Float path = new Path2D.Float(Path2D.WIND_EVEN_ODD);
		String[] tokens = data.trim().split("[\\s,]+");
		int index = 0;

		while(index < tokens.length)
		{
			String command = tokens[index++];

			switch(command)
			{
				case "M", "L" -> {
					float x = number(tokens, index++);
					float y = number(tokens, index++);

					if("M".equals(command))
						path.moveTo(x, y);
					else
						path.lineTo(x, y);
				}
				case "Z" -> path.closePath();
				default -> throw new IOException(
					"不支持的路径命令：" + command + "（只生成 M/L/Z）");
			}
		}

		return new Outline(path, viewBox[0], viewBox[1]);
	}

	/** {@code viewBox="0 0 W H"} → [W, H]。 */
	private static float[] parseViewBox(String svg) throws IOException
	{
		String value = attribute(svg, "viewBox");

		if(value == null)
			throw new IOException("矢量字标没有 viewBox");

		String[] parts = value.trim().split("[\\s,]+");

		if(parts.length != 4)
			throw new IOException("viewBox 不是四个数：" + value);

		try
		{
			return new float[]{Float.parseFloat(parts[2]),
				Float.parseFloat(parts[3])};
		}catch(NumberFormatException e)
		{
			throw new IOException("viewBox 解析失败：" + value, e);
		}
	}

	private static String attribute(String svg, String name)
	{
		int at = svg.indexOf(name + "=\"");

		if(at < 0)
			return null;

		int start = at + name.length() + 2;
		int end = svg.indexOf('"', start);
		return end < 0 ? null : svg.substring(start, end);
	}

	private static float number(String[] tokens, int index) throws IOException
	{
		if(index >= tokens.length)
			throw new IOException("路径数据提前结束");

		try
		{
			return Float.parseFloat(tokens[index]);
		}catch(NumberFormatException e)
		{
			throw new IOException("路径里的数字无效：" + tokens[index], e);
		}
	}

	/**
	 * 光栅化成 alpha（0..255）：按 {@link #SUPERSAMPLE} 倍渲染，再用三角核
	 * 缩小到目标尺寸。
	 *
	 * <p>
	 * <b>为什么缩小这一步要"糊"一点</b>：字标的发丝在这个尺寸下只有 0.3～1 像素，
	 * 用盒子平均（等尺寸精确重采样）会得到 1 像素宽的硬过渡——数学上正确，但在
	 * 500 像素宽的字标上会把"发丝不够粗"这件事暴露得最清楚，看起来就是锯齿。
	 * 三角核把过渡铺到约 {@value #FILTER_RADIUS} 个像素，观感与烘过的位图版一致
	 * （实测过渡像素占比从 7% 升到 20% 以上），而矢量带来的"任何尺寸都等尺寸
	 * 渲染"依然保留。</p>
	 *
	 * <p>
	 * 纯 CPU、无 GL，所以可以单测；{@link BufferedImage} 也不要求图形环境。</p>
	 *
	 * @return {@code width * height} 个 alpha 值
	 */
	static int[] rasterize(Outline outline, int width, int height)
	{
		if(outline == null || width <= 0 || height <= 0)
			return new int[0];

		int wide = width * SUPERSAMPLE;
		int tall = height * SUPERSAMPLE;
		BufferedImage image =
			new BufferedImage(wide, tall, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();

		try
		{
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
				RenderingHints.VALUE_ANTIALIAS_ON);
			graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
				RenderingHints.VALUE_RENDER_QUALITY);
			graphics.setColor(Color.WHITE);
			graphics.scale(wide / (double)outline.width(),
				tall / (double)outline.height());
			graphics.fill(outline.path());
		}finally
		{
			graphics.dispose();
		}

		// 先把超采样的 alpha 取出来
		float[] samples = new float[wide * tall];

		for(int y = 0; y < tall; y++)
			for(int x = 0; x < wide; x++)
				samples[y * wide + x] = image.getRGB(x, y) >>> 24;

		// 再按三角核缩到目标尺寸：核半径按目标像素计
		int[] alpha = new int[width * height];
		float radius = FILTER_RADIUS * SUPERSAMPLE;

		for(int y = 0; y < height; y++)
		{
			int y0 = Math.max(0, (int)Math.floor(y * SUPERSAMPLE + 0.5F
				- radius - SUPERSAMPLE / 2F));
			int y1 = Math.min(tall - 1, (int)Math.ceil(y * SUPERSAMPLE + 0.5F
				+ radius + SUPERSAMPLE / 2F));

			for(int x = 0; x < width; x++)
			{
				int x0 = Math.max(0, (int)Math.floor(x * SUPERSAMPLE + 0.5F
					- radius - SUPERSAMPLE / 2F));
				int x1 = Math.min(wide - 1, (int)Math.ceil(x * SUPERSAMPLE
					+ 0.5F + radius + SUPERSAMPLE / 2F));

				float sum = 0;
				float total = 0;

				for(int sy = y0; sy <= y1; sy++)
				{
					float weightY = tent(sy + 0.5F - (y * SUPERSAMPLE + 0.5F),
						radius);

					if(weightY <= 0)
						continue;

					for(int sx = x0; sx <= x1; sx++)
					{
						float weight = weightY
							* tent(sx + 0.5F - (x * SUPERSAMPLE + 0.5F), radius);

						if(weight <= 0)
							continue;

						sum += weight * samples[sy * wide + sx];
						total += weight;
					}
				}

				alpha[y * width + x] = total <= 0 ? 0
					: Math.max(0, Math.min(255, Math.round(sum / total)));
			}
		}

		return alpha;
	}

	/** 三角核：中心 1，到 radius 处为 0。 */
	private static float tent(float distance, float radius)
	{
		return Math.max(0, 1 - Math.abs(distance) / radius);
	}
}
