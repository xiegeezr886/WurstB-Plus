/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.commands;

import java.util.Arrays;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.wurstclient.command.CmdError;
import net.wurstclient.command.CmdException;
import net.wurstclient.command.CmdSyntaxError;
import net.wurstclient.command.Command;
import net.wurstclient.util.ChatUtils;
import net.wurstclient.util.InventoryUtils;

public final class ModifyCmd extends Command
{
	public ModifyCmd()
	{
		super("modify", "Allows you to modify NBT data of items.",
			".modify add <nbt_data>", ".modify set <nbt_data>",
			".modify remove <nbt_path>", "Use $ for colors, use $$ for $.", "",
			"Example:",
			".modify add {display:{Name:'{\"text\":\"$cRed Name\"}'}}",
			"(changes the item's name to \u00a7cRed Name\u00a7r)");
	}
	
	@Override
	public void call(String[] args) throws CmdException
	{
		LocalPlayer player = MC.player;
		
		if(!player.getAbilities().instabuild)
			throw new CmdError("仅限创造模式。");
		
		if(args.length < 2)
			throw new CmdSyntaxError();
		
		Inventory inventory = player.getInventory();
		int slot = inventory.getSelectedSlot();
		ItemStack stack = inventory.getSelectedItem();
		
		if(stack == null)
			throw new CmdError("You must hold an item in your main hand.");
		
		switch(args[0].toLowerCase())
		{
			case "add":
			add(stack, args);
			break;
			
			case "set":
			set(stack, args);
			break;
			
			case "remove":
			remove(stack, args);
			break;
			
			default:
			throw new CmdSyntaxError();
		}
		
		InventoryUtils.setCreativeStack(slot, stack);
		ChatUtils.message("物品已修改。");
	}
	
	private void add(ItemStack stack, String[] args) throws CmdError
	{
		String nbt = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
		nbt = nbt.replace("$", "\u00a7").replace("\u00a7\u00a7", "$");
		
		try
		{
			CompoundTag tag = TagParser.parseCompoundFully(nbt);
			CustomData.update(DataComponents.CUSTOM_DATA, stack,
				customData -> customData.merge(tag));
			
		}catch(CommandSyntaxException e)
		{
			ChatUtils.message(e.getMessage());
			throw new CmdError("NBT data is invalid.");
		}
	}
	
	private void set(ItemStack stack, String[] args) throws CmdError
	{
		String nbt = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
		nbt = nbt.replace("$", "\u00a7").replace("\u00a7\u00a7", "$");
		
		try
		{
			CompoundTag tag = TagParser.parseCompoundFully(nbt);
			stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
			
		}catch(CommandSyntaxException e)
		{
			ChatUtils.message(e.getMessage());
			throw new CmdError("NBT data is invalid.");
		}
	}
	
	private void remove(ItemStack stack, String[] args) throws CmdException
	{
		if(args.length > 2)
			throw new CmdSyntaxError();
		
		CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
		CompoundTag tag = customData == null ? null : customData.copyTag();
		NbtPath path = parseNbtPath(tag, args[1]);
		
		if(path == null)
			throw new CmdError("The path does not exist.");
		
		path.base.remove(path.key);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
	}
	
	private NbtPath parseNbtPath(CompoundTag tag, String path)
	{
		String[] parts = path.split("\\.");
		
		CompoundTag base = tag;
		if(base == null)
			return null;
		
		for(int i = 0; i < parts.length - 1; i++)
		{
			String part = parts[i];
			
			if(!base.contains(part) || !(base.get(part) instanceof CompoundTag))
				return null;
			
			base = base.getCompound(part).orElse(null);
			if(base == null)
				return null;
		}
		
		if(!base.contains(parts[parts.length - 1]))
			return null;
		
		return new NbtPath(base, parts[parts.length - 1]);
	}
	
	private static class NbtPath
	{
		public CompoundTag base;
		public String key;
		
		public NbtPath(CompoundTag base, String key)
		{
			this.base = base;
			this.key = key;
		}
	}
}
