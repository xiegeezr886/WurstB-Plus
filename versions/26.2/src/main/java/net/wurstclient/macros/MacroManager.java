/*
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.macros;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.wurstclient.WurstClient;
import net.wurstclient.clickgui2.ClickGuiScreen;
import net.wurstclient.command.CmdProcessor;
import net.wurstclient.events.KeyPressListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.util.json.JsonException;
import net.wurstclient.util.json.JsonUtils;
import net.wurstclient.util.json.WsonArray;
import net.wurstclient.util.ScreenRegistry;

public final class MacroManager implements KeyPressListener, UpdateListener
{
	private final List<Macro> macros = new ArrayList<>();
	private final Path file;
	private final CmdProcessor cmdProcessor;
	private final List<PendingExecution> pending = new ArrayList<>();
	private boolean registered;

	public MacroManager(Path wurstFolder, CmdProcessor cmdProcessor)
	{
		this.file = wurstFolder.resolve("macros.json");
		this.cmdProcessor = cmdProcessor;
		load();
	}

	private void load()
	{
		try
		{
			WsonArray wson = JsonUtils.parseFileToArray(file);
			for(int i = 0; i < wson.size(); i++)
			{
				net.wurstclient.util.json.WsonObject obj =
					wson.getObject(i);
				String name = obj.getString("name");
				String key = obj.getString("key");
				List<String> commands = new ArrayList<>();
				WsonArray cmdArray = obj.getArray("commands");
				commands.addAll(cmdArray.getAllStrings());
				Macro macro = new Macro(name, key, commands);
				if(obj.has("enabled"))
					macro.setEnabled(obj.getBoolean("enabled"));
				macros.add(macro);
			}
		}catch(NoSuchFileException e)
		{}catch(IOException | JsonException e)
		{
			System.err.println("Couldn't load macros.json");
			e.printStackTrace();
		}
	}

	public void save()
	{
		JsonArray array = new JsonArray();
		for(Macro macro : macros)
		{
			JsonObject obj = new JsonObject();
			obj.addProperty("name", macro.getName());
			obj.addProperty("key", macro.getKey());
			JsonArray cmds = new JsonArray();
			for(String c : macro.getCommands())
				cmds.add(c);
			obj.add("commands", cmds);
			obj.addProperty("enabled", macro.isEnabled());
			array.add(obj);
		}
		try
		{
			JsonUtils.toJson(array, file);
		}catch(IOException | JsonException e)
		{
			System.err.println("Couldn't save macros.json");
			e.printStackTrace();
		}
	}

	public List<Macro> getAllMacros()
	{
		return Collections.unmodifiableList(macros);
	}

	public void addMacro(Macro macro)
	{
		macros.removeIf(m -> m.getName().equals(macro.getName()));
		macros.add(macro);
		save();
	}

	public void removeMacro(String name)
	{
		macros.removeIf(m -> m.getName().equals(name));
		save();
	}

	@Override
	public void onKeyPress(KeyPressEvent event)
	{
		if(event.getAction() != GLFW.GLFW_PRESS)
			return;
		if(InputConstants.isKeyDown(WurstClient.MC.getWindow(),
			GLFW.GLFW_KEY_F3))
			return;

		Screen screen = WurstClient.MC.gui.screen();
		if(screen != null && !ScreenRegistry.isAny(screen,
			ScreenRegistry.CLICK_GUI, ScreenRegistry.NAVIGATOR))
			return;
		if(screen instanceof ClickGuiScreen clickGui
			&& clickGui.isWaitingForKeybind())
			return;

		String keyName = getKeyName(event);
		for(Macro macro : macros)
		{
			if(!macro.isEnabled()
				|| !macro.getKey().equalsIgnoreCase(keyName))
				continue;

			executeMacro(macro);
		}
	}

	private void executeMacro(Macro macro)
	{
		pending.add(new PendingExecution(macro.getCommands().iterator(), 0));

		if(!registered)
		{
			WurstClient.INSTANCE.getEventManager()
				.add(UpdateListener.class, this);
			registered = true;
		}
	}

	@Override
	public void onUpdate()
	{
		if(Minecraft.getInstance().isPaused())
			return;

		Iterator<PendingExecution> it = pending.iterator();
		pendingLoop:
		while(it.hasNext())
		{
			PendingExecution pe = it.next();
			pe.ticksRemaining--;

			if(pe.ticksRemaining > 0)
				continue;

			while(pe.commands.hasNext())
			{
				String cmd = pe.commands.next().trim();
				if(cmd.isEmpty())
					continue;

				if(cmd.startsWith("_delay:"))
				{
					try
					{
						pe.ticksRemaining = Math.max(0, Integer
							.parseInt(cmd.substring(7)));
					}catch(NumberFormatException e)
					{
						System.err.println(
							"[Macro] Invalid delay value: " + cmd);
					}
					continue pendingLoop;
				}

				cmdProcessor.process(cmd);
				pe.ticksRemaining = 0;
				continue pendingLoop;
			}

			it.remove();
		}

		if(pending.isEmpty() && registered)
		{
			WurstClient.INSTANCE.getEventManager()
				.remove(UpdateListener.class, this);
			registered = false;
		}
	}

	private String getKeyName(KeyPressEvent event)
	{
		return com.mojang.blaze3d.platform.InputConstants
			.getKey(new net.minecraft.client.input.KeyEvent(
				event.getKeyCode(), event.getScanCode(), event.getModifiers()))
			.getName();
	}

	private static class PendingExecution
	{
		final Iterator<String> commands;
		int ticksRemaining;

		PendingExecution(Iterator<String> commands, int ticksRemaining)
		{
			this.commands = commands;
			this.ticksRemaining = ticksRemaining;
		}
	}
}
