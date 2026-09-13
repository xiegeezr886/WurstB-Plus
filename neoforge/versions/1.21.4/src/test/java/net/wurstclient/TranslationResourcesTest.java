/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import org.junit.jupiter.api.Test;

final class TranslationResourcesTest
{
	private static final List<String> TRANSLATION_FILES = List.of("cs_cz",
		"de_de", "en_us", "fr_fr", "it_it", "ja_jp", "ko_kr", "pl_pl",
		"ro_ro", "ru_ru", "tr_tr", "uk_ua", "zh_cn", "zh_cn_names",
		"zh_hk", "zh_tw");

	@Test
	void allTranslationFilesContainValidJson()
	{
		for(String language : TRANSLATION_FILES)
		{
			String resourcePath =
				"/assets/wurst/translations/" + language + ".json";
			try(InputStream stream = getClass().getResourceAsStream(resourcePath))
			{
				assertNotNull(stream, resourcePath);
				assertDoesNotThrow(() -> JsonParser
					.parseReader(new InputStreamReader(stream, UTF_8))
					.getAsJsonObject(), resourcePath);
			}catch(Exception e)
			{
				throw new AssertionError(resourcePath, e);
			}
		}
	}
}
