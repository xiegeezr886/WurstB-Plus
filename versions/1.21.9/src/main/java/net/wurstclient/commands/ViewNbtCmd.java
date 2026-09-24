/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.commands;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.wurstclient.SearchTags;
import net.wurstclient.command.CmdError;
import net.wurstclient.command.CmdException;
import net.wurstclient.command.CmdSyntaxError;
import net.wurstclient.command.Command;
import net.wurstclient.util.ChatUtils;
import net.minecraft.resources.RegistryOps;

@SearchTags({"view nbt", "NBTViewer", "nbt viewer"})
public final class ViewNbtCmd extends Command
{
	public ViewNbtCmd()
	{
		super("viewnbt", "Shows you the NBT data of an item.", ".viewnbt",
			"Copy to clipboard: .viewnbt copy");
	}
	
	@Override
	public void call(String[] args) throws CmdException
	{
		LocalPlayer player = MC.player;
		ItemStack stack = player.getInventory().getSelectedItem();
		if(stack.isEmpty())
			throw new CmdError("You must hold an item in your main hand.");
		
		// 26.1.2 removed ItemStack.save(); serialise through the
		// item codec against the player's registries instead.
		RegistryOps<Tag> ops = player.registryAccess()
			.createSerializationContext(NbtOps.INSTANCE);
		Tag tag =
			ItemStack.CODEC.encodeStart(ops, stack).result().orElse(null);
		String nbt = tag != null ? tag.toString() : "N/A";
		
		switch(String.join(" ", args).toLowerCase())
		{
			case "":
			ChatUtils.message("NBT data: " + nbt);
			break;
			
			case "copy":
			MC.keyboardHandler.setClipboard(nbt);
			ChatUtils.message("NBT data copied to clipboard.");
			break;
			
			default:
			throw new CmdSyntaxError();
		}
	}
}
