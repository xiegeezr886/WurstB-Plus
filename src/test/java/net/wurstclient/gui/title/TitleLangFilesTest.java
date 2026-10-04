/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.gui.title;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Tests the language files the title screens use.
 *
 * <p>
 * The screens resolve their text through {@code Component.translatable}, so a
 * key that only exists in one locale silently renders as the raw key in the
 * other one, and a {@code %s} that only exists in one locale prints a literal
 * {@code %s} or drops an argument. Both are checked here.
 */
final class TitleLangFilesTest
{
	private static final String[] LOCALES = {"en_us", "zh_cn"};
	private static final String LANG_DIR = "/assets/wurst/lang/";

	/** 语言文件里的 {@code "键": "值"}。 */
	private static final Pattern ENTRY =
		Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"");

	/** 源码里的翻译键，例如 {@code "wurst.title.quit"}。 */
	private static final Pattern USED_KEY =
		Pattern.compile("\"(wurst\\.[a-z0-9_.]+)\"");

	private static Map<String, String> read(String locale) throws IOException
	{
		String path = LANG_DIR + locale + ".json";

		try(InputStream stream =
			TitleLangFilesTest.class.getResourceAsStream(path))
		{
			assertNotNull(stream, path + " 不在测试 classpath 上");
			Matcher matcher = ENTRY.matcher(
				new String(stream.readAllBytes(), StandardCharsets.UTF_8));
			Map<String, String> entries = new LinkedHashMap<>();

			while(matcher.find())
				entries.put(matcher.group(1), matcher.group(2));

			assertFalse(entries.isEmpty(), path + " 里一个键都没有");
			return entries;
		}
	}

	private static long placeholders(String value)
	{
		return value.chars().filter(c -> c == '%').count();
	}

	@Test
	void everyLocaleDefinesTheSameKeys() throws IOException
	{
		Set<String> reference = read(LOCALES[0]).keySet();

		for(int i = 1; i < LOCALES.length; i++)
		{
			Set<String> keys = read(LOCALES[i]).keySet();

			for(String key : reference)
				assertTrue(keys.contains(key), LOCALES[i] + " 缺键 " + key);

			for(String key : keys)
				assertTrue(reference.contains(key),
					LOCALES[0] + " 缺键 " + key);
		}
	}

	@Test
	void noTranslationIsEmpty() throws IOException
	{
		for(String locale : LOCALES)
			read(locale).forEach((key, value) -> assertFalse(value.isEmpty(),
				locale + " 的 " + key + " 是空串"));
	}

	/**
	 * 占位符数量必须一致：多一个会打印出字面的 {@code %s}，少一个会把参数
	 * 悄悄丢掉。
	 */
	@Test
	void thePlaceholdersMatchAcrossLocales() throws IOException
	{
		Map<String, String> reference = read(LOCALES[0]);

		for(int i = 1; i < LOCALES.length; i++)
		{
			Map<String, String> other = read(LOCALES[i]);

			for(Map.Entry<String, String> entry : reference.entrySet())
				assertEquals(placeholders(entry.getValue()),
					placeholders(other.get(entry.getKey())),
					entry.getKey() + " 的 %s 数量对不上");
		}
	}

	/** 标题界面源码里用到的键，两种语言都得有。 */
	@Test
	void everyKeyUsedByTheTitleScreensExists() throws IOException
	{
		Path source = Path.of("src", "main", "java", "net", "wurstclient",
			"gui", "title");
		Assumptions.assumeTrue(Files.isDirectory(source),
			"只有从工程根目录跑测试时才能扫源码");

		Set<String> used = new LinkedHashSet<>();

		try(DirectoryStream<Path> files =
			Files.newDirectoryStream(source, "*.java"))
		{
			for(Path file : files)
			{
				Matcher matcher = USED_KEY.matcher(Files.readString(file));

				while(matcher.find())
					used.add(matcher.group(1));
			}
		}

		assertFalse(used.isEmpty(), "标题界面里一个翻译键都没有？");

		for(String locale : LOCALES)
		{
			Set<String> keys = read(locale).keySet();

			for(String key : used)
				assertTrue(keys.contains(key), locale + " 缺 " + key);
		}
	}
}
