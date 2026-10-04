/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A reader for Valve's KeyValues format, which is what {@code
 * libraryfolders.vdf} is written in.
 *
 * <p>
 * KeyValues is not JSON: keys and values are usually quoted but need not be,
 * comments start with {@code //}, and nesting is expressed with braces. Only
 * the subset Steam actually writes is handled here, and only the shape we need
 * - a map of string to either string or another map.
 *
 * <p>
 * Deliberately free of Minecraft types so the parser can be unit tested against
 * fixture strings.
 */
public final class VdfParser
{
	private final String text;
	private int index;

	private VdfParser(String text)
	{
		this.text = text;
	}

	/**
	 * Reads a document.
	 *
	 * <p>
	 * The single top level wrapper is unwrapped, so {@code libraryfolders.vdf}
	 * comes back as the library entries directly rather than nested under its
	 * {@code "libraryfolders"} key - which is the shape every caller wants.
	 *
	 * @throws VdfParseException
	 *             when the document is malformed, so a caller can fall back to
	 *             the well known Steam paths instead of half reading a library
	 *             list.
	 */
	public static Map<String, Object> parse(String text)
		throws VdfParseException
	{
		if(text == null)
			throw new VdfParseException("empty document");

		VdfParser parser = new VdfParser(text);
		parser.skipTrivia();

		// a document is either a bare object or a single "root" { ... } wrapper
		if(parser.peek() != '{')
		{
			String rootKey = parser.readToken();

			if(rootKey != null)
			{
				parser.skipTrivia();

				if(parser.peek() != '{')
					throw new VdfParseException("expected '{' after '" + rootKey
						+ "'");
			}
		}

		Map<String, Object> document = parser.readObject();

		if(document.isEmpty())
			throw new VdfParseException("no entries");

		return document;
	}

	/** Reads a {@code { ... }} block. The caller has already seen the brace. */
	private Map<String, Object> readObject() throws VdfParseException
	{
		expect('{');

		Map<String, Object> map = new LinkedHashMap<>();

		while(true)
		{
			skipTrivia();
			char next = peek();

			if(next == 0)
				throw new VdfParseException("unterminated object");

			if(next == '}')
			{
				index++;
				return map;
			}

			String key = readToken();

			if(key == null)
				throw new VdfParseException("expected a key");

			skipTrivia();

			if(peek() == '{')
				map.put(key, readObject());
			else
			{
				String value = readToken();

				if(value == null)
					throw new VdfParseException("expected a value for '" + key
						+ "'");

				map.put(key, value);
			}
		}
	}

	/**
	 * @return the next quoted or bare token, or null at the end of input.
	 */
	private String readToken()
	{
		skipTrivia();
		char c = peek();

		if(c == 0 || c == '{' || c == '}')
			return null;

		if(c == '"')
		{
			index++;
			StringBuilder builder = new StringBuilder();

			while(true)
			{
				char current = peek();

				if(current == 0)
					return builder.toString();

				index++;

				if(current == '\\' && peek() != 0)
				{
					builder.append(peek());
					index++;
					continue;
				}

				if(current == '"')
					return builder.toString();

				builder.append(current);
			}
		}

		int start = index;

		while(peek() != 0 && !Character.isWhitespace(peek()) && peek() != '{'
			&& peek() != '}')
			index++;

		return start == index ? null : text.substring(start, index);
	}

	private void skipTrivia()
	{
		while(index < text.length())
		{
			char c = text.charAt(index);

			if(Character.isWhitespace(c))
			{
				index++;
				continue;
			}

			if(c == '/' && index + 1 < text.length()
				&& text.charAt(index + 1) == '/')
			{
				while(index < text.length() && text.charAt(index) != '\n')
					index++;
				continue;
			}

			break;
		}
	}

	private void expect(char expected) throws VdfParseException
	{
		skipTrivia();

		if(peek() != expected)
			throw new VdfParseException("expected '" + expected + "'");

		index++;
	}

	private char peek()
	{
		return index < text.length() ? text.charAt(index) : 0;
	}

	/** Thrown when a document cannot be read as KeyValues. */
	public static final class VdfParseException extends Exception
	{
		public VdfParseException(String message)
		{
			super(message);
		}
	}
}
