/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
package net.wurstclient.hacks;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.KeyPressListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.TextFieldSetting;

/**
 * Sends up to three configured chat texts, either by pressing their keys or
 * automatically on an interval.
 */
@SearchTags({"auto text", "chat macro", "macro", "quick chat", "autotext"})
public final class AutoTextHack extends Hack
	implements KeyPressListener, UpdateListener
{
	private final TextFieldSetting text1 = new TextFieldSetting("Text 1",
		"Chat text sent by key 1.\n"
			+ "Messages starting with \"/\" are sent as commands.", "");
	private final TextFieldSetting key1 = new TextFieldSetting("Key 1",
		"Key that sends text 1, e.g. key.keyboard.g.\n"
			+ "Leave empty to disable.", "", AutoTextHack::isValidKey);

	private final TextFieldSetting text2 = new TextFieldSetting("Text 2",
		"Chat text sent by key 2.\n"
			+ "Messages starting with \"/\" are sent as commands.", "");
	private final TextFieldSetting key2 = new TextFieldSetting("Key 2",
		"Key that sends text 2, e.g. key.keyboard.h.\n"
			+ "Leave empty to disable.", "", AutoTextHack::isValidKey);

	private final TextFieldSetting text3 = new TextFieldSetting("Text 3",
		"Chat text sent by key 3.\n"
			+ "Messages starting with \"/\" are sent as commands.", "");
	private final TextFieldSetting key3 = new TextFieldSetting("Key 3",
		"Key that sends text 3, e.g. key.keyboard.j.\n"
			+ "Leave empty to disable.", "", AutoTextHack::isValidKey);

	private final CheckboxSetting intervalMode = new CheckboxSetting(
		"Interval mode",
		"Automatically sends the configured texts on an interval instead of"
			+ " (or in addition to) pressing their keys.\n"
			+ "Non-empty texts are sent in order and then repeated.",
		false);

	private final SliderSetting interval = new SliderSetting("Interval",
		"Ticks to wait between messages in interval mode.", 100, 20, 7200, 20,
		ValueDisplay.INTEGER.withSuffix(" ticks"));

	private int timer;
	private int nextText;

	public AutoTextHack()
	{
		super("AutoText");
		setCategory(Category.CHAT);
		addSetting(text1);
		addSetting(key1);
		addSetting(text2);
		addSetting(key2);
		addSetting(text3);
		addSetting(key3);
		addSetting(intervalMode);
		addSetting(interval);
	}

	@Override
	protected void onEnable()
	{
		timer = interval.getValueI();
		nextText = 0;
		EVENTS.add(KeyPressListener.class, this);
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(KeyPressListener.class, this);
		EVENTS.remove(UpdateListener.class, this);
		timer = 0;
	}

	@Override
	public void onKeyPress(KeyPressEvent event)
	{
		if(event.getAction() != GLFW.GLFW_PRESS)
			return;

		if(MC.gui.screen() != null || MC.player == null || MC.getConnection() == null)
			return;

		int keyCode = event.getKeyCode();
		if(matches(key1, keyCode))
			send(text1.getValue());
		else if(matches(key2, keyCode))
			send(text2.getValue());
		else if(matches(key3, keyCode))
			send(text3.getValue());
	}

	@Override
	public void onUpdate()
	{
		if(!intervalMode.isChecked())
			return;

		if(MC.player == null || MC.level == null || MC.getConnection() == null)
			return;

		if(timer > 0)
		{
			timer--;
			return;
		}

		List<String> texts = getNonEmptyTexts();
		if(texts.isEmpty())
			return;

		send(texts.get(nextText % texts.size()));
		nextText = (nextText + 1) % texts.size();
		timer = interval.getValueI();
	}

	private List<String> getNonEmptyTexts()
	{
		List<String> texts = new ArrayList<>();
		for(TextFieldSetting setting : new TextFieldSetting[]{text1, text2,
			text3})
		{
			String text = setting.getValue().trim();
			if(!text.isEmpty())
				texts.add(text);
		}
		return texts;
	}

	private void send(String rawText)
	{
		String text = rawText.trim();
		if(text.isEmpty())
			return;

		// Prevent getting kicked for sending an oversized message.
		if(text.length() > 256)
			text = text.substring(0, 256);

		ClientPacketListener connection = MC.getConnection();
		if(connection == null)
			return;

		if(text.startsWith("/"))
			connection.sendCommand(text.substring(1));
		else
			connection.sendChat(text);
	}

	private static boolean matches(TextFieldSetting setting, int keyCode)
	{
		String value = setting.getValue().trim();
		if(value.isEmpty())
			return false;

		try
		{
			return InputConstants.getKey(value).getValue() == keyCode;
		}catch(IllegalArgumentException e)
		{
			return false;
		}
	}

	private static boolean isValidKey(String value)
	{
		String key = value.trim();
		if(key.isEmpty())
			return true;

		try
		{
			InputConstants.getKey(key);
			return true;
		}catch(IllegalArgumentException e)
		{
			return false;
		}
	}
}
