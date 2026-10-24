/*
 * Copyright (c) 2025-2026 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;

/**
 * Calls the protected slot click handler from AutoSteal without widening it in
 * the access transformer. Making the superclass method public breaks
 * ForgeGradle's recompilation, because vanilla subclasses override it as
 * protected.
 */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenInvoker
{
	@Invoker("slotClicked")
	void wurst$slotClicked(Slot slot, int slotId, int mouseButton,
		ClickType clickType);
}
