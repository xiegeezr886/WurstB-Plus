/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 *
 * Ported from InvincibleMachineGun (GPL-3.0).
 */
package net.wurstclient.hacks;

import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.client.player.Input;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PacketInputListener;
import net.wurstclient.events.PacketOutputListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.ChatUtils;

@SearchTags({"anti lag", "antilag", "anti pull", "no pull", "rubberband"})
public final class AntiLagHack extends Hack
	implements UpdateListener, PacketInputListener, PacketOutputListener
{
	private final SliderSetting limitPerSecond = new SliderSetting(
		"Limit per second",
		"Maximum number of position packets sent per second.", 100, 1, 10000,
		1, ValueDisplay.INTEGER);

	private final SliderSetting range = new SliderSetting("Range",
		"Teleports farther away than this are not cancelled.", 100, 0.1, 2000,
		0.1, ValueDisplay.DECIMAL.withSuffix(" blocks"));

	private final CheckboxSetting antiPull = new CheckboxSetting("Anti pull",
		"Sends a position packet back after cancelling a teleport.", true);

	private final CheckboxSetting allowVoid = new CheckboxSetting(
		"Allow into void", "Also cancels teleports that put you into the void.",
		false);

	private final CheckboxSetting printWhenLimited = new CheckboxSetting(
		"Print rate limit", "Shows a chat warning when the packet limit is hit.",
		true);

	private final CheckboxSetting vClip = new CheckboxSetting("VClip",
		"Moves the player up when walking into a wall.", true);

	private final SliderSetting vClipStep = new SliderSetting("VClip step",
		"How far the player is moved up.", 1.8, 0.1, 5, 0.1,
		ValueDisplay.DECIMAL.withSuffix(" blocks"))
			.visibleWhen(vClip::isChecked);

	private int lagCounter;
	private long lastResetTime;
	private boolean sendingMove;
	private boolean warned;

	public AntiLagHack()
	{
		super("AntiLag");
		setCategory(Category.MOVEMENT);
		addSetting(limitPerSecond);
		addSetting(range);
		addSetting(antiPull);
		addSetting(allowVoid);
		addSetting(printWhenLimited);
		addSetting(vClip);
		addSetting(vClipStep);
	}

	@Override
	protected void onEnable()
	{
		lagCounter = 0;
		lastResetTime = System.currentTimeMillis();
		sendingMove = false;
		warned = false;
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
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null)
			return;

		long now = System.currentTimeMillis();
		if(now - lastResetTime >= 1000)
		{
			lagCounter = 0;
			lastResetTime = now;
			warned = false;
		}

		if(!vClip.isChecked() || MC.player.input == null)
			return;

		Input keys = MC.player.input;
		boolean moving = keys.up || keys.down || keys.left || keys.right;
		if(moving && MC.player.horizontalCollision)
			MC.player.setPos(MC.player.getX(),
				MC.player.getY() + vClipStep.getValue(), MC.player.getZ());
	}

	@Override
	public void onSentPacket(PacketOutputEvent event)
	{
		if(sendingMove || MC.player == null
			|| !(event.getPacket() instanceof ServerboundMovePlayerPacket))
			return;

		if(MC.player.isFallFlying()
			&& MC.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA))
			return;

		lagCounter++;
		if(lagCounter <= limitPerSecond.getValueI())
			return;

		event.cancel();
		if(printWhenLimited.isChecked() && !warned)
		{
			warned = true;
			ChatUtils.warning("Position packet limit reached, throttling.");
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

		Vec3 serverPos = new Vec3(packet.getX(), packet.getY(), packet.getZ());
		Vec3 playerPos = MC.player.position();
		if(playerPos.distanceTo(serverPos) > range.getValue())
			return;

		event.cancel();

		MC.getConnection()
			.send(new ServerboundAcceptTeleportationPacket(packet.getId()));

		if(!antiPull.isChecked())
			return;

		if(!allowVoid.isChecked() && serverPos.y < MC.level.getMinBuildHeight())
			return;

		sendingMove = true;
		try
		{
			MC.getConnection()
				.send(new ServerboundMovePlayerPacket.Pos(playerPos.x,
					playerPos.y, playerPos.z, MC.player.onGround()));
		}finally
		{
			sendingMove = false;
		}

		MC.player.setPos(playerPos.x, playerPos.y, playerPos.z);
		lagCounter++;
	}
}
