/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
package net.wurstclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSelectionList;

/**
 * Lets the shared main-menu background show through selection lists instead of
 * painting the vanilla dark panel behind every list.
 */
@Mixin(AbstractSelectionList.class)
public abstract class AbstractSelectionListMixin
{
	@Inject(at = @At("HEAD"),
		method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V")
	private void wurst$transparentBackground(GuiGraphics graphics, int mouseX,
		int mouseY, float partialTicks, CallbackInfo ci)
	{
		AbstractSelectionList<?> list =
			(AbstractSelectionList<?>)(Object)this;
		list.setRenderBackground(false);
		// The vanilla top/bottom fade overlays read as dirt bands on top of
		// the shared menu background.
		list.setRenderTopAndBottom(false);
	}
}
