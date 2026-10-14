/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.system.MemoryUtil;

import com.mojang.blaze3d.font.GlyphProvider;

/**
 * 作者包里的真字体：能不能打开、覆盖了场景要显示的字、以及 {@code .otf} 到底行不行。
 *
 * <p>
 * 合成的字体用例只能证明"我按我理解的格式读"，证明不了"这份字体真的能用"。
 * 实测那三种引用都在**真寻那个包**里（工坊 2989215532，47.8 MB）：
 * {@code fonts/workshop/3759258840/yuanshen.ttf}（9 个文本对象）、
 * {@code fonts/Atami-Regular.otf}（1 个）、{@code systemfont_arial}（7 个）。
 * 这一条测试跑真文件，没装就自己跳过。
 * </p>
 *
 * <p>
 * 这里**只到 provider 为止**（STB 打开 + 字形表），不建 {@code FontSet}/{@code Font}
 * —— 那两个要 TextureManager，属于客户端线程的东西，单测里没有。字号取 40：真寻那批
 * 文本对象的 {@code pointsize} 就是几十的量级。
 * </p>
 */
final class WeSceneFontRealFileTest
{
	/** 真寻：工坊 2989215532。字体三种引用都在这个包里。 */
	private static final String DEFAULT_PACKAGE =
		"C:\\Program Files (x86)\\Steam\\steamapps\\workshop\\content\\431960"
			+ "\\2989215532\\scene.pkg";

	private static final String TTF = "fonts/workshop/3759258840/yuanshen.ttf";
	private static final float POINT_SIZE = 40;

	private static Path packageFile()
	{
		String override = System.getProperty("wurstb.scene.pkg");
		return Path.of(override != null ? override : DEFAULT_PACKAGE);
	}

	@Test
	void thePackagedTrueTypeFontOpensAndCoversTheTextOnScreen() throws IOException
	{
		Path file = packageFile();
		Assumptions.assumeTrue(Files.isRegularFile(file),
			"no Wallpaper Engine scene installed at " + file);

		WePackage pkg = WePackage.read(file);
		byte[] ttf = pkg.read(TTF);
		assertNotNull(ttf, "包里没有 " + TTF);

		WeScene scene = WeScene.parse(
			new String(pkg.read(WeScene.SCENE_JSON), StandardCharsets.UTF_8),
			name -> {
				byte[] bytes = pkg.read(name);
				return bytes == null ? null
					: new String(bytes, StandardCharsets.UTF_8);
			});

		ByteBuffer data = WeSceneFont.toDirectBuffer(ttf);
		GlyphProvider opened = null;

		try
		{
			STBTTFontinfo info = WeSceneFont.openFont(data);
			GlyphProvider provider = WeSceneFont.newProvider(data, info, POINT_SIZE);
			opened = provider;
			int lineHeight = WeSceneFont.lineHeight(info, POINT_SIZE);

			System.out.println("[Probe] " + TTF + " = " + ttf.length
				+ " bytes, em=" + (int)POINT_SIZE + "px, 字体行高=" + lineHeight
				+ "px, 字形数=" + provider.getSupportedGlyphs().size());

			int ttfLayers = 0;
			Set<Integer> missing = new LinkedHashSet<>();

			for(WeScene.TextLayer layer : scene.texts())
			{
				boolean usesTtf = TTF.equalsIgnoreCase(layer.font());

				if(usesTtf)
					ttfLayers++;

				// 场景里每一层真正要画的字符都问一遍：缺字形就会画成小方框
				layer.text().codePoints().forEach(codePoint -> {
					if(usesTtf
						&& !provider.getSupportedGlyphs().contains(codePoint))
						missing.add(codePoint);
				});

				System.out.println("[Probe]   文本层「" + layer.name() + "」 font="
					+ (layer.font() == null || layer.font().isBlank() ? "(未指定)"
						: layer.font())
					+ " pointsize=" + layer.pointSize() + " text="
					+ layer.text().replace("\n", "\\n"));
			}

			System.out.println("[Probe] 用作者字体的文本层 = " + ttfLayers
				+ "，缺字形码点 = "
				+ (missing.isEmpty() ? "无"
					: missing.stream()
						.map(cp -> new String(Character.toChars(cp)))
						.reduce((a, b) -> a + " " + b).orElse("")));

			assertTrue(ttfLayers > 0, "真寻这个包里应该有文本层在用这份 .ttf");
			assertTrue(missing.isEmpty(),
				"作者字体缺这些字形，会被回退到 MC 字体：" + missing);

		}finally
		{
			// provider 拥有这块直接缓冲（close() 里自己 memFree），所以**不能再释放**：
			// 那是 double free，实测让测试 JVM 崩在 jemalloc 里
			if(opened != null)
				opened.close();
			else
				MemoryUtil.memFree(data);
		}
	}

	/**
	 * {@code .otf}（CFF 轮廓）必须在 Java 侧就被挡下。
	 *
	 * <p>
	 * 实测这个包里**没有** {@code .otf} 文件：包内字体条目只有
	 * {@code fonts/workshop/3759258840/yuanshen.ttf} 一个（11,454,944 字节），
	 * 场景引用的 {@code fonts/Atami-Regular.otf} 在包里没有对应文件 —— 那一层无论如何
	 * 都要回退。所以 CFF 这一条用**系统里的真 .otf** 验（系统里没有就跳过）。
	 * </p>
	 *
	 * <p>
	 * 这里**故意不把 .otf 交给 STB**：脏数据喂给 {@code stbtt_InitFont} 会让 JVM
	 * 崩在 {@code lwjgl_stb.dll} 里（见 {@link WeSceneFont#requireTrueType}）。
	 * </p>
	 */
	@Test
	void refusesCffOutlineFonts() throws IOException
	{
		Path file = packageFile();
		Assumptions.assumeTrue(Files.isRegularFile(file),
			"no Wallpaper Engine scene installed at " + file);

		WePackage pkg = WePackage.read(file);
		byte[] ttf = pkg.read(TTF);
		assertNotNull(ttf, "包里没有 " + TTF);

		// 真 TTF 必须通过这道格式闸门
		WeSceneFont.requireTrueType(ttf);

		Path otf = firstSystemOtf();
		Assumptions.assumeTrue(otf != null, "系统里没有 .otf 样本可验 CFF 那条路");

		byte[] cff = Files.readAllBytes(otf);
		IOException failure = assertThrows(IOException.class,
			() -> WeSceneFont.requireTrueType(cff));

		System.out.println("[Probe] " + otf.getFileName() + " = " + cff.length
			+ " bytes，Java 侧拒绝：" + failure.getMessage());
	}

	private static Path firstSystemOtf()
	{
		Path dir = Path.of("C:\\Windows\\Fonts");

		if(!Files.isDirectory(dir))
			return null;

		try(Stream<Path> files = Files.list(dir))
		{
			return files
				.filter(path -> path.getFileName().toString()
					.toLowerCase(java.util.Locale.ROOT).endsWith(".otf"))
				.findFirst().orElse(null);

		}catch(IOException e)
		{
			return null;
		}
	}
}
