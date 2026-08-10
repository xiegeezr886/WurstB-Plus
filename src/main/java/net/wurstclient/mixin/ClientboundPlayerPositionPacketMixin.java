/*
 * Copyright (c) 2015-2026 CCBlueX
 * Copyright (c) 2025-2026 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;

@Mixin(ClientboundPlayerPositionPacket.class)
public interface ClientboundPlayerPositionPacketMixin
{
	@Mutable
	@Accessor("yRot")
	void wurst_setYRot(float yaw);

	@Mutable
	@Accessor("xRot")
	void wurst_setXRot(float pitch);
}
