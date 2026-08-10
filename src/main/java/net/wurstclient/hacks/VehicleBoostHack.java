/*
 * Copyright (c) 2015-2026 CCBlueX
 * Copyright (c) 2025-2026 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.client.player.LocalPlayer;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.VehicleBoostPolicy;

@SearchTags({"vehicle boost", "dismount boost"})
public final class VehicleBoostHack extends Hack implements UpdateListener
{
	private final SliderSetting horizontalSpeed = new SliderSetting(
		"Horizontal speed", 2, 0.1, 10, 0.1, ValueDisplay.DECIMAL);
	private final SliderSetting verticalSpeed = new SliderSetting(
		"Vertical speed", 1, 0.1, 5, 0.1, ValueDisplay.DECIMAL);

	private LocalPlayer trackedPlayer;
	private boolean wasPassenger;

	public VehicleBoostHack()
	{
		super("VehicleBoost");
		setCategory(Category.MOVEMENT);
		addSetting(horizontalSpeed);
		addSetting(verticalSpeed);
	}

	@Override
	protected void onEnable()
	{
		trackedPlayer = MC.player;
		wasPassenger = trackedPlayer != null && trackedPlayer.isPassenger();
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		trackedPlayer = null;
		wasPassenger = false;
	}

	@Override
	public void onUpdate()
	{
		LocalPlayer player = MC.player;
		if(player != trackedPlayer)
		{
			trackedPlayer = player;
			wasPassenger = player != null && player.isPassenger();
			return;
		}
		if(player == null)
			return;

		boolean passenger = player.isPassenger();
		if(wasPassenger && !passenger)
			player.setDeltaMovement(VehicleBoostPolicy.velocity(player.getYRot(),
				horizontalSpeed.getValue(), verticalSpeed.getValue()));
		wasPassenger = passenger;
	}
}
