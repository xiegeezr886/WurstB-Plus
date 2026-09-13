/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.perimeter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Scans the sources that produce user visible text and checks that every
 * message they emit exists in both languages.
 *
 * <p>
 * This is the port of the reference mod's translation consistency test: it
 * fails when a new message is added without a Chinese counterpart, so the two
 * languages cannot drift apart unnoticed.
 */
final class PerimeterSourceTextCoverageTest
{
	private static final Pattern DETAIL = Pattern.compile(
		"(?:transition|synchronize)\\(\\s*[A-Za-z_.]+,\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
	
	private static final Pattern FEEDBACK =
		Pattern.compile("feedback\\(context,\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
	
	private static final Pattern TRANSLATION_KEY =
		Pattern.compile("PerimeterText\\.get\\(\"((?:[^\"\\\\]|\\\\.)*)\"");
	
	private static final List<String> SOURCES = List.of(
		"src/main/java/net/wurstclient/perimeter/PerimeterAutomation.java",
		"src/main/java/net/wurstclient/commands/PerimeterDigCommand.java",
		"src/main/java/net/wurstclient/hacks/PerimeterDiggerHack.java",
		"src/main/java/net/wurstclient/commands/PerimeterCmd.java");
	
	@Test
	void everyMessageEmittedByTheSourcesIsTranslated() throws IOException
	{
		Set<String> missing = new LinkedHashSet<>();
		int checked = 0;
		
		for(String relative : SOURCES)
		{
			String source = read(relative);
			
			for(String literal : matches(DETAIL, source))
			{
				checked++;
				
				if(!PerimeterText.has(literal))
					missing.add(relative + " -> detail: " + literal);
			}
			
			for(String literal : matches(FEEDBACK, source))
			{
				checked++;
				
				if(!PerimeterText.has(literal))
					missing.add(relative + " -> feedback: " + literal);
			}
			
			for(String literal : matches(TRANSLATION_KEY, source))
				if(literal.startsWith("perimeterdigger.")
					&& !PerimeterText.has(literal))
					missing.add(relative + " -> key: " + literal);
		}
		
		assertTrue(checked > 30,
			"expected to find the emitted messages, found " + checked);
		assertTrue(missing.isEmpty(),
			"messages without a translation: " + missing);
	}
	
	@Test
	void everyAutomationDetailTemplateIsPresent()
		throws IOException
	{
		String source = read(SOURCES.get(0));
		List<String> details = matches(DETAIL, source);
		
		assertFalse(details.isEmpty(), "no details found in the automation");
		
		for(String detail : details)
			assertTrue(PerimeterText.has(detail),
				"missing translation for: " + detail);
	}
	
	private static List<String> matches(Pattern pattern, String source)
	{
		List<String> found = new ArrayList<>();
		Matcher matcher = pattern.matcher(source);
		
		while(matcher.find())
			found.add(matcher.group(1));
		
		return found;
	}
	
	private static String read(String relative) throws IOException
	{
		return Files.readString(locate(relative), StandardCharsets.UTF_8);
	}
	
	/**
	 * Finds a source file relative to the project directory, searching a few
	 * levels up in case the test runs from a sub directory.
	 */
	private static Path locate(String relative) throws IOException
	{
		Path directory = Path.of(System.getProperty("user.dir")).toAbsolutePath();
		
		for(int level = 0; level < 5 && directory != null;
			level++, directory = directory.getParent())
		{
			Path candidate = directory.resolve(relative);
			
			if(Files.isRegularFile(candidate))
				return candidate;
		}
		
		throw new IOException("cannot locate " + relative + " from "
			+ System.getProperty("user.dir"));
	}
}
