/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
// Ported from Meteor Client (GPL-3.0).
package net.wurstclient.hacks;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PacketInputListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.hud2.NotificationSeverity;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.ChatUtils;

@SearchTags({"notifier", "totem counter", "totem pops", "pearl notifier",
	"low health", "pearl land"})
public final class NotifierHack extends Hack
	implements UpdateListener, PacketInputListener
{
	private final CheckboxSetting lowHealth = new CheckboxSetting(
		"Low health",
		"Notifies you when your health drops below the threshold.", true);

	private final SliderSetting healthThreshold = new SliderSetting(
		"Health threshold",
		"Health points at which the low health warning triggers.", 6, 1,
		19, 1, ValueDisplay.INTEGER.withSuffix(" HP"));

	private final CheckboxSetting totemPops = new CheckboxSetting(
		"Totem pops", "Notifies you when a player pops a totem.", true);

	private final CheckboxSetting pearl = new CheckboxSetting(
		"Pearl landing",
		"Notifies you when an ender pearl lands.", true);

	private final CheckboxSetting ignoreOwn = new CheckboxSetting(
		"Ignore own", "Ignores your own totem pops and pearls.", false);

	private final CheckboxSetting ignoreFriends = new CheckboxSetting(
		"Ignore friends", "Ignores totem pops and pearls of friends.",
		true);

	private final CheckboxSetting hud = new CheckboxSetting(
		"HUD notifications",
		"Uses the HUD notification system instead of chat messages.", true);

	// Packets arrive on the netty thread; process them on the next tick so
	// that chat and HUD updates stay on the main thread.
	private final ConcurrentLinkedQueue<Packet<?>> incoming =
		new ConcurrentLinkedQueue<>();

	private final Map<UUID, Integer> totemPopMap = new HashMap<>();
	private final Map<Integer, PearlTracker> pearlMap = new HashMap<>();

	private ClientLevel lastLevel;
	private boolean lowHealthNotified;

	public NotifierHack()
	{
		super("Notifier");
		setCategory(Category.CHAT);
		addSetting(lowHealth);
		addSetting(healthThreshold);
		addSetting(totemPops);
		addSetting(pearl);
		addSetting(ignoreOwn);
		addSetting(ignoreFriends);
		addSetting(hud);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(PacketInputListener.class, this);
		reset();
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(PacketInputListener.class, this);
		reset();
	}

	@Override
	public void onReceivedPacket(PacketInputEvent event)
	{
		Packet<?> packet = event.getPacket();
		if(packet instanceof ClientboundEntityEventPacket
			|| packet instanceof ClientboundRemoveEntitiesPacket)
			incoming.add(packet);
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null)
		{
			reset();
			return;
		}

		if(MC.level != lastLevel)
		{
			lastLevel = MC.level;
			clearTrackers();
		}

		processPackets();
		checkLowHealth();
		checkTotemDeaths();
		trackPearls();
	}

	private void processPackets()
	{
		Packet<?> packet;
		while((packet = incoming.poll()) != null)
		{
			if(packet instanceof ClientboundEntityEventPacket event)
				handleEntityEvent(event);
			else if(packet instanceof ClientboundRemoveEntitiesPacket event)
				handleRemovedEntities(event);
		}
	}

	private void handleEntityEvent(ClientboundEntityEventPacket event)
	{
		if(!totemPops.isChecked()
			|| event.getEventId() != EntityEvent.TALISMAN_ACTIVATE
			|| MC.level == null)
			return;

		Entity entity = event.getEntity(MC.level);
		if(!(entity instanceof Player player))
			return;

		if(ignoreOwn.isChecked() && player == MC.player)
			return;

		if(ignoreFriends.isChecked()
			&& WURST.getFriends().isFriend(player))
			return;

		int pops = totemPopMap.merge(player.getUUID(), 1, Integer::sum);
		notify("Totem pop", player.getName().getString() + " popped "
			+ pops + (pops == 1 ? " totem." : " totems."));
	}

	private void handleRemovedEntities(ClientboundRemoveEntitiesPacket event)
	{
		for(int id : event.getEntityIds().toIntArray())
		{
			PearlTracker tracker = pearlMap.remove(id);
			if(tracker == null)
				continue;

			if(!pearl.isChecked())
				continue;

			if(ignoreOwn.isChecked() && tracker.own)
				continue;

			if(ignoreFriends.isChecked() && tracker.friend)
				continue;

			BlockPos pos = BlockPos.containing(tracker.lastPos);
			double distance =
				MC.player.position().distanceTo(tracker.lastPos);
			double travelled =
				tracker.startPos.distanceTo(tracker.lastPos);

			notify("Pearl landed", tracker.ownerName + "'s pearl landed at "
				+ pos.getX() + ", " + pos.getY() + ", " + pos.getZ()
				+ String.format(Locale.ROOT,
					" (%.1fm away, travelled %.1fm).", distance,
					travelled));
		}
	}

	private void checkLowHealth()
	{
		if(!lowHealth.isChecked())
		{
			lowHealthNotified = false;
			return;
		}

		float health = MC.player.getHealth();
		if(health <= 0)
		{
			lowHealthNotified = false;
			return;
		}

		int threshold = healthThreshold.getValueI();
		if(health <= threshold)
		{
			if(lowHealthNotified)
				return;

			notify("Low health", String.format(Locale.ROOT,
				"%.1f HP left.", health));
			lowHealthNotified = true;

		}else if(health > threshold + 2)
			lowHealthNotified = false;
	}

	private void checkTotemDeaths()
	{
		for(AbstractClientPlayer player : MC.level.players())
		{
			Integer pops = totemPopMap.get(player.getUUID());
			if(pops == null)
				continue;

			if(player.deathTime <= 0 && player.getHealth() > 0)
				continue;

			totemPopMap.remove(player.getUUID());
			notify("Totem pops", player.getName().getString()
				+ " died after popping " + pops
				+ (pops == 1 ? " totem." : " totems."));
		}
	}

	private void trackPearls()
	{
		if(!pearl.isChecked())
		{
			pearlMap.clear();
			return;
		}

		for(Entity entity : MC.level.entitiesForRendering())
		{
			if(!(entity instanceof ThrownEnderpearl enderPearl))
				continue;

			int id = enderPearl.getId();
			Vec3 position = enderPearl.position();
			PearlTracker tracker = pearlMap.get(id);
			if(tracker != null)
			{
				tracker.lastPos = position;
				continue;
			}

			Entity owner = enderPearl.getOwner();
			String ownerName =
				owner != null ? owner.getName().getString() : "Someone";
			boolean own = owner == MC.player;
			boolean friend = owner instanceof Player player
				&& WURST.getFriends().isFriend(player);

			pearlMap.put(id,
				new PearlTracker(ownerName, own, friend, position));
		}
	}

	private void notify(String title, String message)
	{
		if(hud.isChecked() && WURST.getHudManager() != null)
		{
			WURST.getHudManager().addNotification(title, message,
				NotificationSeverity.INFO);
			return;
		}

		ChatUtils.message(title + ": " + message);
	}

	private void clearTrackers()
	{
		totemPopMap.clear();
		pearlMap.clear();
		incoming.clear();
		lowHealthNotified = false;
	}

	private void reset()
	{
		incoming.clear();
		totemPopMap.clear();
		pearlMap.clear();
		lastLevel = null;
		lowHealthNotified = false;
	}

	private static final class PearlTracker
	{
		private final String ownerName;
		private final boolean own;
		private final boolean friend;
		private final Vec3 startPos;
		private Vec3 lastPos;

		private PearlTracker(String ownerName, boolean own, boolean friend,
			Vec3 startPos)
		{
			this.ownerName = ownerName;
			this.own = own;
			this.friend = friend;
			this.startPos = startPos;
			lastPos = startPos;
		}
	}
}
