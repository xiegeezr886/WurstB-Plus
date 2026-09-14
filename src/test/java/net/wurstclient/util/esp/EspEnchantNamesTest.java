package net.wurstclient.util.esp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class EspEnchantNamesTest
{
	@Test
	void stripsTheNamespace()
	{
		assertEquals("Sh", EspEnchantNames.shortNameOf("minecraft:sharpness"));
		assertEquals("Sh", EspEnchantNames.shortNameOf("sharpness"));
	}

	@Test
	void isCaseInsensitive()
	{
		assertEquals("BoA",
			EspEnchantNames.shortNameOf("Minecraft:Bane_Of_Arthropods"));
	}

	@Test
	void unknownEnchantmentsReturnNull()
	{
		assertNull(EspEnchantNames.shortNameOf("density"));
		assertNull(EspEnchantNames.shortNameOf(""));
		assertNull(EspEnchantNames.shortNameOf(null));
		assertNull(EspEnchantNames.shortNameOf("minecraft:"));
	}

	/**
	 * 1.21.10 的 {@code SWEEPING_EDGE} 在 1.20.1 的注册名是 {@code sweeping}；
	 * 这条断言把「按 1.20.1 注册名建表」这个决定钉住。
	 */
	@Test
	void usesTheOneTwentyOneRegistryNameForSweeping()
	{
		assertEquals("Sw", EspEnchantNames.shortNameOf("sweeping"));
		assertNull(EspEnchantNames.shortNameOf("sweeping_edge"));
	}

	@Test
	void coversTheWholeReferenceTable()
	{
		// OpenPal 的 ENCHANTMENT_NAMES 共 39 项，且这 39 项在 1.20.1 全部存在
		// （唯一需要换算的是 SWEEPING_EDGE → sweeping），所以是 1:1 移植。
		assertEquals(39, EspEnchantNames.size());
	}

	@Test
	void everyShortNameIsActuallyShort()
	{
		for(String path : new String[]{"protection", "bane_of_arthropods",
			"vanishing_curse", "quick_charge", "luck_of_the_sea"})
		{
			String shortName = EspEnchantNames.shortNameOf(path);
			assertNotNull(shortName, path);
			assertTrue(shortName.length() <= 3, path + " -> " + shortName);
		}
	}
}
