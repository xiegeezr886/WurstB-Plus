/*
 * Copyright (c) 2025 LeavesHack contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from LeavesHack (AGPL-3.0).
package net.wurstclient.hacks;

import net.minecraft.client.multiplayer.ClientLevel;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.ChatOutputListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.events.WorldChangeListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.TextFieldSetting;
import net.wurstclient.util.ChatUtils;
import net.wurstclient.util.text.WText;

@SearchTags({"auto login", "login", "register", "password", "AutoLogin"})
public final class AutoLoginHack extends Hack
	implements UpdateListener, WorldChangeListener, ChatOutputListener
{
	private final TextFieldSetting loginCommand = new TextFieldSetting(
		"Login command",
		WText.literal("Command used to log in, without the leading slash."),
		"login");

	private final TextFieldSetting registerCommand = new TextFieldSetting(
		"Register command",
		WText.literal("Command used to register, without the leading slash."),
		"register");

	private final TextFieldSetting password = new TextFieldSetting("Password",
		WText.literal("Password sent to the server. Stored in plain text."), "");

	private final CheckboxSetting register = new CheckboxSetting("Register",
		WText.literal("Runs the register command instead of the login command."),
		false);

	private final CheckboxSetting autoSave = new CheckboxSetting(
		"Auto save password",
		WText.literal("Updates the password whenever you type a matching"
			+ " login or register command."),
		true);

	private final SliderSetting delay = new SliderSetting("Delay",
		WText.literal("Ticks to wait after joining before logging in."), 40, 0,
		200, 5, ValueDisplay.INTEGER.withSuffix(" ticks"));

	private boolean pending;
	private int timer;

	public AutoLoginHack()
	{
		super("AutoLogin");
		setCategory(Category.OTHER);
		addSetting(loginCommand);
		addSetting(registerCommand);
		addSetting(password);
		addSetting(register);
		addSetting(autoSave);
		addSetting(delay);
	}

	@Override
	protected void onEnable()
	{
		pending = MC.player != null && MC.level != null;
		timer = delay.getValueI();
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(WorldChangeListener.class, this);
		EVENTS.add(ChatOutputListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		pending = false;
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(WorldChangeListener.class, this);
		EVENTS.remove(ChatOutputListener.class, this);
	}

	@Override
	public void onWorldChange(ClientLevel world)
	{
		if(world == null)
		{
			pending = false;
			return;
		}

		pending = true;
		timer = delay.getValueI();
	}

	@Override
	public void onUpdate()
	{
		if(!pending)
			return;

		if(MC.player == null || MC.level == null || MC.getConnection() == null)
			return;

		if(timer > 0)
		{
			timer--;
			return;
		}

		pending = false;

		String pw = password.getValue();
		if(pw.isBlank())
		{
			ChatUtils.warning("AutoLogin is enabled, but no password is set.");
			return;
		}

		String command = stripSlash(register.isChecked()
			? registerCommand.getValue() : loginCommand.getValue());
		if(command.isBlank())
			return;

		String message = command + " " + pw;
		if(register.isChecked())
			message += " " + pw;

		MC.getConnection().sendCommand(message);
	}

	@Override
	public void onSentMessage(ChatOutputEvent event)
	{
		if(!autoSave.isChecked())
			return;

		String message = event.getOriginalMessage().trim();
		if(message.startsWith("/"))
			message = message.substring(1);

		String[] args = message.split("\\s+");
		if(args.length < 2)
			return;

		String command = args[0];
		if(!command.equals(stripSlash(loginCommand.getValue()))
			&& !command.equals(stripSlash(registerCommand.getValue())))
			return;

		password.setValue(args[1]);
	}

	private static String stripSlash(String command)
	{
		return command.startsWith("/") ? command.substring(1) : command;
	}
}
