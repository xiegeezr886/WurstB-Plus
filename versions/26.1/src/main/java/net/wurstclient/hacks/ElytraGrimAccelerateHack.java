/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from LeavesHack (AGPL-3.0).
package net.wurstclient.hacks;

import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.PosRot;
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
import net.wurstclient.util.PacketUtils;

/**
 * Grim-friendly elytra acceleration. Below the minimum speed, movement
 * packets are sent with a simulated Y offset so the server keeps applying
 * elytra physics, and server velocity updates that would slow the player
 * down are dropped.
 */
@SearchTags({"elytra grim", "grim accelerate", "elytra accelerate",
	"elytra speed"})
public final class ElytraGrimAccelerateHack extends Hack
	implements UpdateListener, PacketOutputListener, PacketInputListener
{
	private final SliderSetting maxVelocity = new SliderSetting(
		"Max velocity", "Stop accelerating above this horizontal speed.", 4, 0,
		10, 0.1, ValueDisplay.DECIMAL);

	private final SliderSetting minVelocity = new SliderSetting(
		"Min velocity", "Start accelerating below this horizontal speed.", 3.6,
		0, 10, 0.1, ValueDisplay.DECIMAL);

	private final EnumSetting<Mode> mode = new EnumSetting<>("Mode",
		"\u00a7lSimulation\u00a7r - Sends a simulated Y offset.\n\n"
			+ "\u00a7lOff\u00a7r - Only filters velocity packets.",
		Mode.values(), Mode.SIMULATION);

	private final CheckboxSetting filterVelocity = new CheckboxSetting(
		"Filter velocity",
		"Cancel server velocity updates that are slowing you down.", true);

	private boolean working;
	private Vec3 velocity = Vec3.ZERO;
	private int tickCounter;

	public ElytraGrimAccelerateHack()
	{
		super("ElytraGrimAccelerate");
		setCategory(Category.MOVEMENT);
		addSetting(maxVelocity);
		addSetting(minVelocity);
		addSetting(mode);
		addSetting(filterVelocity);
	}

	@Override
	protected void onEnable()
	{
		working = false;
		velocity = Vec3.ZERO;
		tickCounter = 0;
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(PacketOutputListener.class, this);
		EVENTS.add(PacketInputListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(PacketOutputListener.class, this);
		EVENTS.remove(PacketInputListener.class, this);
		working = false;
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null)
			return;

		tickCounter++;

		if(!MC.player.isFallFlying())
		{
			working = false;
			return;
		}

		velocity = MC.player.getDeltaMovement();
		double horizontal = horizontalSpeed(velocity);
		if(horizontal < 0.01)
			return;

		if(mode.getSelected() == Mode.SIMULATION && !working
			&& horizontal < minVelocity.getValue())
			working = true;

		if(horizontal > maxVelocity.getValue())
			working = false;
	}

	@Override
	public void onSentPacket(PacketOutputEvent event)
	{
		if(MC.player == null || !MC.player.isFallFlying()
			|| mode.getSelected() != Mode.SIMULATION || !working)
			return;

		if(!(event.getPacket() instanceof ServerboundMovePlayerPacket packet))
			return;

		// Only position-carrying packets can be adjusted. Upgrading a pure
		// rotation packet would inject a bogus position of 0,0,0.
		if(!(packet instanceof Pos) && !(packet instanceof PosRot))
			return;

		if(horizontalSpeed(velocity) >= minVelocity.getValue())
			return;

		double fakeY = packet.getY(0) + 2.5 * ((tickCounter % 3) + 1);
		event.setPacket(PacketUtils.modifyPosition(packet, packet.getX(0),
			fakeY, packet.getZ(0)));
	}

	@Override
	public void onReceivedPacket(PacketInputEvent event)
	{
		if(MC.player == null)
			return;

		// The server teleported us back; stop accelerating until the next
		// low-speed window.
		if(event.getPacket() instanceof ClientboundPlayerPositionPacket)
		{
			working = false;
			return;
		}

		if(!(event.getPacket() instanceof ClientboundSetEntityMotionPacket packet)
			|| packet.id() != MC.player.getId()
			|| !filterVelocity.isChecked() || !MC.player.isFallFlying())
			return;

		Vec3 incoming = packet.movement();
		double horizontal = incoming.x * incoming.x + incoming.z * incoming.z;

		// A near-zero horizontal update is the server trying to stop us.
		if(horizontal < 1.0E-4)
		{
			event.cancel();
			return;
		}

		// An update opposing our movement is the server slowing us down.
		if(velocity.x * incoming.x + velocity.z * incoming.z < 0)
			event.cancel();
	}

	private double horizontalSpeed(Vec3 vec)
	{
		return Math.sqrt(vec.x * vec.x + vec.z * vec.z);
	}

	private enum Mode
	{
		SIMULATION("Simulation"),
		OFF("Off");

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
