/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.commands;

import net.minecraft.core.BlockPos;
import net.wurstclient.command.CmdError;
import net.wurstclient.command.CmdException;
import net.wurstclient.command.CmdSyntaxError;
import net.wurstclient.command.Command;
import net.wurstclient.hacks.PerimeterDiggerHack;
import net.wurstclient.perimeter.PerimeterArea;
import net.wurstclient.util.ChatUtils;
import net.wurstclient.util.MathUtils;

/**
 * Drives the {@link PerimeterDiggerHack}: plan an inclusive rectangular region,
 * then start, pause, resume, inspect or stop the dig.
 */
public final class PerimeterCmd extends Command
{
	public PerimeterCmd()
	{
		super("perimeter",
			"Automates large-scale perimeter excavation.",
			".perimeter plan rectangle <x0> <z0> <x1> <z1> <minY> <maxY>",
			".perimeter start | pause | resume | stop | status | clear",
			"/perimeterdig for detection, fluids, unloading points, supplies",
			"and the full automation settings");
	}
	
	@Override
	public void call(String[] args) throws CmdException
	{
		PerimeterDiggerHack digger = WURST.getHax().perimeterDiggerHack;
		
		if(args.length == 0)
		{
			status(digger);
			return;
		}
		
		String subcommand = args[0].toLowerCase();
		
		if(subcommand.equals("plan"))
			plan(digger, args);
		else if(subcommand.equals("start"))
			digger.start();
		else if(subcommand.equals("pause"))
			digger.pause();
		else if(subcommand.equals("resume"))
			digger.resume();
		else if(subcommand.equals("stop") || subcommand.equals("clear"))
			digger.stop();
		else if(subcommand.equals("status"))
			status(digger);
		else if(subcommand.equals("help"))
			printHelp();
		else
			throw new CmdSyntaxError("Unknown subcommand: " + args[0]);
	}
	
	private void status(PerimeterDiggerHack digger)
	{
		ChatUtils.message("Perimeter: " + digger.describeStatus());
	}
	
	/**
	 * {@code .perimeter plan rectangle <x0> <z0> <x1> <z1> <minY> <maxY>}, with
	 * both XZ corners and both Y limits inclusive. Coordinates accept
	 * {@code ~} and {@code ~<offset>} like the other Wurst commands.
	 */
	private void plan(PerimeterDiggerHack digger, String[] args)
		throws CmdException
	{
		if(args.length < 2 || !args[1].equalsIgnoreCase("rectangle"))
			throw new CmdSyntaxError(
				"Usage: .perimeter plan rectangle <x0> <z0> <x1> <z1>"
					+ " <minY> <maxY>");
		
		if(args.length != 8)
			throw new CmdSyntaxError();
		
		if(MC.player == null)
			throw new CmdError("Player not available.");
		
		BlockPos playerPos = BlockPos.containing(MC.player.position());
		
		int x0 = parseCoord(args[2], playerPos.getX());
		int z0 = parseCoord(args[3], playerPos.getZ());
		int x1 = parseCoord(args[4], playerPos.getX());
		int z1 = parseCoord(args[5], playerPos.getZ());
		int minY = parseCoord(args[6], playerPos.getY());
		int maxY = parseCoord(args[7], playerPos.getY());
		
		if(minY > maxY)
			throw new CmdSyntaxError(
				"minY (" + minY + ") must not be above maxY (" + maxY + ").");
		
		digger.plan(PerimeterArea.fromXZ(x0, z0, x1, z1, minY, maxY));
	}
	
	private int parseCoord(String input, int relativeTo) throws CmdSyntaxError
	{
		if(MathUtils.isInteger(input))
			return Integer.parseInt(input);
		
		if(input.equals("~"))
			return relativeTo;
		
		if(input.startsWith("~") && MathUtils.isInteger(input.substring(1)))
			return relativeTo + Integer.parseInt(input.substring(1));
		
		throw new CmdSyntaxError("Invalid coordinate: " + input);
	}
}
