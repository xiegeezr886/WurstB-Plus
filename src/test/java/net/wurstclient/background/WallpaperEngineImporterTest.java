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
	 * A scene wallpaper needs Wallpaper Engine's runtime, so we take its preview
	 * and say why.
	 */
	@Test
	void fallsBackToThePreviewForScenes() throws Exception
	{
		wallpaper("222", """
			{"type":"scene","file":"scene.pkg","title":"Aurora",
			 "preview":"preview.jpg"}
			""", "preview.jpg");

		List<Candidate> candidates = WallpaperEngineImporter.scan(
			List.of(library));

		assertEquals(1, candidates.size());

		Candidate candidate = candidates.get(0);
		assertFalse(candidate.playable());
		assertEquals(BackgroundKind.IMAGE, candidate.kind());
		assertTrue(candidate.media().toString().endsWith("preview.jpg"));
		assertTrue(candidate.note().contains("Wallpaper Engine"));
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
			{"type":"scene","file":"s.pkg","title":"Zzz Scene",
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
		assertFalse(scene.isPlayableMedia());
		assertTrue(scene.unplayableReason().contains("场景"));

		assertTrue(ProjectJson.parse("not json") == null);
		assertTrue(ProjectJson.parse(null) == null);
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
