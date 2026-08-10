/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.StopUsingItemListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

@SearchTags({"arrow dmg", "ArrowDamage", "arrow damage"})
public final class ArrowDmgHack extends Hack implements StopUsingItemListener
{
	private final SliderSetting packets = new SliderSetting("Packets",
		"description.wurst.setting.arrowdmg.packets", 200, 2, 7000, 20,
		ValueDisplay.INTEGER);
	
	private final CheckboxSetting yeetTridents =
		new CheckboxSetting("Trident yeet mode",
			"description.wurst.setting.arrowdmg.trident_yeet_mode", false);
	
	public ArrowDmgHack()
	{
		super("ArrowDMG");
		setCategory(Category.COMBAT);
		addSetting(packets);
		addSetting(yeetTridents);
	}
	
	@Override
	protected void onEnable()
	{
		EVENTS.add(StopUsingItemListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(StopUsingItemListener.class, this);
	}
	
	@Override
	public void onStopUsingItem()
	{
		LocalPlayer player = MC.player;
		ClientPacketListener netHandler = player.connection;
		
		if(!isValidItem(player.getMainHandItem().getItem()))
			return;
		
		netHandler.send(
			new ServerboundPlayerCommandPacket(player, Action.START_SPRINTING));
		
		double x = player.getX();
		double y = player.getY();
		double z = player.getZ();
		
		for(int i = 0; i < packets.getValueI() / 2; i++)
		{
			netHandler.send(new ServerboundMovePlayerPacket.Pos(x, y - 1e-10, z,
				true, false));
			netHandler.send(new ServerboundMovePlayerPacket.Pos(x, y + 1e-10, z,
				false, false));
		}
	}
	
	private boolean isValidItem(Item item)
	{
		if(yeetTridents.isChecked() && item == Items.TRIDENT)
			return true;
		
		return item == Items.BOW;
	}
}
