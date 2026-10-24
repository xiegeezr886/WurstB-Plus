/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 *
 * Ported from Meteor Client (GPL-3.0).
 */
package net.wurstclient.hacks;

import java.util.Set;

import net.minecraft.network.protocol.game.ClientboundPlayerLookAtPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.entity.RelativeMovement;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PacketInputListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;

@SearchTags({"no rotate", "norotate", "server rotation", "anti rotate"})
public final class NoRotateHack extends Hack
	implements PacketInputListener, UpdateListener
{
	private final CheckboxSetting preserveYaw = new CheckboxSetting(
		"Preserve yaw", "Keeps your yaw when the server rotates you.", true);

	private final CheckboxSetting preservePitch = new CheckboxSetting(
		"Preserve pitch", "Keeps your pitch when the server rotates you.", true);

	private boolean restorePending;
	private float savedYaw;
	private float savedPitch;

	public NoRotateHack()
	{
		super("NoRotate");
		setCategory(Category.MOVEMENT);
		addSetting(preserveYaw);
		addSetting(preservePitch);
	}

	@Override
	protected void onEnable()
	{
		restorePending = false;
		EVENTS.add(PacketInputListener.class, this);
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(PacketInputListener.class, this);
		EVENTS.remove(UpdateListener.class, this);
		restorePending = false;
	}

	@Override
	public void onReceivedPacket(PacketInputEvent event)
	{
		if(MC.player == null
			|| (!preserveYaw.isChecked() && !preservePitch.isChecked()))
			return;

		// This packet only changes rotation, so it can be dropped outright
		// without desyncing the player's position.
		if(event.getPacket() instanceof ClientboundPlayerLookAtPacket)
		{
			event.cancel();
			return;
		}

		if(!(event.getPacket() instanceof ClientboundPlayerPositionPacket packet))
			return;

		// Position packets also carry teleports, so they cannot be cancelled.
		// Remember the current view instead and restore it after the packet
		// has been applied.
		Set<RelativeMovement> relatives = packet.getRelativeArguments();
		float baseYaw = relatives.contains(RelativeMovement.Y_ROT)
			? MC.player.getYRot() : 0;
		float basePitch = relatives.contains(RelativeMovement.X_ROT)
			? MC.player.getXRot() : 0;
		float resultYaw = baseYaw + packet.getYRot();
		float resultPitch = basePitch + packet.getXRot();

		boolean yawChanged = Math
			.abs(wrapDegrees(resultYaw - MC.player.getYRot())) > 1.0E-4F;
		boolean pitchChanged = Math
			.abs(wrapDegrees(resultPitch - MC.player.getXRot())) > 1.0E-4F;
		if(!yawChanged && !pitchChanged)
			return;

		if(restorePending)
			return;

		savedYaw = MC.player.getYRot();
		savedPitch = MC.player.getXRot();
		restorePending = true;
	}

	@Override
	public void onUpdate()
	{
		if(!restorePending || MC.player == null)
			return;

		restorePending = false;

		if(preserveYaw.isChecked())
			MC.player.setYRot(savedYaw);
		if(preservePitch.isChecked())
			MC.player.setXRot(savedPitch);

		MC.player.setOldPosAndRot();
	}

	private static float wrapDegrees(float degrees)
	{
		degrees %= 360.0F;

		if(degrees >= 180.0F)
			degrees -= 360.0F;
		else if(degrees < -180.0F)
			degrees += 360.0F;

		return degrees;
	}
}
