/*
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.commands;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.wurstclient.DontBlock;
import net.wurstclient.command.CmdError;
import net.wurstclient.command.CmdException;
import net.wurstclient.command.CmdSyntaxError;
import net.wurstclient.command.Command;
import net.wurstclient.waypoints.Waypoint;
import net.wurstclient.util.ChatUtils;

@DontBlock
public final class WaypointsCmd extends Command
{
	public WaypointsCmd()
	{
		super("waypoints",
			"Allows you to manage waypoints.",
			".waypoints add <name> [<x> <y> <z>] [color]",
			".waypoints remove <name>", ".waypoints list [<page>]",
			"Default color is aqua (0xFF00FFFF).",
			"Use a hex color like 0xFFFF0000 for red.");
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

			case "list":
			list(args);
			break;

			default:
			throw new CmdSyntaxError();
		}
	}

	private void add(String[] args) throws CmdException
	{
		if(args.length < 2)
			throw new CmdSyntaxError(
				".waypoints add <name> [<x> <y> <z>] [color]");

		String name = args[1];
		BlockPos pos;
		int color = 0xFF00FFFF;

		if(args.length >= 5)
		{
			try
			{
				int x = Integer.parseInt(args[2]);
				int y = Integer.parseInt(args[3]);
				int z = Integer.parseInt(args[4]);
				pos = new BlockPos(x, y, z);
			}catch(NumberFormatException e)
			{
				throw new CmdSyntaxError("Invalid coordinates.");
			}
		}else
		{
			pos = MC.player.blockPosition();
		}

		if(args.length >= 6)
		{
			try
			{
				color = (int)Long.parseLong(args[5]
					.replace("0x", "").replace("0X", ""), 16);
			}catch(NumberFormatException e)
			{
				throw new CmdSyntaxError("Invalid color.");
			}
		}

		Identifier dim = MC.level.dimension().identifier();
		try
		{
			WURST.getWaypointsManager()
				.add(new Waypoint(name, dim, pos, color));
			ChatUtils.message("Waypoint added: " + name);
		}catch(NullPointerException e)
		{
			throw new CmdError("Waypoints system not available.");
		}
	}

	private void remove(String[] args) throws CmdException
	{
		if(args.length < 2)
			throw new CmdSyntaxError(".waypoints remove <name>");

		WURST.getWaypointsManager().remove(args[1]);
		ChatUtils.message("Waypoint removed: " + args[1]);
	}

	private void list(String[] args) throws CmdException
	{
		int page = parsePage(args);
		List<Waypoint> wps = WURST.getWaypointsManager().getAllWaypoints();

		int pages = Math.max(1, (int)Math.ceil(wps.size() / 8.0));
		page = Math.max(0, Math.min(page, pages - 1));
		int rows = Math.min(8, Math.max(0, wps.size() - page * 8));

		ChatUtils.message(
			"Total: " + wps.size() + (wps.size() == 1 ? " waypoint" : " waypoints"));
		for(int i = page * 8; i < page * 8 + rows; i++)
		{
			Waypoint wp = wps.get(i);
			String colorHex = String.format("#%06X", wp.getColor() & 0xFFFFFF);
			ChatUtils.message(wp.getName() + ": " + wp.getPos().toShortString()
				+ " [" + wp.getDimension().getPath() + "] " + colorHex);
		}

		ChatUtils.message("Page: " + (page + 1) + "/" + pages);
	}

	private int parsePage(String[] args) throws CmdSyntaxError
	{
		if(args.length < 2)
			return 0;
		try
		{
			return Integer.parseInt(args[1]) - 1;
		}catch(NumberFormatException e)
		{
			throw new CmdSyntaxError("Invalid page number.");
		}
	}
}
