/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

import net.wurstclient.background.WallpaperEngineImporter.Candidate;

/**
 * 「导入成预览图的场景」自动修复的**判定**部分（{@link WallpaperEngineRepair#plan}）。
 *
 * <p>
 * 只测纯函数：它不碰磁盘，所以用例是内存里的条目与候选。真正动文件的 {@code apply}
 * 不在单测里跑 —— 那是"往用户库里写 30MB"的操作，错了后果比少一条用例严重。
 * </p>
 */
final class WallpaperEngineRepairTest
{
	private static final String TITLE = "Where Dream Has Been";

	/** 预览动图条目 + 同标题的可播放场景候选 → 应该被选中修复。 */
	@Test
	void upgradesPreviewEntryToScene()
	{
		Map<String, Candidate> plan = WallpaperEngineRepair.plan(
			List.of(entry("dream", BackgroundKind.IMAGE, "media.gif", TITLE)),
			List.of(scene(TITLE)));

		assertEquals(1, plan.size());
		assertTrue(plan.containsKey("dream"));
		assertEquals(BackgroundKind.SCENE, plan.get("dream").kind());
	}

	/** 视频条目的真身本来就是视频，不该被"修"成场景。 */
	@Test
	void leavesVideoEntriesAlone()
	{
		Map<String, Candidate> plan = WallpaperEngineRepair.plan(
			List.of(entry("vid", BackgroundKind.VIDEO, "media.mp4", TITLE)),
			List.of(scene(TITLE)));

		assertTrue(plan.isEmpty());
	}

	/** 候选自己都不可播放（还是预览图）时，别拿它去替换。 */
	@Test
	void ignoresUnplayableCandidate()
	{
		Candidate preview = new Candidate(Path.of("preview.gif"),
			BackgroundKind.IMAGE, TITLE, "需要 Wallpaper Engine 运行时",
			Path.of("preview.gif"));

		Map<String, Candidate> plan = WallpaperEngineRepair.plan(
			List.of(entry("dream", BackgroundKind.IMAGE, "media.gif", TITLE)),
			List.of(preview));

		assertTrue(plan.isEmpty());
	}

	/** 不是从 Wallpaper Engine 导进来的（本地文件），标题撞上也不动。 */
	@Test
	void ignoresOtherOrigins()
	{
		BackgroundEntry local = new BackgroundEntry("mine", BackgroundKind.IMAGE,
			TITLE, "本地文件", 1L);

		assertTrue(
			WallpaperEngineRepair.plan(List.of(local), List.of(scene(TITLE)))
				.isEmpty());
	}

	/** 内置默认背景不在库里，不能被匹配到。 */
	@Test
	void ignoresDefaultEntry()
	{
		assertTrue(WallpaperEngineRepair
			.plan(List.of(BackgroundEntry.DEFAULT), List.of(scene("默认背景")))
			.isEmpty());
	}

	/** 标题对不上就不动 —— 匹配靠标题全等，不做模糊。 */
	@Test
	void ignoresDifferentTitle()
	{
		assertTrue(WallpaperEngineRepair
			.plan(List.of(entry("dream", BackgroundKind.IMAGE, "media.gif", TITLE)),
				List.of(scene("别的作品")))
			.isEmpty());
	}

	/** 写出来的 meta.json 必须能被读回成 SCENE（字段名与 Storage 一致）。 */
	@Test
	void metaRoundTripsAsScene()
	{
		String meta = WallpaperEngineRepair.meta(TITLE, "media.pkg");
		var json = JsonParser.parseString(meta).getAsJsonObject();

		assertEquals("SCENE", json.get("kind").getAsString());
		assertEquals("media.pkg", json.get("file").getAsString());
		assertEquals(TITLE, json.get("title").getAsString());
		assertFalse(json.get("origin").isJsonNull());
	}

	private static BackgroundEntry entry(String id, BackgroundKind kind,
		String file, String title)
	{
		return new BackgroundEntry(id, kind, title, "Wallpaper Engine", 1L);
	}

	private static Candidate scene(String title)
	{
		return new Candidate(Path.of(title + "/scene.pkg"),
			BackgroundKind.SCENE, title, null, null);
	}
}
