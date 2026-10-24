/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 *
 * Ported from InvincibleMachineGun (GPL-3.0).
 */
package net.wurstclient.hacks;

import java.awt.Color;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.ChatInputListener;
import net.wurstclient.events.ChatOutputListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.TextFieldSetting;

/**
 * Highlights or hides incoming chat messages that match a keyword list.
 *
 * <p>
 * Incoming messages are handled through {@link ChatInputListener}, because
 * {@link ChatOutputListener} only sees messages the local player sends. The
 * output listener is still used to optionally block outgoing messages that
 * match the same keyword list.
 */
@SearchTags({"chat highlight", "chat filter", "keyword highlight",
	"word highlight", "chat keywords", "hide chat", "regex chat"})
public final class ChatHighlightHack extends Hack
	implements ChatInputListener, ChatOutputListener
{
	private final TextFieldSetting keywords = new TextFieldSetting("Keywords",
		"Comma-separated keywords or regular expressions to match.", "");

	private final ColorSetting color = new ColorSetting("Color",
		"Color used for highlighted messages.", Color.YELLOW);

	private final CheckboxSetting caseSensitive = new CheckboxSetting(
		"Case sensitivity", "Whether matching is case sensitive.", false);

	private final CheckboxSetting regex = new CheckboxSetting("Regex",
		"Treats every keyword as a regular expression instead of literal text.",
		false);

	private final CheckboxSetting hideMatches = new CheckboxSetting(
		"Hide matches", "Hides matching incoming messages instead of coloring"
			+ " them.",
		false);

	private final CheckboxSetting blockOutgoing = new CheckboxSetting(
		"Block outgoing", "Prevents sending messages that match the keyword"
			+ " list. Commands are never blocked.",
		false);

	private final TextFieldSetting prefix = new TextFieldSetting("Prefix",
		"Text shown in front of highlighted messages. Can be empty.", "[!] ");

	public ChatHighlightHack()
	{
		super("ChatHighlight");
		setCategory(Category.CHAT);
		addSetting(keywords);
		addSetting(color);
		addSetting(caseSensitive);
		addSetting(regex);
		addSetting(hideMatches);
		addSetting(blockOutgoing);
		addSetting(prefix);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(ChatInputListener.class, this);
		EVENTS.add(ChatOutputListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(ChatInputListener.class, this);
		EVENTS.remove(ChatOutputListener.class, this);
	}

	@Override
	public void onReceivedMessage(ChatInputEvent event)
	{
		String text = event.getComponent().getString();
		if(!matches(text))
			return;

		if(hideMatches.isChecked())
		{
			event.cancel();
			return;
		}

		int rgb = color.getColorI() & 0x00FFFFFF;
		Style style = Style.EMPTY.withColor(rgb);

		MutableComponent highlighted = Component.literal(prefix.getValue())
			.withStyle(style);
		highlighted.append(event.getComponent().copy().withStyle(style));
		event.setComponent(highlighted);
	}

	@Override
	public void onSentMessage(ChatOutputEvent event)
	{
		String message = event.getMessage();
		if(message.startsWith("/") || message.startsWith("."))
			return;

		if(blockOutgoing.isChecked() && matches(message))
			event.cancel();
	}

	private boolean matches(String text)
	{
		for(String keyword : keywords.getValue().split(","))
		{
			keyword = keyword.trim();
			if(keyword.isEmpty())
				continue;

			if(regex.isChecked())
			{
				if(matchesRegex(text, keyword))
					return true;

				continue;
			}

			if(caseSensitive.isChecked())
			{
				if(text.contains(keyword))
					return true;

				continue;
			}

			if(text.toLowerCase(Locale.ROOT)
				.contains(keyword.toLowerCase(Locale.ROOT)))
				return true;
		}

		return false;
	}

	private boolean matchesRegex(String text, String keyword)
	{
		try
		{
			int flags = caseSensitive.isChecked() ? 0
				: Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
			return Pattern.compile(keyword, flags).matcher(text).find();

		}catch(PatternSyntaxException e)
		{
			return false;
		}
	}
}
