/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.wurstclient.background.BackgroundKind;
import net.wurstclient.background.ProjectJson;
import net.wurstclient.background.WallpaperEngineImporter;
import net.wurstclient.background.WallpaperEngineImporter.Candidate;

/**
 * Tests that a Wallpaper Engine library turns into the right import list, and
 * that wallpapers we cannot play are offered as their preview instead of being
 * silently dropped or pretending to play.
 */
final class WallpaperEngineImporterTest
{
	@TempDir
	Path library;

	@Test
	void importsVideoWallpapers() throws Exception
	{
		Path video = wallpaper("111", """
			{"type":"video","file":"wall.mp4","title":"Neon City"}
			""", "wall.mp4");

		List<Candidate> candidates = WallpaperEngineImporter.scan(
			List.of(library));

		assertEquals(1, candidates.size());

		Candidate candidate = candidates.get(0);
		assertTrue(candidate.playable());
		assertEquals(BackgroundKind.VIDEO, candidate.kind());
		assertEquals("Neon City", candidate.title());
		assertEquals(video, candidate.media());
	}

	/**
	 * A scene imports its package now that we can draw one, and takes its
	 * thumbnail from the preview because a {@code .pkg} is not an image.
	 */
	@Test
	void importsScenesWithTheirPreviewAsThumbnail() throws Exception
	{
		Path media = wallpaper("222", """
			{"type":"scene","file":"scene.pkg","title":"Aurora",
			 "preview":"preview.jpg"}
			""", "scene.pkg", "preview.jpg");

		List<Candidate> candidates = WallpaperEngineImporter.scan(
			List.of(library));

		assertEquals(1, candidates.size());

		Candidate candidate = candidates.get(0);
		assertTrue(candidate.playable());
		assertEquals(BackgroundKind.SCENE, candidate.kind());
		assertEquals(media, candidate.media());
		assertTrue(candidate.thumbnail().toString().endsWith("preview.jpg"));
	}

	/** 场景包不在（只剩预览图）时才退回预览。 */
	@Test
	void fallsBackToThePreviewWhenTheScenePackageIsMissing() throws Exception
	{
		wallpaper("223", """
			{"type":"scene","file":"scene.pkg","title":"Gone",
			 "preview":"preview.jpg"}
			""", "preview.jpg");

		List<Candidate> candidates = WallpaperEngineImporter.scan(
			List.of(library));

		assertEquals(1, candidates.size());
		assertFalse(candidates.get(0).playable());
		assertEquals(BackgroundKind.IMAGE, candidates.get(0).kind());
		assertTrue(candidates.get(0).media().toString().endsWith("preview.jpg"));
		assertTrue(candidates.get(0).note().contains("场景"));
	}

	/** webm has no decoder behind it here, so it falls back to the preview. */
	@Test
	void doesNotClaimToPlayWebm() throws Exception
	{
		wallpaper("333", """
			{"type":"video","file":"wall.webm","title":"Webm Wall"}
			""", "wall.webm", "preview.png");

		List<Candidate> candidates = WallpaperEngineImporter.scan(
			List.of(library));

		assertEquals(1, candidates.size());
		assertFalse(candidates.get(0).playable());
		assertEquals(BackgroundKind.IMAGE, candidates.get(0).kind());
	}

	/** Playable wallpapers come first, so the useful ones are on top. */
	@Test
	void sortsPlayableFirst() throws Exception
	{
		wallpaper("aaa", """
			{"type":"web","file":"index.html","title":"Zzz Web",
			 "preview":"preview.jpg"}
			""", "preview.jpg");
		wallpaper("bbb", """
			{"type":"video","file":"w.mp4","title":"Aaa Video"}
			""", "w.mp4");

		List<Candidate> candidates = WallpaperEngineImporter.scan(
			List.of(library));

		assertEquals(2, candidates.size());
		assertTrue(candidates.get(0).playable());
		assertEquals("Aaa Video", candidates.get(0).title());
		assertFalse(candidates.get(1).playable());
	}

	@Test
	void skipsFoldersWithoutAProject() throws Exception
	{
		Files.createDirectories(library.resolve("444"));
		Files.writeString(library.resolve("not-a-folder.txt"), "x");

		assertTrue(WallpaperEngineImporter.scan(List.of(library)).isEmpty());
		assertTrue(WallpaperEngineImporter.scan(List.of()).isEmpty());
		assertTrue(WallpaperEngineImporter
			.scan(List.of(library.resolve("missing"))).isEmpty());
	}

	/** A file name that climbs out of the wallpaper folder is refused. */
	@Test
	void refusesPathsOutsideTheWallpaperFolder() throws Exception
	{
		Path outside = library.resolve("secret.png");
		Files.write(outside, new byte[]{1});

		Files.createDirectories(library.resolve("555"));
		Files.writeString(library.resolve("555/project.json"), """
			{"type":"video","file":"../../../secret.png"}
			""");

		assertTrue(WallpaperEngineImporter.scan(List.of(library)).isEmpty());
	}

	@Test
	void parsesProjectJson()
	{
		ProjectJson video =
			ProjectJson.parse("{\"type\":\"video\",\"file\":\"a.mp4\"}");
		assertTrue(video.isPlayableMedia());

		// type is often missing on older projects, so it is inferred
		ProjectJson inferred = ProjectJson.parse("{\"file\":\"a.mp4\"}");
		assertTrue(inferred.isPlayableMedia());

		ProjectJson scene =
			ProjectJson.parse("{\"type\":\"scene\",\"file\":\"a.pkg\"}");
		// 场景包现在能画了，所以它是可播放的
		assertTrue(scene.isPlayableMedia());

		// 场景包不在时才退回预览，理由也要说得对
		ProjectJson missingScene =
			ProjectJson.parse("{\"type\":\"scene\",\"file\":\"a.pkg\"}");
		assertTrue(missingScene.unplayableReason().contains("场景"));

		// web 仍然只能看预览
		ProjectJson web =
			ProjectJson.parse("{\"type\":\"web\",\"file\":\"a.html\"}");
		assertFalse(web.isPlayableMedia());

		assertTrue(ProjectJson.parse("not json") == null);
		assertTrue(ProjectJson.parse(null) == null);
	}

	/**
	 * 回归：目录式场景工程（{@code "file": "scene.json"}，那个文件并不存在）必须
	 * 按同目录的 {@code scene.pkg} 导入成场景，而不是退回预览图。
	 *
	 * <p>
	 * 这不是边角情况：用户 Steam 工坊里 **83 个场景壁纸全部**是这个形态，认不出
	 * {@code scene.pkg} 的话它们会整批变成静态预览图——实机表现就是「场景壁纸除了
	 * 内置的 Persica 都跑不起来」。
	 * </p>
	 */
	@Test
	void importsADirectoryStyleSceneAsItsPackage() throws Exception
	{
		wallpaper("777", """
			{"type":"scene","file":"scene.json","title":"A Directory Scene"}
			""", "scene.pkg");

		Candidate candidate = WallpaperEngineImporter.scan(List.of(library))
			.stream().filter(c -> "A Directory Scene".equals(c.title()))
			.findFirst().orElseThrow();

		assertTrue(candidate.playable(), "目录里有 scene.pkg 就该当成场景导入");
		assertEquals(BackgroundKind.SCENE, candidate.kind());
		assertEquals("scene.pkg", candidate.media().getFileName().toString());
	}

	/** Creates a wallpaper folder and the files it references. */
	private Path wallpaper(String id, String projectJson, String... files)
		throws Exception
	{
		Path folder = library.resolve(id);
		Files.createDirectories(folder);
		Files.writeString(folder.resolve("project.json"), projectJson);

		for(String file : files)
			Files.write(folder.resolve(file), new byte[]{1, 2, 3});

		return folder.resolve(files[0]);
	}
}
