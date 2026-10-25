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
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.screens.OptionsSubScreen;
import net.minecraft.client.gui.screens.Screen;
import net.wurstclient.WurstClient;
import net.wurstclient.gui.ButtonStyleRenderer;
import net.wurstclient.gui.MainMenuBackground;

/**
 * Options screens draw their list (and therefore all sliders, checkboxes and
 * cycle buttons) before {@code Screen.render()} runs. Start the control pass
 * here, hide the list's own dark panel, and use the shared menu background.
 */
@Mixin(OptionsSubScreen.class)
public abstract class OptionsSubScreenMixin
{
	@Inject(at = @At("HEAD"), method = "basicListRender")
	private void wurst$prepareOptionsList(GuiGraphics graphics, OptionsList list,
		int mouseX, int mouseY, float partialTicks, CallbackInfo ci)
	{
		list.setRenderBackground(false);
		ButtonStyleRenderer.beginScreen(graphics, (Screen)(Object)this);
	}

	@Redirect(at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/screens/OptionsSubScreen;renderBackground(Lnet/minecraft/client/gui/GuiGraphics;)V"),
		method = "basicListRender")
	private void wurst$replaceOptionsBackground(OptionsSubScreen screen,
		GuiGraphics graphics)
	{
		if(WurstClient.INSTANCE.getHax().noBackgroundHack
			.shouldCancelBackground(screen))
			return;
		MainMenuBackground.render(graphics, screen);
	}
}
