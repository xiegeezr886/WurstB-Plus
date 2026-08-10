/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.keybinds;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.screens.Screen;
import net.wurstclient.WurstClient;
import net.wurstclient.clickgui2.component.SuperSoftClickGuiScreen;
import net.wurstclient.clickgui2.component.VapeClickGuiScreen;
import net.wurstclient.command.CmdProcessor;
import net.wurstclient.events.KeyPressListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.hack.HackList;
import net.wurstclient.util.ChatUtils;
import net.wurstclient.util.ScreenRegistry;

public final class KeybindProcessor implements KeyPressListener
{
	private final HackList hax;
	private final KeybindList keybinds;
	private final CmdProcessor cmdProcessor;
	private final Map<String, Set<Hack>> heldHacks = new HashMap<>();

	public KeybindProcessor(HackList hax, KeybindList keybinds,
		CmdProcessor cmdProcessor)
	{
		this.hax = hax;
		this.keybinds = keybinds;
		this.cmdProcessor = cmdProcessor;
	}

	@Override
	public void onKeyPress(KeyPressEvent event)
	{
		String keyName = getKeyName(event);
		if(event.getAction() == GLFW.GLFW_RELEASE)
		{
			handleRelease(keyName);
			return;
		}
		if(event.getAction() != GLFW.GLFW_PRESS)
			return;

		if(InputConstants.isKeyDown(WurstClient.MC.getWindow().getWindow(),
			GLFW.GLFW_KEY_F3))
			return;

		Screen screen = WurstClient.MC.screen;
		if(screen != null && !ScreenRegistry.isAny(screen,
			ScreenRegistry.CLICK_GUI, ScreenRegistry.NAVIGATOR))
			return;
		if(screen instanceof SuperSoftClickGuiScreen clickGui
			&& clickGui.isWaitingForKeybind())
			return;
		if(screen instanceof VapeClickGuiScreen vapeGui
			&& vapeGui.isWaitingForKeybind())
			return;

		String cmds = keybinds.getCommands(keyName);
		if(cmds == null)
			return;

		handlePress(keyName, cmds, screen);
	}

	private void handlePress(String keyName, String cmds, Screen screen)
	{
		if(ScreenRegistry.CLICK_GUI.matches(screen)
			&& containsCmd(cmds, "clickgui")
			|| ScreenRegistry.NAVIGATOR.matches(screen)
				&& containsCmd(cmds, "navigator"))
		{
			if(screen instanceof SuperSoftClickGuiScreen clickGui)
				clickGui.onClose();
			else
				WurstClient.MC.setScreen(null);
			return;
		}

		String[] parts = splitParts(cmds);
		for(String cmd : parts)
		{
			KeyAction action = KeyAction.fromPrefix(cmd);
			String stripped = KeyAction.stripPrefix(cmd);

			if(action == KeyAction.TOGGLE)
				processCmd(stripped);
			else
				processHoldEnable(keyName, stripped, action);
		}
	}

	private void handleRelease(String keyName)
	{
		Set<Hack> hacks = heldHacks.remove(keyName);
		if(hacks == null)
			return;

		for(Hack hack : hacks)
			if(hack.isEnabled() && heldHacks.values().stream()
				.noneMatch(other -> other.contains(hack)))
				hack.setEnabled(false);
	}

	private void processHoldEnable(String keyName, String cmd,
		KeyAction action)
	{
		if(!cmd.contains(" ") && !cmd.startsWith("."))
		{
			Hack hack = hax.getHackByName(cmd);
			if(hack != null)
			{
				if(action == KeyAction.SMART && hack.isEnabled())
				{
					hack.setEnabled(false);
					return;
				}

				if(!hack.isEnabled())
				{
					if(hax.tooManyHaxHack.isEnabled()
						&& hax.tooManyHaxHack.isBlocked(hack))
						return;

					hack.setEnabled(true);
					heldHacks.computeIfAbsent(keyName,
						k -> new HashSet<>()).add(hack);
				}
				return;
			}
		}

		if(cmd.startsWith("."))
			cmdProcessor.process(cmd.substring(1));
		else
			cmdProcessor.process(cmd);
	}

	private boolean containsCmd(String cmds, String expected)
	{
		for(String part : splitParts(cmds))
			if(KeyAction.stripPrefix(part).trim().equalsIgnoreCase(expected))
				return true;
		return false;
	}

	private static String[] splitParts(String cmds)
	{
		String escaped = cmds.replace(";;", "\u0000");
		String[] parts = escaped.split(";");
		for(int i = 0; i < parts.length; i++)
			parts[i] = parts[i].replace("\u0000", ";").trim();
		return parts;
	}

	private String getKeyName(KeyPressEvent event)
	{
		int keyCode = event.getKeyCode();
		int scanCode = event.getScanCode();
		return InputConstants.getKey(keyCode, scanCode).getName();
	}

	private void processCmd(String cmd)
	{
		if(cmd.startsWith("."))
			cmdProcessor.process(cmd.substring(1));
		else if(cmd.contains(" "))
			cmdProcessor.process(cmd);
		else
		{
			Hack hack = hax.getHackByName(cmd);

			if(hack == null)
			{
				cmdProcessor.process(cmd);
				return;
			}

			if(!hack.isEnabled() && hax.tooManyHaxHack.isEnabled()
				&& hax.tooManyHaxHack.isBlocked(hack))
			{
				ChatUtils.error(hack.getName() + " is blocked by TooManyHax.");
				return;
			}

			hack.setEnabled(!hack.isEnabled());
		}
	}
}
