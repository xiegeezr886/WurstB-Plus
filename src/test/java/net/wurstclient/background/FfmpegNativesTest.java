/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * natives 要随模组一起发布：7 个 DLL 放在
 * {@code src/main/resources/assets/wurst/ffmpeg/} 下，运行时由 {@link FfmpegNatives}
 * 解压到 {@code gameDir/ffmpeg/} 再加载。
 *
 * <p>
 * 「真的解压并加载」这一步需要 Minecraft 的资源管理器，这里跑不了（单测没有客户端）；
 * 这里能验的是更早、也更容易忘的一步：<b>那 7 个文件真的在资源目录里</b>。少了任何
 * 一个，游戏里都会退化成"视频背景放不了"，而且只在真正用到时才发现。
 * </p>
 *
 * <p>
 * 加载顺序也在验：Windows 只从进程搜索路径解析 DLL 自己的导入，所以
 * {@code libwinpthread-1} 必须排在 {@code avutil-59} 之前、{@code zlib1} 必须排在
 * {@code avformat-61} 之前（见 {@link FfmpegNatives} 里的说明与
 * native/ffmpeg/README.md 第 4 节）。
 * </p>
 */
final class FfmpegNativesTest
{
	/** native/ffmpeg/README.md 里记的那 7 个文件加起来的大小。 */
	private static final long DLL_TOTAL = 7_605_466L;

	private static final Path RESOURCES =
		Path.of("src", "main", "resources", "assets", "wurst", "ffmpeg");

	@Test
	void everyNativeIsInTheModResources()
	{
		List<String> names = FfmpegNatives.loadedNames();

		assertEquals(7, names.size(), "要发布的 DLL 个数（不含 vf_ffmpeg_test.exe）");

		long total = 0;

		for(String name : names)
		{
			Path file = RESOURCES.resolve(name);

			assertTrue(Files.isRegularFile(file),
				"资源目录里少了 " + file + "（游戏里会退化成「视频背景放不了」）");

			try
			{
				total += Files.size(file);

			}catch(java.io.IOException e)
			{
				throw new AssertionError(e);
			}
		}

		// 重新构建过 natives 的话，这个数字与 native/ffmpeg/README.md 要一起改
		assertEquals(DLL_TOTAL, total, "DLL 总大小（见 native/ffmpeg/README.md）");
	}

	/** 构建产物里那个命令行工具不发布：它只有开发时用得上。 */
	@Test
	void theTestHarnessDoesNotShip()
	{
		assertTrue(Files.notExists(RESOURCES.resolve("vf_ffmpeg_test.exe")),
			"vf_ffmpeg_test.exe 不该进模组资源");
	}

	@Test
	void loadOrderPutsTheWindowsRuntimesFirst()
	{
		List<String> names = FfmpegNatives.loadedNames();

		int pthread = names.indexOf("libwinpthread-1.dll");
		int zlib = names.indexOf("zlib1.dll");
		int avutil = names.indexOf("avutil-59.dll");
		int avformat = names.indexOf("avformat-61.dll");
		int shim = names.indexOf("vf_ffmpeg.dll");

		assertTrue(pthread >= 0 && pthread < avutil,
			"libwinpthread-1.dll 必须在 avutil-59.dll 之前：" + names);
		assertTrue(zlib >= 0 && zlib < avformat,
			"zlib1.dll 必须在 avformat-61.dll 之前：" + names);
		assertEquals(names.size() - 1, shim,
			"shim 自己最后加载（它依赖另外六个）：" + names);
	}

	/**
	 * LGPL 要求随分发附带许可文本：它们和 DLL 放在一起，进同一个 jar。
	 *
	 * <p>
	 * 名字是**小写重命名**过的：Minecraft 的资源路径只允许 {@code [a-z0-9_.-/]}，
	 * 而原始文件名 `COPYING.LGPLv2.1` / `LICENSE.md` 带大写字母——放在
	 * {@code assets/} 下会被 {@code ResourceLocation.tryBuild} 判为非法路径
	 * （开发环境里那句日志还会直接抛异常）。内容与 {@code native/ffmpeg/} 下的
	 * 原始文件逐字节相同。
	 * </p>
	 */
	@Test
	void licenceTextsShipWithTheDlls()
	{
		for(String name : new String[]{"copying-lgplv2.1.txt",
			"license-ffmpeg.txt"})
		{
			Path file = RESOURCES.resolve(name);

			assertTrue(Files.isRegularFile(file), "少了对 LGPL 的许可文本：" + file);
		}
	}

	/** 资源目录里每个文件名都必须是合法的 MC 资源路径（只允许小写）。 */
	@Test
	void resourceNamesAreValidResourcePaths() throws java.io.IOException
	{
		try(java.util.stream.Stream<Path> files = Files.list(RESOURCES))
		{
			for(Path file : files.toList())
			{
				String name = file.getFileName().toString();

				assertTrue(name.equals(name.toLowerCase(java.util.Locale.ROOT)),
					"资源目录里的文件名必须全小写，否则 Minecraft 会当成非法路径："
						+ name);
			}
		}
	}
}
