package net.wurstclient.render.skia;

import java.io.IOException;
import java.io.InputStream;

import org.jetbrains.skia.Data;
import org.jetbrains.skia.FontMgr;
import org.jetbrains.skia.FontStyle;
import org.jetbrains.skia.Typeface;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * 苹方三字重的 Skia Typeface 缓存（矢量渲染，任意缩放清晰）。
 */
public final class SkiaFontManager
{
	private static SkiaFontManager instance;
	private Typeface regular;
	private Typeface light;
	private Typeface semibold;
	private Typeface latin;
	private Typeface cjk;

	public static SkiaFontManager get()
	{
		if(instance == null)
			instance = new SkiaFontManager();
		return instance;
	}

	private SkiaFontManager()
	{}

	public Typeface regular()
	{
		if(regular == null)
			regular = load("font/pingfang_regular.ttf");
		return regular;
	}

	public Typeface light()
	{
		if(light == null)
			light = load("font/pingfang_light.ttf");
		return light;
	}

	public Typeface semibold()
	{
		if(semibold == null)
			semibold = load("font/pingfang_semibold.ttf");
		return semibold;
	}

	/**
	 * 拉丁文/数字用的字面：SF Pro Rounded（客户端 HUD 那套 {@code wurst:rise}
	 * 字体）。
	 *
	 * <p>
	 * 单独拿它出来是因为它**没有中文字形**（PIL 比对确认：中文渲染结果与私用区
	 * 的缺字字形一致）。Twilight 界面基本全中文，所以只对不含汉字的文本用它，
	 * 汉字仍然走 {@link #regular()} 的苹方——和 macOS 上「SF + 苹方」同一套搭配。
	 * </p>
	 */
	public Typeface latin()
	{
		if(latin == null)
			latin = load("font/sf_pro_rounded_regular.otf");
		return latin;
	}

	/**
	 * 汉字用的字面：**系统已装的中文字体**（Windows 上微软雅黑、macOS 上苹方
	 * SC、Linux 上常见 Noto / 文泉驿），按 {@link #CJK_FAMILIES} 的顺序问
	 * Skia 要第一个存在的。
	 *
	 * <p>
	 * 仓库里那份苹方只当最后的兜底：它的 provider 图集只有 18px，任何非整数倍
	 * 放大都发虚，已经没有别的调用方想要它了；但整屏豆腐块更糟，所以找不到系统
	 * 字体时仍然用它。</p>
	 */
	public Typeface cjk()
	{
		if(cjk == null)
		{
			cjk = loadSystemCjk();

			if(cjk == null)
				cjk = regular();
		}

		return cjk;
	}

	private static final String[] CJK_FAMILIES = {"Microsoft YaHei UI",
		"Microsoft YaHei", "PingFang SC", "Noto Sans CJK SC",
		"Source Han Sans SC", "WenQuanYi Micro Hei", "SimHei", "SimSun"};

	private static Typeface loadSystemCjk()
	{
		FontMgr fontMgr = FontMgr.Companion.getDefault();

		for(String family : CJK_FAMILIES)
		{
			Typeface typeface =
				fontMgr.matchFamilyStyle(family, new FontStyle(400));

			if(typeface != null)
				return typeface;
		}

		return null;
	}

	private static Typeface load(String path)
	{
		ResourceLocation location = new ResourceLocation("wurst", path);
		try(InputStream stream = Minecraft.getInstance()
			.getResourceManager().open(location))
		{
			byte[] bytes = stream.readAllBytes();
			FontMgr fontMgr = FontMgr.Companion.getDefault();
			return fontMgr.makeFromData(
				Data.Companion.makeFromBytes(bytes, 0, bytes.length), 0);
		}catch(IOException e)
		{
			throw new IllegalStateException("Failed to load font " + path,
				e);
		}
	}
}
