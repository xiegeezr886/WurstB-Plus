/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.ArrayDeque;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PacketOutputListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.DontSaveState;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.FakePlayerEntity;

@DontSaveState
@SearchTags({"LagSwitch", "lag switch"})
public final class BlinkHack extends Hack
	implements UpdateListener, PacketOutputListener
{
	private final SliderSetting limit = new SliderSetting("Limit",
		"Automatically restarts Blink once the given number of packets have been suspended.\n\n"
			+ "0 = no limit",
		0, 0, 5000, 1, ValueDisplay.INTEGER.withLabel(0, "disabled"));

	private final CheckboxSetting allPackets = new CheckboxSetting(
		"All packets",
		"Also cancels attack, interact, and block place packets.\n"
			+ "Full freeze on the server side.",
		false);

	private final ArrayDeque<Packet<?>> packets = new ArrayDeque<>();
	private ServerboundMovePlayerPacket lastMovePacket;
	private int movePacketCount;
	private FakePlayerEntity fakePlayer;

	public BlinkHack()
	{
		super("Blink");
		setCategory(Category.MOVEMENT);
		addSetting(limit);
		addSetting(allPackets);
	}

	@Override
	public String getRenderName()
	{
		if(limit.getValueI() == 0)
			return getName() + " [" + movePacketCount + "]";
		return getName() + " [" + movePacketCount + "/" + limit.getValueI()
			+ "]";
	}

	@Override
	protected boolean canEnable()
	{
		return MC.player != null && MC.level != null
			&& MC.getConnection() != null;
	}

	@Override
	protected void onEnable()
	{
		clearQueuedPackets();
		fakePlayer = new FakePlayerEntity();

		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(PacketOutputListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(PacketOutputListener.class, this);

		if(fakePlayer != null)
		{
			fakePlayer.despawn();
			fakePlayer = null;
		}

		flushQueuedPackets();
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null
			|| MC.getConnection() == null)
		{
			clearQueuedPackets();
			setEnabled(false);
			return;
		}

		if(limit.getValueI() == 0)
			return;

		if(movePacketCount >= limit.getValueI())
		{
			setEnabled(false);
			setEnabled(true);
		}
	}

	@Override
	public void onSentPacket(PacketOutputEvent event)
	{
		Packet<?> packet = event.getPacket();

		if(packet instanceof ServerboundMovePlayerPacket movePacket)
		{
			event.cancel();
			queueMovePacket(movePacket);
			return;
		}

		if(allPackets.isChecked()
			&& (packet instanceof ServerboundPlayerActionPacket
				|| packet instanceof ServerboundUseItemOnPacket
				|| packet instanceof ServerboundInteractPacket
				|| packet instanceof ServerboundUseItemPacket))
		{
			event.cancel();
			packets.addLast(packet);
		}
	}

	private void queueMovePacket(ServerboundMovePlayerPacket packet)
	{
		if(isDuplicateMovePacket(packet))
			return;

		packets.addLast(packet);
		lastMovePacket = packet;
		movePacketCount++;
	}

	private boolean isDuplicateMovePacket(ServerboundMovePlayerPacket packet)
	{
		return lastMovePacket != null
			&& packet.isOnGround() == lastMovePacket.isOnGround()
			&& packet.getYRot(-1) == lastMovePacket.getYRot(-1)
			&& packet.getXRot(-1) == lastMovePacket.getXRot(-1)
			&& packet.getX(-1) == lastMovePacket.getX(-1)
			&& packet.getY(-1) == lastMovePacket.getY(-1)
			&& packet.getZ(-1) == lastMovePacket.getZ(-1);
	}

	private void flushQueuedPackets()
	{
		ClientPacketListener networkHandler = MC.getConnection();
		if(networkHandler == null)
		{
			clearQueuedPackets();
			return;
		}

		try
		{
			while(!packets.isEmpty())
				networkHandler.send(packets.removeFirst());
		}finally
		{
			clearQueuedPackets();
		}
	}

	private void clearQueuedPackets()
	{
		packets.clear();
		lastMovePacket = null;
		movePacketCount = 0;
	}

	public void cancel()
	{
		clearQueuedPackets();
		if(fakePlayer != null && MC.player != null && MC.level != null)
			fakePlayer.resetPlayerPosition();
		setEnabled(false);
	}
}
