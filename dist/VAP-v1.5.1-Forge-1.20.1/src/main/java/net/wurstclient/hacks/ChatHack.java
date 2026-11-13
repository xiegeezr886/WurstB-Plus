/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
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
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.ChatInputListener;
import net.wurstclient.events.ChatOutputListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.ChatUtils;

/**
 * Chat tweaks ported from Soar's ChatMod, adapted to Wurst's event system.
 *
 * <p>Simplifications:
 * <ul>
 * <li>Soar's smooth scrolling, chat heads and compact mode need GuiNewChat
 * mixins, which are out of scope here, so they are omitted.</li>
 * <li>Soar's "background" toggle maps to the vanilla chat opacity option,
 * exposed here as a custom message opacity.</li>
 * <li>Soar's infinite visible chat lines also need a mixin; this port instead
 * keeps an unbounded chat input history client-side.</li>
 * </ul>
 */
@SearchTags({"chat", "chat tweaks", "anti clear", "timestamps", "chat history",
	"infinite history", "chat opacity", "anti spam", "Soar Chat"})
public final class ChatHack extends Hack
	implements ChatInputListener, ChatOutputListener, UpdateListener
{
	private static final Pattern ANTI_CLEAR_REGEX =
		Pattern.compile("\\n(\\n|\\s)+\\n");
	private static final DateTimeFormatter TIME_FORMAT =
		DateTimeFormatter.ofPattern("HH:mm");
	private static final DateTimeFormatter TIME_FORMAT_SECONDS =
		DateTimeFormatter.ofPattern("HH:mm:ss");

	private final CheckboxSetting antiClear = new CheckboxSetting("Anti-clear",
		"Prevents servers from clearing your chat with empty lines.", true);

	private final CheckboxSetting timestamps = new CheckboxSetting(
		"Timestamps", "Adds client-side timestamps to chat messages.", true);

	private final CheckboxSetting showSeconds = new CheckboxSetting(
		"Show seconds", "Shows seconds in the timestamps.", false);

	private final CheckboxSetting keepHistory = new CheckboxSetting(
		"Keep history",
		"Prevents the chat history from being cleared when disconnecting.",
		true);

	private final CheckboxSetting infiniteHistory = new CheckboxSetting(
		"Infinite history",
		"Keeps an unbounded client-side chat input history.", false);

	private final CheckboxSetting customOpacity = new CheckboxSetting(
		"Custom opacity", "Overrides the vanilla chat opacity.", false);

	private final SliderSetting messageOpacity = new SliderSetting(
		"Message opacity", "How visible the chat background is.", 1, 0, 1, 0.05,
		ValueDisplay.PERCENTAGE);

	private final CheckboxSetting antiSpam = new CheckboxSetting("Anti-spam",
		"Blocks duplicate messages from filling your chat.", false);

	private final SliderSetting antiSpamDepth = new SliderSetting(
		"Spam depth", "How many recent chat lines to check for duplicates.", 20,
		1, 100, 1, ValueDisplay.INTEGER);

	private final List<String> savedHistory = new ArrayList<>();
	private double originalOpacity;
	private boolean opacityApplied;

	public ChatHack()
	{
		super("Chat");
		setCategory(Category.CHAT);
		addSetting(antiClear);
		addSetting(timestamps);
		addSetting(showSeconds);
		addSetting(keepHistory);
		addSetting(infiniteHistory);
		addSetting(customOpacity);
		addSetting(messageOpacity);
		addSetting(antiSpam);
		addSetting(antiSpamDepth);
		showSeconds.visibleWhen(timestamps::isChecked);
		messageOpacity.visibleWhen(customOpacity::isChecked);
		antiSpamDepth.visibleWhen(antiSpam::isChecked);
	}

	@Override
	protected void onEnable()
	{
		savedHistory.clear();
		originalOpacity = MC.options.chatOpacity().get();
		opacityApplied = false;
		EVENTS.add(ChatInputListener.class, this);
		EVENTS.add(ChatOutputListener.class, this);
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(ChatInputListener.class, this);
		EVENTS.remove(ChatOutputListener.class, this);
		EVENTS.remove(UpdateListener.class, this);
		savedHistory.clear();
		if(opacityApplied)
		{
			MC.options.chatOpacity().set(originalOpacity);
			opacityApplied = false;
		}
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
	public void onSentMessage(ChatOutputEvent event)
	{
		if(!infiniteHistory.isChecked() || event.isCancelled())
			return;

		String message = event.getMessage();
		if(savedHistory.isEmpty()
			|| !savedHistory.get(savedHistory.size() - 1).equals(message))
			savedHistory.add(message);
	}

	@Override
	public void onUpdate()
	{
		if(customOpacity.isChecked())
		{
			MC.options.chatOpacity().set(messageOpacity.getValue());
			opacityApplied = true;
		}else if(opacityApplied)
		{
			MC.options.chatOpacity().set(originalOpacity);
			opacityApplied = false;
		}

		if(!keepHistory.isChecked() && !infiniteHistory.isChecked())
		{
			savedHistory.clear();
			return;
		}

		if(MC.gui == null)
			return;

		List<String> recentChat = MC.gui.getChat().getRecentChat();

		if(savedHistory.isEmpty())
		{
			recentChat.forEach(savedHistory::add);
			return;
		}

		int savedSize = savedHistory.size();
		int recentSize = recentChat.size();

		if(recentSize == savedSize && recentSize > 0 && !recentChat
			.get(recentSize - 1).equals(savedHistory.get(savedSize - 1)))
		{
			// A new message rolled the full history ring buffer.
			if(infiniteHistory.isChecked())
			{
				savedHistory.add(recentChat.get(recentSize - 1));
				return;
			}
		}

		if(recentSize >= savedSize)
		{
			// Normal growth (or a same-size replacement): take a snapshot.
			savedHistory.clear();
			recentChat.forEach(savedHistory::add);
			return;
		}

		// The history was cleared (e.g. by disconnecting), so put it back.
		recentChat.clear();
		for(String message : savedHistory)
			recentChat.add(message);
	}
}
