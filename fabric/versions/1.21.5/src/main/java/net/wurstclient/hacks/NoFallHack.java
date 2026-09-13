/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 * Copyright (c) 2015-2026 CCBlueX
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.StatusOnly;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PacketOutputListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.mixin.ServerboundMovePlayerPacketMixin;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

@SearchTags({"no fall", "fall damage", "ground spoof"})
public final class NoFallHack extends Hack
	implements UpdateListener, PacketOutputListener
{
	private final EnumSetting<Mode> mode = new EnumSetting<>("Mode",
		"\u00a7lOnGround\u00a7r - Sends a minimal grounded packet.\n"
			+ "\u00a7lPosition\u00a7r - Sends position and grounded state.\n"
			+ "\u00a7lSmart\u00a7r - Uses a conservative falling threshold.\n"
			+ "\u00a7lGroundSpoof\u00a7r - Rewrites the next movement packet.",
		Mode.values(), Mode.SMART);

	private final SliderSetting triggerDistance = new SliderSetting(
		"Trigger distance", "Fall distance before NoFall activates.", 2.5,
		0.5, 8, 0.1, ValueDisplay.DECIMAL.withSuffix(" blocks"));

	private final CheckboxSetting resetDistance = new CheckboxSetting(
		"Reset fall distance", "Resets the local fall distance after spoofing.",
		true);

	private final CheckboxSetting allowElytra = new CheckboxSetting(
		"Allow elytra", "Allows NoFall while using an elytra.", false);

	private ServerboundMovePlayerPacket pendingResetPacket;

	public NoFallHack()
	{
		super("NoFall");
		setCategory(Category.MOVEMENT);
		addSetting(mode);
		addSetting(triggerDistance);
		addSetting(resetDistance);
		addSetting(allowElytra);
	}

	@Override
	public String getRenderName()
	{
		return getName() + " [" + mode.getSelected() + "]";
	}

	@Override
	protected void onEnable()
	{
		WURST.getHax().antiHungerHack.setEnabled(false);
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(PacketOutputListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(PacketOutputListener.class, this);
		pendingResetPacket = null;
	}

	@Override
	public void onUpdate()
	{
		LocalPlayer player = MC.player;
		if(!shouldProtect(player) || mode.getSelected() == Mode.GROUND_SPOOF)
			return;

		ServerboundMovePlayerPacket packet = switch(mode.getSelected())
		{
			case ON_GROUND, SMART -> new StatusOnly(true, false);
			case POSITION -> new Pos(player.getX(), player.getY(), player.getZ(),
				true, false);
			case GROUND_SPOOF -> throw new IllegalStateException();
		};
		sendAndReset(player, packet);
	}

	@Override
	public void onSentPacket(PacketOutputEvent event)
	{
		if(!(event.getPacket() instanceof ServerboundMovePlayerPacket packet))
			return;

		LocalPlayer player = MC.player;
		if(packet == pendingResetPacket)
		{
			scheduleReset(event, player, packet);
			return;
		}
		if(mode.getSelected() != Mode.GROUND_SPOOF || !shouldProtect(player))
			return;

		((ServerboundMovePlayerPacketMixin)(Object)packet)
			.wurst_setOnGround(true);
		scheduleReset(event, player, packet);
	}

	private void sendAndReset(LocalPlayer player,
		ServerboundMovePlayerPacket packet)
	{
		ServerboundMovePlayerPacket previous = pendingResetPacket;
		pendingResetPacket = packet;
		try
		{
			player.connection.send(packet);
		}finally
		{
			pendingResetPacket = previous;
		}
	}

	private void scheduleReset(PacketOutputEvent event, LocalPlayer player,
		ServerboundMovePlayerPacket packet)
	{
		if(!resetDistance.isChecked() || player == null)
			return;

		event.runAfterSend(() -> {
			if(event.getPacket() != packet
				|| !((ServerboundMovePlayerPacketMixin)(Object)packet)
					.wurst_isOnGround())
				return;
			player.fallDistance = 0;
		});
	}

	private boolean shouldProtect(LocalPlayer player)
	{
		if(player == null || player.isCreative() || player.isSpectator()
			|| player.isPassenger() || player.onClimbable()
			|| player.isInWater() || player.isInLava()
			|| player.isNoGravity())
			return false;
		if(player.isFallFlying() && !allowElytra.isChecked())
			return false;

		double threshold = triggerDistance.getValue();
		if(mode.getSelected() == Mode.SMART)
			threshold = Math.max(threshold, 2.5);
		return player.getDeltaMovement().y < 0
			&& player.fallDistance - player.getDeltaMovement().y >= threshold;
	}

	private enum Mode
	{
		ON_GROUND("OnGround"),
		POSITION("Position"),
		SMART("Smart"),
		GROUND_SPOOF("GroundSpoof");

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
