package net.wurstclient.render.skia;

import java.io.IOException;
import java.io.InputStream;

import org.jetbrains.skia.Data;
import org.jetbrains.skia.FontMgr;
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
