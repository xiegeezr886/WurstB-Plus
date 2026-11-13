/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from InvincibleMachineGun (GPL-3.0).
package net.wurstclient.hacks;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.ChatInputListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.TextFieldSetting;

@SearchTags({"chat filter", "profanity filter", "bad words", "clean chat",
	"word filter"})
public final class ChatFilterHack extends Hack implements ChatInputListener
{
	private final TextFieldSetting badWords = new TextFieldSetting("Bad words",
		"Words to replace, separated by |. Matching is case-sensitive.", "");

	private final TextFieldSetting replacement = new TextFieldSetting(
		"Replacement",
		"Character used to replace each letter of a blocked word.", "*");

	public ChatFilterHack()
	{
		super("ChatFilter");
		setCategory(Category.CHAT);
		addSetting(badWords);
		addSetting(replacement);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(ChatInputListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(ChatInputListener.class, this);
	}

	@Override
	public void onReceivedMessage(ChatInputEvent event)
	{
		Component message = event.getComponent();
		String rawText = message.getString();
		List<String> words = getWords();

		if(words.isEmpty() || !containsAny(rawText, words))
			return;

		String character = getReplacementCharacter();
		MutableComponent filtered = Component.empty();
		message.visit((style, text) -> {
			filtered.append(Component.literal(filterText(text, words, character))
				.withStyle(style));
			return Optional.empty();
		}, Style.EMPTY);

		event.setComponent(filtered);
	}

	private List<String> getWords()
	{
		List<String> words = new ArrayList<>();
		for(String part : badWords.getValue().split("\\|"))
		{
			String word = part.trim();
			if(!word.isEmpty() && !words.contains(word))
				words.add(word);
		}

		// Longer words must be replaced first so that a short word cannot
		// destroy part of a longer match.
		words.sort((a, b) -> b.length() - a.length());
		return words;
	}

	private String getReplacementCharacter()
	{
		String value = replacement.getValue();
		return value.isEmpty() ? "*" : value.substring(0, 1);
	}

	private boolean containsAny(String text, List<String> words)
	{
		for(String word : words)
			if(text.contains(word))
				return true;

		return false;
	}

	private String filterText(String text, List<String> words, String character)
	{
		String result = text;
		for(String word : words)
			if(result.contains(word))
				result = result.replace(word, character.repeat(word.length()));

		return result;
	}
}
