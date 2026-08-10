/*
 * Copyright (c) 2015-2026 CCBlueX
 * Copyright (c) 2025-2026 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.Set;

import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.entity.RelativeMovement;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PacketInputListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.mixin.ClientboundPlayerPositionPacketMixin;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.util.RotationCorrectionPolicy;

@SearchTags({"no rotate", "NoRotateSet", "server rotation"})
public final class NoRotateHack extends Hack implements PacketInputListener
{
	private final CheckboxSetting preserveYaw =
		new CheckboxSetting("Preserve yaw", true);
	private final CheckboxSetting preservePitch =
		new CheckboxSetting("Preserve pitch", true);

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
		EVENTS.add(PacketInputListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(PacketInputListener.class, this);
	}

	@Override
	public void onReceivedPacket(PacketInputEvent event)
	{
		if(MC.player == null
			|| !(event.getPacket() instanceof ClientboundPlayerPositionPacket packet))
			return;

		Set<RelativeMovement> relatives = packet.getRelativeArguments();
		ClientboundPlayerPositionPacketMixin accessor =
			(ClientboundPlayerPositionPacketMixin)(Object)packet;
		if(preserveYaw.isChecked())
			accessor.wurst_setYRot(RotationCorrectionPolicy.packetRotation(
				MC.player.getYRot(), relatives.contains(RelativeMovement.Y_ROT)));
		if(preservePitch.isChecked())
			accessor.wurst_setXRot(RotationCorrectionPolicy.packetRotation(
				MC.player.getXRot(), relatives.contains(RelativeMovement.X_ROT)));
	}
}
