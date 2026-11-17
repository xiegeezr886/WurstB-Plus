/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.wurstclient.Feature;
import net.wurstclient.WurstClient;
import net.wurstclient.command.CmdError;
import net.wurstclient.command.CmdSyntaxError;
import net.wurstclient.settings.Setting;

public enum CmdUtils
{
	;
	
	private static final Minecraft MC = WurstClient.MC;
	
	public static Feature findFeature(String name) throws CmdError
	{
		WurstClient wurst = WurstClient.INSTANCE;
		Stream<Feature> hudFeatures = wurst.getHudManager() == null
			? Stream.empty() : wurst.getHudManager().getAllFeatures().stream();
		Stream<Feature> stream = Stream.of(wurst.getHax().getAllHax().stream(),
			wurst.getCmds().getAllCmds().stream(),
			wurst.getOtfs().getAllOtfs().stream(), hudFeatures).flatMap(s -> s);
		stream = stream.filter(f -> name.equalsIgnoreCase(f.getName()));
		Feature feature = stream.findFirst().orElse(null);
		
		if(feature == null)
			throw new CmdError(
				"A feature named \"" + name + "\" could not be found.");
		
		return feature;
	}
	
	public static Setting findSetting(Feature feature, String name)
		throws CmdError
	{
		String query = name.replace("_", " ").toLowerCase();
		
		// Top-level settings and their aliases keep working as before.
		for(Setting setting : feature.getSettings().values())
			if(setting.matchesName(query))
				return setting;
		
		String[] path = query.split("/");
		if(path.length > 1)
			return findSettingByPath(feature, path, name);

		List<SettingMatch> matches = new ArrayList<>();
		collectSettingMatches(feature.getSettings().values(), "", query,
			matches);

		if(matches.size() == 1)
			return matches.get(0).setting();

		if(matches.size() > 1)
			throw new CmdError("Multiple settings named \"" + name + "\" in "
				+ feature.getName() + ": " + formatSettingPaths(matches)
				+ ". Use the full path, e.g. " + matches.get(0).path() + ".");

		throw new CmdError("A setting named \"" + query
			+ "\" could not be found in " + feature.getName() + ".");
	}

	private static Setting findSettingByPath(Feature feature, String[] path,
		String originalName) throws CmdError
	{
		Iterable<Setting> level = feature.getSettings().values();
		Setting current = null;

		for(String segment : path)
		{
			List<Setting> matches = new ArrayList<>();
			for(Setting setting : level)
				if(setting.matchesName(segment))
					matches.add(setting);

			if(matches.isEmpty())
				throw new CmdError("A setting named \"" + originalName
					+ "\" could not be found in " + feature.getName() + ".");

			if(matches.size() > 1)
				throw new CmdError("Ambiguous setting path segment \"" + segment
					+ "\" in " + feature.getName() + ".");

			current = matches.get(0);
			level = current.getChildren();
		}

		return current;
	}

	private static void collectSettingMatches(Iterable<Setting> settings,
		String parentPath, String query, List<SettingMatch> matches)
	{
		for(Setting setting : settings)
		{
			String path = parentPath.isEmpty() ? setting.getName()
				: parentPath + "/" + setting.getName();

			if(setting.matchesName(query))
				matches.add(new SettingMatch(setting, path));

			collectSettingMatches(setting.getChildren(), path, query, matches);
		}
	}

	private static String formatSettingPaths(List<SettingMatch> matches)
	{
		StringBuilder result = new StringBuilder();
		for(SettingMatch match : matches)
		{
			if(result.length() > 0)
				result.append(", ");
			result.append(match.path());
		}
		return result.toString();
	}

	private record SettingMatch(Setting setting, String path)
	{
	}
	
	public static Item parseItem(String nameOrId) throws CmdSyntaxError
	{
		Item item = ItemUtils.getItemFromNameOrID(nameOrId);
		
		if(item == null)
			throw new CmdSyntaxError(
				"\"" + nameOrId + "\" is not a valid item.");
		
		return item;
	}
	
	public static void giveItem(ItemStack stack) throws CmdError
	{
		Inventory inventory = MC.player.getInventory();
		int slot = inventory.getFreeSlot();
		if(slot < 0)
			throw new CmdError("Cannot give item. Your inventory is full.");
		
		InventoryUtils.setCreativeStack(slot, stack);
	}
}
