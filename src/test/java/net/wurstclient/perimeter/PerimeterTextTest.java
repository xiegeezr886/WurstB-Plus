/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.perimeter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

/**
 * Mirrors the reference mod's translation consistency test: every message has
 * to exist in English and in Simplified Chinese, and the state names have to
 * cover every state.
 */
final class PerimeterTextTest
{
	private static final Locale ENGLISH = Locale.US;
	private static final Locale CHINESE = Locale.SIMPLIFIED_CHINESE;
	
	@Test
	void everyMessageExistsInBothLanguages()
	{
		assertFalse(PerimeterText.keys().isEmpty());
		
		for(String key : PerimeterText.keys())
		{
			assertTrue(PerimeterText.has(key), "missing key: " + key);
			
			String english = PerimeterText.get(ENGLISH, key);
			String chinese = PerimeterText.get(CHINESE, key);
			
			assertFalse(english.isBlank(), "blank English: " + key);
			assertFalse(chinese.isBlank(), "blank Chinese: " + key);
		}
	}
	
	@Test
	void stateNamesAreNotLeftInEnglishForChineseClients()
	{
		for(String key : PerimeterText.keys())
			if(key.startsWith("perimeterdigger.state."))
				assertNotEquals(PerimeterText.get(ENGLISH, key),
					PerimeterText.get(CHINESE, key),
					"state name not translated: " + key);
	}
	
	@Test
	void everyStateHasATranslatedName()
	{
		for(PerimeterAutomationState state : PerimeterAutomationState
			.values())
		{
			String key = "perimeterdigger.state." + state.id();
			assertTrue(PerimeterText.has(key), "missing state name: " + key);
			
			String english = PerimeterText.get(ENGLISH, key);
			String chinese = PerimeterText.get(CHINESE, key);
			
			assertFalse(english.isBlank());
			assertFalse(chinese.isBlank());
			assertNotEquals(english, chinese,
				"state name not translated: " + key);
		}
	}
	
	@Test
	void chineseIsUsedOnlyForChineseLocales()
	{
		assertTrue(PerimeterText.isChinese(CHINESE));
		assertTrue(PerimeterText.isChinese(Locale.CHINA));
		assertTrue(PerimeterText.isChinese(new Locale("zh", "TW")));
		assertFalse(PerimeterText.isChinese(ENGLISH));
		assertFalse(PerimeterText.isChinese(Locale.GERMANY));
		assertFalse(PerimeterText.isChinese(null));
	}
	
	@Test
	void argumentsAreSubstituted()
	{
		assertEquals("mining batch of 64 blocks",
			PerimeterText.get(ENGLISH, "mining batch of %d blocks", 64));
		assertEquals("本批次挖掘 64 个方块",
			PerimeterText.get(CHINESE, "mining batch of %d blocks", 64));
		assertEquals("travelling to unloading point main",
			PerimeterText.get(ENGLISH, "travelling to unloading point %s",
				"main"));
	}
	
	@Test
	void unknownKeysFallBackToTheKeyItself()
	{
		assertEquals("perimeterdigger.does.not.exist",
			PerimeterText.get(ENGLISH, "perimeterdigger.does.not.exist"));
		assertFalse(PerimeterText.has("perimeterdigger.does.not.exist"));
	}
	
	@Test
	void commandMessagesAreCovered()
	{
		for(String key : List.of("perimeterdigger.command.started",
			"perimeterdigger.command.stopped",
			"perimeterdigger.command.paused",
			"perimeterdigger.command.resumed",
			"perimeterdigger.command.not_running",
			"perimeterdigger.command.problems",
			"perimeterdigger.command.saved",
			"perimeterdigger.command.loaded",
			"perimeterdigger.command.unknown_value",
			"perimeterdigger.command.set",
			"perimeterdigger.command.added",
			"perimeterdigger.command.removed",
			"perimeterdigger.command.unknown_option",
			"perimeterdigger.command.baritone_missing"))
			assertTrue(PerimeterText.has(key), "missing command text: " + key);
	}
}
