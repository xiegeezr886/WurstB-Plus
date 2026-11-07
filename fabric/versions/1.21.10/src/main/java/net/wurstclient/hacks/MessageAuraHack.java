/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
// Ported from Meteor Client (GPL-3.0).
package net.wurstclient.hacks;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.events.WorldChangeListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.TextFieldSetting;

@SearchTags({"message aura", "AutoMessage", "auto message", "chat timer",
	"message timer", "advertise", "chat spam"})
public final class MessageAuraHack extends Hack implements UpdateListener, WorldChangeListener
{
	private final EnumSetting<Mode> mode = new EnumSetting<>("Mode",
		"Timer broadcasts; Nearby privately messages players entering render distance.",
		Mode.values(), Mode.TIMER);
	private final TextFieldSetting nearbyMessage = new TextFieldSetting("Nearby message",
		"Private message sent to newly visible players.", "WurstB+ Plus on top!");
	private final CheckboxSetting ignoreFriends = new CheckboxSetting("Ignore friends",
		"Skip friends in Nearby mode.", false);
	private final Set<UUID> visiblePlayers = new HashSet<>();
	private final ArrayDeque<UUID> recipients = new ArrayDeque<>();
	private ClientLevel trackedWorld;
	private Mode previousMode;

	private final TextFieldSetting messages = new TextFieldSetting("Messages",
		"Messages to send, separated by |.\n"
			+ "Messages starting with / are sent as commands.",
		"WurstB+ Plus on top!");

	private final SliderSetting delay = new SliderSetting("Delay",
		"Ticks to wait between messages.", 100, 20, 7200, 20,
		ValueDisplay.INTEGER.withSuffix(" ticks"));

	private final CheckboxSetting randomOrder = new CheckboxSetting(
		"Random order", "Sends the messages in a random order each cycle.",
		true);

	private final CheckboxSetting disableOnDisconnect = new CheckboxSetting(
		"Disable on disconnect",
		"Disables this hack when you leave the server.", true);

	private final ArrayDeque<String> queue = new ArrayDeque<>();
	private int timer;

	public MessageAuraHack()
	{
		super("MessageAura");
		setCategory(Category.CHAT);
		addSetting(messages);
		addSetting(delay);
		addSetting(randomOrder);
		addSetting(disableOnDisconnect);
		addSetting(mode);
		addSetting(nearbyMessage);
		addSetting(ignoreFriends);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(WorldChangeListener.class, this);
		queue.clear();
		timer = 0;
		previousMode = mode.getSelected();
		rememberPlayers(MC.level);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(WorldChangeListener.class, this);
		queue.clear();
		recipients.clear();
		visiblePlayers.clear();
		trackedWorld = null;
		timer = 0;
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null || MC.getConnection() == null)
		{
			rememberPlayers(null);
			if(disableOnDisconnect.isChecked())
				setEnabled(false);
			return;
		}

		if(trackedWorld != MC.level || previousMode != mode.getSelected())
		{
			rememberPlayers(MC.level);
			queue.clear();
			timer = 0;
			previousMode = mode.getSelected();
		}
		if(mode.getSelected() == Mode.NEARBY)
		{
			updateRecipients();
			if(timer > 0)
				timer--;
			else
				sendNearbyMessage();
			return;
		}

		if(timer > 0)
		{
			timer--;
			return;
		}

		if(queue.isEmpty())
			fillQueue();

		String message = queue.poll();
		if(message == null)
			return;

		// Prevent getting kicked for sending an oversized message.
		if(message.length() > 256)
			message = message.substring(0, 256);

		ClientPacketListener connection = MC.getConnection();
		if(message.startsWith("/"))
			connection.sendCommand(message.substring(1));
		else
			connection.sendChat(message);

		timer = delay.getValueI();
	}

	private void fillQueue()
	{
		List<String> parsed = new ArrayList<>();

		for(String part : messages.getValue().split("\\|"))
		{
			String message = part.trim();
			if(!message.isEmpty())
				parsed.add(message);
		}

		if(parsed.isEmpty())
			return;

		if(randomOrder.isChecked())
			Collections.shuffle(parsed);

		queue.addAll(parsed);
	}

	@Override
	public void onWorldChange(ClientLevel world)
	{
		rememberPlayers(world);
		queue.clear();
		timer = 0;
		if(world == null && disableOnDisconnect.isChecked())
			setEnabled(false);
	}

	private void rememberPlayers(ClientLevel world)
	{
		trackedWorld = world;
		visiblePlayers.clear();
		recipients.clear();
		if(world != null)
			for(Player player : world.players())
				visiblePlayers.add(player.getUUID());
	}

	private void updateRecipients()
	{
		Set<UUID> current = new HashSet<>();
		for(Player player : MC.level.players())
		{
			UUID id = player.getUUID();
			current.add(id);
			if(!id.equals(MC.player.getUUID()) && !visiblePlayers.contains(id)
				&& (!ignoreFriends.isChecked() || !WURST.getFriends().contains(player.getScoreboardName())))
				recipients.add(id);
		}
		recipients.removeIf(id -> !current.contains(id));
		visiblePlayers.clear();
		visiblePlayers.addAll(current);
	}

	private void sendNearbyMessage()
	{
		while(!recipients.isEmpty())
		{
			UUID id = recipients.remove();
			Player player = MC.level.players().stream()
				.filter(p -> p.getUUID().equals(id)).findFirst().orElse(null);
			if(player == null || ignoreFriends.isChecked()
				&& WURST.getFriends().contains(player.getScoreboardName()))
				continue;
			String text = nearbyMessage.getValue().trim();
			if(text.isEmpty())
				continue;
			String command = "msg " + player.getScoreboardName() + " " + text;
			if(command.length() > 256)
				command = command.substring(0, 256);
			MC.getConnection().sendCommand(command);
			timer = delay.getValueI();
			return;
		}
	}

	private enum Mode
	{
		TIMER, NEARBY
	}
}
