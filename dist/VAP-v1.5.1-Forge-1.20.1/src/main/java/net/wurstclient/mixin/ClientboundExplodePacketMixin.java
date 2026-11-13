/*
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixin;

import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientboundExplodePacket.class)
public interface ClientboundExplodePacketMixin
{
	@Mutable
	@Accessor("knockbackX")
	void wurst_setKnockbackX(float knockbackX);

	@Mutable
	@Accessor("knockbackY")
	void wurst_setKnockbackY(float knockbackY);

	@Mutable
	@Accessor("knockbackZ")
	void wurst_setKnockbackZ(float knockbackZ);
}
