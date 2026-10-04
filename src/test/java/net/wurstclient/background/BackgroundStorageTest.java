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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.wurstclient.background.BackgroundEntry;
import net.wurstclient.background.BackgroundKind;
import net.wurstclient.background.BackgroundStorage;

/**
 * Tests the background library: id safety, the import round trip, and that a
 * broken entry degrades to null instead of throwing.
 */
final class BackgroundStorageTest
{
	@TempDir
	Path tempDir;

	@Test
	void rejectsUnsafeIds()
	{
		assertFalse(BackgroundStorage.isValidId(null));
		assertFalse(BackgroundStorage.isValidId(""));
		assertFalse(BackgroundStorage.isValidId(".."));
		assertFalse(BackgroundStorage.isValidId("."));
		assertFalse(BackgroundStorage.isValidId("a/b"));
		assertFalse(BackgroundStorage.isValidId("a\\b"));
		assertFalse(BackgroundStorage.isValidId("C:evil"));
		assertFalse(BackgroundStorage.isValidId("a.b"));
		assertFalse(BackgroundStorage.isValidId("a*b"));
		// the built-in default is virtual, never a folder
		assertFalse(BackgroundStorage.isValidId("default"));

		assertTrue(BackgroundStorage.isValidId("my-wallpaper"));
		assertTrue(BackgroundStorage.isValidId("winter_2024"));
		assertTrue(BackgroundStorage.isValidId("a"));
		// a Chinese title still gives a usable folder name
		assertTrue(BackgroundStorage.isValidId("星空"));
	}

	@Test
	void slugifiesDisplayNames()
	{
		assertEquals("my-wallpaper", BackgroundStorage.slugify("My Wallpaper!"));
		assertEquals("a-b-c", BackgroundStorage.slugify("  a  b  c  "));
		assertEquals("winter", BackgroundStorage.slugify("///winter///"));
		assertEquals("background", BackgroundStorage.slugify(""));
		assertEquals("background", BackgroundStorage.slugify(null));
		assertEquals("星空", BackgroundStorage.slugify("星空"));
		assertTrue(BackgroundStorage.slugify("x".repeat(200)).length() <= 48);
	}

	@Test
	void importsReadsListsAndDeletes() throws Exception
	{
		Path source = tempDir.resolve("holiday.png");
		Files.write(source, new byte[]{1, 2, 3, 4});

		BackgroundStorage storage =
			new BackgroundStorage(tempDir.resolve("library"));
		String id = storage.importFile(source, BackgroundKind.IMAGE, "Holiday",
			"holiday.png", new byte[]{9, 9});

		assertTrue(BackgroundStorage.isValidId(id));

		BackgroundEntry entry = storage.read(id);
		assertTrue(entry != null);
		assertEquals("Holiday", entry.title());
		assertEquals("holiday.png", entry.origin());
		assertEquals(BackgroundKind.IMAGE, entry.kind());

		// the media file kept its extension
		Path media = storage.mediaPath(id);
		assertTrue(media != null && media.toString().endsWith(".png"));
		assertTrue(Files.isRegularFile(storage.thumbnailPath(id)));

		assertEquals(1, storage.list().size());
		assertTrue(storage.list().get(0).importedAtMs() > 0);

		assertTrue(storage.delete(id));
		assertEquals(0, storage.list().size());
		assertTrue(storage.read(id) == null);
	}

	/** Two wallpapers with the same title must not overwrite each other. */
	@Test
	void deduplicatesIds() throws Exception
	{
		Path source = tempDir.resolve("a.png");
		Files.write(source, new byte[]{1});

		BackgroundStorage storage =
			new BackgroundStorage(tempDir.resolve("library"));
		String first = storage.importFile(source, BackgroundKind.IMAGE, "Same",
			null, null);
		String second = storage.importFile(source, BackgroundKind.IMAGE, "Same",
			null, null);

		assertFalse(first.equals(second));
		assertEquals(2, storage.list().size());
	}

	/** A half-copied entry must disappear from the list, not blow up. */
	@Test
	void dropsEntriesWhoseMediaIsMissing() throws Exception
	{
		Path source = tempDir.resolve("a.png");
		Files.write(source, new byte[]{1});

		BackgroundStorage storage =
			new BackgroundStorage(tempDir.resolve("library"));
		String id = storage.importFile(source, BackgroundKind.IMAGE, "Gone",
			null, null);
		Files.delete(storage.mediaPath(id));

		assertTrue(storage.read(id) == null);
		assertTrue(storage.list().isEmpty());
	}

	@Test
	void readsGifAndVideoExtensions() throws Exception
	{
		assertEquals(BackgroundKind.GIF, BackgroundKind.fromFileName("a.GIF"));
		assertEquals(BackgroundKind.VIDEO, BackgroundKind.fromFileName("a.mp4"));
		assertEquals(BackgroundKind.IMAGE,
			BackgroundKind.fromFileName("a.JPG"));
		// webm has no decoder behind it, so it is not a kind we accept
		assertTrue(BackgroundKind.fromFileName("a.webm") == null);
		assertTrue(BackgroundKind.fromFileName("a.txt") == null);
		assertTrue(BackgroundKind.fromFileName(null) == null);

		Path source = tempDir.resolve("loop.gif");
		Files.write(source, "GIF89a".getBytes(StandardCharsets.US_ASCII));

		BackgroundStorage storage =
			new BackgroundStorage(tempDir.resolve("library"));
		String id = storage.importFile(source, BackgroundKind.GIF, "Loop", null,
			null);

		assertEquals(BackgroundKind.GIF, storage.read(id).kind());
		assertTrue(storage.mediaPath(id).toString().endsWith(".gif"));

		List<BackgroundEntry> entries = storage.list();
		assertEquals(1, entries.size());
	}
}
