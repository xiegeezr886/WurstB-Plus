/*
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.commands;

import java.util.List;

import net.wurstclient.DontBlock;
import net.wurstclient.command.CmdError;
import net.wurstclient.command.CmdException;
import net.wurstclient.command.CmdSyntaxError;
import net.wurstclient.command.Command;
import net.wurstclient.proxy.ProxyConfig;
import net.wurstclient.proxy.ProxyConfig.ProxyType;
import net.wurstclient.util.ChatUtils;

@DontBlock
public final class ProxyCmd extends Command
{
	public ProxyCmd()
	{
		super("proxy", "Manages SOCKS4/SOCKS5 proxies.",
			".proxy add <name> <type> <host> <port> [user] [pass]",
			".proxy remove <name>", ".proxy set <name>",
			".proxy clear", ".proxy list");
	}

	@Override
	public void call(String[] args) throws CmdException
	{
		if(args.length < 1)
			throw new CmdSyntaxError();

		switch(args[0].toLowerCase())
		{
			case "add":
			add(args);
			break;
			case "remove":
			remove(args);
			break;
			case "set":
			set(args);
			break;
			case "clear":
			clear();
			break;
			case "list":
			list(args);
			break;
			default:
			throw new CmdSyntaxError();
		}
	}

	private void add(String[] args) throws CmdException
	{
		if(args.length < 5 || args.length == 6 || args.length > 7)
			throw new CmdSyntaxError(
				".proxy add <name> <socks4|socks5> <host> <port> [user] [pass]");

		String name = args[1];
		ProxyType type;
		try
		{
			type = ProxyType.valueOf(args[2].toUpperCase());
		}catch(IllegalArgumentException e)
		{
			throw new CmdSyntaxError("Type must be socks4 or socks5.");
		}

		String host = args[3];
		int port;
		try
		{
			port = Integer.parseInt(args[4]);
		}catch(NumberFormatException e)
		{
			throw new CmdSyntaxError("Invalid port number.");
		}
		if(port < 1 || port > 65535)
			throw new CmdSyntaxError("Port must be between 1 and 65535.");

		ProxyConfig pc = new ProxyConfig(name, type, host, port);
		if(args.length >= 7)
		{
			pc.setUsername(args[5]);
			pc.setPassword(args[6]);
		}

		WURST.getProxyManager().add(pc);
		ChatUtils.message("Proxy added: " + name);
	}

	private void remove(String[] args) throws CmdException
	{
		if(args.length < 2)
			throw new CmdSyntaxError(".proxy remove <name>");

		if(!WURST.getProxyManager().remove(args[1]))
			throw new CmdError("Unknown proxy: " + args[1]);
		ChatUtils.message("Proxy removed: " + args[1]);
	}

	private void set(String[] args) throws CmdException
	{
		if(args.length < 2)
			throw new CmdSyntaxError(".proxy set <name>");

		if(!WURST.getProxyManager().setActiveProxy(args[1]))
			throw new CmdError("Unknown proxy: " + args[1]);
		ChatUtils.message("Active proxy set to: " + args[1]);
	}

	private void clear()
	{
		WURST.getProxyManager().clearActiveProxy();
		ChatUtils.message("Proxy cleared.");
	}

	private void list(String[] args)
	{
		List<ProxyConfig> proxies = WURST.getProxyManager().getAllProxies();
		ProxyConfig active = WURST.getProxyManager().getActiveProxy();

		ChatUtils.message("Proxies (" + proxies.size() + "):");
		for(ProxyConfig pc : proxies)
		{
			String activeMark = pc.equals(active) ? " \u00a7a[ACTIVE]\u00a7r"
				: "";
			String auth = pc.hasAuth() ? " (auth)" : "";
			ChatUtils.message("  " + pc.getName() + ": "
				+ pc.getType().getDisplayName() + " "
				+ pc.getHost() + ":" + pc.getPort() + auth + activeMark);
		}

		if(proxies.isEmpty())
			ChatUtils.message("  No proxies configured.");
	}
}
