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
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Random;

import net.minecraft.client.multiplayer.PlayerInfo;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.TextFieldSetting;
import net.wurstclient.util.ChatUtils;

/**
 * Repeating chat sender with optional whisper mode.
 *
 * <p>
 * The original module shipped a large built-in message list. This port keeps
 * the scheduling, random suffix and whisper logic but leaves the message list
 * empty so that only user-supplied text is ever sent.
 */
@SearchTags({"auto kou zi", "auto message", "message timer", "whisper spam",
	"chat timer"})
public final class AutoKouZiHack extends Hack implements UpdateListener
{
	private static final String CHARACTERS =
		"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

	private final SliderSetting delay = new SliderSetting("Delay",
		"Seconds between messages.", 3, 0, 20, 0.1, ValueDisplay.DECIMAL);

	private final CheckboxSetting autoDisable = new CheckboxSetting(
		"Auto disable", "Disables the hack when you leave the server.", true);

	private final CheckboxSetting commandMode = new CheckboxSetting(
		"Command mode", "Sends every message as a command.", false);

	private final TextFieldSetting messages = new TextFieldSetting("Messages",
		"Messages to send, separated by |. One is picked at random.", "");

	private final TextFieldSetting defaultMessage = new TextFieldSetting(
		"Default message",
		"Used when the message list is empty. Leave empty to send nothing.", "");

	private final SliderSetting randomLength = new SliderSetting(
		"Random length",
		"Number of random letters appended to each message.", 0, 0, 20, 1,
		ValueDisplay.INTEGER);

	private final CheckboxSetting whisperMode = new CheckboxSetting(
		"Whisper mode", "Sends messages as whispers.", false);

	private final CheckboxSetting randomWhisper = new CheckboxSetting(
		"Random whisper", "Picks a random online player as the recipient.", false)
			.visibleWhen(whisperMode::isChecked);

	private final TextFieldSetting targetPlayer = new TextFieldSetting(
		"Target player", "Recipient used when random whisper is off.", "")
			.visibleWhen(() -> whisperMode.isChecked()
				&& !randomWhisper.isChecked());

	private final TextFieldSetting whisperCommand = new TextFieldSetting(
		"Whisper command", "Command used for whispers, without the slash.",
		"msg").visibleWhen(whisperMode::isChecked);

	private final CheckboxSetting checkSelf = new CheckboxSetting("Check self",
		"Excludes your own name when picking a random recipient.", false)
			.visibleWhen(() -> whisperMode.isChecked()
				&& randomWhisper.isChecked());

	private final Random random = new Random();
	private int timer;

	public AutoKouZiHack()
	{
		super("AutoKouZi");
		setCategory(Category.CHAT);
		addSetting(delay);
		addSetting(autoDisable);
		addSetting(commandMode);
		addSetting(messages);
		addSetting(defaultMessage);
		addSetting(randomLength);
		addSetting(whisperMode);
		addSetting(randomWhisper);
		addSetting(targetPlayer);
		addSetting(whisperCommand);
		addSetting(checkSelf);
	}

	@Override
	protected void onEnable()
	{
		if(MC.player == null)
		{
			setEnabled(false);
			return;
		}

		if(whisperMode.isChecked() && !randomWhisper.isChecked()
			&& targetPlayer.getValue().trim().isEmpty())
		{
			ChatUtils.error("AutoKouZi: no target player set.");
			setEnabled(false);
			return;
		}

		timer = Math.max(1, (int)Math.round(delay.getValue() * 20));
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		timer = 0;
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.getConnection() == null)
		{
			if(autoDisable.isChecked())
				setEnabled(false);
			return;
		}

		if(timer > 0)
		{
			timer--;
			return;
		}

		String message = getRandomMessage();
		if(message.isEmpty())
			return;

		int length = randomLength.getValueI();
		if(length > 0)
			message += " " + randomString(length);

		if(whisperMode.isChecked())
		{
			String target = getTargetPlayer();
			if(target == null)
			{
				ChatUtils.warning("AutoKouZi: no valid recipient found.");
				timer = Math.max(1,
					(int)Math.round(delay.getValue() * 20));
				return;
			}

			message = "/" + stripSlash(whisperCommand.getValue()).trim() + " "
				+ target + " " + message;
		}

		if(message.length() > 256)
			message = message.substring(0, 256);

		if(commandMode.isChecked() || message.startsWith("/"))
			MC.getConnection().sendCommand(stripSlash(message));
		else
			MC.getConnection().sendChat(message);

		timer = Math.max(1, (int)Math.round(delay.getValue() * 20));
	}

	private String getRandomMessage()
	{
		List<String> list = split(messages.getValue());
		if(list.isEmpty())
			return defaultMessage.getValue().trim();

		return list.get(random.nextInt(list.size()));
	}

	private String getTargetPlayer()
	{
		if(!randomWhisper.isChecked())
			return targetPlayer.getValue().trim();

		Collection<PlayerInfo> online = MC.getConnection().getOnlinePlayers();
		if(online.isEmpty())
			return null;

		List<PlayerInfo> list = new ArrayList<>(online);
		String self = MC.getUser().getName();

		for(int attempts = 0; attempts < 50; attempts++)
		{
			PlayerInfo info = list.get(random.nextInt(list.size()));
			String name = info.getProfile().name();

			if(checkSelf.isChecked() && Objects.equals(name, self))
				continue;

			return name;
		}

		return null;
	}

	private static List<String> split(String value)
	{
		List<String> result = new ArrayList<>();
		for(String part : value.split("\\|"))
		{
			String entry = part.trim();
			if(!entry.isEmpty())
				result.add(entry);
		}
		return result;
	}

	private String randomString(int length)
	{
		StringBuilder builder = new StringBuilder(length);
		for(int i = 0; i < length; i++)
			builder.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
		return builder.toString();
	}

	private static String stripSlash(String command)
	{
		return command.startsWith("/") ? command.substring(1) : command;
	}
}
