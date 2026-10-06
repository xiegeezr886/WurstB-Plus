/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * 精简版 FFmpeg natives（4 个 FFmpeg DLL + zlib1 + libwinpthread-1 + 1 个 JNI
 * shim DLL，共 7 个、7,600,438 字节）的引导器。
 *
 * <p>
 * 打包方式跟 {@code net.wurstclient.render.skia.SkikoNatives} 一致：DLL 不进 jarJar，
 * 而是作为普通资源放在 {@code assets/wurst/ffmpeg/} 下，首次使用时解压到
 * {@code gameDir/ffmpeg/} 再加载。刻意绕开 jarJar，因为 jarJar 会重定位资源路径，
 * 而 DLL 之间是按文件名互相依赖的，路径一乱就加载不起来。
 * </p>
 *
 * <h2>加载顺序</h2>
 * <p>
 * {@code vf_ffmpeg.dll} 依赖另外 4 个 FFmpeg DLL。这里先把依赖按依赖顺序显式加载，
 * 再加载 shim 本身，于是不需要改动 {@code java.library.path}，也不依赖工作目录。
 * </p>
 *
 * <h2>给命令行测试用的后门</h2>
 * <p>
 * 设置系统属性 {@code -Dwurst.ffmpeg.dir=<含 7 个 DLL 的目录>} 可以跳过解压，
 * 直接从该目录加载。{@link FfmpegVideoDecoderTest} 与单测就是这么在没有 Minecraft
 * 的情况下跑起来的（Gradle 的 test 任务把它指向 {@code native/ffmpeg/dll}）。
 * </p>
 */
final class FfmpegNatives
{
	/**
	 * JNI 侧 {@code frame_to_rgba} 会往 buffer 尾部写
	 * {@code AV_INPUT_BUFFER_PADDING_SIZE} 字节的余量：{@code nextFrame} 走的是
	 * {@code GetPrimitiveArrayCritical}，拿到的可能就是 Java 数组本体，所以
	 * libswscale 的 SIMD 越界读/写绝不能越过数组末尾。Java 侧分配缓冲时必须带上这段余量。
	 */
	static final int PADDING = 64;

	/**
	 * Load order is dependency order, and it is NOT optional.
	 *
	 * Windows only resolves a DLL's own imports from the process search path
	 * (application directory, System32, PATH) - not from the directory the DLL
	 * itself sits in. So loading {@code avutil-59.dll} by absolute path still
	 * fails with "Can't find dependent libraries" unless its own dependency
	 * {@code libwinpthread-1.dll} is already in the process, and
	 * {@code avformat-61.dll} likewise needs {@code zlib1.dll}. Pre-loading
	 * both first is what makes the absolute-path approach work without having
	 * to touch {@code java.library.path} or the working directory.
	 */
	private static final String[] DLLS = {"libwinpthread-1.dll", "zlib1.dll",
		"avutil-59.dll", "swscale-8.dll", "avcodec-61.dll", "avformat-61.dll",
		"vf_ffmpeg.dll"};

	/** 资源目录（相对 assets/wurst/）。 */
	private static final String RESOURCE_DIR = "ffmpeg";

	private static Boolean ready;
	private static String failure = "";

	private FfmpegNatives()
	{
	}

	/**
	 * 确保 natives 已就绪。失败返回 {@code false} 并把原因记进
	 * {@link FfmpegVideoDecoder#lastLibraryError()}，不抛异常——调用方多半要静默退回
	 * 别的解码路径。
	 */
	static synchronized boolean ensure()
	{
		if(ready != null)
			return ready;

		try
		{
			File dir = resolveDirectory();
			for(String name : DLLS)
			{
				File dll = new File(dir, name);
				if(!dll.isFile())
					throw new IOException("missing native: " + dll);

				// 绝对路径加载单个文件，而不是 loadLibrary：这样既不用改
				// java.library.path，也不受当前工作目录影响。
				System.load(dll.getAbsolutePath());
			}

			ready = Boolean.TRUE;
			failure = "";
			return true;
		}catch(Throwable t)
		{
			ready = Boolean.FALSE;
			failure = t.toString();
			System.err.println("[ffmpeg] failed to load natives: " + failure);
			return false;
		}
	}

	private static File resolveDirectory() throws IOException
	{
		// 1) 显式指定的目录：命令行测试与排障用。
		String explicit = System.getProperty("wurst.ffmpeg.dir");
		if(explicit != null && !explicit.isEmpty())
		{
			File dir = new File(explicit);
			if(!dir.isDirectory())
				throw new IOException(
					"wurst.ffmpeg.dir is not a directory: " + explicit);
			return dir;
		}

		// 2) 从 mod 资源解压到 gameDir/ffmpeg/。
		File dir = new File(gameDirectory(), RESOURCE_DIR);
		if(!dir.isDirectory() && !dir.mkdirs())
			throw new IOException("Cannot create " + dir);

		for(String name : DLLS)
			extract(name, new File(dir, name));

		return dir;
	}

	private static File gameDirectory()
	{
		Minecraft mc = Minecraft.getInstance();
		if(mc != null && mc.gameDirectory != null)
			return mc.gameDirectory;

		// 理论上到不了这里；真到了就用进程工作目录兜底，总比 NPE 好。
		return new File(System.getProperty("user.dir", "."));
	}

	private static void extract(String resourcePath, File target)
		throws IOException
	{
		// 资源命名空间固定写 "wurst"（assets/wurst/...），跟 SkikoNatives 一致。
		// MC 1.20.1 的 ResourceLocation 是两参构造，没有 fromNamespaceAndPath。
		ResourceLocation location = new ResourceLocation("wurst",
			RESOURCE_DIR + "/" + resourcePath);

		try(InputStream in = Minecraft.getInstance().getResourceManager()
			.open(location))
		{
			// 先写临时文件再改名：避免上次运行留下的半个 DLL 被 System.load 到。
			Path tmp = target.toPath().resolveSibling(target.getName() + ".tmp");
			Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
			Files.move(tmp, target.toPath(),
				StandardCopyOption.REPLACE_EXISTING);
		}
	}

	/** 供 {@link FfmpegVideoDecoder#lastLibraryError()} 读取。 */
	static String failure()
	{
		return failure;
	}

	/** 要加载的 DLL 名字，按加载顺序（单测按它核对发布的资源与顺序）。 */
	static List<String> loadedNames()
	{
		return new ArrayList<>(List.of(DLLS));
	}
}
