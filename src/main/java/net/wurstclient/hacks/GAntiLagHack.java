/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from InvincibleMachineGun v1.9.0 (GPL-3.0).
package net.wurstclient.hacks;

import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PacketInputListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.ServerEquivTp;

@SearchTags({"g anti lag", "gantilag", "anti pullback", "no setback",
	"防回拉"})
public final class GAntiLagHack extends Hack
	implements UpdateListener, PacketInputListener
{
	private final SliderSetting range = new SliderSetting("Range",
		"Only position packets closer than this are cancelled.", 100, 0.1, 200,
		0.1, ValueDisplay.DECIMAL.withSuffix(" blocks"));

	private final SliderSetting limitPerSecond = new SliderSetting(
		"Limit per second", "Maximum number of blocked pullbacks per second.",
		12, 1, 20, 1, ValueDisplay.INTEGER);

	private final CheckboxSetting dedup = new CheckboxSetting(
		"Deduplicate same tick",
		"Only answer the first pullback inside a short time window.", true);

	private final SliderSetting dedupWindowMs = new SliderSetting(
		"Dedup window", "How long packets still count as the same tick.", 50,
		10, 1000, 10, ValueDisplay.INTEGER.withSuffix("ms"));

	private final SliderSetting dedupMax = new SliderSetting(
		"Max answers per window",
		"Maximum number of pullbacks answered inside one window.", 1, 1, 20, 1,
		ValueDisplay.INTEGER);

	private final SliderSetting packetDistance = new SliderSetting(
		"Packet distance",
		"Distance covered by each movement packet when the"
			+ " TPServerEquivalent gate is active.",
		10, 1, 100, 0.5, ValueDisplay.DECIMAL.withSuffix(" blocks"));

	private int lagCounter;
	private long lastResetTime;
	private long lastHandleNanos;
	private int handledInWindow;

	public GAntiLagHack()
	{
		super("GAntiLag");
		setCategory(Category.MOVEMENT);
		addSetting(range);
		addSetting(limitPerSecond);
		addSetting(dedup);
		addSetting(dedupWindowMs);
		addSetting(dedupMax);
		addSetting(packetDistance);
	}

	@Override
	protected void onEnable()
	{
		lagCounter = 0;
		lastResetTime = System.currentTimeMillis();
		lastHandleNanos = 0;
		handledInWindow = 0;
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(PacketInputListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(PacketInputListener.class, this);
	}

	@Override
	public void onUpdate()
	{
		long now = System.currentTimeMillis();
		if(now - lastResetTime >= 1000)
		{
			lagCounter = 0;
			lastResetTime = now;
		}
	}

	@Override
	public void onReceivedPacket(PacketInputEvent event)
	{
		if(MC.player == null || MC.level == null
			|| MC.getConnection() == null
			|| !(event.getPacket() instanceof ClientboundPlayerPositionPacket packet))
			return;

		if(lagCounter > limitPerSecond.getValueI())
			return;

		Vec3 serverPos =
			new Vec3(packet.getX(), packet.getY(), packet.getZ());
		Vec3 playerPos = MC.player.position();

		if(playerPos.distanceTo(serverPos) > range.getValue())
			return;

		boolean serverFree = ServerEquivTp.canStand(serverPos);
		boolean playerFree = ServerEquivTp.canStand(playerPos);
		if(!serverFree && !playerFree)
			return;

		if(dedup.isChecked())
		{
			long now = System.nanoTime();
			long windowNs =
				(long)Math.max(1, dedupWindowMs.getValueI()) * 1_000_000L;

			if(lastHandleNanos != 0 && now - lastHandleNanos < windowNs)
			{
				if(handledInWindow >= dedupMax.getValueI())
				{
					event.cancel();
					MC.getConnection()
						.send(new ServerboundAcceptTeleportationPacket(
							packet.getId()));
					return;
				}
			}else
				handledInWindow = 0;

			lastHandleNanos = now;
			handledInWindow++;
		}

		event.cancel();
		MC.getConnection().send(
			new ServerboundAcceptTeleportationPacket(packet.getId()));

		if(serverFree)
			ServerEquivTp.sendPath("GAntiLag", serverPos, playerPos,
				packetDistance.getValue());
		else
			ServerEquivTp.sendPos(playerPos.x, playerPos.y, playerPos.z,
				MC.player.onGround());

		MC.player.setPos(playerPos.x, playerPos.y, playerPos.z);
		lagCounter++;
	}
}
