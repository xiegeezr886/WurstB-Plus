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
 * Skia Typeface 缓存（矢量渲染，任意缩放清晰）。
 *
 * <p>
 * <b>中文字面来自系统，不再随包分发。</b>此前仓库里带了三份苹方
 * （{@code assets/wurst/font/pingfang_*.ttf}，共 31.3 MB）当兜底。那三份文件是
 * Apple 的系统字体，来源压缩包却以 MIT 名义再许可——第三方无权这样做，
 * 因此已从仓库删除。
 * </p>
 *
 * <p>
 * 现在按字重向 Skia 要系统里已装的中文字体（见 {@link #CJK_FAMILIES}），
 * 找不到就退回 Skia 的默认字面。取不到字体时不再抛异常：这三份文件不存在了，
 * 任何"加载失败就抛"的写法都会让歌词与 ESP 直接崩，而缺字只是显示变差。
 * </p>
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
			regular = resolve(400);
		return regular;
	}

	public Typeface light()
	{
		if(light == null)
			light = resolve(300);
		return light;
	}

	public Typeface semibold()
	{
		if(semibold == null)
			semibold = resolve(600);
		return semibold;
	}

	/**
	 * 拉丁文/数字用的字面：SF Pro Rounded（客户端 HUD 那套 {@code wurst:rise}
	 * 字体）。
	 *
	 * <p>
	 * 单独拿它出来是因为它**没有中文字形**（PIL 比对确认：中文渲染结果与私用区
	 * 的缺字字形一致）。Twilight 界面基本全中文，所以只对不含汉字的文本用它，
	 * 汉字走 {@link #cjk()}——和 macOS 上「SF + 系统中文」同一套搭配。
	 * </p>
	 *
	 * <p>
	 * 注：SF Pro Rounded 同样是 Apple 的字体，存在与苹方同类的授权问题，
	 * 详见 THIRD-PARTY-NOTICES.md。它不在这次删除范围内。
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
	 */
	public Typeface cjk()
	{
		if(cjk == null)
			cjk = resolve(400);

		return cjk;
	}

	private static final String[] CJK_FAMILIES = {"Microsoft YaHei UI",
		"Microsoft YaHei", "PingFang SC", "Noto Sans CJK SC",
		"Source Han Sans SC", "WenQuanYi Micro Hei", "SimHei", "SimSun"};

	/**
	 * 按字重要系统中文面；该字重没有就退到 400，再不行退到 Skia 默认字面。
	 * 逐级回退而不是抛异常：这三层都可能落空，而落空只该让字形变差。
	 */
	private static Typeface resolve(int weight)
	{
		Typeface typeface = matchSystemCjk(weight);

		if(typeface == null && weight != 400)
			typeface = matchSystemCjk(400);

		if(typeface == null)
			typeface = FontMgr.Companion.getDefault()
				.matchFamilyStyle(null, new FontStyle(weight));

		if(typeface == null)
			typeface = FontMgr.Companion.getDefault()
				.matchFamilyStyle(null, new FontStyle(400));

		return typeface;
	}

	private static Typeface matchSystemCjk(int weight)
	{
		FontMgr fontMgr = FontMgr.Companion.getDefault();

		for(String family : CJK_FAMILIES)
		{
			Typeface typeface =
				fontMgr.matchFamilyStyle(family, new FontStyle(weight));

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
