/*
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.addon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;

import net.wurstclient.WurstClient;
import net.wurstclient.command.BrigadierCommand;
import net.wurstclient.command.CmdList;
import net.wurstclient.command.Command;
import net.wurstclient.hack.Hack;
import net.wurstclient.hack.HackList;

public final class AddonManager
{
	private final List<WurstAddon> addons = new ArrayList<>();
	private final List<String> loadedAddonNames = new ArrayList<>();

	public void discoverAddons()
	{
		try
		{
			ServiceLoader<WurstAddon> loader =
				ServiceLoader.load(WurstAddon.class);
			for(WurstAddon addon : loader)
				registerAddon(addon);
		}catch(ServiceConfigurationError | RuntimeException e)
		{
			System.err.println(
				"[AddonManager] Failed to discover addons: " + e.getMessage());
		}
	}

	public void registerAddon(WurstAddon addon)
	{
		Objects.requireNonNull(addon, "addon");
		String addonName = Objects.requireNonNull(addon.getName(),
			"addon name");
		if(addonName.isBlank())
			throw new IllegalArgumentException("Addon name cannot be blank");
		if(loadedAddonNames.stream()
			.anyMatch(name -> name.equalsIgnoreCase(addonName)))
			return;

		try
		{
			addon.onInitialize();
			Hack[] hacks = Objects.requireNonNull(addon.getHacks(),
				"addon hacks");
			Command[] commands = Objects.requireNonNull(addon.getCommands(),
				"addon commands");
			BrigadierCommand[] brigadierCommands = Objects.requireNonNull(
				addon.getBrigadierCommands(), "addon brigadier commands");

			validateRegistrations(hacks, commands, brigadierCommands);
			registerHacks(hacks);
			registerCommands(commands);
			registerBrigadierCommands(brigadierCommands);

			addons.add(addon);
			loadedAddonNames.add(addonName);
		}catch(RuntimeException | LinkageError e)
		{
			System.err.println("[AddonManager] Addon " + addonName
				+ " failed to initialize: " + e.getMessage());
			return;
		}

		System.out.println("[AddonManager] Loaded addon: " + addonName
			+ " v" + addon.getVersion() + " by " + addon.getAuthor());
	}

	private void validateRegistrations(Hack[] hacks, Command[] commands,
		BrigadierCommand[] brigadierCommands)
	{
		HackList hackList = WurstClient.INSTANCE.getHax();
		CmdList cmdList = WurstClient.INSTANCE.getCmds();
		Set<String> hackNames = new HashSet<>();
		Set<String> commandNames = new HashSet<>();

		for(Hack hack : hacks)
		{
			Objects.requireNonNull(hack, "addon hack");
			String name = hack.getName().toLowerCase(java.util.Locale.ROOT);
			if(!hackNames.add(name) || hackList.getHackByName(hack.getName()) != null)
				throw new IllegalArgumentException(
					"Hack name collision: " + hack.getName());
		}

		for(Command command : commands)
		{
			Objects.requireNonNull(command, "addon command");
			String name = command.getName().replaceFirst("^\\.", "")
				.toLowerCase(java.util.Locale.ROOT);
			if(!commandNames.add(name) || cmdList.hasCommand(name))
				throw new IllegalArgumentException(
					"Command name collision: " + command.getName());
		}

		for(BrigadierCommand command : brigadierCommands)
		{
			Objects.requireNonNull(command, "addon brigadier command");
			String name = command.getName().toLowerCase(java.util.Locale.ROOT);
			if(!commandNames.add(name) || cmdList.hasCommand(name)
				|| cmdList.hasBrigadierCommand(name))
				throw new IllegalArgumentException(
					"Command name collision: " + command.getName());
		}
	}

	private void registerHacks(Hack[] hacks)
	{
		HackList hackList = WurstClient.INSTANCE.getHax();
		for(Hack hack : hacks)
		{
			hackList.registerAddonHack(hack);
			System.out.println("[AddonManager]   + hack: " + hack.getName());
		}
	}

	private void registerCommands(Command[] commands)
	{
		CmdList cmdList = WurstClient.INSTANCE.getCmds();
		for(Command cmd : commands)
		{
			cmdList.registerAddonCommand(cmd);
			System.out.println("[AddonManager]   + command: " + cmd.getName());
		}
	}

	private void registerBrigadierCommands(BrigadierCommand[] commands)
	{
		CmdList cmdList = WurstClient.INSTANCE.getCmds();
		for(BrigadierCommand cmd : commands)
		{
			cmdList.registerBrigadierCommand(cmd);
			System.out.println("[AddonManager]   + brigadier cmd: "
				+ cmd.getName());
		}
	}

	public List<WurstAddon> getAddons()
	{
		return Collections.unmodifiableList(addons);
	}
}
