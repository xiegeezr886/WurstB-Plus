/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from InvincibleMachineGun (GPL-3.0).
package net.wurstclient.hacks;

import java.util.Random;

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

/**
 * Periodically nudges the player downwards while flying so the server keeps
 * receiving movement that looks like gravity.
 */
@SearchTags({"flight anti kick", "anti kick", "flight", "fly bypass"})
public final class FlightAntiKickHack extends Hack implements UpdateListener
{
	private final EnumSetting<Mode> mode = new EnumSetting<>("Mode",
		"How the anti-kick pulse is applied.", Mode.values(), Mode.DIP);

	private final SliderSetting delay = new SliderSetting("Delay",
		"Ticks between anti-kick pulses.", 25, 5, 80, 1,
		ValueDisplay.INTEGER.withSuffix(" ticks"));

	private final SliderSetting dipDistance = new SliderSetting(
		"Dip distance", "How far down each pulse moves you.", 0.04, 0.01, 0.1,
		0.001, ValueDisplay.DECIMAL.withSuffix("m"));

	private final CheckboxSetting onlyIfActive = new CheckboxSetting(
		"Only with Flight", "Only runs while the Flight hack is enabled.",
		true);

	private final CheckboxSetting useRandom = new CheckboxSetting(
		"Random delay", "Randomizes the delay of every pulse.", true);

	private final SliderSetting randomRange = new SliderSetting(
		"Random range", "Maximum random ticks added or removed from the delay.",
		5, 1, 20, 1, ValueDisplay.INTEGER);

	private final Random random = new Random();
	private int timer;
	private int currentDelay;

	public FlightAntiKickHack()
	{
		super("FlightAntiKick");
		setCategory(Category.MOVEMENT);
		addSetting(mode);
		addSetting(delay);
		addSetting(dipDistance);
		addSetting(onlyIfActive);
		addSetting(useRandom);
		addSetting(randomRange);
		randomRange.visibleWhen(useRandom::isChecked);
	}

	@Override
	public String getRenderName()
	{
		return getName() + " [" + mode.getSelected() + "]";
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		timer = 0;
		resetDelay();
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.player.connection == null)
			return;

		if(onlyIfActive.isChecked() && !WURST.getHax().flightHack.isEnabled())
			return;

		if(MC.player.onGround())
			return;

		if(mode.getSelected() == Mode.GRAVITY)
		{
			Vec3 velocity = MC.player.getDeltaMovement();
			if(velocity.y >= 0)
				MC.player.setDeltaMovement(velocity.x, -0.005, velocity.z);
			return;
		}

		if(++timer < currentDelay)
			return;

		executePulse();
		timer = 0;
		resetDelay();
	}

	private void executePulse()
	{
		double dip = dipDistance.getValue();

		if(mode.getSelected() == Mode.DIP)
		{
			Vec3 velocity = MC.player.getDeltaMovement();
			MC.player.setDeltaMovement(velocity.x, -dip, velocity.z);
			return;
		}

		MC.player.connection.send(new ServerboundMovePlayerPacket.Pos(
			MC.player.getX(), MC.player.getY() - dip, MC.player.getZ(), false,
			MC.player.horizontalCollision));
	}

	private void resetDelay()
	{
		if(!useRandom.isChecked())
		{
			currentDelay = delay.getValueI();
			return;
		}

		int range = randomRange.getValueI();
		currentDelay =
			delay.getValueI() + random.nextInt(range * 2 + 1) - range;
		currentDelay = Math.max(1, currentDelay);
	}

	public enum Mode
	{
		DIP("Physical dip"),
		PACKET("Packet dip"),
		GRAVITY("Vertical gravity");

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
