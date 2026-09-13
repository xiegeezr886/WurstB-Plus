/*
 * Copyright (c) 2026 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.state.GuiRenderState;

/**
 * Forge keeps {@code GuiGraphics.guiRenderState} private (NeoForge widens it
 * through its own access transformer), so this accessor mixin is the
 * loader-neutral way to reach it at compile time.
 */
@Mixin(GuiGraphics.class)
public interface GuiGraphicsAccessor
{
	@Accessor("guiRenderState")
	GuiRenderState getGuiRenderState();
}
