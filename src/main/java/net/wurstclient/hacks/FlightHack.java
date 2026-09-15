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
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.hack.HackConflictGroup;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.hacks.flight.BoostFlightMode;
import net.wurstclient.hacks.flight.FlightMode;
import net.wurstclient.hacks.flight.RocketFlightMode;
import net.wurstclient.hacks.flight.VanillaFlightMode;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

@SearchTags({"FlyHack", "fly hack", "flying"})
public final class FlightHack extends Hack implements UpdateListener
{
	public final SliderSetting horizontalSpeed = new SliderSetting(
		"Horizontal Speed", 1, 0.05, 50, 0.05, ValueDisplay.DECIMAL);

	public final SliderSetting verticalSpeed = new SliderSetting(
		"Vertical Speed", 1, 0.05, 20, 0.05, ValueDisplay.DECIMAL);

	private final CheckboxSetting slowSneaking = new CheckboxSetting(
		"Slow sneaking", "Reduces horizontal speed while sneaking.", true);

	private final EnumSetting<Mode> mode = new EnumSetting<>("Mode",
		"\u00a7lVanilla\u00a7r - Direct directional velocity flight.\n"
			+ "\u00a7lBoost\u00a7r - Accelerates smoothly up to the speed limit.\n"
			+ "\u00a7lRocket\u00a7r - Uses stronger vertical controls.",
		Mode.values(), Mode.VANILLA);

	private final SliderSetting glide = new SliderSetting("Glide",
		"Vertical motion while neither jump nor sneak is held.", 0, -1, 1,
		0.01, ValueDisplay.DECIMAL);

	private final CheckboxSetting antiKick = new CheckboxSetting("Anti-Kick",
		"Periodically applies a small downward movement.", false);

	private final SliderSetting antiKickInterval = new SliderSetting(
		"Anti-Kick Interval", "Ticks between anti-kick pulses.", 40, 5, 80,
		1, ValueDisplay.INTEGER.withSuffix(" ticks"));

	private final SliderSetting antiKickDistance = new SliderSetting(
		"Anti-Kick Distance", "Downward velocity used by anti-kick.", 0.04,
		0.01, 0.2, 0.001, ValueDisplay.DECIMAL.withSuffix("m"));

	private int tickCounter;
	private boolean previousFlying;
	private LocalPlayer trackedPlayer;

	public FlightHack()
	{
		super("Flight");
		setCategory(Category.MOVEMENT);
		addConflictGroup(HackConflictGroup.MOVEMENT_CONTROL);
		addSetting(horizontalSpeed);
		addSetting(verticalSpeed);
		addSetting(mode);
		addSetting(glide);
		addSetting(slowSneaking);
		addSetting(antiKick);
		addSetting(antiKickInterval);
		addSetting(antiKickDistance);
	}

	@Override
	public String getRenderName()
	{
		return getName() + " [" + mode.getSelected() + "]";
	}

	@Override
	protected void onEnable()
	{
		tickCounter = 0;
		trackedPlayer = null;
		trackPlayer(MC.player);
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		restoreTrackedPlayer();
	}

	@Override
	public void onUpdate()
	{
		LocalPlayer player = MC.player;
		if(player == null)
			return;
		if(player.isSpectator())
		{
			if(trackedPlayer == player)
				trackedPlayer = null;
			else
				restoreTrackedPlayer();
			return;
		}
		if(player.isPassenger() || player.isFallFlying())
			return;
		trackPlayer(player);

		player.getAbilities().flying = false;
		float forward = player.input.forwardImpulse;
		float sideways = player.input.leftImpulse;
		double horizontal = horizontalSpeed.getValue();
		if(slowSneaking.isChecked() && MC.options.keyShift.isDown())
			horizontal *= 0.3;

		FlightMode selectedMode = mode.getSelected().impl();
		Vec3 movement = selectedMode.getHorizontalMovement(
			player.getDeltaMovement(), forward, sideways, player.getYRot(),
			horizontal);

		double vertical = verticalSpeed.getValue()
			* selectedMode.getVerticalMultiplier();
		double y = MC.options.keyJump.isDown() ? vertical
			: MC.options.keyShift.isDown() ? -vertical : glide.getValue();
		boolean verticalInput = MC.options.keyJump.isDown()
			|| MC.options.keyShift.isDown();
		if(verticalInput)
			tickCounter = 0;
		else if(antiKick.isChecked()
			&& ++tickCounter >= antiKickInterval.getValueI())
		{
			y = -antiKickDistance.getValue();
			tickCounter = 0;
		}

		player.setDeltaMovement(movement.x, y, movement.z);
		player.fallDistance = 0;
	}

	private void trackPlayer(LocalPlayer player)
	{
		if(player == null || player == trackedPlayer)
			return;

		restoreTrackedPlayer();
		trackedPlayer = player;
		previousFlying = player.getAbilities().flying;
	}

	private void restoreTrackedPlayer()
	{
		if(trackedPlayer != null)
			trackedPlayer.getAbilities().flying = previousFlying;
		trackedPlayer = null;
	}

	private enum Mode
	{
		VANILLA("Vanilla", new VanillaFlightMode()),
		BOOST("Boost", new BoostFlightMode()),
		ROCKET("Rocket", new RocketFlightMode());

		private final String name;
		private final FlightMode impl;

		Mode(String name, FlightMode impl)
		{
			this.name = name;
			this.impl = impl;
		}

		public FlightMode impl()
		{
			return impl;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}
}
