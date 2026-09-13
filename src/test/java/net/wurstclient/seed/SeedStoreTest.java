/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.seed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Everything here works on a store created with
 * {@link SeedStore#atPath(Path, String)}, so the real
 * {@code config/seed-predictor/seeds.json} is never touched.
 */
final class SeedStoreTest
{
	@TempDir
	Path tempDir;
	
	@Test
	void numericTextIsParsedAsALong()
	{
		assertEquals(123L, SeedStore.parseSeed("123"));
		assertEquals(-5L, SeedStore.parseSeed("-5"));
		assertEquals(0L, SeedStore.parseSeed("0"));
		assertEquals(Long.MIN_VALUE, SeedStore.parseSeed("-9223372036854775808"));
	}
	
	@Test
	void nonNumericTextUsesTheJavaStringHash()
	{
		assertEquals((long)"WurstB".strip().hashCode(),
			SeedStore.parseSeed("WurstB"));
		assertEquals((long)"WurstB".hashCode(),
			SeedStore.parseSeed("WurstB"));
		assertEquals((long)"16".strip().hashCode(),
			SeedStore.parseSeed(" 16 "));
	}
	
	@Test
	void describeIsReadable()
	{
		assertTrue(SeedStore.describe(123L).contains("123"));
		assertTrue(SeedStore.describe(-5L).contains("-5"));
		assertFalse(SeedStore.describe(123L).isBlank());
		assertEquals(SeedStore.describe(123L), SeedStore.describe(123L));
	}
	
	@Test
	void entriesRoundTripThroughTheFile()
	{
		Path file = tempDir.resolve("seeds.json");
		SeedStore store = SeedStore.atPath(file, "server:example.org");
		
		assertEquals(file, store.path());
		assertEquals("server:example.org", store.identity());
		assertTrue(store.load());
		assertNull(store.stored());
		assertTrue(store.all().isEmpty());
		
		store.set(1234L, "1.20.1");
		
		assertTrue(Files.isRegularFile(file));
		assertNotNull(store.stored());
		assertEquals(1234L, store.stored().seed);
		assertEquals("1.20.1", store.stored().version);
		assertEquals(1, store.all().size());
		
		SeedStore reloaded = SeedStore.atPath(file, "server:example.org");
		assertTrue(reloaded.load());
		assertEquals(1234L, reloaded.stored().seed);
		assertEquals("1.20.1", reloaded.stored().version);
		
		reloaded.setFromText("WurstB", "1.20.1");
		assertEquals((long)"WurstB".strip().hashCode(), reloaded.stored().seed);
	}
	
	@Test
	void currentFallsBackToTheStoredEntryWithoutAServer()
	{
		Path file = tempDir.resolve("seeds.json");
		SeedStore store = SeedStore.atPath(file, "server:example.org");
		
		assertNull(store.current());
		
		store.set(42L, "1.20.1");
		
		assertNotNull(store.current());
		assertEquals(42L, store.current().seed);
	}
	
	@Test
	void differentIdentitiesShareOneFile()
	{
		Path file = tempDir.resolve("seeds.json");
		SeedStore first = SeedStore.atPath(file, "server:one");
		SeedStore second = SeedStore.atPath(file, "singleplayer:D:/saves/world");
		
		first.set(1L, "1.20.1");
		second.set(2L, "1.20.1");
		
		SeedStore reloaded = SeedStore.atPath(file, "server:one");
		assertTrue(reloaded.load());
		assertEquals(2, reloaded.all().size());
		assertEquals(1L, reloaded.all().get("server:one").seed);
		assertEquals(2L, reloaded.all().get("singleplayer:D:/saves/world").seed);
		assertEquals(1L, reloaded.stored().seed);
	}
	
	@Test
	void removeDeletesOnlyTheOwnEntry()
	{
		Path file = tempDir.resolve("seeds.json");
		SeedStore store = SeedStore.atPath(file, "server:example.org");
		store.set(7L, "1.20.1");
		
		SeedStore other = SeedStore.atPath(file, "server:other");
		other.set(8L, "1.20.1");
		
		// one store instance owns the file, a second one has to reload it
		// before it may write back
		assertTrue(store.load());
		assertTrue(store.remove());
		
		SeedStore reloaded = SeedStore.atPath(file, "server:example.org");
		assertTrue(reloaded.load());
		assertNull(reloaded.stored());
		assertFalse(reloaded.remove());
		assertEquals(1, reloaded.all().size());
		assertNotNull(reloaded.all().get("server:other"));
	}
	
	@Test
	void deleteRemovesTheWholeFile()
	{
		Path file = tempDir.resolve("seeds.json");
		SeedStore store = SeedStore.atPath(file, "server:example.org");
		store.set(7L, "1.20.1");
		
		assertTrue(store.delete());
		assertFalse(Files.exists(file));
		assertFalse(store.delete());
		assertTrue(store.all().isEmpty());
	}
	
	@Test
	void corruptFilesAreReportedButDoNotThrow() throws Exception
	{
		Path file = tempDir.resolve("seeds.json");
		SeedStore store = SeedStore.atPath(file, "server:example.org");
		
		Files.writeString(file, "this is not json");
		
		assertFalse(store.load());
		assertNull(store.stored());
	}
}
