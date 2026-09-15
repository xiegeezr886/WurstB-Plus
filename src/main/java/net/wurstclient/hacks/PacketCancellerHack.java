/*
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.LinkedHashSet;
import java.util.Set;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PacketInputListener;
import net.wurstclient.events.PacketOutputListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;

@SearchTags({"packet cancel", "anti packet kick", "packet filter"})
public final class PacketCancellerHack extends Hack
	implements PacketInputListener, PacketOutputListener
{
	private final CheckboxSetting cancelBossEvent =
		new CheckboxSetting("Cancel Boss Event", false);
	private final CheckboxSetting cancelEntityData =
		new CheckboxSetting("Cancel Entity Data", false);
	private final CheckboxSetting cancelMovement =
		new CheckboxSetting("Cancel Movement", false);
	private final CheckboxSetting cancelPlayerInfo =
		new CheckboxSetting("Cancel Player Info", false);
	private final CheckboxSetting cancelPassengers =
		new CheckboxSetting("Cancel Passengers", false);

	private final Set<Class<?>> cancelledTypes = new LinkedHashSet<>();

	public PacketCancellerHack()
	{
		super("PacketCanceller");
		setCategory(Category.OTHER);
		addSetting(cancelBossEvent);
		addSetting(cancelEntityData);
		addSetting(cancelMovement);
		addSetting(cancelPlayerInfo);
		addSetting(cancelPassengers);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(PacketInputListener.class, this);
		EVENTS.add(PacketOutputListener.class, this);
		updateCancelledTypes();
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(PacketInputListener.class, this);
		EVENTS.remove(PacketOutputListener.class, this);
		cancelledTypes.clear();
	}

	private void updateCancelledTypes()
	{
		cancelledTypes.clear();
		if(cancelBossEvent.isChecked())
			cancelledTypes.add(ClientboundBossEventPacket.class);
		if(cancelEntityData.isChecked())
			cancelledTypes.add(ClientboundSetEntityDataPacket.class);
		if(cancelMovement.isChecked())
			cancelledTypes.add(ClientboundMoveEntityPacket.class);
		if(cancelPlayerInfo.isChecked())
			cancelledTypes.add(ClientboundPlayerInfoUpdatePacket.class);
		if(cancelPassengers.isChecked())
			cancelledTypes.add(ClientboundSetPassengersPacket.class);
	}

	@Override
	public void onReceivedPacket(PacketInputEvent event)
	{
		updateCancelledTypes();
		if(isCancelled(event.getPacket()))
			event.cancel();
	}

	@Override
	public void onSentPacket(PacketOutputEvent event)
	{
		updateCancelledTypes();
		if(isCancelled(event.getPacket()))
			event.cancel();
	}

	/**
	 * 用 {@code isInstance} 判断而不是 {@code getClass()} 相等。
	 *
	 * <p>
	 * "Cancel Movement" 勾选后加进来的是抽象类
	 * {@code ClientboundMoveEntityPacket}（见 {@link #updateCancelledTypes()}），
	 * 而服务器真正发出来的是它的子类 {@code ClientboundMoveEntityPacket.Pos} /
	 * {@code .Rot} / {@code .PosRot}。旧实现用
	 * {@code cancelledTypes.contains(event.getPacket().getClass())} 比较，
	 * 抽象父类永远不等于子类 ⇒ 这个开关一个移动包都取消不了，
	 * 表现为"勾了没用"。
	 */
	private boolean isCancelled(Packet<?> packet)
	{
		for(Class<?> type : cancelledTypes)
			if(type.isInstance(packet))
				return true;

		return false;
	}
}
