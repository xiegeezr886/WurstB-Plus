package net.wurstclient.render.skia;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * Skiko natives 引导器。
 *
 * <p>natives 随 mod 资源打包（assets/wurst/skiko/），避免 jarJar 重定位资源
 * 路径导致 Skiko 无法在嵌套 jar 中定位 DLL。首次使用前把
 * {@code skiko-windows-x64.dll} 与 {@code icudtl.dat} 解压到
 * {@code gameDir/skiko/}，再通过 {@code skiko.library.path} /
 * {@code skiko.data.path} 系统属性显式指定加载位置（Skiko 官方加载机制）。</p>
 */
public final class SkikoNatives
{
	private static volatile boolean ready;
	private static volatile boolean failed;

	public static boolean ensure()
	{
		if(ready)
			return true;
		if(failed)
			return false;
		synchronized(SkikoNatives.class)
		{
			if(ready)
				return true;
			if(failed)
				return false;
			try
			{
				File dir = new File(Minecraft.getInstance().gameDirectory,
					"skiko");
				if(!dir.isDirectory() && !dir.mkdirs())
					throw new IOException("Cannot create " + dir);
				extract("skiko/skiko-windows-x64.dll",
					new File(dir, "skiko-windows-x64.dll"));
				extract("skiko/icudtl.dat", new File(dir, "icudtl.dat"));
				// skiko.library.path 是目录：skiko 内部以
				// File(dir, "skiko-windows-x64.dll") 解析后再 System.load
				System.setProperty("skiko.library.path",
					dir.getAbsolutePath());
				System.setProperty("skiko.data.path",
					dir.getAbsolutePath());
				ready = true;
				return true;
			}catch(IOException e)
			{
				failed = true;
				throw new IllegalStateException(
					"Failed to prepare Skiko natives", e);
			}
		}
	}

	private static File extract(String resourcePath, File target)
		throws IOException
	{
		ResourceLocation location = new ResourceLocation("wurst",
			resourcePath);
		try(InputStream in = Minecraft.getInstance().getResourceManager()
			.open(location))
		{
			Files.copy(in, target.toPath(),
				StandardCopyOption.REPLACE_EXISTING);
		}
		return target;
	}
}
