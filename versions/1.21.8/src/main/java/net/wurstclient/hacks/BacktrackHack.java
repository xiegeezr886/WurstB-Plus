/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 *
 * Ported from InvincibleMachineGun (GPL-3.0).
 */
package net.wurstclient.hacks;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PacketInputListener;
import net.wurstclient.events.PacketOutputListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

@SearchTags({"backtrack", "lag", "reach", "fake lag"})
public final class BacktrackHack extends Hack
	implements UpdateListener, PacketInputListener, PacketOutputListener
{
	private final EnumSetting<Mode> mode = new EnumSetting<>("Mode",
		"\u00a7lLagBehind\u00a7r - Releases queued entity movements gradually.\n"
			+ "\u00a7lFreeze\u00a7r - Applies all queued movements at once.",
		Mode.values(), Mode.LAG_BEHIND);

	private final CheckboxSetting smart = new CheckboxSetting("Smart",
		"Only backtracks while a target is nearby.", true);

	private final SliderSetting minRange = new SliderSetting("Min range",
		"Minimum distance for an entity to be tracked.", 2.5, 0.5, 10, 0.1,
		ValueDisplay.DECIMAL);

	private final SliderSetting maxRange = new SliderSetting("Max range",
		"Maximum distance for an entity to be tracked.", 7.5, 0.5, 10, 0.1,
		ValueDisplay.DECIMAL);

	private final SliderSetting maxReach = new SliderSetting("Max reach",
		"Drops attacks that would be sent from too far away.", 4, 0.05, 6, 0.05,
		ValueDisplay.DECIMAL);

	private final SliderSetting minLagMs = new SliderSetting("Min lag",
		"Minimum simulated lag in milliseconds.", 125, 5, 5000, 5,
		ValueDisplay.INTEGER.withSuffix(" ms"));

	private final SliderSetting maxLagMs = new SliderSetting("Max lag",
		"Maximum simulated lag in milliseconds.", 275, 5, 5000, 5,
		ValueDisplay.INTEGER.withSuffix(" ms"));

	private final SliderSetting maxPacketData = new SliderSetting(
		"Max packet data", "Maximum queued movement packets per entity.", 7, 1,
		100, 1, ValueDisplay.INTEGER);

	private final CheckboxSetting targetMovements = new CheckboxSetting(
		"Movements", "Delay outgoing movement packets.", true);

	private final CheckboxSetting targetSwings = new CheckboxSetting("Swings",
		"Delay outgoing swing packets.", true);

	private final CheckboxSetting targetAttacks = new CheckboxSetting("Attacks",
		"Delay outgoing attack packets.", true);

	private final CheckboxSetting targetActions = new CheckboxSetting(
		"Entity actions", "Delay outgoing player command packets.", true);

	private final CheckboxSetting targetDigs = new CheckboxSetting("Digs",
		"Delay outgoing player action packets.", true);

	private final CheckboxSetting targetPlacements = new CheckboxSetting(
		"Placements", "Delay outgoing item use packets.", true);

	private final CheckboxSetting targetTransactions = new CheckboxSetting(
		"Transactions", "Delay outgoing pong packets.", true);

	private final CheckboxSetting targetKeepAlives = new CheckboxSetting(
		"Keep-alives", "Delay outgoing keep-alive packets.", true);

	private final List<BacktrackData> packetData =
		new CopyOnWriteArrayList<>();
	private final List<TimedPacket> inboundPackets =
		new CopyOnWriteArrayList<>();
	private final List<TimedPacket> outboundPackets =
		new CopyOnWriteArrayList<>();

	private int randomizedMilliseconds;
	private long lastResetTime;
	private boolean shouldBacktrackSmart;
	private boolean isSending;

	public BacktrackHack()
	{
		super("Backtrack");
		setCategory(Category.COMBAT);
		addSetting(mode);
		addSetting(smart);
		addSetting(minRange);
		addSetting(maxRange);
		addSetting(maxReach);
		addSetting(minLagMs);
		addSetting(maxLagMs);
		addSetting(maxPacketData);
		addSetting(targetMovements);
		addSetting(targetSwings);
		addSetting(targetAttacks);
		addSetting(targetActions);
		addSetting(targetDigs);
		addSetting(targetPlacements);
		addSetting(targetTransactions);
		addSetting(targetKeepAlives);
	}

	@Override
	protected void onEnable()
	{
		resetTimer();
		packetData.clear();
		inboundPackets.clear();
		outboundPackets.clear();
		shouldBacktrackSmart = false;
		isSending = false;

		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(PacketInputListener.class, this);
		EVENTS.add(PacketOutputListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(PacketInputListener.class, this);
		EVENTS.remove(PacketOutputListener.class, this);
		flushAll();
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null)
			return;

		long now = System.currentTimeMillis();

		if(smart.isChecked())
		{
			double closestDistance = Double.MAX_VALUE;
			for(BacktrackData data : packetData)
			{
				Entity entity = MC.level.getEntity(data.entityId);
				if(entity == null)
					continue;

				closestDistance =
					Math.min(closestDistance, MC.player.distanceTo(entity));
			}

			if(closestDistance >= maxRange.getValue())
				shouldBacktrackSmart = false;

			if(!shouldBacktrackSmart)
			{
				flushAll();
				return;
			}
		}

		if(now - lastResetTime >= randomizedMilliseconds)
		{
			releaseTimedPackets(now);

			if(mode.getSelected() == Mode.FREEZE)
			{
				for(BacktrackData data : packetData)
				{
					for(Packet<?> packet : data.movements)
						applyInbound(packet);
					data.movements.clear();
				}
			}

			resetTimer();
		}

		if(mode.getSelected() != Mode.LAG_BEHIND)
			return;

		int releaseThreshold = Math.max(1, randomizedMilliseconds / 50);
		for(BacktrackData data : packetData)
		{
			if(data.movements.size() < releaseThreshold)
				continue;

			if(!data.movements.isEmpty())
				applyInbound(data.movements.remove(0));
		}
	}

	private void releaseTimedPackets(long now)
	{
		for(TimedPacket timed : inboundPackets)
		{
			if(now - timed.time < randomizedMilliseconds)
				continue;

			inboundPackets.remove(timed);
			applyInbound(timed.packet);
		}

		Vec3 playerPos = MC.player.position();
		for(TimedPacket timed : outboundPackets)
		{
			if(now - timed.time < randomizedMilliseconds)
				continue;

			outboundPackets.remove(timed);

			if(timed.packet instanceof ServerboundInteractPacket)
			{
				boolean outOfReach = false;
				for(BacktrackData data : packetData)
				{
					if(data.actualPosition == null)
						continue;

					if(data.actualPosition.distanceTo(playerPos) >= maxReach
						.getValue())
					{
						outOfReach = true;
						break;
					}
				}

				if(outOfReach)
					continue;
			}

			sendOutbound(timed.packet);
		}
	}

	@Override
	public void onSentPacket(PacketOutputEvent event)
	{
		if(isSending)
			return;

		Packet<?> packet = event.getPacket();
		boolean intercept = targetMovements.isChecked()
			&& packet instanceof ServerboundMovePlayerPacket;

		if(!intercept)
			intercept = targetSwings.isChecked()
				&& packet instanceof ServerboundSwingPacket;
		if(!intercept)
			intercept = targetActions.isChecked()
				&& packet instanceof ServerboundPlayerCommandPacket;
		if(!intercept)
			intercept = targetDigs.isChecked()
				&& packet instanceof ServerboundPlayerActionPacket;
		if(!intercept)
			intercept = targetPlacements.isChecked()
				&& (packet instanceof ServerboundUseItemOnPacket
					|| packet instanceof ServerboundUseItemPacket);
		if(!intercept)
			intercept = targetTransactions.isChecked()
				&& packet instanceof ServerboundPongPacket;
		if(!intercept)
			intercept = targetKeepAlives.isChecked()
				&& packet instanceof ServerboundKeepAlivePacket;
		if(!intercept)
			intercept = targetAttacks.isChecked()
				&& packet instanceof ServerboundInteractPacket;

		if(!intercept)
			return;

		if(packet instanceof ServerboundInteractPacket)
			shouldBacktrackSmart = true;

		outboundPackets.add(new TimedPacket(packet));
		event.cancel();
	}

	@Override
	public void onReceivedPacket(PacketInputEvent event)
	{
		if(MC.player == null || MC.level == null)
			return;

		Packet<?> packet = event.getPacket();
		Entity entity;

		if(packet instanceof ClientboundMoveEntityPacket move)
			entity = move.getEntity(MC.level);
		else if(packet instanceof ClientboundEntityPositionSyncPacket sync)
			entity = MC.level.getEntity(sync.id());
		else if(packet instanceof ClientboundSetEntityDataPacket data)
			entity = MC.level.getEntity(data.id());
		else
			return;

		if(entity == null || entity == MC.player)
			return;

		double distance = MC.player.distanceTo(entity);
		if(distance < minRange.getValue() || distance > maxRange.getValue())
			return;

		BacktrackData data = retrieveData(entity.getId());
		data.actualPosition = entity.position();

		if(data.movements.size() > maxPacketData.getValueI())
			return;

		data.movements.add(packet);
		event.cancel();
	}

	private void flushAll()
	{
		for(BacktrackData data : packetData)
		{
			for(Packet<?> packet : data.movements)
				applyInbound(packet);
		}
		packetData.clear();

		for(TimedPacket timed : inboundPackets)
			applyInbound(timed.packet);
		inboundPackets.clear();

		for(TimedPacket timed : outboundPackets)
			sendOutbound(timed.packet);
		outboundPackets.clear();
	}

	private void resetTimer()
	{
		lastResetTime = System.currentTimeMillis();
		int min = minLagMs.getValueI();
		int max = Math.max(min, maxLagMs.getValueI());
		randomizedMilliseconds = min + (int)(Math.random() * (max - min + 1));
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private void applyInbound(Packet<?> packet)
	{
		ClientPacketListener connection = MC.getConnection();
		if(connection == null)
			return;

		((Packet)packet).handle(connection);
	}

	private void sendOutbound(Packet<?> packet)
	{
		ClientPacketListener connection = MC.getConnection();
		if(connection == null)
			return;

		isSending = true;
		try
		{
			connection.send(packet);
		}finally
		{
			isSending = false;
		}
	}

	private BacktrackData retrieveData(int entityId)
	{
		for(BacktrackData data : packetData)
			if(data.entityId == entityId)
				return data;

		BacktrackData data = new BacktrackData(entityId);
		packetData.add(data);
		return data;
	}

	private final class BacktrackData
	{
		private final int entityId;
		private Vec3 actualPosition;
		private final List<Packet<?>> movements =
			new CopyOnWriteArrayList<>();

		private BacktrackData(int entityId)
		{
			this.entityId = entityId;
		}
	}

	private static final class TimedPacket
	{
		private final Packet<?> packet;
		private final long time = System.currentTimeMillis();

		private TimedPacket(Packet<?> packet)
		{
			this.packet = packet;
		}
	}

	private enum Mode
	{
		LAG_BEHIND("LagBehind"),
		FREEZE("Freeze");

		private final String name;

		Mode(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}
}
