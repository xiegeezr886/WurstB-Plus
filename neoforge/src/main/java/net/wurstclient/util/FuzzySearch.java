/*
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

import java.text.Normalizer;
import java.util.Locale;

public final class FuzzySearch
{
	public static final int NO_MATCH = -1;

	private FuzzySearch()
	{}

	public static int score(String text, String query)
	{
		String candidate = normalize(text);
		String normalizedQuery = normalize(query);
		if(candidate.isEmpty() || normalizedQuery.isEmpty())
			return NO_MATCH;

		if(candidate.equals(normalizedQuery))
			return 1200;
		if(candidate.startsWith(normalizedQuery))
			return 1120 - lengthPenalty(candidate, normalizedQuery);
		int substringIndex = candidate.indexOf(normalizedQuery);
		if(substringIndex >= 0)
			return 1040 - Math.min(120, substringIndex * 4)
				- lengthPenalty(candidate, normalizedQuery);

		String compactCandidate = compact(candidate);
		String compactQuery = compact(normalizedQuery);
		if(compactCandidate.equals(compactQuery))
			return 1100;
		if(compactCandidate.startsWith(compactQuery))
			return 1020 - lengthPenalty(compactCandidate, compactQuery);
		substringIndex = compactCandidate.indexOf(compactQuery);
		if(substringIndex >= 0)
			return 960 - Math.min(120, substringIndex * 4)
				- lengthPenalty(compactCandidate, compactQuery);

		String[] candidateWords = words(candidate);
		String[] queryWords = words(normalizedQuery);
		String acronym = acronym(candidateWords);
		if(compactQuery.length() >= 2 && !acronym.isEmpty())
		{
			if(acronym.equals(compactQuery))
				return 940;
			if(acronym.startsWith(compactQuery))
				return 900 - lengthPenalty(acronym, compactQuery);
		}

		int total = 0;
		int weakest = Integer.MAX_VALUE;
		for(String queryWord : queryWords)
		{
			int best = NO_MATCH;
			for(String candidateWord : candidateWords)
				best = Math.max(best,
					scoreWord(candidateWord, queryWord));
			if(best == NO_MATCH)
				return NO_MATCH;
			total += best;
			weakest = Math.min(weakest, best);
		}
		return weakest + total / Math.max(1, queryWords.length * 10);
	}

	public static boolean fuzzyWordMatch(String text, String query)
	{
		return score(text, query) != NO_MATCH;
	}

	public static boolean fuzzyMultiWordMatch(String text, String query)
	{
		return score(text, query) != NO_MATCH;
	}

	public static String normalize(String text)
	{
		if(text == null || text.isBlank())
			return "";

		String decomposed = Normalizer.normalize(text, Normalizer.Form.NFKD);
		StringBuilder result = new StringBuilder(decomposed.length());
		boolean separated = true;
		for(int index = 0; index < decomposed.length(); index++)
		{
			char current = decomposed.charAt(index);
			if(current == '\u00a7')
			{
				if(index + 1 < decomposed.length()
					&& isFormattingCode(decomposed.charAt(index + 1)))
					index++;
				if(!separated)
					result.append(' ');
				separated = true;
				continue;
			}
			if(Character.getType(current) == Character.NON_SPACING_MARK)
				continue;

			boolean letterOrDigit = Character.isLetterOrDigit(current);
			if(!letterOrDigit)
			{
				if(!separated)
					result.append(' ');
				separated = true;
				continue;
			}

			if(!separated && Character.isUpperCase(current)
				&& startsCamelWord(decomposed, index))
				result.append(' ');
			result.append(Character.toLowerCase(current));
			separated = false;
		}

		int length = result.length();
		if(length > 0 && result.charAt(length - 1) == ' ')
			result.setLength(length - 1);
		return result.toString();
	}

	public static int levenshteinDistance(CharSequence first,
		CharSequence second)
	{
		int firstLength = first.length();
		int secondLength = second.length();
		if(firstLength == 0)
			return secondLength;
		if(secondLength == 0)
			return firstLength;

		int[] previous = new int[secondLength + 1];
		int[] current = new int[secondLength + 1];
		for(int column = 0; column <= secondLength; column++)
			previous[column] = column;

		for(int row = 1; row <= firstLength; row++)
		{
			current[0] = row;
			char firstChar = first.charAt(row - 1);
			for(int column = 1; column <= secondLength; column++)
			{
				int cost = firstChar == second.charAt(column - 1) ? 0 : 1;
				current[column] = Math.min(Math.min(previous[column] + 1,
					current[column - 1] + 1), previous[column - 1] + cost);
			}
			int[] swap = previous;
			previous = current;
			current = swap;
		}
		return previous[secondLength];
	}

	public static int damerauLevenshteinDistance(CharSequence first,
		CharSequence second)
	{
		int firstLength = first.length();
		int secondLength = second.length();
		int[][] distances = new int[firstLength + 1][secondLength + 1];
		for(int row = 0; row <= firstLength; row++)
			distances[row][0] = row;
		for(int column = 0; column <= secondLength; column++)
			distances[0][column] = column;

		for(int row = 1; row <= firstLength; row++)
			for(int column = 1; column <= secondLength; column++)
			{
				int cost = first.charAt(row - 1) == second.charAt(column - 1)
					? 0 : 1;
				int value = Math.min(Math.min(distances[row - 1][column] + 1,
					distances[row][column - 1] + 1),
					distances[row - 1][column - 1] + cost);
				if(row > 1 && column > 1
					&& first.charAt(row - 1) == second.charAt(column - 2)
					&& first.charAt(row - 2) == second.charAt(column - 1))
					value = Math.min(value,
						distances[row - 2][column - 2] + 1);
				distances[row][column] = value;
			}
		return distances[firstLength][secondLength];
	}

	private static int scoreWord(String candidate, String query)
	{
		if(candidate.equals(query))
			return 880;
		if(candidate.startsWith(query))
			return 820 - lengthPenalty(candidate, query);
		int index = candidate.indexOf(query);
		if(index >= 0)
			return 760 - Math.min(80, index * 5)
				- lengthPenalty(candidate, query);

		if(query.length() >= 3)
		{
			int gap = subsequenceGap(candidate, query);
			if(gap >= 0)
				return 680 - Math.min(180, gap * 12)
					- lengthPenalty(candidate, query);

			int distance = damerauLevenshteinDistance(candidate, query);
			if(distance <= maxEditDistance(query.length()))
				return 620 - distance * 80
					- Math.abs(candidate.length() - query.length()) * 6;
		}
		return NO_MATCH;
	}

	private static int subsequenceGap(String candidate, String query)
	{
		int queryIndex = 0;
		int first = -1;
		int last = -1;
		for(int index = 0; index < candidate.length()
			&& queryIndex < query.length(); index++)
			if(candidate.charAt(index) == query.charAt(queryIndex))
			{
				if(first < 0)
					first = index;
				last = index;
				queryIndex++;
			}
		if(queryIndex != query.length())
			return NO_MATCH;
		return last - first + 1 - query.length();
	}

	private static int maxEditDistance(int queryLength)
	{
		if(queryLength <= 4)
			return 1;
		if(queryLength <= 7)
			return 2;
		return 3;
	}

	private static int lengthPenalty(String candidate, String query)
	{
		return Math.min(100, Math.abs(candidate.length() - query.length()) * 2);
	}

	private static String compact(String value)
	{
		return value.replace(" ", "");
	}

	private static String[] words(String value)
	{
		return value.split(" +");
	}

	private static String acronym(String[] words)
	{
		if(words.length < 2)
			return "";
		StringBuilder acronym = new StringBuilder(words.length);
		for(String word : words)
			if(!word.isEmpty())
				acronym.append(word.charAt(0));
		return acronym.toString();
	}

	private static boolean startsCamelWord(String text, int index)
	{
		char previous = text.charAt(index - 1);
		if(Character.isLowerCase(previous) || Character.isDigit(previous))
			return true;
		return Character.isUpperCase(previous) && index + 1 < text.length()
			&& Character.isLowerCase(text.charAt(index + 1));
	}

	private static boolean isFormattingCode(char character)
	{
		char lower = Character.toLowerCase(character);
		return lower >= '0' && lower <= '9' || lower >= 'a' && lower <= 'f'
			|| lower >= 'k' && lower <= 'o' || lower == 'r';
	}
}
