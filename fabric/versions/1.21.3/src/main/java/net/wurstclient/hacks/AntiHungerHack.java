/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PacketOutputListener;
import net.wurstclient.events.PreMotionListener;
import net.wurstclient.events.WorldChangeListener;
import net.minecraft.client.multiplayer.ClientLevel;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.hack.DontSaveState;
import net.wurstclient.hack.Hack;
import net.wurstclient.util.PacketUtils;

@DontSaveState
@SearchTags({"anti hunger"})
public final class AntiHungerHack extends Hack implements PacketOutputListener,
	PreMotionListener, WorldChangeListener
{
	// Sprint and landing behavior adapted from Meteor Client (GPL-3.0).
	private final CheckboxSetting sprint = new CheckboxSetting("Spoof sprinting",
		"阻止发送开始疾跑的数据包。", true);
	private final CheckboxSetting onGround = new CheckboxSetting("Spoof on ground",
		"修改地面状态以减少饥饿消耗；保留落地时的数据包。", true);
	private boolean lastOnGround;
	private boolean preserveLanding;

	public AntiHungerHack()
	{
		super("AntiHunger");
		setCategory(Category.MOVEMENT);
		addSetting(sprint);
		addSetting(onGround);
	}
	
	@Override
	protected void onEnable()
	{
		WURST.getHax().noFallHack.setEnabled(false);
		EVENTS.add(PacketOutputListener.class, this);
		EVENTS.add(PreMotionListener.class, this);
		EVENTS.add(WorldChangeListener.class, this);
		onWorldChange(MC.level);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(PacketOutputListener.class, this);
		EVENTS.remove(PreMotionListener.class, this);
		EVENTS.remove(WorldChangeListener.class, this);
		preserveLanding = false;
	}
	
	@Override
	public void onSentPacket(PacketOutputEvent event)
	{
		if(MC.player == null || MC.gameMode == null)
			return;
		if(event.getPacket() instanceof ServerboundMovePlayerPacket && preserveLanding)
		{
			preserveLanding = false;
			return;
		}
		if(MC.player.isPassenger() || MC.player.isInWater() || MC.player.isUnderWater())
			return;
		if(sprint.isChecked() && event.getPacket() instanceof ServerboundPlayerCommandPacket command
			&& command.getAction() == ServerboundPlayerCommandPacket.Action.START_SPRINTING)
		{
			event.cancel();
			return;
		}
		if(!(event.getPacket() instanceof ServerboundMovePlayerPacket packet))
			return;
		
		if(!onGround.isChecked() || !MC.player.onGround() || MC.player.fallDistance > 0)
			return;
		
		if(MC.gameMode.isDestroying())
			return;
		
		event.setPacket(PacketUtils.modifyOnGround(packet, false));
	}

	@Override
	public void onPreMotion()
	{
		if(MC.player == null)
			return;
		if(MC.player.onGround() && !lastOnGround && onGround.isChecked())
			preserveLanding = true;
		lastOnGround = MC.player.onGround();
	}

	@Override
	public void onWorldChange(ClientLevel world)
	{
		lastOnGround = MC.player != null && MC.player.onGround();
		preserveLanding = false;
	}
}
