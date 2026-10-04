/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.Test;

import net.wurstclient.background.VdfParser.VdfParseException;

/**
 * Tests the KeyValues reader against the shapes Steam actually writes.
 */
final class VdfParserTest
{
	/** The modern nested form: numbered objects that each hold a path. */
	@Test
	void readsNestedLibraries() throws VdfParseException
	{
		String document = """
			"libraryfolders"
			{
				"0"
				{
					"path"		"C:\\\\Steam"
					"label"		""
					"apps"
					{
						"431960"		"123456"
					}
				}
				"1"
				{
					"path"		"D:\\\\Games\\\\SteamLibrary"
				}
			}
			""";

		Map<String, Object> folders = VdfParser.parse(document);

		assertEquals(2, folders.size());

		@SuppressWarnings("unchecked")
		Map<String, Object> first = (Map<String, Object>)folders.get("0");
		assertEquals("C:\\Steam", first.get("path"));
		assertEquals("", first.get("label"));
	}

	/** The legacy flat form, which only lists paths under numeric keys. */
	@Test
	void readsFlatLibraries() throws VdfParseException
	{
		String document = """
			"LibraryFolders"
			{
				"TimeNextStatsReport"		"1234567890"
				"1"		"D:\\\\SteamLibrary"
				"2"		"E:\\\\SteamLibrary"
			}
			""";

		Map<String, Object> folders = VdfParser.parse(document);

		assertEquals("D:\\SteamLibrary", folders.get("1"));
		assertEquals("E:\\SteamLibrary", folders.get("2"));
	}

	/** Comments and whitespace must not become entries. */
	@Test
	void skipsComments() throws VdfParseException
	{
		String document = """
			// a leading comment
			"root"
			{
				"path"		"C:\\\\Steam"	// trailing comment
			}
			""";

		Map<String, Object> root = VdfParser.parse(document);

		assertEquals(1, root.size());
		assertEquals("C:\\Steam", root.get("path"));
	}

	@Test
	void rejectsMalformedDocuments()
	{
		assertThrows(VdfParseException.class, () -> VdfParser.parse(""));
		assertThrows(VdfParseException.class, () -> VdfParser.parse(null));
		assertThrows(VdfParseException.class,
			() -> VdfParser.parse("\"root\" { \"key\""));
		assertThrows(VdfParseException.class,
			() -> VdfParser.parse("\"root\" \"value\""));
	}
}
