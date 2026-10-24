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

import net.minecraft.network.protocol.game.ClientboundPlayerLookAtPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket;
import net.minecraft.world.entity.PositionMoveRotation;
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

		// These two packets only change rotation, so they can be dropped
		// outright without desyncing the player's position.
		if(event.getPacket() instanceof ClientboundPlayerLookAtPacket
			|| event.getPacket() instanceof ClientboundPlayerRotationPacket)
		{
			event.cancel();
			return;
		}

		if(!(event.getPacket() instanceof ClientboundPlayerPositionPacket packet))
			return;

		// Position packets also carry teleports, so they cannot be cancelled.
		// Remember the current view instead and restore it after the packet
		// has been applied.
		PositionMoveRotation current = PositionMoveRotation.of(MC.player);
		PositionMoveRotation result = PositionMoveRotation.calculateAbsolute(
			current, packet.change(), packet.relatives());

		boolean yawChanged =
			Math.abs(wrapDegrees(result.yRot() - current.yRot())) > 1.0E-4F;
		boolean pitchChanged =
			Math.abs(wrapDegrees(result.xRot() - current.xRot())) > 1.0E-4F;
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

		MC.player.setOldRot();
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
