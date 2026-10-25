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
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.wurstclient.WurstClient;
import net.wurstclient.gui.MainMenuBackground;

/**
 * Container screens draw the world directly and never call
 * {@code Screen.renderBackground}. Paint the shared menu background behind the
 * container panel as well.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin
{
	@Inject(at = @At("HEAD"),
		method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V")
	private void wurst$drawMenuBackground(GuiGraphics graphics, int mouseX,
		int mouseY, float partialTicks, CallbackInfo ci)
	{
		Screen screen = (Screen)(Object)this;
		if(WurstClient.INSTANCE.getHax().noBackgroundHack
			.shouldCancelBackground(screen))
			return;
		MainMenuBackground.render(graphics, screen);
	}
}
