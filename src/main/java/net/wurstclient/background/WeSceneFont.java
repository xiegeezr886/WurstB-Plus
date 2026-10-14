/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.List;
import java.util.Locale;

import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTruetype;
import org.lwjgl.system.MemoryUtil;

import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.font.TrueTypeGlyphProvider;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.resources.ResourceLocation;

/**
 * 作者包内的字体，加载成 MC 能画的 {@link Font}。
 *
 * <p>
 * <b>能加载的只有 glyf 型 {@code .ttf}</b>：MC 的字形加载器是 STB TrueType
 * （{@code com.mojang.blaze3d.font.TrueTypeGlyphProvider}），它读 TTF 的
 * {@code glyf} 轮廓；{@code .otf} 用的是 CFF 轮廓，STB 打不开
 * （{@code stbtt_InitFont} 直接返回 0）。所以 {@code fonts/Atami-Regular.otf}
 * 与 {@code systemfont_arial} 这类系统字体引用一律回退到 MC 自带字体
 * （见 {@link #canLoad(String)}）。</p>
 *
 * <p>
 * 三处都是**照着 MC 自己的加载流程**写的，不是自创：
 * {@code FontSet(textureManager, id).reload(List.of(provider))} 然后
 * {@code new Font(id -> set, false)}（{@code Font} 的构造是
 * {@code Function<ResourceLocation, FontSet>}）。字号用 STB 的
 * {@code stbtt_ScaleForPixelHeight}，即 provider 的 {@code size} 参数就是
 * **em 的像素高** —— 这正好是 WE 的 {@code pointsize} 语义（画布像素），
 * 所以直接拿 {@code pointsize} 当 {@code size}，不再做"除以 9"的折算。</p>
 *
 * <p>
 * <b>行高必须自己算</b>：{@code Font.lineHeight} 是**写死的 9**（字节码里就是
 * {@code bipush 9}），与 provider 无关；自定义字体的真实行高只能从
 * {@code stbtt_GetFontVMetrics} 的 ascent/descent/lineGap 乘 pointScale 得到。
 * 这个值是 {@code renderText} 排版（行距、块高）的依据。</p>
 */
public final class WeSceneFont implements AutoCloseable
{
	/** WE 的系统字体引用前缀：字体在系统里，不在包里，只能回退。 */
	private static final String SYSTEM_PREFIX = "systemfont_";

	/** STB 只吃 glyf 型 TTF。 */
	private static final String TTF_SUFFIX = ".ttf";

	/** sfnt 版本标记（文件前 4 字节，大端）。 */
	private static final int SFNT_TRUETYPE = 0x00010000;
	private static final int SFNT_TRUE = 0x74727565; // 'true'
	private static final int SFNT_TYP1 = 0x74797031; // 'typ1'
	private static final int SFNT_TTCF = 0x74746366; // 'ttcf' 字体集合
	private static final int SFNT_OTTO = 0x4F54544F; // 'OTTO' = CFF 轮廓

	/**
	 * 过采样倍数：字形先按这个倍数光栅化，再缩到目标尺寸，缩小时更干净。
	 *
	 * <p>
	 * 这是**我选的取舍，不是从自带资源抄的**：1.20.1 自带的
	 * {@code assets/minecraft/font/include/unifont.json} 里 {@code providers}
	 * 是**空数组** —— MC 并没有内置 TTF 字体，全部是点阵。倍数越大字形图集占得越多，
	 * 2 是"够清晰且不撑爆图集"的一档。
	 * </p>
	 */
	private static final float OVERSAMPLE = 2.0F;

	private final ResourceLocation id;
	private final Font font;
	private final FontSet set;
	private final GlyphProvider provider;
	private final float pointSize;
	private final int lineHeight;
	private boolean closed;

	private WeSceneFont(ResourceLocation id, Font font, FontSet set,
		GlyphProvider provider, float pointSize, int lineHeight)
	{
		this.id = id;
		this.font = font;
		this.set = set;
		this.provider = provider;
		this.pointSize = pointSize;
		this.lineHeight = lineHeight;
	}

	/**
	 * 这个字体引用能不能用 MC 的 TrueType 加载器吃下。
	 *
	 * <p>
	 * 只有 {@code .ttf} 可以；{@code systemfont_*} 是系统字体、{@code .otf} 是
	 * CFF 轮廓，两者都返回 {@code false}，由调用方回退。
	 * </p>
	 */
	public static boolean canLoad(String fontRef)
	{
		if(fontRef == null)
			return false;

		String name = fontRef.trim().toLowerCase(Locale.ROOT);

		return name.endsWith(TTF_SUFFIX) && !name.startsWith(SYSTEM_PREFIX);
	}

	/**
	 * 加载一份字体。
	 *
	 * @param pointSize
	 *            em 的像素高（WE 的 {@code pointsize}）
	 * @param ttf
	 *            字体文件字节（从作品包里读出来的）
	 * @param id
	 *            给 {@link FontSet} 用的唯一 id（同一次运行里不能重复）
	 */
	public static WeSceneFont load(String fontRef, float pointSize, byte[] ttf,
		ResourceLocation id) throws IOException
	{
		if(ttf == null || ttf.length == 0)
			throw new IOException("字体文件是空的：" + fontRef);

		// 先验格式再交给 STB：见 requireTrueType 里记的那次 JVM 崩溃
		requireTrueType(ttf);

		float size = Math.max(1, pointSize);
		ByteBuffer data = toDirectBuffer(ttf);
		GlyphProvider provider = null;
		FontSet set = null;

		try
		{
			STBTTFontinfo info = openFont(data);

			// **缓冲从这一行起归 provider 所有**：TrueTypeGlyphProvider.close() 里
			// 除了 STBTTFontinfo.free() 还会 MemoryUtil.memFree(fontMemory)
			// （字节码核对过），所以调用方绝不能再释放一次 —— 那是一次 double free，
			// 实测会让 JVM 直接崩在 jemalloc 里（hs_err_pid 里 EXCEPTION_ACCESS_VIOLATION）。
			GlyphProvider opened = newProvider(data, info, size);
			provider = opened;

			// 这个局部量只赋值一次：Font 的构造要一个捕获它的 lambda
			FontSet created =
				new FontSet(Minecraft.getInstance().getTextureManager(), id);
			created.reload(List.of(opened));
			set = created;

			int lineHeight = lineHeight(info, size);
			Font font = new Font(location -> created, false);

			return new WeSceneFont(id, font, created, opened, size, lineHeight);

		}catch(IOException | RuntimeException e)
		{
			// 已经交出去的由 FontSet/provider 自己收尾（含字形缓冲）；
			// 只有连 provider 都没建起来时，这块缓冲才还归这里释放
			if(set != null)
				set.close();
			else if(provider != null)
				provider.close();
			else
				MemoryUtil.memFree(data);

			if(e instanceof IOException)
				throw (IOException)e;

			throw new IOException("加载字体失败（" + fontRef + "）：" + e, e);
		}
	}

	/**
	 * 在 Java 侧先验一遍 sfnt 格式，**通过之后才允许碰 STB**。
	 *
	 * <p>
	 * <b>这道检查是防崩，不是洁癖。</b>实测把 18 字节垃圾喂给
	 * {@code stbtt_InitFont}，JVM 直接崩在 {@code lwjgl_stb.dll} 里
	 * （hs_err：{@code EXCEPTION_ACCESS_VIOLATION}，帧就是
	 * {@code STBTruetype.nstbtt_InitFont}）—— 那是**原生崩溃，Java 侧 catch 不到**。
	 * 也就是说：如果某个作品包里那份 {@code .ttf} 是坏的/截断的，直接交给 STB
	 * 会把游戏带走。所以先看前 4 字节的 sfnt 标记：{@code 0x00010000}、
	 * {@code true}、{@code typ1}、{@code ttcf} 放行；{@code OTTO}（CFF 轮廓，
	 * 也就是 {@code .otf}）与其它一律在这里拒绝。
	 * </p>
	 *
	 * <p>
	 * 纯字节检查，不碰原生库，可以单测 —— 上面那次崩溃正是"用脏数据测 STB"造成的。
	 * </p>
	 */
	static void requireTrueType(byte[] bytes) throws IOException
	{
		if(bytes == null || bytes.length < 12)
			throw new IOException("字体文件太小，不是 sfnt 字体（"
				+ (bytes == null ? 0 : bytes.length) + " 字节）");

		int tag = (bytes[0] & 0xFF) << 24 | (bytes[1] & 0xFF) << 16
			| (bytes[2] & 0xFF) << 8 | bytes[3] & 0xFF;

		if(tag == SFNT_OTTO)
			throw new IOException("这是 CFF 轮廓的 OpenType（OTTO），STB 的 glyf 加载器打不开");

		if(tag != SFNT_TRUETYPE && tag != SFNT_TRUE && tag != SFNT_TYP1
			&& tag != SFNT_TTCF)
			throw new IOException(
				String.format("不是 sfnt 字体（前 4 字节 %08X）", tag));
	}

	/** STB 需要一个**直接**缓冲，并且这块内存要活到 provider 关闭为止。 */
	static ByteBuffer toDirectBuffer(byte[] bytes)
	{
		ByteBuffer buffer = MemoryUtil.memAlloc(bytes.length);
		buffer.put(bytes).flip();
		return buffer;
	}

	/**
	 * 用 STB 打开字体。打不开（例如 CFF 轮廓的 {@code .otf}）抛
	 * {@link IOException}，不返回半截对象。**不碰 GL，可单测。**
	 */
	static STBTTFontinfo openFont(ByteBuffer data) throws IOException
	{
		STBTTFontinfo info = STBTTFontinfo.malloc();

		if(!STBTruetype.stbtt_InitFont(info, data))
		{
			info.free();
			throw new IOException("STB 打不开这份字体（不是 glyf 型 TrueType？）");
		}

		return info;
	}

	/** {@code size} 就是 em 的像素高（{@code stbtt_ScaleForPixelHeight}）。 */
	static GlyphProvider newProvider(ByteBuffer data, STBTTFontinfo info,
		float size)
	{
		return new TrueTypeGlyphProvider(data, info, size, OVERSAMPLE, 0, 0, "");
	}

	/**
	 * 字体自己的行高（像素）：{@code (ascent - descent + lineGap) × pointScale}。
	 * MC 的 {@code Font.lineHeight} 是常数 9，对自定义字体没有意义。
	 */
	static int lineHeight(STBTTFontinfo info, float size)
	{
		IntBuffer ascent = MemoryUtil.memAllocInt(1);
		IntBuffer descent = MemoryUtil.memAllocInt(1);
		IntBuffer lineGap = MemoryUtil.memAllocInt(1);

		try
		{
			STBTruetype.stbtt_GetFontVMetrics(info, ascent, descent, lineGap);

			float scale = STBTruetype.stbtt_ScaleForPixelHeight(info, size);
			int height = Math.round(
				(ascent.get(0) - descent.get(0) + lineGap.get(0)) * scale);

			return Math.max(1, height);

		}finally
		{
			MemoryUtil.memFree(ascent);
			MemoryUtil.memFree(descent);
			MemoryUtil.memFree(lineGap);
		}
	}

	public ResourceLocation id()
	{
		return id;
	}

	public Font font()
	{
		return font;
	}

	/** 字体自己的行高（像素，em 高 = {@link #pointSize()} 时）。 */
	public int lineHeight()
	{
		return lineHeight;
	}

	public float pointSize()
	{
		return pointSize;
	}

	/** 这个码点在作者字体里有没有字形（没有就回退到 MC 字体）。 */
	public boolean supports(int codePoint)
	{
		return provider.getSupportedGlyphs().contains(codePoint);
	}

	@Override
	public void close()
	{
		if(closed)
			return;

		closed = true;

		// FontSet 会逐个关掉 provider（连带 STB 的 font info）与字形图集；
		// provider 自己又持有字形缓冲，所以这里**不能再释放缓冲**（见 load 里的说明）。
		set.close();
	}
}
