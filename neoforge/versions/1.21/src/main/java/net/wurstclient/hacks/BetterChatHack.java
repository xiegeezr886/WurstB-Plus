/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
// Ported from Meteor Client (GPL-3.0).
package net.wurstclient.hacks;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import net.minecraft.ChatFormatting;
import net.minecraft.client.GuiMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.ArrayListDeque;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.ChatInputListener;
import net.wurstclient.events.ChatInputListener.ChatInputEvent;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.ChatUtils;

@SearchTags({"better chat", "anti spam", "chat history", "timestamps",
	"chat tweaks"})
public final class BetterChatHack extends Hack
	implements ChatInputListener, UpdateListener
{
	private static final Pattern ANTI_CLEAR_REGEX =
		Pattern.compile("\\n(\\n|\\s)+\\n");
	private static final DateTimeFormatter TIME_FORMAT =
		DateTimeFormatter.ofPattern("HH:mm");
	private static final DateTimeFormatter TIME_FORMAT_SECONDS =
		DateTimeFormatter.ofPattern("HH:mm:ss");

	private final CheckboxSetting antiSpam = new CheckboxSetting("Anti-spam",
		"Blocks duplicate messages from filling your chat.", true);

	private final SliderSetting antiSpamDepth = new SliderSetting(
		"Spam depth", "How many recent chat lines to check for duplicates.", 20,
		1, 100, 1, ValueDisplay.INTEGER);

	private final CheckboxSetting antiClear = new CheckboxSetting("Anti-clear",
		"Prevents servers from clearing your chat with empty lines.", true);

	private final CheckboxSetting keepHistory = new CheckboxSetting(
		"Keep history",
		"Prevents the chat history from being cleared when disconnecting.",
		true);

	private final CheckboxSetting timestamps = new CheckboxSetting(
		"Timestamps", "Adds client-side timestamps to chat messages.", false);

	private final CheckboxSetting showSeconds = new CheckboxSetting(
		"Show seconds", "Shows seconds in the timestamps.", false);

	private final List<String> savedHistory = new ArrayList<>();

	public BetterChatHack()
	{
		super("BetterChat");
		setCategory(Category.CHAT);
		addSetting(antiSpam);
		addSetting(antiSpamDepth);
		addSetting(antiClear);
		addSetting(keepHistory);
		addSetting(timestamps);
		addSetting(showSeconds);
	}

	@Override
	protected void onEnable()
	{
		savedHistory.clear();
		EVENTS.add(ChatInputListener.class, this);
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(ChatInputListener.class, this);
		EVENTS.remove(UpdateListener.class, this);
		savedHistory.clear();
	}

	@Override
	public void onReceivedMessage(ChatInputEvent event)
	{
		Component message = event.getComponent();
		String text = message.getString();

		if(antiClear.isChecked()
			&& ANTI_CLEAR_REGEX.matcher(text).find())
		{
			event.cancel();
			return;
		}

		if(antiSpam.isChecked() && isDuplicate(event, text))
		{
			event.cancel();
			return;
		}

		if(timestamps.isChecked())
		{
			String time = LocalTime.now().format(
				showSeconds.isChecked() ? TIME_FORMAT_SECONDS : TIME_FORMAT);
			MutableComponent prefix =
				Component.literal("[" + time + "] ")
					.withStyle(ChatFormatting.GRAY);
			event.setComponent(prefix.append(message));
		}
	}

	private boolean isDuplicate(ChatInputEvent event, String text)
	{
		List<GuiMessage.Line> lines = event.getChatLines();
		if(lines.isEmpty())
			return false;

		int checked = 0;
		for(GuiMessage.Line line : lines)
		{
			if(checked++ >= antiSpamDepth.getValueI())
				break;

			if(ChatUtils.getAsString(line).equals(text))
				return true;
		}

		return false;
	}

	@Override
	public void onUpdate()
	{
		if(!keepHistory.isChecked())
		{
			savedHistory.clear();
			return;
		}

		if(MC.gui == null)
			return;

		ArrayListDeque<String> recentChat = MC.gui.getChat().getRecentChat();

		if(recentChat.size() >= savedHistory.size())
		{
			savedHistory.clear();
			recentChat.forEach(savedHistory::add);
			return;
		}

		// The history was cleared (e.g. by disconnecting), so put it back.
		recentChat.clear();
		for(String message : savedHistory)
			recentChat.addLast(message);
	}
}
